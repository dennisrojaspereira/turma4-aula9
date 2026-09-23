# Rollback: volta 100% dos pagamentos para o Fraud legado (equivalente a rollback.sh).
& (Join-Path $PSScriptRoot "fraud-mode.ps1") -Mode LEGACY
