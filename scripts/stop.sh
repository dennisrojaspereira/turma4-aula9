#!/usr/bin/env bash
# Para tudo que o docker compose subiu. Use -v para apagar os dados do banco.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose --profile app down "$@"
