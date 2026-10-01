#!/usr/bin/env bash
# Sobe o Keycloak (lab 22) com o realm techpix pre-provisionado (docker/keycloak/techpix-realm.json).
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose --profile auth up -d keycloak

echo "Aguardando o Keycloak importar o realm techpix (ate 180s)..."
deadline=$((SECONDS + 180))
until curl -fsS http://localhost:8180/realms/techpix/.well-known/openid-configuration >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo "ERRO: Keycloak nao ficou pronto em 180s. Veja: docker logs techpix-keycloak" >&2
    exit 1
  fi
  sleep 2
done

cat <<'EOF'

Keycloak pronto.

  Admin console:  http://localhost:8180  (admin / admin)
  Realm:          techpix
  Usuarios:       maria / techpix123  (techpix-admin, techpix-user)
                  joao  / techpix123  (techpix-user)

Para ligar a seguranca no monolito (profile secure):

  SPRING_PROFILES_ACTIVE=secure ./mvnw -pl monolith spring-boot:run

e abra http://localhost:8080/login no navegador.

Token via curl (password grant, so para demonstracao):

  TOKEN=$(curl -s -X POST http://localhost:8180/realms/techpix/protocol/openid-connect/token \
    -d grant_type=password -d client_id=techpix-monolith -d client_secret=techpix-monolith-secret \
    -d username=maria -d password=techpix123 | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/me
EOF
