# Gera carga contra o monolito (equivalente a load-test.sh).
param([string]$Mode = "baseline", [int]$Vus = 10, [string]$Duration = "30s")
Set-Location (Join-Path $PSScriptRoot "..")
switch ($Mode) {
    "baseline" { $Vus = 5;  $Duration = "30s" }
    "growth"   { $Vus = 40; $Duration = "60s" }
    "custom"   { }
    default    { Write-Host "uso: load-test.ps1 [baseline|growth|custom -Vus N -Duration 30s]"; exit 1 }
}
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
Write-Host "== load-test mode=$Mode vus=$Vus duration=$Duration"
k6 run -e VUS=$Vus -e DURATION=$Duration -e BASE_URL=$base load-tests/payment-load.js
