#!/usr/bin/env bash
# Mostra as metricas que respondem "onde o tempo esta sendo gasto?" direto do Actuator.
# Nao precisa de Prometheus nem Grafana.
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
M="$BASE/actuator/metrics"

# stat <metric> <count|mean_ms|max_ms|mean|value> [tag:value ...]
stat() {
  local url="$M/$1?"
  local metric="$1" mode="$2"; shift 2
  for t in "$@"; do url="$url&tag=$t"; done
  curl -s "$url" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print('n/a'); sys.exit()
m = {x['statistic']: x['value'] for x in d.get('measurements', [])}
unit = d.get('baseUnit') or ''
mode = '$mode'
def ms(v): return v * 1000 if unit == 'seconds' else v
try:
    if mode == 'count':   print(int(m['COUNT']))
    elif mode == 'value': print(round(m['VALUE'], 2))
    elif mode == 'max_ms': print(round(ms(m['MAX']), 1))
    elif mode == 'mean_ms':
        total = m.get('TOTAL_TIME', m.get('TOTAL'))
        print(round(ms(total / m['COUNT']), 1) if m['COUNT'] else 'n/a')
    elif mode == 'mean':
        print(round(m['TOTAL'] / m['COUNT'], 1) if m['COUNT'] else 'n/a')
except Exception:
    print('n/a')
" 2>/dev/null || echo "n/a"
}

echo "== Perfil de Fraud"
curl -s "$BASE/admin/fraud/profile"; echo; echo
PROFILE=$(curl -s "$BASE/admin/fraud/profile" | python3 -c "import sys,json; print(json.load(sys.stdin)['profile'])" 2>/dev/null || echo "")

echo "== Payment (POST /payments)"
echo "  requests:        $(stat payment.create count)"
echo "  mean ms:         $(stat payment.create mean_ms)"
echo "  max ms:          $(stat payment.create max_ms)"
echo "  queries/payment: $(stat payment.queries mean)"
echo

echo "== Fraud (FraudService.evaluate)"
echo "  mean ms:         $(stat fraud.evaluation mean_ms profile:$PROFILE)"
echo "  max ms:          $(stat fraud.evaluation max_ms profile:$PROFILE)"
echo "  queries/fraud:   $(stat fraud.queries mean profile:$PROFILE)"
echo

echo "== Pool de conexoes (HikariCP)"
echo "  max:             $(stat hikaricp.connections.max value)"
echo "  active:          $(stat hikaricp.connections.active value)"
echo "  pending:         $(stat hikaricp.connections.pending value)   <- threads esperando conexao agora"
echo "  acquire mean ms: $(stat hikaricp.connections.acquire mean_ms)   <- quanto se espera por uma conexao"
echo "  usage mean ms:   $(stat hikaricp.connections.usage mean_ms)   <- quanto tempo cada conexao fica presa"
echo

echo "== Tempo medio por regra de Fraud (ms) e consultas por regra (perfil $PROFILE)"
rules=$(curl -s "$M/fraud.rule" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for t in d.get('availableTags', []):
    if t['tag'] == 'rule':
        print(' '.join(t['values']))
" 2>/dev/null || true)
for rule in $rules; do
  printf '  %-20s %8s ms  %6s queries\n' "$rule" "$(stat fraud.rule mean_ms rule:$rule profile:$PROFILE)" "$(stat fraud.rule.queries mean rule:$rule profile:$PROFILE)"
done | grep -v " n/a ms" | sort -k2 -n -r
