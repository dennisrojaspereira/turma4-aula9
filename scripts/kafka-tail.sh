#!/usr/bin/env bash
# Mostra os eventos do topico payment-events, do inicio, com a chave (payerAccountId).
set -euo pipefail
cd "$(dirname "$0")/.."
# MSYS_NO_PATHCONV: o Git Bash converteria /opt/kafka/... em C:/Program Files/Git/opt/...
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment-events --from-beginning \
  --property print.key=true --property key.separator=' | ' "$@"
