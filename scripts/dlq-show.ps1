# Aula 9: mostra a Dead Letter Queue. 'scripts/dlq-show.sh replay' reprocessa apos corrigir a causa. (equivalente a dlq-show.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" dlq @args
