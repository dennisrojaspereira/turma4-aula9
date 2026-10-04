#!/usr/bin/env bash
# Aula 9: reprocessa um pagamento. Sem flag so pergunta; --executar simula sem idempotencia; --idempotency-key CHAVE repete com seguranca.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
exec "$PY" "$DIR/aula9/lab9.py" retry "$@"
