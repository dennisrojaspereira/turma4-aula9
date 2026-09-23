# Popula o "passado" da Tech Pix (equivalente a seed.sh).
param([int]$Accounts = 2000, [int]$Payments = 200000, [int]$Blacklist = 2000)
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
Write-Host "Gerando $Accounts contas, $Payments pagamentos e $Blacklist entradas de blacklist..."
Invoke-RestMethod -Method Post -Uri "$base/admin/seed?accounts=$Accounts&payments=$Payments&blacklist=$Blacklist" | ConvertTo-Json
