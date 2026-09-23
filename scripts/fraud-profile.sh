#!/usr/bin/env bash
# Troca o perfil de regras de Fraud em tempo de execucao.
#   scripts/fraud-profile.sh SIMPLE
#   scripts/fraud-profile.sh HEAVY
#   scripts/fraud-profile.sh            -> mostra o perfil atual
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
if [ $# -eq 0 ]; then
  curl -s "$BASE/admin/fraud/profile"; echo; exit 0
fi
curl -s -X PUT "$BASE/admin/fraud/profile" -H 'Content-Type: application/json' -d "{\"profile\":\"$1\"}"
echo
