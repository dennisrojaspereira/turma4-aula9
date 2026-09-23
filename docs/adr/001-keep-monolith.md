# ADR 001 — Manter o monólito

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 1

## Context

A Tech Pix nasce com cinco capacidades de negócio (Account, Payment, Ledger, Fraud, Notification). O volume é de 10 TPS, p95 de 180 ms, banco em 20% de CPU. O time é pequeno. Fraud tem cinco regras.

A pergunta que surge em toda empresa nova é: "não deveríamos começar já com microserviços?"

## Decision

Um único processo Spring Boot e um único PostgreSQL. Uma transação por pagamento.

## Alternatives

- **Microserviços desde o início.** Rejeitado. Não existe nenhum problema observável que a distribuição resolveria. Ela traria rede, timeout, retry, consistência eventual e complexidade de deploy sem contrapartida.
- **Monólito modular desde o início.** Considerado. Boundaries explícitos custam pouco e serão introduzidos quando o código de Fraud começar a se entrelaçar com Payment. Não é necessário hoje.

## Consequences

Positivas:

- Deploy único, debug local, consistência forte.
- Qualquer pessoa do time entende o fluxo completo lendo `PaymentService`.

Negativas:

- Todos os módulos escalam juntos. Se Fraud precisar de mais CPU, Payment paga a conta.
- Todos fazem deploy juntos. Um bug em Notification pode atrasar um hotfix em Payment.
- Nenhum índice além das chaves primárias. Isso só importa quando as tabelas crescerem.
- Fraud lê tabelas de Payment diretamente. Hoje é conveniência. Amanhã é acoplamento.

Aceitamos essas consequências porque **ainda não doem**. Este ADR será revisitado quando alguma delas doer de forma mensurável.
