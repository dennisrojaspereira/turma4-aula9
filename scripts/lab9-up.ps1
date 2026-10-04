# Aula 9: sobe o ambiente do laboratorio "Cade o Pix?" (equivalente a lab9-up.sh).
#   scripts/lab9-up.ps1        -> sobe o servidor em background e reinicia o estado
#   scripts/lab9-up.ps1 stop   -> derruba o servidor
param([string]$Action)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($Action -eq "stop") {
    python "$dir\aula9\lab9.py" stop
} else {
    python "$dir\aula9\lab9.py" reset
    python "$dir\aula9\lab9.py" up
}
