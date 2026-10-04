#!/usr/bin/env bash
# Aula 9: snapshot do dashboard de metricas (RED / USE / Golden Signals).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" metrics "$@"
