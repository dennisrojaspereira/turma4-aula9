#!/usr/bin/env bash
# Gera carga contra o monolito.
#   scripts/load-test.sh baseline   -> 5 VUs por 30s  (a Tech Pix do inicio)
#   scripts/load-test.sh growth     -> 40 VUs por 60s (a Tech Pix depois do crescimento)
#   scripts/load-test.sh custom VUS DURATION
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-baseline}"
case "$MODE" in
  baseline) VUS=5;  DURATION=30s ;;
  growth)   VUS=40; DURATION=60s ;;
  custom)   VUS="${2:-10}"; DURATION="${3:-30s}" ;;
  *) echo "uso: $0 [baseline|growth|custom VUS DURATION]"; exit 1 ;;
esac
echo "== load-test mode=$MODE vus=$VUS duration=$DURATION"
k6 run -e VUS="$VUS" -e DURATION="$DURATION" -e BASE_URL="${TECHPIX_URL:-http://localhost:8080}" load-tests/payment-load.js
