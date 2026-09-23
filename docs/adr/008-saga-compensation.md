# ADR 008 — Compensação (Saga mínima) para a liquidação de pagamentos

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 14

## Context

Desde o lab 04 o pagamento não é mais uma transação única. Desde o lab 08 ele atravessa dois processos. Desde o lab 18, dois bancos:

```text
1. Payment   registra PENDING                 (techpix, tx local)
2. Fraud     avalia e registra EVALUATED      (fraud_db, tx local, outro processo)
3. Payment   liquida: saldo + ledger + APPROVED (techpix, tx local)
4. Payment   publica PaymentApproved          (Kafka)
```

Se o passo 3 falhar (ledger indisponível), o passo 2 **já aconteceu em outro banco**. Não há `ROLLBACK` que alcance `fraud_db`. O Fraud Service ficaria com um pagamento `EVALUATED` para sempre, contando na velocity do pagador, sem que dinheiro algum tenha se movido.

Isso é uma operação que atravessa serviços e precisa de coordenação. **Só agora** ela existe. Antes, não havia motivo para falar em Saga.

## Decision

Compensação por evento, na forma mais simples que resolve o problema:

- Payment captura a falha do passo 3, marca o pagamento como `FAILED` (não `REJECTED`: não é fraude) e publica `PaymentFailed`.
- Fraud consome `PaymentFailed` e marca o registro como `FAILED`. Ele deixa de contar como rejeição; o pagador não é penalizado por uma falha de infraestrutura.
- O cliente recebe 503 e pode tentar um novo pagamento.

```text
local transaction  (Payment: FAILED)
      +
event              (PaymentFailed)
      +
compensation       (Fraud: EVALUATED -> FAILED)
```

Isso é **coreografia**: cada serviço reage a eventos; ninguém coordena. Escolhida porque há um único passo a compensar e dois participantes.

## Alternatives

- **Transação distribuída (2PC/XA) entre `techpix` e `fraud_db`.** Rejeitada: acopla disponibilidade dos dois bancos, é lenta, e o Kafka não participa.
- **Orquestração (um coordenador com máquina de estados).** Adequada quando a saga tem vários passos, ordem, timeouts e compensações em cadeia (reserva, cobrança, envio). Para um passo, é máquina demais. Se Notification, Ledger e Account virarem serviços, reavaliar.
- **Fraud não registrar nada até o `PaymentApproved` chegar.** Elimina a compensação, mas cega a velocity para pagamentos em andamento: uma rajada de 50 pagamentos em 10 segundos seria invisível até os eventos chegarem. Rejeitada.
- **Não compensar e aceitar `EVALUATED` órfãos.** Tolerável a curto prazo, errado a longo: o pagador acumula histórico falso.

## Consequences

Positivas:

- Sem transação distribuída. Cada serviço tem uma transação local e um evento.
- O pagador não é punido por falha de infraestrutura.

Negativas:

- **Janela de inconsistência.** Entre a falha em Payment e o consumo em Fraud, o registro está `EVALUATED`. Uma avaliação nesse intervalo o conta. É eventual.
- **Dual write de novo.** `FAILED` é commitado e depois `PaymentFailed` é publicado. Se o Kafka estiver fora nesse instante, a compensação se perde. A resposta é o outbox pattern (evento gravado na mesma transação, publicado por um relay). Não implementado; é a próxima dívida documentada.
- **Semântica nova para todo mundo.** `FAILED` existe em Payment, no evento e na visão de Fraud. Cada consumidor futuro precisa saber que não é `REJECTED`.
- **Coreografia não escala em complexidade.** Com cinco serviços e três compensações, ninguém consegue responder "em que estado está o pagamento X?" sem juntar logs. Esse é o momento de orquestrar.
