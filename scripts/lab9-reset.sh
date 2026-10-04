#!/usr/bin/env bash
# Aula 9: volta o laboratorio ao estado inicial (incidente 1, PIX-928371 em UNKNOWN).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" reset "$@"
