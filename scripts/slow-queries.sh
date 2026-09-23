#!/usr/bin/env bash
# Top consultas por tempo total no PostgreSQL, via pg_stat_statements.
#   scripts/slow-queries.sh          -> top 10
#   scripts/slow-queries.sh reset    -> zera as estatisticas (util antes de um teste de carga)
set -euo pipefail
cd "$(dirname "$0")/.."
PSQL="docker compose exec -T postgres psql -U techpix -d techpix"

$PSQL -q -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;" >/dev/null

if [ "${1:-}" = "reset" ]; then
  $PSQL -q -c "SELECT pg_stat_statements_reset();" >/dev/null
  echo "pg_stat_statements zerado."
  exit 0
fi

$PSQL -c "
SELECT
  calls,
  round(total_exec_time::numeric / 1000, 1)        AS total_s,
  round(mean_exec_time::numeric, 2)                AS mean_ms,
  round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 1) AS pct,
  rows / GREATEST(calls, 1)                        AS rows_per_call,
  left(regexp_replace(query, '\s+', ' ', 'g'), 90) AS query
FROM pg_stat_statements
WHERE query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC
LIMIT 10;
"

echo
echo "== Sequential scans por tabela (quem esta varrendo a tabela inteira?)"
$PSQL -c "
SELECT relname, seq_scan, seq_tup_read, idx_scan, n_live_tup
FROM pg_stat_user_tables
ORDER BY seq_tup_read DESC;
"
