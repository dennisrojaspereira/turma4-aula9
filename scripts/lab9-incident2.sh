#!/usr/bin/env bash
# Aula 9: ativa o desafio final (segundo incidente, sem causa informada). 'off' volta ao incidente 1.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" incident2 "$@"
