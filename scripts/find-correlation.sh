#!/usr/bin/env bash
# Aula 9: segue um correlation_id atraves dos servicos, em ordem. Uso: scripts/find-correlation.sh abc123
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" correlation "$@"
