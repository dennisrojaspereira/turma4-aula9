# Aula 9: reconcilia uma transacao contra o ledger e o PSP. Uso: scripts/reconcile.sh PIX-928371 (equivalente a reconcile.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" reconcile @args
