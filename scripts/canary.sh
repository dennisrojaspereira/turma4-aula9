#!/usr/bin/env bash
# Canary: manda N% dos pagamentos para o Fraud Service, com efeito real.
#   scripts/canary.sh 10      -> modo CANARY, 10%
#   scripts/canary.sh         -> status
# Atalhos: canary-10.sh, canary-50.sh, canary-100.sh. Rollback: rollback.sh
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
if [ $# -eq 0 ]; then
  echo "mode:   $(curl -s "$BASE/admin/fraud/mode")"
  echo "canary: $(curl -s "$BASE/admin/fraud/canary")"
  exit 0
fi
PCT="$1"
curl -s -X PUT "$BASE/admin/fraud/canary" -H 'Content-Type: application/json' -d "{\"percentage\":$PCT}" >/dev/null
curl -s -X PUT "$BASE/admin/fraud/mode" -H 'Content-Type: application/json' -d '{"mode":"CANARY"}' >/dev/null
echo "canary: $PCT% dos pagamentos decididos pelo Fraud Service"
echo "criterios para o proximo degrau: scripts/canary-status.sh"
