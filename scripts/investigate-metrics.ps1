# Aula 9: snapshot do dashboard de metricas (RED / USE / Golden Signals). (equivalente a investigate-metrics.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" metrics @args
