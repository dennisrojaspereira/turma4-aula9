# ADR 007 — Kafka para os fatos de Payment

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 13

## Context

Com o ADR 006, o Fraud Service deixou de ler `payments`. Sua visão local (`payment_history`) nasce vazia. Um Fraud Service sem histórico é cego para velocity, ticket médio, reputação de destinatário.

Fraud precisa saber, para cada pagamento: quem pagou, para quem, quanto, com qual dispositivo, quando, e se foi aprovado ou rejeitado. Nada mais. E não pode mais perguntar ao banco de Payment.

## Decision

Payment **publica fatos** no Kafka, tópico `payment-events`:

- `PaymentApproved` e `PaymentRejected`, com `eventId` determinístico (`paymentId + tipo`);
- chave da mensagem = `payerAccountId`: fatos do mesmo pagador vão para a mesma partição, em ordem;
- payload = o que outros contextos precisam (`amount`, `deviceId`, `payerAccountOpenedAt`, `occurredAt`); sem `rejection_reason`, sem saldo, sem ledger;
- publicado **depois** do commit da liquidação.

Fraud **consome** no consumer group `fraud-service` e mantém `payment_history` e `account_activity`. O consumo é idempotente por `eventId` (`processed_events`, na mesma transação da atualização).

`PaymentCreated` **não** é publicado: nenhum consumidor precisa dele hoje. Fraud registra o pagamento em andamento na própria avaliação (status `EVALUATED`).

Kafka entra **só aqui**. A decisão de fraude continua síncrona por HTTP (Payment precisa da resposta antes de liquidar). Notification, Ledger e Account não publicam nem consomem nada: não há motivo.

## Alternatives

- **Fraud continuar lendo `payments` por uma view/réplica.** Reduz o acoplamento de schema, mantém o de disponibilidade. Rejeitado após o ADR 006.
- **Payment chamar Fraud por HTTP a cada aprovação ("avise o Fraud").** Acoplamento temporal: se Fraud estiver fora, Payment falha ou perde o aviso. Retry vira responsabilidade de Payment. Rejeitado.
- **Fila ponto a ponto (RabbitMQ, SQS).** Funciona para um consumidor. Kafka foi escolhido porque os fatos de pagamento interessam a mais de um contexto (Fraud hoje; relatórios, notificações e antifraude analítico amanhã) e porque o log retido permite reconstruir a visão local do zero (replay). Para um único consumidor, uma fila seria mais simples.
- **CDC (Debezium) sobre `payments`.** Publica mudanças da tabela sem tocar no código de Payment. Poderoso, mas expõe o **schema** de Payment como contrato, que é exatamente o acoplamento que acabamos de remover. Rejeitado; seria aceitável com um outbox table como fonte.
- **Decisão de fraude também assíncrona.** Mudaria o contrato com o cliente (pagamento fica pendente). Fora de escopo.

## Consequences

Positivas:

- Fraud tem o histórico sem depender do banco nem da disponibilidade de Payment.
- Payment não sabe quem consome. Um novo consumidor não exige mudança em Payment.
- O log do Kafka permite reconstruir `payment_history` (replay do tópico).

Negativas:

- **Consistência eventual.** Entre o commit em Payment e a linha em `payment_history` há um intervalo. Um pagamento pode ser avaliado antes de o anterior chegar à visão local. Mitigado (não eliminado) pelo registro `EVALUATED` na própria avaliação.
- **Entrega pelo menos uma vez.** Duplicatas são normais. Sem idempotência, `account_activity` conta em dobro. `IdempotencyIT` mostra o problema e a solução.
- **Dual write.** Payment commita no banco e depois publica. Se o Kafka estiver fora entre os dois, o pagamento existe e o fato se perde. A solução é o outbox pattern (gravar o evento na mesma transação e publicar a partir da tabela). Não implementado; documentado no lab 20.
- **Ordem só por chave.** Fatos de pagadores diferentes chegam em qualquer ordem. Uma regra que dependesse de ordem global entre pagadores estaria errada.
- **Mais infraestrutura.** Um broker para operar, tópicos para governar, contratos de evento para versionar.
- **Duplicação de dados.** `payment_history` é uma cópia. Cópias precisam de política: por quanto tempo? o que fazer se divergirem?
