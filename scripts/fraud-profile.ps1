# Troca o perfil de regras de Fraud em tempo de execucao (equivalente a fraud-profile.sh).
param([string]$Profile)
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
if (-not $Profile) {
    Invoke-RestMethod -Uri "$base/admin/fraud/profile" | ConvertTo-Json
} else {
    Invoke-RestMethod -Method Put -Uri "$base/admin/fraud/profile" -ContentType "application/json" -Body "{`"profile`":`"$Profile`"}" | ConvertTo-Json
}
