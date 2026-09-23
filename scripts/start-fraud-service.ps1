# Sobe o Fraud Service localmente pelo Maven (equivalente a start-fraud-service.sh).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
docker compose up -d postgres | Out-Null
Write-Host "Iniciando Fraud Service em http://localhost:8081"
.\mvnw.cmd -q -pl fraud-service spring-boot:run
