# Troca o modo do Strangler em tempo de execucao (equivalente a fraud-mode.sh).
param([string]$Mode)
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
if (-not $Mode) {
    Invoke-RestMethod -Uri "$base/admin/fraud/mode" | ConvertTo-Json
} else {
    Invoke-RestMethod -Method Put -Uri "$base/admin/fraud/mode" -ContentType "application/json" -Body "{`"mode`":`"$Mode`"}" | ConvertTo-Json
}
