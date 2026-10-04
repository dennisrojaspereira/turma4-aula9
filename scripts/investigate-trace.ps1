# Aula 9: trace distribuido (waterfall de spans) de uma transacao. Uso: scripts/investigate-trace.sh PIX-928371 (equivalente a investigate-trace.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" trace @args
