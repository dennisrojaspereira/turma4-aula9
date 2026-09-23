# Liga o Parallel Run (equivalente a enable-parallel-run.sh).
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
Invoke-RestMethod -Method Delete -Uri "$base/admin/fraud/parallel-run" | Out-Null
& (Join-Path $PSScriptRoot "fraud-mode.ps1") -Mode PARALLEL
Write-Host "Relatorio: .\scripts\parallel-run-report.ps1"
