# Navega pelos momentos da arquitetura (equivalente a step.sh).
#   .\scripts\step.ps1            -> lista as tags
#   .\scripts\step.ps1 4          -> checkout da etapa 4
#   .\scripts\step.ps1 main       -> volta para main
param([string]$Step = "list")
Set-Location (Join-Path $PSScriptRoot "..")
if ($Step -eq "list") { git tag --list 'aula07-step-*' | Sort-Object; exit 0 }
if ($Step -eq "main") { git checkout -q main; Write-Host "main: a historia completa"; exit 0 }
$tag = git tag --list ("aula07-step-{0:D2}-*" -f [int]$Step) | Select-Object -First 1
if (-not $tag) { Write-Host "etapa $Step nao existe"; exit 1 }
git checkout -q $tag
Write-Host "agora em $tag (git checkout main para voltar)"
