# Lab 04 — Otimizar antes de distribuir

**Slide suportado:** 6 — Otimizar antes de distribuir
**Tag:** `aula07-step-04-optimized`

## Problema

O lab 03 provou: Fraud é o hotspot, e o custo está em consultas sem índice, um N+1, dois carregamentos do histórico inteiro e uma espera de rede dentro da transação.

Nenhum desses problemas é resolvido por um serviço separado. Um Fraud Service faria as mesmas consultas no mesmo banco. A solução mais barata é otimizar Fraud **onde ele está**.

## O que observar

```text
queries/payment        antes e depois
fraud p95              antes e depois
throughput             antes e depois
pg_stat_statements     as consultas de Fraud devem sumir do topo
seq_scan vs idx_scan   em payments
usage mean ms          quanto tempo cada conexao fica presa
```

## Hipóteses

Cada medida abaixo ataca uma linha específica do lab 03:

| Evidência (lab 03) | Medida | Onde |
|---|---|---|
| 34 mil seq scans em `payments` | índices em (payer, created_at), (payee, created_at), (device, created_at) | [V3__fraud_indexes.sql](../../monolith/src/main/resources/db/migration/V3__fraud_indexes.sql) |
| velocity 3 consultas, average-ticket e behavioral-hour carregando o histórico inteiro, recent-rejections N+1 | uma consulta agregada com `count(*) FILTER`, `sum`, `avg` | [PayerHistoryRule.java](../../monolith/src/main/java/com/techpix/fraud/rules/optimized/PayerHistoryRule.java) |
| payee-reputation e payee-velocity: 2 seq scans | uma consulta agregada | [PayeeHistoryRule.java](../../monolith/src/main/java/com/techpix/fraud/rules/optimized/PayeeHistoryRule.java) |
| blacklist: 3 consultas por pagamento | cache em memória com refresh a cada 30 s | [BlacklistCache.java](../../monolith/src/main/java/com/techpix/fraud/rules/optimized/BlacklistCache.java) |
| `usage mean` de 1 s: conexão presa durante a avaliação | avaliar fraude **fora** da transação | [PaymentService.java](../../monolith/src/main/java/com/techpix/payment/PaymentService.java) |

## Mudança

Um terceiro perfil, `HEAVY_OPTIMIZED`, com 13 regras que cobrem o mesmo risco das 17 do `HEAVY`. Os limiares e pesos são idênticos. O que mudou foi o custo.

O fluxo de pagamento passou a ter três fases:

```text
tx curta   ->  registrar PENDING
sem tx     ->  Fraud avalia (cada consulta pega e devolve a conexao)
tx curta   ->  liquidar ou rejeitar
```

## Como executar

```bash
scripts/start.sh                       # Flyway aplica os indices (V3) no banco existente
scripts/fraud-profile.sh HEAVY
scripts/load-test.sh custom 40 45s
scripts/show-metrics.sh
scripts/fraud-profile.sh HEAVY_OPTIMIZED
scripts/load-test.sh custom 40 45s
scripts/show-metrics.sh
scripts/slow-queries.sh
```

Para mostrar só o efeito dos índices, ao vivo:

```bash
scripts/indexes.sh drop
scripts/load-test.sh custom 40 30s
scripts/indexes.sh create
scripts/load-test.sh custom 40 30s
```

## Como testar

- `OptimizedFraudIT`: o perfil otimizado faz menos da metade das consultas e toma a mesma decisão com o mesmo score; a blacklist em cache bloqueia com zero consultas.
- `QueryCountIT`, `FraudProfileIT`: continuam verdes. Os perfis antigos continuam existindo, o que permite comparar ao vivo.

## Resultado (medido)

Mesma máquina, mesma carga (40 VUs, 45 s), mesmos 200 mil pagamentos históricos.

| | HEAVY, sem índices, 1 transação (lab 03) | HEAVY, com índices, 3 fases | HEAVY_OPTIMIZED |
|---|---|---|---|
| Throughput | 9 TPS | 133 TPS | **193 TPS** |
| p95 HTTP | 6,86 s | 326 ms | **213 ms** |
| p95 Fraud | 1,44 s | 249 ms | **126 ms** |
| queries / pagamento | 32,5 | 33,9 | **16** |
| queries / Fraud | 21,9 | 23,6 | **6** |
| conexão presa por pagamento (`usage mean`) | 1.040 ms | 2,4 ms | **3,1 ms** |
| espera por conexão (`acquire mean`) | 2.929 ms | 3,2 ms | **3,2 ms** |

A segunda coluna é reveladora: **só os índices e a transação curta** deram 14x de throughput, sem mudar uma regra. A terceira coluna mostra o que reescrever as consultas acrescenta.

Por regra, depois:

```text
  external-provider        37.6 ms     0.0 queries   <- agora e a regra mais cara
  new-account               8.0 ms     1.0 queries
  payer-history             6.0 ms     1.0 queries   <- era velocity+average+behavioral+rejections: 100+ ms
  payee-history             4.9 ms     1.0 queries
  device-fingerprint        4.8 ms     1.0 queries   <- era 95 ms; mesma consulta, agora com indice
  blacklist-cached          0.0 ms     0.0 queries   <- era 26 ms e 3 consultas
  ml-scoring                0.4 ms     0.0 queries
```

No banco, a consulta mais cara agora leva 0,5 ms. `payments` passou de 34 mil seq scans para 197 mil index scans.

## Se isso bastasse, poderíamos parar aqui

Esta frase é o ponto central do slide 6. Se a Tech Pix atende seus requisitos com 193 TPS e p95 de 213 ms, **não há motivo para distribuir**. O monólito otimizado é a arquitetura correta.

Repare no que sobrou como custo dominante de Fraud: `external-provider` (espera de rede) e, quando as iterações do modelo sobem, `ml-scoring` (CPU). Nenhum dos dois é problema de banco. Guarde essa observação para o lab 06.

## Trade-offs

| Otimização | Custo |
|---|---|
| índices | escrita um pouco mais cara; espaço em disco |
| consulta agregada | SQL mais complexo, regras acopladas em uma consulta |
| cache de blacklist | uma conta banida pode levar até 30 s para ser bloqueada |
| fraude fora da transação | se o processo cair entre as fases, sobra um pagamento PENDING. Antes isso não existia. |

O último é o mais importante. Ao dividir a transação, abrimos mão de uma garantia. Fizemos isso conscientemente e medimos o ganho. Isso é engenharia; fazer sem medir é aposta.

## Pergunta para discussão

> A transação única era uma garantia. Quanto ela custava? Quanto vale?

## Professor Notes

```text
Pergunta:
Depois de otimizar, ainda precisamos extrair Fraud?

Resposta:
Não necessariamente. Se os requisitos estao atendidos, o monolito otimizado e a resposta.

Erro comum:
Tratar otimizacao como "gambiarra" e distribuicao como "solucao definitiva".
Indices resolveram 14x. Nenhum microservico resolve 14x.

Conceito:
Otimizar antes de distribuir. A solucao mais simples que resolve o problema medido.

Transicao:
O codigo de Fraud cresceu regra a regra dentro do monolito. Antes de qualquer outra decisao,
precisamos saber onde Fraud comeca e termina. Modularidade primeiro.
```

## Próximo problema

Fraud está rápido, mas seu código está entrelaçado com Payment: qualquer classe pode chamar qualquer repositório. Antes de pensar em extrair, precisamos de fronteiras. [Lab 05](05-modular-monolith.md).
