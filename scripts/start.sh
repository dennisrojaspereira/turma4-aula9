#!/usr/bin/env bash
# Sobe o PostgreSQL e o monolito localmente (monolito roda pelo Maven, fora do Docker, para facilitar debug).
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose up -d postgres
echo "Aguardando PostgreSQL..."
until docker compose exec -T postgres pg_isready -U techpix -d techpix >/dev/null 2>&1; do sleep 1; done
echo "PostgreSQL pronto. Iniciando monolito em http://localhost:8080"
./mvnw -q -pl monolith spring-boot:run
