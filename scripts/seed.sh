#!/usr/bin/env bash
# Popula o "passado" da Tech Pix: contas, historico de pagamentos e blacklist.
# Uso: scripts/seed.sh [accounts] [payments] [blacklist]
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
ACCOUNTS="${1:-2000}"
PAYMENTS="${2:-200000}"
BLACKLIST="${3:-2000}"

echo "Gerando $ACCOUNTS contas, $PAYMENTS pagamentos e $BLACKLIST entradas de blacklist..."
curl -s -X POST "$BASE/admin/seed?accounts=$ACCOUNTS&payments=$PAYMENTS&blacklist=$BLACKLIST"
echo
