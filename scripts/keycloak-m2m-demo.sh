#!/usr/bin/env bash
# Demonstra o realm techpix com usuarios e servicos REAIS conversando:
#   1. um USUARIO (carlos.andrade) faz login e recebe um token de usuario
#   2. o MONOLITO (service account) pega um token client_credentials para chamar o Fraud
#   3. o FRAUD SERVICE valida o token do monolito via introspection -> AUTORIZADO
#   4. o mesmo Fraud valida o token do USUARIO -> NEGADO (sem a role fraud-evaluate)
#   5. a chamada real: POST /fraud/evaluations com o Bearer do monolito
#   scripts/keycloak-m2m-demo.sh
# Pre-requisito: scripts/keycloak-up.sh (realm techpix importado).
set -euo pipefail

KC="${KEYCLOAK_URL:-http://localhost:8180}/realms/techpix/protocol/openid-connect"
FRAUD="${FRAUD_URL:-http://localhost:8091}"
PY="$(command -v python3 || command -v python)"

claims() {  # decodifica o payload do JWT (so para exibir; validar e papel do introspection)
  "$PY" -c '
import base64, json, sys
tok = sys.stdin.read().strip().split(".")[1]
tok += "=" * (-len(tok) % 4)
d = json.loads(base64.urlsafe_b64decode(tok))
keep = {k: d.get(k) for k in ("sub","preferred_username","name","azp","aud","roles","exp") if k in d}
print(json.dumps(keep, indent=2, ensure_ascii=False))
'
}

field() { "$PY" -c "import json,sys; print(json.load(sys.stdin).get('$1',''))"; }

echo "=== 1. USUARIO REAL: carlos.andrade faz login (password grant, demo) ==="
USER_TOKEN=$(curl -sf -X POST "$KC/token" \
  -d grant_type=password -d client_id=techpix-monolith -d client_secret=techpix-monolith-secret \
  -d username=carlos.andrade -d password=techpix123 | field access_token)
echo "token de usuario emitido. Claims:"
echo "$USER_TOKEN" | claims
echo

echo "=== 2. SERVICO: o monolito pede token proprio (client_credentials) ==="
SVC_TOKEN=$(curl -sf -X POST "$KC/token" \
  -d grant_type=client_credentials \
  -d client_id=techpix-monolith -d client_secret=techpix-monolith-secret | field access_token)
echo "token de SERVICO emitido (nenhum usuario envolvido). Claims:"
echo "$SVC_TOKEN" | claims
echo "  azp = quem chama (techpix-monolith); aud inclui techpix-fraud-service;"
echo "  roles do service account: fraud-evaluate, payments-write"
echo

echo "=== 3. O FRAUD SERVICE valida o token do monolito (token introspection) ==="
INTRO=$(curl -sf -u techpix-fraud-service:techpix-fraud-secret \
  -X POST "$KC/token/introspect" -d "token=$SVC_TOKEN")
ACTIVE=$(echo "$INTRO" | field active)
ROLES=$(echo "$INTRO" | "$PY" -c "import json,sys; d=json.load(sys.stdin); print(' '.join(d.get('realm_access',{}).get('roles',[])))")
echo "introspection: active=$ACTIVE  azp=$(echo "$INTRO" | field azp)  roles=[$ROLES]"
if [ "$ACTIVE" = "True" ] || [ "$ACTIVE" = "true" ]; then
  case " $ROLES " in
    *" fraud-evaluate "*) echo "decisao do Fraud Service: AUTORIZADO (tem a role fraud-evaluate)" ;;
    *) echo "decisao do Fraud Service: NEGADO (sem a role fraud-evaluate)" ;;
  esac
fi
echo

echo "=== 4. E se o token do USUARIO tentasse chamar o Fraud direto? ==="
INTRO_U=$(curl -sf -u techpix-fraud-service:techpix-fraud-secret \
  -X POST "$KC/token/introspect" -d "token=$USER_TOKEN")
ROLES_U=$(echo "$INTRO_U" | "$PY" -c "import json,sys; d=json.load(sys.stdin); print(' '.join(d.get('realm_access',{}).get('roles',[])))")
echo "introspection do token de carlos.andrade: roles=[$ROLES_U]"
case " $ROLES_U " in
  *" fraud-evaluate "*) echo "decisao: AUTORIZADO" ;;
  *) echo "decisao do Fraud Service: NEGADO -> usuario final nao chama servico interno (403)" ;;
esac
echo

echo "=== 5. A chamada real: monolito -> Fraud Service com o Bearer ==="
BODY='{"paymentId":"00000000-0000-0000-0000-000000000001","payerAccountId":"00000000-0000-0000-0000-00000000a001","payeeAccountId":"00000000-0000-0000-0000-00000000a002","amount":250.00,"deviceId":"demo-keycloak","occurredAt":"2026-10-02T21:03:01Z"}'
if RESP=$(curl -sf -m 10 -X POST "$FRAUD/fraud/evaluations" \
    -H "Authorization: Bearer $SVC_TOKEN" -H 'Content-Type: application/json' \
    -d "$BODY" 2>/dev/null); then
  echo "POST $FRAUD/fraud/evaluations -> 200"
  echo "$RESP" | "$PY" -m json.tool
  echo
  echo "Obs: o Fraud Service do lab ainda ACEITA chamadas sem token (a validacao que"
  echo "voces viram no passo 3 e o que o profile 'secure' adicionaria aqui tambem)."
else
  echo "Fraud Service nao respondeu em $FRAUD (suba o cluster: scripts/k8s-up.sh,"
  echo "ou aponte FRAUD_URL=http://localhost:8081 para o compose)."
fi
echo
echo "Resumo: usuario autentica para USAR o app; servico autentica para FALAR com outro"
echo "servico; quem recebe valida o token e decide por ROLE. Identidade != permissao."
