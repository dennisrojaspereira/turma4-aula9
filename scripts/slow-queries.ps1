# Top consultas por tempo total no PostgreSQL (equivalente a slow-queries.sh).
param([string]$Action)
Set-Location (Join-Path $PSScriptRoot "..")

docker compose exec -T postgres psql -U techpix -d techpix -q -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;" | Out-Null

if ($Action -eq "reset") {
    docker compose exec -T postgres psql -U techpix -d techpix -q -c "SELECT pg_stat_statements_reset();" | Out-Null
    Write-Host "pg_stat_statements zerado."
    exit 0
}

$sql = @"
SELECT calls,
  round(total_exec_time::numeric / 1000, 1) AS total_s,
  round(mean_exec_time::numeric, 2) AS mean_ms,
  round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 1) AS pct,
  rows / GREATEST(calls, 1) AS rows_per_call,
  left(regexp_replace(query, '\s+', ' ', 'g'), 90) AS query
FROM pg_stat_statements
WHERE query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC LIMIT 10;
"@
docker compose exec -T postgres psql -U techpix -d techpix -c $sql

Write-Host ""
Write-Host "== Sequential scans por tabela"
docker compose exec -T postgres psql -U techpix -d techpix -c "SELECT relname, seq_scan, seq_tup_read, idx_scan, n_live_tup FROM pg_stat_user_tables ORDER BY seq_tup_read DESC;"
