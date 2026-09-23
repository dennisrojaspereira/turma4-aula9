#!/usr/bin/env bash
# Liga o Parallel Run: o legado decide, o Fraud Service roda em shadow, o comparador conta.
set -euo pipefail
BASE="${TECHPIX_URL:-http://localhost:8080}"
curl -s -X DELETE "$BASE/admin/fraud/parallel-run" >/dev/null
"$(dirname "$0")/fraud-mode.sh" PARALLEL
echo "Relatorio: scripts/parallel-run-report.sh"
