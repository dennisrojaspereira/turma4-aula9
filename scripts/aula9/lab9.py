#!/usr/bin/env python3
"""Aula 9 - Cade o Pix? Simulador do incidente de observabilidade.

Este arquivo faz o papel do "ambiente de producao" do laboratorio:

  serve             sobe o servidor HTTP (health, /payments, retry, reconciliacao)
  up [porta]        sobe o servidor em background e grava o pid
  stop              derruba o servidor
  reset             volta o laboratorio ao estado inicial (incidente 1)
  status            mostra o estado atual do laboratorio
  logs <txid>       logs estruturados de todos os servicos para uma transacao
  correlation <id>  todos os eventos de um correlation_id, em ordem
  trace <txid>      trace distribuido (waterfall) da transacao
  metrics           snapshot do dashboard (RED / USE / Golden Signals)
  retry <txid> [--executar | --idempotency-key CHAVE]
  reconcile <txid>  reconciliacao contra ledger e PSP
  dlq [replay]      mostra (ou reprocessa) a Dead Letter Queue
  incident2 [on|off] liga o segundo incidente (desafio final)

Sem dependencias externas. Mesmo espirito do scripts/painel.py.
A saida usa apenas ASCII para funcionar em qualquer console Windows.
"""

import json
import os
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.path.dirname(os.path.abspath(__file__))
STATE_FILE = os.path.join(BASE, ".lab9-state.json")
PID_FILE = os.path.join(BASE, ".lab9-server.pid")
LOG_FILE = os.path.join(BASE, ".lab9-server.log")
PORT = int(os.environ.get("TECHPIX_LAB9_PORT", "8080"))

# ---------------------------------------------------------------------------
# Estado
# ---------------------------------------------------------------------------

INITIAL_STATE = {
    "incident": 1,
    "payments": {
        "PIX-928371": {
            "transaction_id": "PIX-928371",
            "amount": 250.00,
            "payer_account": "ACC-1001",
            "payee_account": "ACC-2044",
            "created_at": "2026-10-02T21:03:01",
            "status": "UNKNOWN",
            "correlation_id": "abc123",
            "trace_id": "4f2a9c01d7e3b8a6",
            "ledger": "DEBITED",
            "psp_truth": "COMPLETED",
            "psp_e2e_id": "E20261002210300017",
            "psp_executions": 1,
            "reconciled": False,
        },
        "PIX-554219": {
            "transaction_id": "PIX-554219",
            "amount": 180.00,
            "payer_account": "ACC-3310",
            "payee_account": "ACC-1288",
            "created_at": "2026-10-02T21:41:02",
            "status": "APPROVED",
            "correlation_id": "def456",
            "trace_id": "9b1cf3aa21e07c44",
            "ledger": "DEBITED",
            "psp_truth": "COMPLETED",
            "psp_e2e_id": "E20261002214100442",
            "psp_executions": 1,
            "reconciled": False,
            "total_ms": 3940,
        },
    },
    "sandbox_retries": 0,
    "dlq_armed": False,     # a mensagem entra na DLQ depois da reconciliacao
    "dlq_replayed": False,
}


def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, encoding="utf-8") as f:
                return json.load(f)
        except (ValueError, OSError):
            pass
    return json.loads(json.dumps(INITIAL_STATE))


def save_state(state):
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)


# ---------------------------------------------------------------------------
# Evidencias - incidente 1 (deploy v1.13.4 derruba o pool do psp-adapter)
# ---------------------------------------------------------------------------

LOGS_1 = [
    # transacao saudavel ANTES do deploy v1.13.4 (21:00)
    {"ts": "2026-10-02T20:58:10.221", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-927754", "correlation_id": "q7m2z9", "trace_id": "77aa0b3c91d2e4f0",
     "message": "pagamento recebido", "status": "PROCESSING", "amount": 95.00},
    {"ts": "2026-10-02T20:58:10.530", "service": "psp-adapter", "level": "INFO",
     "transaction_id": "PIX-927754", "correlation_id": "q7m2z9", "trace_id": "77aa0b3c91d2e4f0",
     "message": "transferencia confirmada pelo PSP", "duration_ms": 182, "pool_active": 4, "pool_max": 20},
    {"ts": "2026-10-02T20:58:10.610", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-927754", "correlation_id": "q7m2z9", "trace_id": "77aa0b3c91d2e4f0",
     "message": "pagamento concluido", "status": "APPROVED", "total_ms": 389},

    # transacao DEPOIS do deploy: ainda conclui, mas ja sofre com o pool
    {"ts": "2026-10-02T21:02:41.102", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-928366", "correlation_id": "x4t8w2", "trace_id": "c3d19e7b50f8a2d4",
     "message": "pagamento recebido", "status": "PROCESSING", "amount": 1200.00},
    {"ts": "2026-10-02T21:02:43.310", "service": "psp-adapter", "level": "WARN",
     "transaction_id": "PIX-928366", "correlation_id": "x4t8w2", "trace_id": "c3d19e7b50f8a2d4",
     "message": "pool de conexoes esgotado, requisicao aguardando", "pool_active": 2, "pool_max": 2,
     "pool_pending": 9, "wait_ms": 2104},
    {"ts": "2026-10-02T21:02:45.440", "service": "psp-adapter", "level": "INFO",
     "transaction_id": "PIX-928366", "correlation_id": "x4t8w2", "trace_id": "c3d19e7b50f8a2d4",
     "message": "transferencia confirmada pelo PSP", "duration_ms": 4310, "pool_active": 2, "pool_max": 2},
    {"ts": "2026-10-02T21:02:45.520", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-928366", "correlation_id": "x4t8w2", "trace_id": "c3d19e7b50f8a2d4",
     "message": "pagamento concluido", "status": "APPROVED", "total_ms": 4418},

    # a transacao do chamado: PIX-928371
    {"ts": "2026-10-02T21:03:01.102", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "pagamento recebido", "status": "PROCESSING", "amount": 250.00,
     "payer_account": "ACC-1001", "payee_account": "ACC-2044"},
    {"ts": "2026-10-02T21:03:01.311", "service": "fraud-service", "level": "INFO",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "avaliacao concluida", "decision": "APPROVED", "score": 12,
     "rules_evaluated": 17, "duration_ms": 40},
    {"ts": "2026-10-02T21:03:02.040", "service": "ledger", "level": "INFO",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "lancamento registrado", "entry": "DEBIT", "account": "ACC-1001",
     "amount": 250.00, "status": "DEBITED"},
    {"ts": "2026-10-02T21:03:03.012", "service": "psp-adapter", "level": "WARN",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "pool de conexoes esgotado, requisicao aguardando", "pool_active": 2, "pool_max": 2,
     "pool_pending": 14, "wait_ms": 2874},
    {"ts": "2026-10-02T21:03:08.021", "service": "psp-adapter", "level": "ERROR",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "timeout aguardando resposta do PSP", "timeout_ms": 5000,
     "error": "ReadTimeout", "psp_e2e_id": None},
    {"ts": "2026-10-02T21:03:08.034", "service": "payment-service", "level": "WARN",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "sem confirmacao do PSP; estado indeterminado", "status": "UNKNOWN",
     "reason": "psp-timeout"},
    {"ts": "2026-10-02T21:03:08.102", "service": "notification-service", "level": "INFO",
     "transaction_id": "PIX-928371", "correlation_id": "abc123", "trace_id": "4f2a9c01d7e3b8a6",
     "message": "notificacao adiada ate estado final", "channel": "push",
     "customer_name": "Carlos Andrade", "cpf": "123.456.789-09"},
]

TRACE_1 = r"""
trace_id: 4f2a9c01d7e3b8a6        (transaction_id=PIX-928371, correlation_id=abc123)

POST /payments ................................. 5212 ms   payment-service
  |- fraud.check ...............................   40 ms   fraud-service     OK
  |- ledger.debit ..............................   21 ms   ledger            OK
  '- psp.transfer .............................. 5003 ms   psp-adapter       TIMEOUT
       |- pool.acquire ......................... 2874 ms     <- esperando conexao livre
       '- http.post /transfers ................. 2101 ms     sem resposta (cancelado aos 5000 ms)
"""

METRICS_1 = """
DASHBOARD Tech Pix - janela 20:45 -> 21:15 (02/10/2026)
========================================================================================
hora    pix/s   http_5xx%   cpu_pay   cpu_psp   psp_p95_ms   pool_pending   unknown_total
20:45   15.8    0.1         31%       18%          180            0              0
20:50   16.1    0.1         32%       17%          175            0              0
20:55   15.9    0.2         30%       19%          190            0              0
21:00 * 16.0    0.2         31%       18%          210            2              0
21:05   16.2    0.4         33%       20%         4900           14             11
21:10   15.7    0.5         32%       19%         5000           15             23
21:15   16.0    0.4         31%       18%         4950           16             25
========================================================================================
* 21:00  deploy payment-stack v1.13.4 concluido (annotation do CD)

Outros paineis:
  kafka consumer lag (todos os grupos) ........ 0
  postgres conexoes ativas .................... 14/50 (normal)
  memoria payment-service / psp-adapter ....... estavel
  health checks (todos os servicos) ........... UP

Labels disponiveis nas metricas de pagamento: status, provider, operation
(IDs de transacao NAO sao labels de metrica. Procure IDs em logs e traces.)
"""

DLQ_MESSAGE = {
    "topic": "payment-events.DLQ",
    "original_topic": "payment-events",
    "partition": 1,
    "offset": 48213,
    "key": "ACC-1001",
    "timestamp": "2026-10-02T21:12:41.007",
    "headers": {
        "x-original-consumer-group": "notification-service",
        "x-exception": "DeserializationException: campo 'amount' esperava numero, recebeu string \"250,00\"",
        "x-attempts": "4 (1 entrega + 3 retries com backoff)",
    },
    "payload": {
        "eventId": "7c1d2f0a-5e4b-3a19-9c8d-f06e71b2a455",
        "type": "PaymentStatusChanged",
        "paymentId": "PIX-928371",
        "from": "UNKNOWN",
        "to": "APPROVED",
        "amount": "250,00",
        "occurredAt": "2026-10-02T21:12:40",
    },
}

# ---------------------------------------------------------------------------
# Evidencias - incidente 2 (desafio final: deploy v1.13.5 liga perfil HEAVY)
# ---------------------------------------------------------------------------

LOGS_2 = [
    {"ts": "2026-10-02T21:41:02.090", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-554219", "correlation_id": "def456", "trace_id": "9b1cf3aa21e07c44",
     "message": "pagamento recebido", "status": "PROCESSING", "amount": 180.00,
     "payer_account": "ACC-3310", "payee_account": "ACC-1288"},
    {"ts": "2026-10-02T21:41:04.710", "service": "fraud-service", "level": "WARN",
     "transaction_id": "PIX-554219", "correlation_id": "def456", "trace_id": "9b1cf3aa21e07c44",
     "message": "avaliacao lenta", "decision": "APPROVED", "score": 8,
     "rules_evaluated": 17, "ml_iterations": 2000000, "duration_ms": 2612},
    {"ts": "2026-10-02T21:41:04.760", "service": "ledger", "level": "INFO",
     "transaction_id": "PIX-554219", "correlation_id": "def456", "trace_id": "9b1cf3aa21e07c44",
     "message": "lancamento registrado", "entry": "DEBIT", "account": "ACC-3310",
     "amount": 180.00, "status": "DEBITED"},
    {"ts": "2026-10-02T21:41:05.020", "service": "psp-adapter", "level": "INFO",
     "transaction_id": "PIX-554219", "correlation_id": "def456", "trace_id": "9b1cf3aa21e07c44",
     "message": "transferencia confirmada pelo PSP", "duration_ms": 236,
     "pool_active": 3, "pool_max": 20},
    {"ts": "2026-10-02T21:41:05.980", "service": "payment-service", "level": "INFO",
     "transaction_id": "PIX-554219", "correlation_id": "def456", "trace_id": "9b1cf3aa21e07c44",
     "message": "pagamento concluido", "status": "APPROVED", "total_ms": 3890},
    {"ts": "2026-10-02T21:41:06.100", "service": "fraud-service", "level": "WARN",
     "transaction_id": None, "correlation_id": None, "trace_id": None,
     "message": "consumer atrasado no topico payment-events", "group": "fraud-service",
     "lag": 1800, "tendencia": "crescendo"},
    {"ts": "2026-10-02T21:42:00.000", "service": "reconciliation-job", "level": "WARN",
     "transaction_id": None, "correlation_id": None, "trace_id": None,
     "message": "backlog de reconciliacao crescendo", "pending": 340, "oldest_age_s": 420},
]

TRACE_2 = r"""
trace_id: 9b1cf3aa21e07c44        (transaction_id=PIX-554219, correlation_id=def456)

POST /payments ................................. 3940 ms   payment-service
  |- fraud.check ............................... 2612 ms   fraud-service     OK (lento)
  |    '- rule.ml-risk ......................... 2458 ms     <- 17 regras, ml_iterations=2000000
  |- ledger.debit ..............................   19 ms   ledger            OK
  '- psp.transfer ..............................  236 ms   psp-adapter       OK
"""

METRICS_2 = """
DASHBOARD Tech Pix - janela 21:20 -> 21:45 (02/10/2026)
==========================================================================================
hora    pix/s   fraud_p95_ms   cpu_fraud   kafka_lag   reconc_backlog   psp_p95_ms   unknown
21:20   15.9        62            35%           0             4             185          0
21:25   16.0        60            34%           0             3             190          0
21:30   16.1        63            36%           0             5             180          0
21:35 * 15.8       950            71%         220            40             190          1
21:40   15.9      2600            93%        1100           180             200          2
21:45   15.6      2650            95%        1800           340             195          3
==========================================================================================
* 21:35  deploy payment-stack v1.13.5 concluido (annotation do CD)
         changelog: "fix: PSP_POOL_SIZE 2 -> 20" e "fraud-service: FRAUD_PROFILE=HEAVY"

Outros paineis:
  psp pool_pending ............................ 0 (o problema da noite foi corrigido)
  http_5xx% ................................... 0.2 (baixo)
  cpu payment-service / psp-adapter ........... 31% / 18% (normal)
  postgres .................................... normal
  health checks (todos os servicos) ........... UP
"""


def dataset(state):
    if state["incident"] == 2:
        return {"logs": LOGS_2, "trace": TRACE_2, "metrics": METRICS_2,
                "tx": "PIX-554219"}
    return {"logs": LOGS_1, "trace": TRACE_1, "metrics": METRICS_1,
            "tx": "PIX-928371"}


# ---------------------------------------------------------------------------
# Comandos de evidencia
# ---------------------------------------------------------------------------

def print_log_line(rec):
    clean = {k: v for k, v in rec.items() if v is not None}
    print(json.dumps(clean, ensure_ascii=True))


def cmd_logs(state, txid):
    ds = dataset(state)
    found = [r for r in ds["logs"] if r.get("transaction_id") == txid]
    if not found:
        print("Nenhum log encontrado para transaction_id=%s" % txid)
        print("Dica: confira o id com o suporte, ou procure por correlation_id.")
        return
    print("# logs estruturados | transaction_id=%s | %d eventos em %d servicos"
          % (txid, len(found), len({r["service"] for r in found})))
    print()
    for rec in found:
        print_log_line(rec)


def cmd_correlation(state, cid):
    ds = dataset(state)
    found = sorted([r for r in ds["logs"] if r.get("correlation_id") == cid],
                   key=lambda r: r["ts"])
    if not found:
        print("Nenhum evento para correlation_id=%s" % cid)
        return
    print("# correlation_id=%s | %d eventos" % (cid, len(found)))
    print()
    for rec in found:
        print_log_line(rec)
    print()
    print("timeline:")
    for rec in found:
        hora = rec["ts"][11:19]
        extra = ""
        for key in ("status", "decision", "entry", "error", "wait_ms", "duration_ms"):
            if rec.get(key) is not None:
                extra += " %s=%s" % (key, rec[key])
        print("  %s  %-20s %-5s %s%s" % (hora, rec["service"], rec["level"],
                                         rec["message"], extra))


def cmd_trace(state, txid):
    ds = dataset(state)
    pay = state["payments"].get(txid)
    if not pay or txid != ds["tx"]:
        print("Nenhum trace indexado para %s neste periodo." % txid)
        return
    print(ds["trace"])


def cmd_metrics(state):
    print(dataset(state)["metrics"])


def cmd_dlq(state, replay=False):
    if state["incident"] == 2:
        print("payment-events.DLQ: vazia neste periodo.")
        print("(O problema do desafio final nao esta na DLQ. Siga as outras evidencias.)")
        return
    if not state["dlq_armed"]:
        print("payment-events.DLQ: vazia ate o momento.")
        print("(A DLQ recebe mensagens quando um consumer esgota os retries.)")
        return
    if state["dlq_replayed"]:
        print("payment-events.DLQ: vazia (1 mensagem reprocessada com sucesso as 21:14).")
        return
    if not replay:
        print("payment-events.DLQ: 1 mensagem")
        print()
        print(json.dumps(DLQ_MESSAGE, indent=2, ensure_ascii=True))
        print()
        print("Fluxo que levou ate aqui:")
        print("  payment-events -> notification-service -> retry 1 -> retry 2 -> retry 3 -> DLQ")
        print()
        print("Para reprocessar depois de corrigir a causa: scripts/dlq-show.sh replay")
        return
    # replay
    state["dlq_replayed"] = True
    save_state(state)
    print("Replay da payment-events.DLQ")
    print("  1. payload corrigido: amount \"250,00\" -> 250.00 (causa do erro de parse)")
    print("  2. reenviado para payment-events")
    print("  3. notification-service consumiu: evento aplicado (notificacao enviada 1x)")
    print("  4. entrega duplicada do mesmo eventId: IGNORADA (tabela processed_events)")
    print()
    print("Replay so e seguro porque o consumer e idempotente.")


# ---------------------------------------------------------------------------
# Comandos que conversam com o "sistema" (via HTTP, com fallback local)
# ---------------------------------------------------------------------------

def server_call(method, path, headers=None):
    url = "http://localhost:%d%s" % (PORT, path)
    req = urllib.request.Request(url, method=method, data=b"" if method == "POST" else None)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except (urllib.error.URLError, OSError):
        return None, None


def do_retry(state, txid, idempotency_key=None):
    """Logica de retry; usada pelo servidor e pelo fallback offline."""
    pay = state["payments"].get(txid)
    if not pay:
        return 404, {"error": "pagamento nao encontrado", "transaction_id": txid}
    if idempotency_key:
        return 200, {
            "transaction_id": txid,
            "idempotency_key": idempotency_key,
            "idempotent_replay": True,
            "status": pay["status"],
            "psp_executions": pay["psp_executions"],
            "message": "requisicao ja conhecida: retornando o resultado anterior; "
                       "nenhuma nova transferencia foi enviada ao PSP",
        }
    state["sandbox_retries"] += 1
    save_state(state)
    return 200, {
        "mode": "SIMULACAO (copia da transacao em ambiente controlado)",
        "transaction_id": txid + "-SANDBOX",
        "first_request": "PSP processou a transferencia; a RESPOSTA sofreu timeout",
        "retry_request": "PSP processou DE NOVO: ele nao tem como saber que e a mesma operacao",
        "result": "DUPLICATE TRANSFER: o cliente seria debitado duas vezes",
        "psp_executions": 2,
        "lesson": "retry de operacao financeira sem idempotencia duplica dinheiro",
    }


def do_reconcile(state, txid):
    pay = state["payments"].get(txid)
    if not pay:
        return 404, {"error": "pagamento nao encontrado", "transaction_id": txid}
    sources = {
        "techpix_payment_service": pay["status"],
        "ledger": pay["ledger"],
        "psp": pay["psp_truth"] + " (e2e_id=" + pay["psp_e2e_id"] + ")",
    }
    if pay["status"] == "UNKNOWN" and pay["psp_truth"] == "COMPLETED":
        pay["status"] = "APPROVED"
        pay["reconciled"] = True
        state["dlq_armed"] = True   # o evento de mudanca de estado falha no consumer
        save_state(state)
        return 200, {
            "transaction_id": txid,
            "sources": sources,
            "decision": "UNKNOWN -> APPROVED",
            "evidence": "PSP confirmou liquidacao (e2e_id) e o ledger ja tinha o debito",
            "result": "Reconciliation completed payment=%s status=APPROVED" % txid,
            "note": "evento PaymentStatusChanged publicado em payment-events",
        }
    return 200, {
        "transaction_id": txid,
        "sources": sources,
        "decision": "nenhuma divergencia entre as fontes",
        "note": "se ha atraso, o problema e latencia/backlog, nao divergencia de estado",
    }


def cmd_retry(state, args):
    if not args:
        print("uso: retry-payment.sh <transaction_id> [--executar | --idempotency-key CHAVE]")
        return
    txid = args[0]
    flags = args[1:]
    if not flags:
        print("ATENCAO - voce esta prestes a reprocessar uma operacao FINANCEIRA.")
        print()
        print("  transaction_id : %s" % txid)
        print("  status atual   : %s" % state["payments"].get(txid, {}).get("status", "?"))
        print("  ledger         : %s" % state["payments"].get(txid, {}).get("ledger", "?"))
        print()
        print("Antes de apertar o botao, responda:")
        print("  1. Voce SABE o que aconteceu com a primeira tentativa?")
        print("  2. Se o PSP processou e so a resposta se perdeu, o que o retry faz?")
        print("  3. O PSP consegue reconhecer que e a mesma operacao?")
        print()
        print("Para simular o retry SEM idempotencia (ambiente controlado):")
        print("  scripts/retry-payment.sh %s --executar" % txid)
        print("Para repetir COM chave de idempotencia:")
        print("  scripts/retry-payment.sh %s --idempotency-key %s" % (txid, txid))
        return
    key = None
    if "--idempotency-key" in flags:
        i = flags.index("--idempotency-key")
        key = flags[i + 1] if len(flags) > i + 1 else txid
        headers = {"Idempotency-Key": key}
    else:
        headers = {}
    status, body = server_call("POST", "/payments/%s/retry" % txid, headers)
    if status is None:
        print("[servidor do lab fora do ar em localhost:%d - executando offline]" % PORT)
        status, payload = do_retry(state, txid, key)
    else:
        payload = json.loads(body)
    print(json.dumps(payload, indent=2, ensure_ascii=True))


def cmd_reconcile(state, args):
    if not args:
        print("uso: reconcile.sh <transaction_id>")
        return
    txid = args[0]
    status, body = server_call("POST", "/reconciliation/%s" % txid)
    if status is None:
        print("[servidor do lab fora do ar em localhost:%d - executando offline]" % PORT)
        status, payload = do_reconcile(state, txid)
    else:
        payload = json.loads(body)
    print(json.dumps(payload, indent=2, ensure_ascii=True))


# ---------------------------------------------------------------------------
# Servidor HTTP
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# /metrics: reencena o incidente em loop para o Prometheus/Grafana.
# Loop de 20 min: metade "calmaria", na metade acontece o "deploy" e a
# degradacao cresce ate o fim do ciclo. O dashboard "Aula 9 - Cade o Pix?"
# (docker/grafana/dashboards/aula9.json) le estas series.
# ---------------------------------------------------------------------------

LOOP_SECONDS = 1200


def _ramp(phase, start, baseline, peak):
    """baseline ate 'start'; depois cresce linearmente ate 'peak' no fim do loop."""
    if phase < start:
        return baseline
    return baseline + (peak - baseline) * min(1.0, (phase - start) / (1.0 - start))


def render_metrics(state):
    phase = (time.time() % LOOP_SECONDS) / LOOP_SECONDS  # 0.0 -> 1.0; deploy em 0.5
    deploy = 1 if phase >= 0.5 else 0
    # info-metric com POUCOS exemplares (as transacoes do incidente), no estilo
    # dos exemplars do OpenTelemetry: a ponte metrica -> trace. Nao confundir com
    # label por transacao (cardinalidade!): aqui sao 2 series fixas, didaticas.
    tx = "PIX-554219" if state["incident"] == 2 else "PIX-928371"
    pay = state["payments"][tx]
    lines = [
        "# HELP techpix_lab9_info Incidente ativo do laboratorio Aula 9",
        "# TYPE techpix_lab9_info gauge",
        'techpix_lab9_info{incident="%d"} 1' % state["incident"],
        "# HELP techpix_incident_trace_info Exemplar do incidente: a ponte metrica -> logs -> trace",
        "# TYPE techpix_incident_trace_info gauge",
        'techpix_incident_trace_info{transaction_id="%s",correlation_id="%s",trace_id="%s",status="%s"} 1'
        % (tx, pay["correlation_id"], pay["trace_id"], pay["status"]),
        "# TYPE techpix_payments_per_second gauge",
        "techpix_payments_per_second %.1f" % (15.8 + 0.4 * phase),
        "# TYPE techpix_http_5xx_percent gauge",
        "techpix_http_5xx_percent %.2f" % (0.1 if not deploy else 0.4),
        "# TYPE techpix_deploy_active gauge",
    ]
    if state["incident"] == 2:
        lines += [
            'techpix_deploy_active{version="v1.13.5"} %d' % deploy,
            "# TYPE techpix_fraud_p95_ms gauge",
            "techpix_fraud_p95_ms %.0f" % _ramp(phase, 0.5, 62, 2650),
            "# TYPE techpix_psp_transfer_p95_ms gauge",
            "techpix_psp_transfer_p95_ms 190",
            "# TYPE techpix_cpu_percent gauge",
            'techpix_cpu_percent{service="payment-service"} 31',
            'techpix_cpu_percent{service="psp-adapter"} 18',
            'techpix_cpu_percent{service="fraud-service"} %.0f' % _ramp(phase, 0.5, 35, 95),
            "# TYPE techpix_kafka_consumer_lag gauge",
            "techpix_kafka_consumer_lag %.0f" % _ramp(phase, 0.5, 0, 1800),
            "# TYPE techpix_reconciliation_backlog gauge",
            "techpix_reconciliation_backlog %.0f" % _ramp(phase, 0.5, 4, 340),
            "# TYPE techpix_psp_pool_pending gauge",
            "techpix_psp_pool_pending 0",
            "# TYPE techpix_payments_unknown gauge",
            "techpix_payments_unknown %.0f" % _ramp(phase, 0.5, 0, 3),
            "# TYPE techpix_sli_pix_under_5s_ratio gauge",
            "techpix_sli_pix_under_5s_ratio %.4f" % (0.9995 if not deploy else 0.9990),
        ]
    else:
        lines += [
            'techpix_deploy_active{version="v1.13.4"} %d' % deploy,
            "# TYPE techpix_psp_transfer_p95_ms gauge",
            "techpix_psp_transfer_p95_ms %.0f" % _ramp(phase, 0.5, 185, 4950),
            "# TYPE techpix_fraud_p95_ms gauge",
            "techpix_fraud_p95_ms 60",
            "# TYPE techpix_cpu_percent gauge",
            'techpix_cpu_percent{service="payment-service"} 31',
            'techpix_cpu_percent{service="psp-adapter"} 18',
            'techpix_cpu_percent{service="fraud-service"} 35',
            "# TYPE techpix_psp_pool_pending gauge",
            "techpix_psp_pool_pending %.0f" % _ramp(phase, 0.5, 0, 16),
            "# TYPE techpix_kafka_consumer_lag gauge",
            "techpix_kafka_consumer_lag 0",
            "# TYPE techpix_reconciliation_backlog gauge",
            "techpix_reconciliation_backlog 0",
            "# TYPE techpix_payments_unknown gauge",
            "techpix_payments_unknown %.0f" % _ramp(phase, 0.5, 0, 25),
            "# TYPE techpix_sli_pix_under_5s_ratio gauge",
            "techpix_sli_pix_under_5s_ratio %.4f" % (0.9995 if not deploy else 0.9975),
        ]
    return "\n".join(lines) + "\n"


HEALTH = {
    "status": "UP",
    "components": {
        "db": {"status": "UP"},
        "kafka": {"status": "UP"},
        "diskSpace": {"status": "UP"},
        "ping": {"status": "UP"},
    },
}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload, indent=2, ensure_ascii=True).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # sem ruido no console
        pass

    def do_GET(self):
        state = load_state()
        if self.path == "/actuator/health":
            self._send(200, HEALTH)
        elif self.path == "/metrics":
            body = render_metrics(state).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path.startswith("/payments/"):
            txid = self.path.split("/")[2]
            pay = state["payments"].get(txid)
            if not pay:
                self._send(404, {"error": "payment not found", "transaction_id": txid})
                return
            self._send(200, {
                "transaction_id": pay["transaction_id"],
                "amount": pay["amount"],
                "payer_account": pay["payer_account"],
                "payee_account": pay["payee_account"],
                "created_at": pay["created_at"],
                "status": pay["status"],
                "correlation_id": pay["correlation_id"],
                "trace_id": pay["trace_id"],
            })
        elif self.path == "/":
            self._send(200, {
                "service": "techpix payment-service (simulador Aula 9)",
                "endpoints": ["/actuator/health", "/payments/{id}",
                              "POST /payments/{id}/retry", "POST /reconciliation/{id}"],
            })
        else:
            self._send(404, {"error": "not found", "path": self.path})

    def do_POST(self):
        state = load_state()
        parts = [p for p in self.path.split("/") if p]
        if len(parts) == 3 and parts[0] == "payments" and parts[2] == "retry":
            key = self.headers.get("Idempotency-Key")
            code, payload = do_retry(state, parts[1], key)
            self._send(code, payload)
        elif len(parts) == 2 and parts[0] == "reconciliation":
            code, payload = do_reconcile(state, parts[1])
            self._send(code, payload)
        else:
            self._send(404, {"error": "not found", "path": self.path})


def cmd_serve():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("payment-service (simulador Aula 9) em http://localhost:%d" % PORT)
    print("Ctrl+C para parar.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


def cmd_up():
    status, _ = server_call("GET", "/actuator/health")
    if status == 200:
        print("Servidor do lab ja responde em http://localhost:%d" % PORT)
        return
    probe_busy = _port_busy()
    if probe_busy:
        print("ERRO: a porta %d ja esta em uso por outro processo." % PORT)
        print("Pare o monolito da Aula 7/8 (docker compose --profile app down)")
        print("ou escolha outra porta: TECHPIX_LAB9_PORT=8086 scripts/lab9-up.sh")
        sys.exit(1)
    flags = 0
    if os.name == "nt":
        flags = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    with open(LOG_FILE, "w") as log:
        proc = subprocess.Popen(
            [sys.executable, os.path.abspath(__file__), "serve"],
            stdout=log, stderr=log,
            creationflags=flags if os.name == "nt" else 0,
            start_new_session=(os.name != "nt"),
        )
    with open(PID_FILE, "w") as f:
        f.write(str(proc.pid))
    for _ in range(20):
        time.sleep(0.25)
        status, _ = server_call("GET", "/actuator/health")
        if status == 200:
            print("Lab da Aula 9 no ar: http://localhost:%d (pid %d)" % (PORT, proc.pid))
            print()
            print("Teste voce mesmo:  curl localhost:%d/actuator/health" % PORT)
            print("Para derrubar:     scripts/lab9-up.sh stop")
            return
    print("O servidor nao respondeu. Veja o log: %s" % LOG_FILE)
    sys.exit(1)


def _port_busy():
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", PORT)) == 0
    finally:
        s.close()


def cmd_stop():
    if not os.path.exists(PID_FILE):
        print("Nenhum pid registrado (%s)." % PID_FILE)
        return
    with open(PID_FILE) as f:
        pid = int(f.read().strip())
    try:
        os.kill(pid, signal.SIGTERM)
        print("Servidor do lab (pid %d) finalizado." % pid)
    except OSError as e:
        print("Nao consegui finalizar o pid %d: %s" % (pid, e))
    os.remove(PID_FILE)


def cmd_reset():
    save_state(json.loads(json.dumps(INITIAL_STATE)))
    print("Estado do laboratorio reiniciado (incidente 1, PIX-928371 em UNKNOWN).")


def cmd_incident2(state, args):
    mode = args[0] if args else "on"
    if mode == "off":
        state["incident"] = 1
        save_state(state)
        print("De volta ao incidente 1 (PIX-928371).")
        return
    state["incident"] = 2
    save_state(state)
    print("DESAFIO FINAL ativado.")
    print()
    print("Novo chamado do suporte (02/10/2026, 21:47):")
    print('  "Clientes reclamam que o Pix esta demorando. Nada caiu, nenhum alerta')
    print('   de erro disparou, mas a fila de reconciliacao nao para de crescer."')
    print()
    print("  transaction_id de exemplo: PIX-554219   (cliente reclamou de lentidao)")
    print()
    print("Use o que voce aprendeu: logs, correlation, trace, metrics, state,")
    print("DLQ, reconciliacao. Preencha o Incident Report do template.")
    print("Ninguem vai te dizer a causa desta vez.")


def cmd_status(state):
    print("incidente ativo : %d" % state["incident"])
    for txid, pay in state["payments"].items():
        print("  %s  status=%s  ledger=%s  reconciled=%s"
              % (txid, pay["status"], pay["ledger"], pay["reconciled"]))
    status, _ = server_call("GET", "/actuator/health")
    print("servidor        : %s (porta %d)"
          % ("UP" if status == 200 else "fora do ar", PORT))


# ---------------------------------------------------------------------------

def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return
    cmd, rest = args[0], args[1:]
    state = load_state()
    if cmd == "serve":
        cmd_serve()
    elif cmd == "up":
        cmd_up()
    elif cmd == "stop":
        cmd_stop()
    elif cmd == "reset":
        cmd_reset()
    elif cmd == "status":
        cmd_status(state)
    elif cmd == "logs":
        cmd_logs(state, rest[0] if rest else "PIX-928371")
    elif cmd == "correlation":
        cmd_correlation(state, rest[0] if rest else "abc123")
    elif cmd == "trace":
        cmd_trace(state, rest[0] if rest else "PIX-928371")
    elif cmd == "metrics":
        cmd_metrics(state)
    elif cmd == "dlq":
        cmd_dlq(state, replay=bool(rest and rest[0] == "replay"))
    elif cmd == "retry":
        cmd_retry(state, rest)
    elif cmd == "reconcile":
        cmd_reconcile(state, rest)
    elif cmd == "incident2":
        cmd_incident2(state, rest)
    else:
        print("comando desconhecido: %s" % cmd)
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
