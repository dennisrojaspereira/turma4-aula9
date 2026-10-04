#!/usr/bin/env python3
"""Synthetic monitoring da Tech Pix (Aula 9): um robo refaz a jornada do Pix
de tempos em tempos e conta o resultado ao Prometheus.

    python scripts/synthetic-monitor.py run              uma execucao, veredicto no console
    python scripts/synthetic-monitor.py start [seg]      monitor em background (padrao: 120s)
    python scripts/synthetic-monitor.py status           ultimo resultado + historico
    python scripts/synthetic-monitor.py stop             para o monitor

Alvo: TECHPIX_URL (padrao http://localhost:8090, o monolito no kind).
Usa o k6 (load-tests/synthetic-pix.js) quando instalado; sem k6, faz a mesma
jornada em Python puro. Metricas em http://localhost:8098/metrics (job
techpix-synthetic no Prometheus; painel na Visao Geral do Grafana).
"""

import json
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.environ.get("TECHPIX_URL", "http://localhost:8090")
PORT = int(os.environ.get("TECHPIX_SYNTHETIC_PORT", "8098"))
K6_SCRIPT = os.path.join(ROOT, "load-tests", "synthetic-pix.js")
STATE_FILE = os.path.join(ROOT, "scripts", "aula9", ".synthetic-state.json")
PID_FILE = os.path.join(ROOT, "scripts", "aula9", ".synthetic-monitor.pid")

STATE = {"runs": 0, "failures": 0, "history": []}  # history: ultimos 50 resultados


def http_json(method, path, payload=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=json.dumps(payload).encode() if payload is not None else None,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return r.status, json.loads(r.read().decode())


def probe_python():
    """A mesma jornada do synthetic-pix.js, sem k6."""
    _, payer = http_json("POST", "/accounts", {"ownerName": "Synthetic Probe", "initialBalance": 10})
    _, payee = http_json("POST", "/accounts", {"ownerName": "Synthetic Sink", "initialBalance": 0})
    st, pay = http_json("POST", "/payments", {
        "payerAccountId": payer["id"], "payeeAccountId": payee["id"],
        "amount": 1.0, "deviceId": "synthetic-probe"})
    if st != 201 or pay.get("status") != "APPROVED":
        raise AssertionError("pagamento nao aprovado: http=%s status=%s reason=%s"
                             % (st, pay.get("status"), pay.get("rejectionReason")))
    _, got = http_json("GET", "/payments/" + pay["id"])
    if got.get("status") != "APPROVED":
        raise AssertionError("GET /payments divergente: %s" % got.get("status"))
    _, saldo = http_json("GET", "/accounts/" + payee["id"])
    if float(saldo.get("balance", 0)) != 1.0:
        raise AssertionError("recebedor nao foi creditado (balance=%s)" % saldo.get("balance"))


def probe_k6():
    p = subprocess.run([shutil.which("k6"), "run", "--quiet",
                        "-e", "BASE_URL=" + BASE, K6_SCRIPT],
                       capture_output=True, text=True, timeout=120)
    if p.returncode != 0:
        tail = (p.stdout or "") + (p.stderr or "")
        raise AssertionError("k6 reprovou (thresholds): " + tail.strip().splitlines()[-1][:160])


def run_probe():
    engine = "k6" if shutil.which("k6") else "python"
    t0 = time.time()
    try:
        (probe_k6 if engine == "k6" else probe_python)()
        ok, detail = True, ""
    except Exception as e:  # qualquer falha de jornada = synthetic DOWN
        ok, detail = False, str(e)[:200]
    ms = int((time.time() - t0) * 1000)
    result = {"ts": int(time.time()), "ok": ok, "ms": ms, "engine": engine,
              "target": BASE, "detail": detail}
    STATE["runs"] += 1
    STATE["failures"] += 0 if ok else 1
    STATE["history"] = (STATE["history"] + [result])[-50:]
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(STATE, f, indent=2)
    except OSError:
        pass
    return result


def render_metrics():
    last = STATE["history"][-1] if STATE["history"] else None
    lines = [
        "# HELP techpix_synthetic_success Ultima jornada sintetica do Pix (1=ok, 0=falhou)",
        "# TYPE techpix_synthetic_success gauge",
        "techpix_synthetic_success %d" % (1 if last and last["ok"] else 0),
        "# TYPE techpix_synthetic_duration_ms gauge",
        "techpix_synthetic_duration_ms %d" % (last["ms"] if last else 0),
        "# TYPE techpix_synthetic_last_run_timestamp_seconds gauge",
        "techpix_synthetic_last_run_timestamp_seconds %d" % (last["ts"] if last else 0),
        "# TYPE techpix_synthetic_runs_total counter",
        "techpix_synthetic_runs_total %d" % STATE["runs"],
        "# TYPE techpix_synthetic_failures_total counter",
        "techpix_synthetic_failures_total %d" % STATE["failures"],
    ]
    return "\n".join(lines) + "\n"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        if self.path == "/metrics":
            body, ctype = render_metrics().encode(), "text/plain; version=0.0.4"
        else:
            body, ctype = json.dumps(STATE, indent=2).encode(), "application/json"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def cmd_serve(interval):
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print("synthetic monitor: alvo=%s intervalo=%ds metricas em :%d/metrics" % (BASE, interval, PORT))
    while True:
        r = run_probe()
        print("[%s] %s %dms %s %s" % (time.strftime("%H:%M:%S"), "OK " if r["ok"] else "FAIL",
                                      r["ms"], r["engine"], r["detail"]))
        time.sleep(interval)


def cmd_run():
    r = run_probe()
    print("alvo:    %s" % r["target"])
    print("motor:   %s" % r["engine"])
    print("duracao: %d ms" % r["ms"])
    if r["ok"]:
        print("veredicto: OK - a jornada do Pix (contas -> pagamento -> APPROVED -> credito) funciona agora")
    else:
        print("veredicto: FALHOU - %s" % r["detail"])
        print("O robo descobriu antes do cliente. Investigue: logs, metricas, trace (lab 23).")
        sys.exit(1)


def cmd_start(interval):
    try:
        with urllib.request.urlopen("http://localhost:%d/metrics" % PORT, timeout=2):
            print("monitor ja esta rodando em :%d" % PORT)
            return
    except Exception:
        pass
    flags = 0x00000008 | 0x00000200 if os.name == "nt" else 0
    proc = subprocess.Popen(
        [sys.executable, os.path.abspath(__file__), "serve", str(interval)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=flags if os.name == "nt" else 0,
        start_new_session=(os.name != "nt"))
    with open(PID_FILE, "w") as f:
        f.write(str(proc.pid))
    print("synthetic monitor no ar (pid %d): a cada %ds testa %s" % (proc.pid, interval, BASE))
    print("  resultado:  python scripts/synthetic-monitor.py status")
    print("  metricas:   http://localhost:%d/metrics  (painel na Visao Geral do Grafana)" % PORT)


def cmd_stop():
    if not os.path.exists(PID_FILE):
        print("nenhum pid registrado")
        return
    with open(PID_FILE) as f:
        pid = int(f.read().strip())
    try:
        os.kill(pid, signal.SIGTERM)
        print("monitor (pid %d) finalizado" % pid)
    except OSError as e:
        print("nao consegui finalizar %d: %s" % (pid, e))
    os.remove(PID_FILE)


def cmd_status():
    try:
        with urllib.request.urlopen("http://localhost:%d/status" % PORT, timeout=3) as r:
            data = json.loads(r.read().decode())
    except Exception:
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, encoding="utf-8") as f:
                data = json.load(f)
            print("(monitor parado; mostrando o ultimo estado salvo)")
        else:
            print("monitor parado e sem historico. Rode: python scripts/synthetic-monitor.py start")
            return
    print("execucoes: %d  falhas: %d" % (data["runs"], data["failures"]))
    for r in data["history"][-10:]:
        print("  %s  %s  %5dms  %s  %s" % (time.strftime("%H:%M:%S", time.localtime(r["ts"])),
                                           "OK " if r["ok"] else "FAIL", r["ms"], r["engine"], r["detail"]))


def main():
    args = sys.argv[1:]
    cmd = args[0] if args else "run"
    if cmd == "run":
        cmd_run()
    elif cmd == "serve":
        cmd_serve(int(args[1]) if len(args) > 1 else 120)
    elif cmd == "start":
        cmd_start(int(args[1]) if len(args) > 1 else 120)
    elif cmd == "stop":
        cmd_stop()
    elif cmd == "status":
        cmd_status()
    else:
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
