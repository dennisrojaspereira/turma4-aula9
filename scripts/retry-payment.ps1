# Aula 9: reprocessa um pagamento. Sem flag so pergunta; --executar simula sem idempotencia; --idempotency-key CHAVE repete com seguranca. (equivalente a retry-payment.sh)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$dir\aula9\lab9.py" retry @args
