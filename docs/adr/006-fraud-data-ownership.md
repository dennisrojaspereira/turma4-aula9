# ADR 006 — Ownership de dados: Fraud Store separado

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 12

## Context

Desde o ADR 003, o Fraud Service roda em processo próprio, mas lê `payments` e `accounts` do PostgreSQL do monólito, atrás de uma ACL. A ACL isola o conhecimento sobre o schema em um arquivo, mas não elimina o acoplamento. Evidências acumuladas:

- Para testar o Fraud Service, precisamos de uma **cópia do schema do monólito** (`legacy-schema.sql`), que ninguém mantém sincronizada.
- `scripts/shared-db-breakage-demo.sh`: um `ALTER TABLE payments RENAME COLUMN` feito pelo time de Payment derruba o Fraud Service. Ninguém avisou porque ninguém sabia que precisava.
- O Fraud Service escala para 5 Pods (lab 11); cada um abre um pool contra o mesmo banco que Payment usa. Escalar Fraud pressiona o banco de Payment.
- `fraud_evaluations` e `fraud_blacklist` são tabelas de Fraud, mas vivem em migrations do monólito (V1, V2, V3). Quem é o dono?

A pergunta do lab 18: **quanta autonomia realmente conquistamos?** Resposta honesta: autonomia de runtime e de deploy. Zero autonomia de dados.

## Decision

O Fraud Service passa a ter um **armazenamento lógico próprio** (Fraud Store):

- banco `fraud_db`, usuário `fraud`, na mesma instância PostgreSQL do laboratório;
- o usuário `fraud` **não consegue conectar** ao banco `techpix`, e vice-versa (`REVOKE CONNECT`);
- schema criado e evoluído pelo Flyway **do Fraud Service**, em `fraud-service/src/main/resources/db/migration`;
- `fraud_evaluations` e `fraud_blacklist` migram para lá (são de Fraud);
- o histórico de pagamentos vira uma **visão local** (`payment_history`): uma cópia intencional, com as colunas que Fraud precisa e mais nenhuma;
- as regras leem a visão local pela mesma porta `PaymentHistory`; a implementação `LegacySchemaPaymentHistory` continua no código, desligada, para reproduzir a etapa 8.

O princípio não é "microserviço = banco físico obrigatório". É: **dados precisam ter um dono claro, e ninguém além do dono escreve ou lê a tabela diretamente.** Instância separada é uma decisão de isolamento de falha e performance, tomada à parte.

## Alternatives

- **Instância PostgreSQL separada.** Mais isolamento (falha, CPU, I/O). Rejeitada para o laboratório pelo custo de memória no notebook. Em produção, seria a escolha natural para Fraud, que tem perfil de carga distinto. A diferença entre "database" e "instância" é explicada, não escondida.
- **Schema separado no mesmo banco (`fraud.*`).** Dá ownership, mas não impede um `SELECT public.payments` por descuido; a fronteira é convenção. Rejeitado porque queremos a fronteira **imposta** (o usuário `fraud` não enxerga `payments`).
- **Views ou réplica de leitura do banco de Payment.** Fraud leria uma view mantida por Payment. Reduz o acoplamento de schema (a view é o contrato), mas mantém o acoplamento de disponibilidade e de performance. É uma etapa intermediária legítima que decidimos pular porque o laboratório quer chegar ao problema seguinte (como a visão local é alimentada).
- **Manter o banco compartilhado.** Rejeitado pelas evidências acima.

## Consequences

Positivas:

- Migration de Payment não quebra Fraud. Testes de Fraud não precisam do schema de Payment.
- Escalar Fraud não pressiona o banco de Payment.
- Ownership explícito: `fraud_db` é de Fraud. Ponto.

Negativas:

- **O JOIN desapareceu.** `SELECT ... FROM payments` não existe mais para Fraud. A visão local nasce **vazia**. Um Fraud Service com visão local vazia é cego para velocity. Isso é o problema que Kafka resolve (ADR 007), e é por isso que Kafka entra depois deste ADR, não antes.
- **Duplicação intencional de dados.** `payment_history` é uma cópia. Cópias ficam desatualizadas; a consistência entre `payments` e `payment_history` é eventual.
- **Dois bancos para operar**, com backups, migrations e credenciais separadas.
- **Fraud não sabe mais quando uma conta foi aberta.** Não é dono de `accounts`. O que sabe vem dos eventos (`payerAccountOpenedAt`). É uma aproximação aceita.
- **A regra `new-account` muda de semântica** sem ninguém alterar seu código: "conta aberta há menos de 24 h" vira "Fraud nunca viu esta conta". Divergências desse tipo aparecem no Parallel Run.
