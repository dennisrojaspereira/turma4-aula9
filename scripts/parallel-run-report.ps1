# Relatorio do Parallel Run (equivalente a parallel-run-report.sh).
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
$d = Invoke-RestMethod -Uri "$base/admin/fraud/parallel-run"
Write-Host "== Parallel Run"
Write-Host "  comparacoes:        $($d.total)"
Write-Host "  match rate:         $(if ($null -eq $d.matchRate) { 'n/a' } else { '{0:P1}' -f $d.matchRate })"
$d.outcomes.PSObject.Properties | ForEach-Object { Write-Host ("    {0,-18} {1}" -f $_.Name, $_.Value) }
Write-Host ("  legacy latency ms:  {0:N1}" -f $d.legacyLatencyMeanMs)
Write-Host ("  new latency ms:     {0:N1}" -f $d.newLatencyMeanMs)
Write-Host ("  score diff (media): {0:N2}" -f $d.scoreDifferenceMean)
Write-Host ""
Write-Host "== Ultimas divergencias"
$d.lastDivergences | Select-Object -First 10 | ForEach-Object {
    Write-Host ("  {0}  {1,-18} legacy={2}/{3}  new={4}/{5}" -f $_.paymentId.Substring(0, 8), $_.outcome, $_.legacyScore, $_.legacyDecision, $_.newScore, $_.newDecision)
}
