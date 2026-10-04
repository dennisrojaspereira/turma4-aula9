#!/usr/bin/env bash
# OPA client: mostra a Tech Pix consultando o OPA server (policy as code) com
# identidades REAIS do Keycloak. O servico nao decide; ele PERGUNTA.
#   1. carlos (techpix-user) faz Pix de R$ 250            -> ALLOW
#   2. carlos tenta Pix de R$ 5.000                       -> DENY (limite do papel)
#   3. maria (admin) faz Pix de R$ 5.000                  -> ALLOW
#   4. maria faz Pix de R$ 25.000 sem compliance          -> DENY (exige aprovacao)
#   5. o mesmo Pix com aprovacao de beatriz (compliance)  -> ALLOW
#   6. token de SERVICO do monolito chama fraud.evaluate  -> ALLOW (role fraud-evaluate)
#   7. token de USUARIO tenta fraud.evaluate              -> DENY
#   scripts/opa-demo.sh
# Pre-requisitos: docker compose --profile auth up -d (keycloak + opa).
set -euo pipefail

OPA="${OPA_URL:-http://localhost:8181}/v1/data/techpix/authz/decision"
KC="${KEYCLOAK_URL:-http://localhost:8180}/realms/techpix/protocol/openid-connect"
PY="$(command -v python3 || command -v python)"

token_roles() {  # pega um token no Keycloak e devolve as roles como JSON array
  curl -sf -X POST "$KC/token" "$@" \
    -d client_id=techpix-monolith -d client_secret=techpix-monolith-secret |
  "$PY" -c '
import base64, json, sys
tok = json.load(sys.stdin)["access_token"].split(".")[1]
tok += "=" * (-len(tok) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(tok)).get("roles", [])))
'
}

ask() {  # ask "descricao" '<input json>'
  echo "--- $1"
  echo "    input: $2"
  curl -sf "$OPA" -d "{\"input\": $2}" | "$PY" -c '
import json, sys
d = json.load(sys.stdin).get("result", {})
verdict = "ALLOW" if d.get("allow") else "DENY"
reasons = "; ".join(d.get("reasons", []))
print("    OPA:   %s%s" % (verdict, ("  (" + reasons + ")") if reasons else ""))
'
  echo
}

echo "=== roles vindas de tokens REAIS do Keycloak ==="
CARLOS=$(token_roles -d grant_type=password -d username=carlos.andrade -d password=techpix123)
MARIA=$(token_roles -d grant_type=password -d username=maria -d password=techpix123)
SVC=$(token_roles -d grant_type=client_credentials)
echo "  carlos.andrade: $CARLOS"
echo "  maria:          $MARIA"
echo "  monolito (svc): $SVC"
echo

ask "1. carlos, Pix de R\$ 250" \
  "{\"action\":\"payment.create\",\"amount\":250,\"roles\":$CARLOS}"
ask "2. carlos, Pix de R\$ 5.000" \
  "{\"action\":\"payment.create\",\"amount\":5000,\"roles\":$CARLOS}"
ask "3. maria (admin), Pix de R\$ 5.000" \
  "{\"action\":\"payment.create\",\"amount\":5000,\"roles\":$MARIA}"
ask "4. maria, Pix de R\$ 25.000 SEM compliance" \
  "{\"action\":\"payment.create\",\"amount\":25000,\"roles\":$MARIA,\"approvals\":[]}"
ask "5. maria, Pix de R\$ 25.000 COM aprovacao de compliance (beatriz)" \
  "{\"action\":\"payment.create\",\"amount\":25000,\"roles\":$MARIA,\"approvals\":[\"techpix-compliance\"]}"
ask "6. SERVICO monolito chama fraud.evaluate" \
  "{\"action\":\"fraud.evaluate\",\"roles\":$SVC}"
ask "7. USUARIO carlos tenta fraud.evaluate" \
  "{\"action\":\"fraud.evaluate\",\"roles\":$CARLOS}"

echo "A regra mora em docker/opa/policies/techpix.rego - edite com o OPA no ar"
echo "(ele recarrega sozinho) e rode de novo: a politica muda SEM redeploy."
echo "Identidade (Keycloak) diz QUEM voce e; politica (OPA) diz O QUE pode."
