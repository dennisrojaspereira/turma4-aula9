#!/usr/bin/env bash
# Abre duas contas e faz um pagamento. Mostra o fluxo completo em três chamadas.
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"

alice=$(curl -s -X POST "$BASE/accounts" -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","initialBalance":1000}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
bob=$(curl -s -X POST "$BASE/accounts" -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","initialBalance":0}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

echo "Alice: $alice"
echo "Bob:   $bob"
echo
echo "POST /payments"
curl -s -X POST "$BASE/payments" -H 'Content-Type: application/json' \
  -d "{\"payerAccountId\":\"$alice\",\"payeeAccountId\":\"$bob\",\"amount\":250,\"deviceId\":\"demo-device\"}"
echo
echo
echo "Saldo de Alice:"
curl -s "$BASE/accounts/$alice"
echo
