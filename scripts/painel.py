#!/usr/bin/env python3
# Painel da Aula 07: uma pagina local com botoes que executam os scripts da demo.
#   python scripts/painel.py   ->  http://localhost:8099
# So aceita conexoes de 127.0.0.1 e so executa os comandos da lista ACTIONS.
import os
import re
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PORT = 8099
# O bash do WSL (System32) nao serve; usamos o do Git for Windows.
BASH = next(
    (p for p in (
        r"C:\Program Files\Git\bin\bash.exe",
        r"C:\Program Files (x86)\Git\bin\bash.exe",
    ) if os.path.exists(p)),
    "bash",
)
ENV = {
    **os.environ,
    "TECHPIX_URL": "http://localhost:8090",
    "FRAUD_URL": "http://localhost:8091",
    "TECHPIX_GIT_URL": "http://host.docker.internal:3001/techpix/tech-pix.git",
    "MSYS_NO_PATHCONV": "1",
}

KAFKA_TAIL = (
    "kubectl -n techpix-dev exec deploy/kafka -- /opt/kafka/bin/kafka-console-consumer.sh "
    "--bootstrap-server localhost:9092 --topic payment-events --from-beginning "
    "--property print.key=true --property 'key.separator= | ' --timeout-ms 8000 2>&1 "
    "| grep -Ev 'TimeoutException|ERROR Error processing' || true"
)

# id -> (rotulo, comando bash). A pagina agrupa por secao, na ordem da aula.
ACTIONS = {
    # Status
    "pods":            ("Pods de dev", "kubectl -n techpix-dev get pods -o wide"),
    "envs":            ("Os ambientes (dev/prod)", "for ns in techpix-dev techpix-prod; do echo \"== $ns\"; kubectl -n $ns get deploy; echo; done"),
    "targets":         ("Targets do Prometheus", "curl -s http://localhost:9090/api/v1/targets | python -c \"import json,sys; d=json.load(sys.stdin); [print(t['labels'].get('app'), t['scrapeUrl'], '->', t['health']) for t in d['data']['activeTargets']]\""),
    # Demo basica
    "demo":            ("Fazer um pagamento", "bash scripts/demo-payment.sh"),
    "seed":            ("Seed: 2.000 contas, 200.000 pagamentos", "bash scripts/seed.sh"),
    "metrics":         ("Onde o tempo e gasto (Actuator)", "bash scripts/show-metrics.sh"),
    # Carga
    "burst":           ("Rajada: 20 pagamentos", "for i in $(seq 1 20); do bash scripts/demo-payment.sh >/dev/null 2>&1; echo \"pagamento $i ok\"; done"),
    "load-baseline":   ("k6 baseline (5 VUs, 30s)", "bash scripts/load-test.sh baseline"),
    "load-growth":     ("k6 growth (40 VUs, 60s)", "bash scripts/load-test.sh growth"),
    # Strangler / modos
    "mode":            ("Modo atual", "bash scripts/fraud-mode.sh"),
    "mode-legacy":     ("LEGACY (rollback total)", "bash scripts/fraud-mode.sh LEGACY"),
    "mode-parallel":   ("PARALLEL (parallel run)", "bash scripts/fraud-mode.sh PARALLEL"),
    "parallel-report": ("Relatorio do parallel run", "bash scripts/parallel-run-report.sh"),
    "mode-new":        ("NEW (100% Fraud Service)", "bash scripts/fraud-mode.sh NEW"),
    "rollback":        ("Rollback", "bash scripts/rollback.sh"),
    # Canary
    "canary-10":       ("Canary 10%", "bash scripts/canary.sh 10"),
    "canary-50":       ("Canary 50%", "bash scripts/canary.sh 50"),
    "canary-100":      ("Canary 100%", "bash scripts/canary.sh 100"),
    "canary-status":   ("Status do canary", "bash scripts/canary-status.sh"),
    # Escala
    "hpa-prod":        ("HPA do prod", "kubectl -n techpix-prod get hpa"),
    "top":             ("kubectl top pods (dev)", "kubectl -n techpix-dev top pods || echo 'metrics-server ainda sem dados'"),
    # Kafka
    "kafka-tail":      ("Eventos de payment-events (8s)", KAFKA_TAIL),
    # Caos (lab 20)
    "chaos-latency":   ("Latencia +800ms no Fraud", "bash scripts/chaos.sh latency 800"),
    "chaos-errors":    ("50% de erros no Fraud", "bash scripts/chaos.sh errors 0.5"),
    "chaos-off":       ("Desligar o caos", "bash scripts/chaos.sh off"),
    "chaos-status":    ("Status do caos", "bash scripts/chaos.sh"),
    # GitOps / Argo CD
    "argocd-apps":     ("Status das Applications", "kubectl -n argocd get applications"),
    "drift":           ("Drift demo: escala na mao, Argo desfaz", "bash scripts/argocd-drift-demo.sh"),
    "argocd-forward":  ("Reabrir UI (port-forward 8443)", "(kubectl -n argocd port-forward svc/argocd-server 8443:443 >/dev/null 2>&1 &) && echo 'UI: https://localhost:8443  usuario: admin' && sleep 1"),
    "argocd-pass":     ("Senha do admin", "kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo"),
    "gitea-push":      ("Push de novos commits para o Gitea", "bash scripts/local-git-server.sh push"),
    # Istio
    "istio-sidecars":  ("Sidecars (2/2 = no mesh)", "kubectl -n techpix-dev get pods"),
    "istio-vs":        ("VirtualService ativo", "kubectl -n techpix-dev get virtualservice -o yaml 2>/dev/null | grep -E 'name:|fault:|fixedDelay|httpStatus|value:|timeout:|attempts:' || echo 'nenhum VirtualService ativo'"),
    "istio-delay":     ("Mesh: +800ms no Fraud + pagamento", "kubectl apply -f istio/fault-delay.yaml && echo '(modo NEW: o pagamento passa pelo Fraud Service remoto)' && bash scripts/fraud-mode.sh NEW >/dev/null && echo '-> compare fraudDurationMs com um pagamento normal (~40ms):' && bash scripts/demo-payment.sh"),
    "istio-abort":     ("Mesh: 50% de erros 500 + pagamento", "kubectl apply -f istio/fault-abort.yaml && echo '(modo NEW: o pagamento passa pelo Fraud Service remoto)' && bash scripts/fraud-mode.sh NEW >/dev/null && echo '-> retry/fallback do lab 20 reagindo aos 500 do mesh:' && bash scripts/demo-payment.sh"),
    "istio-resilience":("Mesh: retry 2x + timeout 2s", "kubectl apply -f istio/resilience.yaml && kubectl -n techpix-dev get virtualservice"),
    "istio-off":       ("Desligar o mesh (remover VS)", "kubectl -n techpix-dev delete virtualservice fraud-service --ignore-not-found && echo 'VirtualService removido'"),
    # Historia
    "steps":           ("Listar as 14 etapas (tags)", "git tag -l 'aula07-*'"),
}

SECTIONS = [
    ("Status", ["pods", "envs", "targets"]),
    ("Demo básica", ["demo", "seed", "metrics"]),
    ("Carga (k6)", ["burst", "load-baseline", "load-growth"]),
    ("Strangler — modos", ["mode", "mode-legacy", "mode-parallel", "parallel-report", "mode-new", "rollback"]),
    ("Canary", ["canary-10", "canary-50", "canary-100", "canary-status"]),
    ("Escala", ["hpa-prod", "top"]),
    ("Kafka", ["kafka-tail"]),
    ("Caos — lab 20 (em código)", ["chaos-latency", "chaos-errors", "chaos-off", "chaos-status"]),
    ("Istio — lab 20 pelo mesh", ["istio-sidecars", "istio-delay", "istio-abort", "istio-resilience", "istio-vs", "istio-off"]),
    ("GitOps / Argo CD", ["argocd-apps", "drift", "argocd-forward", "argocd-pass", "gitea-push"]),
    ("História", ["steps"]),
]

LINKS = [
    ("Arquitetura", "/arquitetura"),
    ("Monólito dev", "http://localhost:8090/actuator/health"),
    ("Fraud dev", "http://localhost:8091/actuator/health/readiness"),
    ("Prod", "http://localhost:8094/actuator/health"),
    ("Prometheus", "http://localhost:9090/targets"),
    ("Grafana", "http://localhost:3000/d/techpix"),
    ("Argo CD", "https://localhost:8443"),
    ("Gitea", "http://localhost:3001/techpix/tech-pix"),
    ("Kiali (mesh)", "http://localhost:20001/kiali/console/graph/namespaces/?namespaces=techpix-dev"),
]

PAGE = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Painel Aula 07</title>
<style>
  :root {
    --bg: #0d1117; --surface: #161b22; --border: #30363d;
    --text: #e6edf3; --muted: #8b949e; --accent: #2f81f7; --green: #3fb950;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: var(--bg); color: var(--text); font-family: "Segoe UI", system-ui, sans-serif; }
  header { padding: 1.2rem 2rem; border-bottom: 1px solid var(--border); background: var(--surface);
           display: flex; align-items: baseline; gap: 1rem; flex-wrap: wrap; }
  header h1 { font-size: 1.3rem; }
  header span { color: var(--muted); font-size: .9rem; }
  .layout { display: grid; grid-template-columns: 340px 1fr; gap: 0; height: calc(100vh - 64px); }
  .menu { overflow-y: auto; border-right: 1px solid var(--border); padding: 1rem; }
  .menu h2 { font-size: .75rem; text-transform: uppercase; letter-spacing: .08em;
             color: var(--muted); margin: 1.1rem 0 .4rem; }
  .menu h2:first-child { margin-top: 0; }
  button.act { display: block; width: 100%; text-align: left; margin: .25rem 0; padding: .5rem .7rem;
               background: var(--surface); color: var(--text); border: 1px solid var(--border);
               border-radius: 6px; cursor: pointer; font-size: .88rem; }
  button.act:hover { border-color: var(--accent); }
  button.act:disabled { opacity: .45; cursor: wait; }
  .k6form { display: flex; gap: .4rem; margin: .25rem 0; align-items: center; }
  .k6form input { width: 4.5rem; padding: .45rem .5rem; background: var(--bg); color: var(--text);
                  border: 1px solid var(--border); border-radius: 6px; font-size: .88rem; }
  .k6form button { flex: 1; margin: 0; }
  .k6form label { font-size: .78rem; color: var(--muted); }
  .links { padding: .6rem 2rem; border-bottom: 1px solid var(--border); display: flex; gap: 1.2rem; flex-wrap: wrap; }
  .links a { color: var(--accent); text-decoration: none; font-size: .88rem; }
  .links a:hover { text-decoration: underline; }
  .creds { padding: .45rem 2rem; border-bottom: 1px solid var(--border); display: flex; gap: 1.4rem;
           flex-wrap: wrap; font-size: .8rem; color: var(--muted); background: var(--surface); }
  .creds b { color: var(--text); font-weight: 600; }
  .creds code { font-family: Consolas, Menlo, monospace; color: var(--green); }
  .out-wrap { display: flex; flex-direction: column; min-width: 0; }
  .out-head { padding: .5rem 1.2rem; font-size: .85rem; color: var(--muted);
              border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; }
  .out-head .running { color: var(--green); }
  pre { flex: 1; overflow: auto; padding: 1rem 1.2rem; font-family: Consolas, Menlo, monospace;
        font-size: .82rem; line-height: 1.45; white-space: pre-wrap; word-break: break-word; }
  pre .cmd { color: var(--accent); }
  @media (max-width: 800px) { .layout { grid-template-columns: 1fr; height: auto; }
                              pre { min-height: 40vh; } }
</style>
</head>
<body>
<header><h1>Tech Pix — Painel da Aula 07</h1><span>clique numa etapa; a saída aparece ao lado</span></header>
<div class="links">__LINKS__</div>
<div class="creds">__CREDS__</div>
<div class="layout">
  <nav class="menu">__MENU__</nav>
  <div class="out-wrap">
    <div class="out-head"><span id="label">pronto</span><span id="state"></span></div>
    <pre id="out">Bem-vindo! Clique em "Pods do cluster" para começar.</pre>
  </div>
</div>
<script>
const out = document.getElementById('out');
const label = document.getElementById('label');
const state = document.getElementById('state');
let running = false;
async function run(id, name) {
  if (running) return;
  running = true;
  document.querySelectorAll('button.act').forEach(b => b.disabled = true);
  label.textContent = name;
  state.textContent = 'executando...';
  state.className = 'running';
  out.textContent = '';
  try {
    const r = await fetch('/run/' + id, { method: 'POST' });
    const reader = r.body.getReader();
    const dec = new TextDecoder();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      out.textContent += dec.decode(value, { stream: true });
      out.scrollTop = out.scrollHeight;
    }
  } catch (e) {
    out.textContent += '\\n[erro de conexão: ' + e + ']';
  }
  state.textContent = 'concluído';
  state.className = '';
  running = false;
  document.querySelectorAll('button.act').forEach(b => b.disabled = false);
}
function runK6() {
  const vus = document.getElementById('vus').value;
  const dur = document.getElementById('dur').value;
  run('load-custom?vus=' + encodeURIComponent(vus) + '&dur=' + encodeURIComponent(dur),
      'k6 custom (' + vus + ' VUs, ' + dur + ')');
}
</script>
</body>
</html>"""


ARCH_PAGE = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Arquitetura — Aula 07</title>
<style>
  body { background: #0d1117; color: #e6edf3; font-family: "Segoe UI", system-ui, sans-serif;
         margin: 0; padding: 1rem 16px; max-width: 1400px; margin-inline: auto; }
  header { display: flex; align-items: baseline; gap: 1rem; margin-bottom: .4rem; flex-wrap: wrap; }
  h1 { font-size: 1.2rem; }
  h2 { font-size: 1.05rem; margin: 2.2rem 0 .3rem; padding-top: 1rem; border-top: 1px solid #30363d; }
  p.desc { color: #8b949e; font-size: .9rem; margin: 0 0 .8rem; max-width: 900px; }
  a { color: #2f81f7; text-decoration: none; font-size: .9rem; }
  nav.idx { display: flex; gap: 1.1rem; flex-wrap: wrap; margin-bottom: .5rem; }
  .mermaid { background: #0d1117; overflow-x: auto; }
</style>
</head>
<body>
<header><h1>Tech Pix — todos os sistemas</h1><a href="/">&larr; voltar ao painel</a></header>
<nav class="idx">
  <a href="#implantacao">Implantação</a>
  <a href="#c4-1">C4 · 1 Contexto</a>
  <a href="#c4-2">C4 · 2 Containers</a>
  <a href="#c4-3">C4 · 3 Componentes</a>
  <a href="#c4-4">C4 · 4 Código</a>
</nav>
<h2 id="implantacao">Visão de implantação — o que está rodando agora</h2>
<p class="desc">Tudo que existe na máquina durante a aula: o cluster kind com os ambientes
gerenciados pelo Argo CD, o mesh do Istio, e as ferramentas de apoio fora do cluster.</p>
<pre class="mermaid">
flowchart LR
  subgraph HOST["Sua máquina"]
    PAINEL["Painel :8099<br/>botões das etapas"]
    K6["k6<br/>carga: baseline / growth / custom"]
    PROM["Prometheus :9090<br/>(Compose)"]
    GRAF["Grafana :3000<br/>dashboard techpix"]
    GITEA["Gitea :3001<br/>git local"]
  end

  subgraph KIND["Cluster kind &quot;techpix&quot;"]
    subgraph ARGONS["ns argocd"]
      ARGO["Argo CD :8443<br/>sync + selfHeal + prune"]
    end
    subgraph ISTION["ns istio-system"]
      ISTIOD["istiod<br/>injeta sidecars"]
      KIALI["Kiali :20001<br/>grafo do mesh"]
      PROMI["Prometheus in-cluster<br/>métricas do mesh"]
    end
    subgraph DEV["ns techpix-dev — :8090/:8091 (sidecars Envoy)"]
      MONO["Monolith<br/>Account · Payment · Ledger<br/>Strangler: LEGACY/PARALLEL/CANARY/NEW"]
      FRAUD["Fraud Service<br/>regras + visão local"]
      PG[("Postgres<br/>techpix + fraud_db")]
      KAF[("Kafka<br/>payment-events")]
    end
    QA["ns techpix-qa — :8092<br/>canary 10%"]
    PROD["ns techpix-prod — :8094<br/>HPA no fraud"]
  end

  PAINEL -- "scripts (.sh)" --> MONO
  K6 -- "POST /payments" --> MONO
  MONO -- "HTTP via sidecar<br/>timeout + retry (lab 20)" --> FRAUD
  MONO --> PG
  FRAUD --> PG
  MONO -- "fatos: PaymentApproved" --> KAF
  KAF -- "consumer idempotente" --> FRAUD
  ARGO -- "pull gitops/overlays" --> GITEA
  ARGO -- "sync" --> DEV
  ARGO -- "sync" --> QA
  ARGO -- "sync" --> PROD
  ISTIOD -. "sidecar + VirtualService<br/>fault injection / retry" .-> DEV
  KIALI --> PROMI
  PROMI -. "scrape mesh" .-> DEV
  PROM -- "scrape :8090/:8091" --> MONO
  GRAF --> PROM
</pre>

<h2 id="c4-1">C4 — Nível 1: Contexto</h2>
<p class="desc">Quem usa a Tech Pix e com o que ela conversa. Um sistema de pagamentos Pix
com análise de fraude; o instrutor opera o laboratório pelo painel.</p>
<pre class="mermaid">
C4Context
  title Tech Pix — Diagrama de Contexto
  Person(cliente, "Cliente", "Abre conta e faz pagamentos Pix")
  Person(instrutor, "Instrutor", "Opera as demos pelo painel e pelos scripts")
  System(techpix, "Tech Pix", "Processa pagamentos com análise de fraude, ledger e notificação")
  System_Ext(extprov, "Provedor externo de risco", "Consultado pela regra external-provider (simulado no lab)")
  System(gitops, "Plataforma GitOps", "Gitea + Argo CD: o Git descreve os ambientes, o cluster converge")
  System(obs, "Observabilidade", "Prometheus, Grafana e Kiali: métricas, dashboards e o grafo do mesh")
  Rel(cliente, techpix, "paga via", "HTTP/JSON")
  Rel(instrutor, techpix, "opera", "painel :8099")
  Rel(techpix, extprov, "consulta risco", "HTTP")
  Rel(gitops, techpix, "implanta e reconcilia")
  Rel(obs, techpix, "coleta métricas")
</pre>

<h2 id="c4-2">C4 — Nível 2: Containers</h2>
<p class="desc">Dentro da Tech Pix: o monólito (que já foi tudo), o Fraud Service extraído,
um banco por serviço e o Kafka carregando os fatos de pagamento. Cada processo em dev roda
com um sidecar Envoy (Istio).</p>
<pre class="mermaid">
C4Container
  title Tech Pix — Diagrama de Containers
  Person(cliente, "Cliente")
  System_Boundary(tp, "Tech Pix") {
    Container(mono, "Monolith", "Spring Boot 3 / Java 21", "Account, Payment, Ledger, Notification e o Strangler de Fraud (LEGACY/PARALLEL/CANARY/NEW)")
    Container(fraud, "Fraud Service", "Spring Boot 3 / Java 21", "Serviço extraído: 17 regras, RiskEngine, visão local própria")
    ContainerDb(pg, "PostgreSQL", "techpix + fraud_db", "Database per Service: cada serviço com usuário e schema próprios")
    ContainerQueue(kafka, "Kafka", "tópico payment-events", "Fatos publicados pelo Payment; consumidos com idempotência")
  }
  Rel(cliente, mono, "POST /payments", "HTTP :8090")
  Rel(mono, fraud, "avalia risco", "HTTP :8081 via sidecar — timeout + retry com backoff")
  Rel(mono, pg, "lê/escreve", "JDBC (techpix)")
  Rel(fraud, pg, "visão local", "JDBC (fraud_db)")
  Rel(mono, kafka, "publica PaymentApproved/Rejected")
  Rel(kafka, fraud, "consumer idempotente atualiza a visão local")
</pre>

<h2 id="c4-3">C4 — Nível 3: Componentes</h2>
<p class="desc">Dentro dos dois serviços: no monólito, o caminho do pagamento e o Strangler;
no Fraud Service, o domínio isolado por portas e o ACL que traduz o modelo legado.</p>
<pre class="mermaid">
C4Component
  title Monolith — componentes do caminho do pagamento
  Container_Boundary(m, "Monolith") {
    Component(api, "PaymentController", "REST", "Recebe POST /payments")
    Component(psvc, "PaymentService", "Serviço", "Orquestra: fraude, débito, ledger, notificação; saga por compensação se a liquidação falha")
    Component(facade, "Fraud Facade (Strangler)", "Branch by Abstraction", "Decide por modo: LEGACY, PARALLEL, CANARY, NEW")
    Component(legacy, "Fraud legado", "in-process", "As regras originais dentro do monólito")
    Component(canary, "CanaryRouter", "Roteador", "hash do pagador → N% vai ao serviço novo")
    Component(client, "FraudRemoteClient", "HTTP", "timeout, RetryPolicy com backoff + jitter, fallback")
    Component(ledger, "LedgerService", "Serviço", "Partida dobrada; ponto da compensação")
    Component(pub, "PaymentEventPublisher", "Kafka", "Publica os fatos do pagamento")
  }
  Rel(api, psvc, "chama")
  Rel(psvc, facade, "avalia fraude")
  Rel(facade, legacy, "LEGACY / PARALLEL")
  Rel(facade, canary, "CANARY")
  Rel(canary, client, "N%")
  Rel(facade, client, "NEW")
  Rel(psvc, ledger, "lança")
  Rel(psvc, pub, "publica fato")
</pre>
<pre class="mermaid">
C4Component
  title Fraud Service — componentes
  Container_Boundary(f, "Fraud Service") {
    Component(fapi, "FraudController", "REST", "POST /fraud/evaluate + admin/chaos")
    Component(acl, "ACL", "Anticorruption Layer", "Traduz o modelo do monólito para o domínio novo")
    Component(engine, "RiskEngine", "Domínio puro", "17 regras por trás de portas; sem dependência de framework")
    Component(store, "Visão local", "fraud_db", "Os dados que Fraud precisa, mantidos por eventos")
    Component(cons, "PaymentEventsConsumer", "Kafka", "Idempotente: processa cada evento uma vez")
  }
  Rel(fapi, acl, "traduz")
  Rel(acl, engine, "avalia")
  Rel(engine, store, "consulta")
  Rel(cons, store, "atualiza")
</pre>

<h2 id="c4-4">C4 — Nível 4: Código</h2>
<p class="desc">O nível de código só vale a pena onde a estrutura ensina algo. Aqui, os três
mecanismos que a aula constrói à mão — e que o Istio depois entrega por configuração.</p>
<pre class="mermaid">
classDiagram
  class FraudEvaluator {
    &lt;&lt;interface&gt;&gt;
    +evaluate(payment) Decision
  }
  class LegacyFraudAdapter { +evaluate(payment) }
  class RemoteFraudAdapter { +evaluate(payment) }
  class CanaryRouter {
    -percentage int
    +route(payerId) Target
  }
  class RetryPolicy {
    -maxAttempts int
    -baseBackoff Duration
    +executeComRetry(call)
    +jitter() Duration
  }
  class RiskEngine {
    -rules List~FraudRule~
    +assess(context) RiskScore
  }
  class FraudRule {
    &lt;&lt;interface&gt;&gt;
    +applies(context) bool
    +score() int
  }
  FraudEvaluator <|.. LegacyFraudAdapter
  FraudEvaluator <|.. RemoteFraudAdapter
  RemoteFraudAdapter --> RetryPolicy : envolve chamadas
  CanaryRouter --> FraudEvaluator : escolhe
  RiskEngine o-- FraudRule : 17 regras
</pre>
<script type="module">
  import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";
  mermaid.initialize({ startOnLoad: true, theme: "dark" });
</script>
</body>
</html>"""


def argocd_password():
    try:
        out = subprocess.run(
            ["kubectl", "-n", "argocd", "get", "secret", "argocd-initial-admin-secret",
             "-o", "jsonpath={.data.password}"],
            capture_output=True, text=True, timeout=5,
        ).stdout.strip()
        if out:
            import base64
            return base64.b64decode(out).decode()
    except Exception:
        pass
    return "(use o botão 'Senha do admin')"


def build_creds():
    items = [
        ("Argo CD", f"admin / <code>{argocd_password()}</code>"),
        ("Gitea", "techpix / <code>techpix123</code>"),
        ("Postgres", "techpix / <code>techpix</code> · fraud / <code>fraud</code> "
                     "(interno: kubectl -n techpix-dev port-forward svc/postgres 5432:5432)"),
        ("Grafana", "sem login (anônimo, Admin)"),
        ("Prometheus e apps", "sem senha"),
    ]
    return " ".join(f"<span><b>{name}:</b> {info}</span>" for name, info in items)


def build_page():
    menu = []
    for title, ids in SECTIONS:
        menu.append(f"<h2>{title}</h2>")
        for aid in ids:
            lbl = ACTIONS[aid][0]
            menu.append(
                f"<button class=\"act\" onclick=\"run('{aid}', '{lbl}')\">{lbl}</button>"
            )
        if title.startswith("Carga"):
            menu.append(
                '<div class="k6form"><label>VUs</label><input id="vus" value="10">'
                '<label>tempo</label><input id="dur" value="30s">'
                '<button class="act" onclick="runK6()">k6 custom</button></div>'
            )
    links = " ".join(f'<a href="{url}" target="_blank">{name} ↗</a>' for name, url in LINKS)
    return (PAGE.replace("__MENU__", "\n".join(menu))
                .replace("__LINKS__", links)
                .replace("__CREDS__", build_creds()))


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"  # resposta delimitada pelo fechamento: streaming simples

    def log_message(self, fmt, *args):
        sys.stderr.write("  %s\n" % (fmt % args))

    def do_GET(self):
        if self.path == "/arquitetura":
            body = ARCH_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path in ("/", "/index.html"):
            body = build_page().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)

    def do_POST(self):
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(self.path)
        aid = parsed.path.removeprefix("/run/")
        if aid == "load-custom":
            qs = parse_qs(parsed.query)
            vus = (qs.get("vus") or ["10"])[0]
            dur = (qs.get("dur") or ["30s"])[0]
            if not re.fullmatch(r"\d{1,4}", vus) or not re.fullmatch(r"\d{1,4}[smh]", dur):
                self.send_error(400, "vus deve ser um numero e tempo algo como 30s, 2m")
                return
            cmd = f"bash scripts/load-test.sh custom {vus} {dur}"
        elif aid in ACTIONS:
            _, cmd = ACTIONS[aid]
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(f"$ {cmd}\n\n".encode("utf-8"))
        self.wfile.flush()
        proc = subprocess.Popen(
            [BASH, "-c", cmd], cwd=ROOT, env=ENV,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        try:
            for line in proc.stdout:
                self.wfile.write(line.decode("utf-8", errors="replace").encode("utf-8"))
                self.wfile.flush()
        except (BrokenPipeError, ConnectionError):
            proc.kill()
        rc = proc.wait()
        try:
            self.wfile.write(f"\n[exit {rc}]\n".encode("utf-8"))
        except (BrokenPipeError, ConnectionError):
            pass


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Painel da Aula 07: http://localhost:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
