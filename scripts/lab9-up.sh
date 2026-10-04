#!/usr/bin/env bash
# Aula 9: sobe o ambiente do laboratorio "Cade o Pix?" (simulador do payment-service na porta 8080).
#   scripts/lab9-up.sh         -> sobe o servidor em background e reinicia o estado
#   scripts/lab9-up.sh stop    -> derruba o servidor
# Porta alternativa: TECHPIX_LAB9_PORT=8086 scripts/lab9-up.sh
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
if [ "${1:-}" = "stop" ]; then
  exec "$PY" "$DIR/aula9/lab9.py" stop
fi
"$PY" "$DIR/aula9/lab9.py" reset
exec "$PY" "$DIR/aula9/lab9.py" up
