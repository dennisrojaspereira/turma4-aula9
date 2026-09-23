# Para tudo que o docker compose subiu. Passe -v para apagar os dados do banco.
Set-Location (Join-Path $PSScriptRoot "..")
docker compose --profile app down @args
