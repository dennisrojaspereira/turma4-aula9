# ADR 003 — Extrair Fraud como serviço, de forma incremental

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 6 e 7

## Context

Fraud foi otimizado (lab 04) e modularizado (lab 05). Seu custo dominante passou a ser CPU e rede. Medido (lab 06): dobrar o custo de CPU de Fraud deixou uma rota sem Fraud 8x mais lenta, porque dividem o processo.

O [Decision Framework](../architecture/decision-framework.md) mostra sete de nove critérios apontando para perfis operacionais distintos entre Payment e Fraud.

## Decision

Fraud vira um processo Spring Boot separado, o **Fraud Service**, exposto por HTTP.

A extração é incremental (Strangler Fig):

1. Payment passa a chamar Fraud por uma fachada (`FraudFacade`) que escolhe entre a implementação legada (in-process) e a remota, por configuração alterável em runtime.
2. O legado **não é removido**. Convive com o novo até o novo provar que é equivalente (Parallel Run) e seguro (Canary).
3. Rollback é uma mudança de configuração, não um deploy.

O Fraud Service começa usando o **mesmo PostgreSQL** do monólito. Isso é uma dívida assumida conscientemente: o objetivo desta etapa é separar o runtime, não os dados.

## Alternatives

- **Manter no monólito e escalar tudo.** Rejeitado pelo custo: 30 réplicas de Account, Ledger e Notification que ninguém pediu, cada uma com pool no mesmo banco.
- **Extrair tudo (Account, Ledger, Notification também).** Rejeitado. Nenhum deles tem perfil operacional diferente de Payment. Extrair sem evidência é adicionar rede sem contrapartida.
- **Fraud assíncrono (fila) em vez de HTTP.** Considerado. Payment precisa da decisão antes de liquidar; uma fila exigiria que o pagamento ficasse pendente esperando resposta. Possível, mas muda o contrato com o cliente. HTTP síncrono preserva o contrato atual. Kafka entrará, mas para outro problema (histórico), não para a decisão.
- **Extração big-bang.** Rejeitado. Sem rollback simples, sem comparação. O Strangler Fig custa uma fachada e vale cada linha.

## Consequences

Positivas:

- Fraud escala sozinho. CPU de Fraud não rouba CPU de Payment.
- Deploy de regras não exige deploy de Payment.
- Falha de Fraud pode ser tratada como degradação, não como queda.

Negativas (e todas vão aparecer nos labs seguintes):

- **Rede entre Payment e Fraud.** Latência, timeout, retry, erro parcial. Antes, uma chamada de método não falhava.
- **Duas unidades de deploy.** Versionamento de contrato entre elas.
- **Observabilidade distribuída.** Um pagamento agora atravessa dois processos.
- **Dados compartilhados.** Fraud Service lê `payments` diretamente. Uma migration de Payment pode quebrar Fraud em silêncio. Endereçado no ADR 006.
- **Consistência.** A avaliação de Fraud e a liquidação do pagamento já estavam em transações separadas desde o lab 04. Agora estão em processos separados. Uma falha entre as fases deixa estado a reconciliar.
- **Onde Fraud roda?** Alguém precisa iniciar, reiniciar, escalar e encontrar o processo. Isso é o problema que Kubernetes resolve, e é por isso que ele entra na sequência, e não antes.
