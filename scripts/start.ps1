# Sobe o PostgreSQL e o monolito localmente (equivalente a start.sh).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

docker compose up -d postgres
Write-Host "Aguardando PostgreSQL..."
do {
    Start-Sleep -Seconds 1
    docker compose exec -T postgres pg_isready -U techpix -d techpix | Out-Null
} until ($LASTEXITCODE -eq 0)
Write-Host "PostgreSQL pronto. Iniciando monolito em http://localhost:8080"
.\mvnw.cmd -q -pl monolith spring-boot:run
