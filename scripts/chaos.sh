#!/usr/bin/env bash
# Injeta falhas no Fraud Service para ver timeout, retry e fallback acontecerem do lado do Payment.
#   scripts/chaos.sh latency 800      -> toda avaliacao demora +800 ms
#   scripts/chaos.sh errors 0.5       -> 50% das avaliacoes respondem 500
#   scripts/chaos.sh both 800 0.5
#   scripts/chaos.sh off
#   scripts/chaos.sh                  -> status
set -euo pipefail
FRAUD="${FRAUD_URL:-http://localhost:8081}"
case "${1:-status}" in
  latency) body="{\"latencyMs\":${2:-800},\"errorRate\":0}" ;;
  errors)  body="{\"latencyMs\":0,\"errorRate\":${2:-0.5}}" ;;
  both)    body="{\"latencyMs\":${2:-800},\"errorRate\":${3:-0.5}}" ;;
  off)     body='{"latencyMs":0,"errorRate":0}' ;;
  status)  curl -s "$FRAUD/admin/fraud/chaos"; echo; exit 0 ;;
  *) echo "uso: $0 [latency MS|errors RATE|both MS RATE|off|status]"; exit 1 ;;
esac
curl -s -X PUT "$FRAUD/admin/fraud/chaos" -H 'Content-Type: application/json' -d "$body"; echo
echo "Do lado do Payment: curl localhost:8080/actuator/metrics/fraud.remote.retries ; scripts/canary-status.sh"
