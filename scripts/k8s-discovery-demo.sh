#!/usr/bin/env bash
# Service Discovery ao vivo: Payment chama http://fraud-service, nunca um IP de Pod.
# O script mata um Pod de Fraud no meio de uma sequencia de pagamentos. Nenhum pagamento deve falhar.
set -euo pipefail
cd "$(dirname "$0")/.."
export TECHPIX_URL=http://localhost:8090
NS="-n techpix"

echo "== Endpoints do Service fraud-service (os IPs que o Service conhece AGORA):"
kubectl $NS get endpoints fraud-service -o jsonpath='{range .subsets[*].addresses[*]}{.ip}{"\n"}{end}'
echo
echo "== O que o monolito conhece:"
kubectl $NS get configmap monolith-config -o jsonpath='{.data.TECHPIX_FRAUD_SERVICE_URL}'; echo
echo

scripts/fraud-mode.sh NEW >/dev/null
alice=$(curl -s -X POST "$TECHPIX_URL/accounts" -H 'Content-Type: application/json' -d '{"ownerName":"Alice","initialBalance":100000}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
bob=$(curl -s -X POST "$TECHPIX_URL/accounts" -H 'Content-Type: application/json' -d '{"ownerName":"Bob","initialBalance":0}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

victim=$(kubectl $NS get pods -l app=fraud-service -o jsonpath='{.items[0].metadata.name}')
echo "== 20 pagamentos; no 5o, o Pod $victim sera apagado"
ok=0; fail=0
for i in $(seq 1 20); do
  if [ "$i" -eq 5 ]; then
    kubectl $NS delete pod "$victim" --wait=false >/dev/null
    echo "   (pod $victim apagado)"
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$TECHPIX_URL/payments" -H 'Content-Type: application/json' \
    -d "{\"payerAccountId\":\"$alice\",\"payeeAccountId\":\"$bob\",\"amount\":1.5,\"deviceId\":\"demo\"}")
  if [ "$code" = "201" ]; then ok=$((ok+1)); else fail=$((fail+1)); fi
  printf "   pagamento %2d -> HTTP %s\n" "$i" "$code"
  sleep 0.5
done
echo
echo "== resultado: ok=$ok falhas=$fail"
echo
echo "== Endpoints agora (repare: IPs diferentes, mesmo nome):"
kubectl $NS get endpoints fraud-service -o jsonpath='{range .subsets[*].addresses[*]}{.ip}{"\n"}{end}'
kubectl $NS get pods -l app=fraud-service
scripts/fraud-mode.sh LEGACY >/dev/null
