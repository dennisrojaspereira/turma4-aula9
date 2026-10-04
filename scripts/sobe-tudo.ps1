# Sobe todo o ambiente das aulas 7-9 com um comando (equivalente: python scripts/sobe-tudo.py).
param([string]$Action)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($Action) { python "$dir\sobe-tudo.py" $Action } else { python "$dir\sobe-tudo.py" }
