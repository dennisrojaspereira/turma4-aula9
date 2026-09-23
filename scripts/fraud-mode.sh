#!/usr/bin/env bash
# Troca o modo do Strangler em tempo de execucao.
#   scripts/fraud-mode.sh LEGACY     -> so o Fraud legado (in-process). Rollback.
#   scripts/fraud-mode.sh NEW        -> so o Fraud Service (remoto)
#   scripts/fraud-mode.sh            -> mostra o modo atual
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
if [ $# -eq 0 ]; then
  curl -s "$BASE/admin/fraud/mode"; echo; exit 0
fi
curl -s -X PUT "$BASE/admin/fraud/mode" -H 'Content-Type: application/json' -d "{\"mode\":\"$1\"}"
echo
