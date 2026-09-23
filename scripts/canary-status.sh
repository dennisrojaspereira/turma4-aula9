#!/usr/bin/env bash
# Os criterios de progressao do canario, lidos das metricas.
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
M="$BASE/actuator/metrics"
py() { python3 -c "$1" 2>/dev/null || echo n/a; }
metric() { curl -s "$M/$1" ; }

echo "== Canary"
echo "  mode:        $(curl -s "$BASE/admin/fraud/mode")"
echo "  percentage:  $(curl -s "$BASE/admin/fraud/canary")"
echo
echo "== Roteamento"
echo "  new:      $(metric 'fraud.canary.routed?tag=target:new' | py "import sys,json; d=json.load(sys.stdin); print(int(d['measurements'][0]['value']))")"
echo "  legacy:   $(metric 'fraud.canary.routed?tag=target:legacy' | py "import sys,json; d=json.load(sys.stdin); print(int(d['measurements'][0]['value']))")"
echo "  fallback: $(metric 'fraud.canary.fallback' | py "import sys,json; d=json.load(sys.stdin); print(int(d['measurements'][0]['value']))")   <- canario falhou, legado decidiu"
echo
echo "== Latencia da chamada remota (fraud.remote.roundtrip)"
metric 'fraud.remote.roundtrip' | py "
import sys, json
d = json.load(sys.stdin)
m = {x['statistic']: x['value'] for x in d['measurements']}
print(f\"  count={int(m['COUNT'])} mean={m['TOTAL_TIME']/m['COUNT']*1000 if m['COUNT'] else 0:.1f}ms max={m['MAX']*1000:.1f}ms\")"
echo
echo "== Criterios para subir o degrau (todos precisam passar):"
echo "  [ ] error rate (fallback / new) < 0.5%"
echo "  [ ] p95 remoto dentro do orcamento de Payment"
echo "  [ ] p99 remoto sem cauda longa"
echo "  [ ] decision mismatch do Parallel Run = 0 no periodo anterior"
echo "  [ ] timeouts = 0"
echo "  Rollback a qualquer momento: scripts/rollback.sh"
