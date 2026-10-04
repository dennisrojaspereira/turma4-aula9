# Aula 9: volta o laboratorio ao estado inicial (incidente 1, PIX-928371 em UNKNOWN). (equivalente a lab9-reset.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" reset @args
