#!/usr/bin/env bash
# Republica o fato de um pagamento com o MESMO eventId: simula uma redelivery do Kafka.
#   scripts/replay-event.sh <paymentId>
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
[ $# -eq 1 ] || { echo "uso: $0 <paymentId>"; exit 1; }
curl -s -X POST "$BASE/admin/events/replay/$1"
echo
echo "No Fraud Service: docker compose logs fraud-service | grep -E 'aplicado|DUPLICADO' | tail -3"
