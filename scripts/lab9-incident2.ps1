# Aula 9: ativa o desafio final (segundo incidente, sem causa informada). 'off' volta ao incidente 1. (equivalente a lab9-incident2.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" incident2 @args
