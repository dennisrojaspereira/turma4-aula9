# Aula 9: logs estruturados de todos os servicos para uma transacao. Uso: scripts/investigate-logs.sh PIX-928371 (equivalente a investigate-logs.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" logs @args
