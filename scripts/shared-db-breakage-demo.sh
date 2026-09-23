#!/usr/bin/env bash
# Shared Database ao vivo: o time de Payment renomeia uma coluna. O Fraud Service (que le a tabela deles) quebra.
# Requer o Fraud Service em modo banco compartilhado (etapa 8):
#   FRAUD_DB_NAME=techpix FRAUD_DB_USER=techpix FRAUD_DB_PASSWORD=techpix FRAUD_FLYWAY_ENABLED=false \
#   FRAUD_HISTORY_SOURCE=LEGACY_SCHEMA scripts/start-fraud-service.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PSQL="docker compose exec -T postgres psql -U techpix -d techpix -q"
FRAUD="${FRAUD_URL:-http://localhost:8081}"
body='{"paymentId":"11111111-1111-1111-1111-111111111111","payerAccountId":"22222222-2222-2222-2222-222222222222","payeeAccountId":"33333333-3333-3333-3333-333333333333","amount":10,"deviceId":"demo","occurredAt":"2026-03-01T12:00:00Z"}'

echo "== 1. Fraud Service avaliando normalmente:"
curl -s -o /dev/null -w "   HTTP %{http_code}\n" -X POST "$FRAUD/fraud/evaluations" -H 'Content-Type: application/json' -d "$body"

echo "== 2. O time de Payment faz uma migration 'inocente' no banco DELES:"
echo "   ALTER TABLE payments RENAME COLUMN device_id TO device_fingerprint;"
$PSQL -c "ALTER TABLE payments RENAME COLUMN device_id TO device_fingerprint;"

echo "== 3. Fraud Service agora:"
curl -s -o /dev/null -w "   HTTP %{http_code}   <- ninguem avisou o Fraud Service. Ninguem sabia que precisava.\n" -X POST "$FRAUD/fraud/evaluations" -H 'Content-Type: application/json' -d "$body"

echo "== 4. Desfazendo (na vida real, isso e um incidente e um rollback de migration):"
$PSQL -c "ALTER TABLE payments RENAME COLUMN device_fingerprint TO device_id;"
curl -s -o /dev/null -w "   HTTP %{http_code}\n" -X POST "$FRAUD/fraud/evaluations" -H 'Content-Type: application/json' -d "$body"
