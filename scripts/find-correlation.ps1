# Aula 9: segue um correlation_id atraves dos servicos, em ordem. Uso: scripts/find-correlation.sh abc123 (equivalente a find-correlation.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" correlation @args
