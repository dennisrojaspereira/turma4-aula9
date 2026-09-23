#!/usr/bin/env bash
# Rollback: volta 100% dos pagamentos para o Fraud legado, in-process. Uma chamada HTTP, nenhum deploy.
set -euo pipefail
exec "$(dirname "$0")/fraud-mode.sh" LEGACY
