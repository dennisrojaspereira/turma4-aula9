#!/usr/bin/env bash
# Mostra os eventos do topico payment-events, do inicio, com a chave (payerAccountId).
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment-events --from-beginning \
  --property print.key=true --property key.separator=' | ' "$@"
