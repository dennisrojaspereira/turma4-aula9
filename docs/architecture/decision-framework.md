# Decision Framework — extrair Fraud?

Este documento existe porque "vamos fazer microserviços" não é uma decisão. É uma vontade. Uma decisão compara critérios com evidência.

## Evidência medida (lab 04 e lab 06)

Depois da otimização, o custo dominante de Fraud não é mais banco. É espera de rede (provider externo) e CPU (modelo). Aumentando só o custo do modelo de Fraud, com a mesma carga:

| | Fraud barato (ML 200k iterações) | Fraud caro (ML 20M iterações) |
|---|---|---|
| `GET /accounts/{id}` p50 (rota **sem** Fraud) | 3,8 ms | 30 ms |
| `GET /accounts/{id}` p99 | 195 ms | 404 ms |
| `POST /payments` p95 | 462 ms | 919 ms |
| Throughput | 171 req/s | 98 req/s |

Uma rota que não chama Fraud ficou 8x mais lenta porque Fraud consome a CPU do mesmo processo. Para dar CPU a Fraud, precisaríamos escalar o monólito inteiro: Payment, Account, Ledger e Notification junto.

## Critérios

| Critério | Payment | Fraud | Diferença relevante? |
|---|---|---|---|
| **Escala** | proporcional ao número de pagamentos, previsível | proporcional ao número de regras x histórico x custo do modelo; picos quando o modelo muda | sim |
| **CPU** | baixa: validação, duas transações curtas | alta: modelo de risco; cresce com a sofisticação do modelo | sim |
| **Memória** | baixa | média: cache de blacklist, futuramente features do modelo | moderada |
| **Latência** | crítica: o cliente espera com o app aberto | tolerante a algumas dezenas de ms extras; já paga 30 ms de provider externo | sim |
| **Deploy independente** | raro: fluxo estável | frequente: regras mudam toda semana, modelo mensalmente | sim |
| **Ritmo de mudança** | baixo | alto | sim |
| **Isolamento de falha** | uma falha aqui é o incidente | uma falha aqui deveria degradar, não derrubar pagamentos | sim |
| **Equipe** | time de pagamentos | time de risco, com perfil de dados | sim |
| **Dados** | dono de `payments`, `accounts`, `ledger` | precisa de histórico de pagamentos, dono de `fraud_evaluations` e `fraud_blacklist` | conflito: Fraud lê dados de Payment |

## A pergunta

> Por que escalar Payment 30 vezes porque Fraud precisa de mais CPU?

Com 5 réplicas de Payment atendendo a demanda de pagamentos e Fraud precisando de 30 réplicas para o modelo, o monólito exige 30 réplicas de tudo. Isso é 25 réplicas de Account, Ledger e Notification que ninguém pediu, cada uma com seu pool de conexões no mesmo banco.

## Conclusão

Sete critérios de nove apontam para perfis operacionais distintos. O único critério que aponta contra é **dados**: Fraud precisa do histórico de Payment. Esse é o custo real da extração e será tratado explicitamente (labs 18 e 19). Não vamos fingir que ele não existe.

**Decisão: extrair Fraud como serviço.** Registrada no [ADR 003](../adr/003-extract-fraud-service.md).

## O que esta decisão NÃO diz

- Não diz que Account, Ledger ou Notification devem ser extraídos. Nenhum deles tem perfil operacional diferente de Payment. Continuam no monólito.
- Não diz que a extração deve ser feita de uma vez. Será incremental (Strangler Fig).
- Não diz que Fraud precisa de um banco próprio hoje. Isso virá quando o acoplamento de dados doer.
