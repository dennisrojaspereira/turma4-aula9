#!/usr/bin/env bash
# Sobe o Fraud Service localmente pelo Maven, na porta 8081, usando o mesmo PostgreSQL do monolito.
#   FRAUD_WARMUP_SECONDS=20 scripts/start-fraud-service.sh   -> demora 20s para ficar "ready" (lab 12)
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d postgres >/dev/null
echo "Iniciando Fraud Service em http://localhost:8081"
./mvnw -q -pl fraud-service spring-boot:run
