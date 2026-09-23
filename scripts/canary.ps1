# Canary: manda N% dos pagamentos para o Fraud Service (equivalente a canary.sh).
param([int]$Percentage = -1)
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
if ($Percentage -lt 0) {
    Write-Host "mode:   $((Invoke-RestMethod -Uri "$base/admin/fraud/mode") | ConvertTo-Json -Compress)"
    Write-Host "canary: $((Invoke-RestMethod -Uri "$base/admin/fraud/canary") | ConvertTo-Json -Compress)"
    exit 0
}
Invoke-RestMethod -Method Put -Uri "$base/admin/fraud/canary" -ContentType "application/json" -Body "{`"percentage`":$Percentage}" | Out-Null
Invoke-RestMethod -Method Put -Uri "$base/admin/fraud/mode" -ContentType "application/json" -Body '{"mode":"CANARY"}' | Out-Null
Write-Host "canary: $Percentage% dos pagamentos decididos pelo Fraud Service"
