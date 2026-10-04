#!/usr/bin/env bash
# Aula 9: mostra a Dead Letter Queue. 'scripts/dlq-show.sh replay' reprocessa apos corrigir a causa.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" dlq "$@"
