#!/usr/bin/env bash
# Aula 9: reconcilia uma transacao contra o ledger e o PSP. Uso: scripts/reconcile.sh PIX-928371
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" reconcile "$@"
