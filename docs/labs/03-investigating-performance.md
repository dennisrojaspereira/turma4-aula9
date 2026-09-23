# Lab 03 — Sintoma não é causa

**Slides suportados:** 4 — Sintoma não é causa · 5 — Encontramos o hotspot
**Tag:** `aula07-step-03-observability`

## Problema

Sabemos que está lento. Sabemos que o banco está saturado. Não sabemos **quem** está consumindo o banco nem **onde** o tempo é gasto dentro de um pagamento.

Sem instrumentos, qualquer decisão é palpite. Com instrumentos, a conclusão vira número.

## O que observar

Instrumentos adicionados nesta etapa, todos sem ferramenta externa obrigatória:

| Instrumento | Onde | Pergunta que responde |
|---|---|---|
| `payment.create` (timer) | Actuator | quanto demora um pagamento? |
| `fraud.evaluation` (timer) | Actuator | quanto disso é Fraud? |
| `fraud.rule{rule=...}` (timer) | Actuator | qual regra custa mais? |
| `payment.queries`, `fraud.queries` | Actuator | quantas consultas por pagamento? |
| `fraud.rule.queries{rule=...}` | Actuator | quantas consultas por regra? |
| `hikaricp.connections.acquire` | Actuator | quanto se espera por uma conexão? |
| `hikaricp.connections.usage` | Actuator | quanto tempo cada conexão fica presa? |
| `pg_stat_statements` | PostgreSQL | quais consultas custam mais no banco? |
| `pg_stat_user_tables.seq_scan` | PostgreSQL | quem varre a tabela inteira? |
| linha de log por pagamento | stdout | `totalMs=... fraudMs=... queries=...` |

O contador de consultas é um proxy do DataSource, em [QueryCountingDataSource.java](../../monolith/src/main/java/com/techpix/shared/observability/QueryCountingDataSource.java). Sem dependência nova. Cada regra é cronometrada em [FraudService.java](../../monolith/src/main/java/com/techpix/fraud/FraudService.java).

## Hipóteses

1. O banco está lento porque a máquina é pequena. (Então todas as consultas seriam lentas, inclusive as de Account e Ledger.)
2. Payment faz consultas demais. (Então `payment.queries - fraud.queries` seria alto.)
3. Fraud faz consultas demais e/ou consultas caras. (Então `fraud.queries` seria alto e `pg_stat_statements` mostraria consultas de Fraud no topo.)

## Mudança

Apenas instrumentação. **Nenhuma linha de lógica de negócio mudou.** Isso é importante: instrumentar primeiro, mudar depois.

## Como executar

```bash
scripts/start.sh                    # recria o postgres com pg_stat_statements
scripts/seed.sh                     # se ainda não populou
scripts/slow-queries.sh reset
scripts/fraud-profile.sh HEAVY
scripts/load-test.sh custom 40 45s
scripts/show-metrics.sh
scripts/slow-queries.sh
```

Opcional, com Prometheus e Grafana:

```bash
docker compose --profile observability up -d
# Grafana em http://localhost:3000, dashboard "Tech Pix — Payment vs Fraud"
```

## Resultado (medido)

`scripts/show-metrics.sh` depois de 45 s de carga com 40 usuários virtuais:

```text
== Payment (POST /payments)
  mean ms:         1036.0
  queries/payment: 32.5

== Fraud (FraudService.evaluate)
  mean ms:         957.6          <- 92% do tempo do pagamento
  queries/fraud:   21.9           <- 67% das consultas do pagamento

== Pool de conexoes (HikariCP)
  max:             10
  acquire mean ms: 2929.0         <- espera-se 3x mais por uma conexao do que se trabalha
  usage mean ms:   1040.2         <- cada conexao fica presa 1 s por pagamento

== Tempo medio por regra de Fraud (ms) e consultas por regra
  velocity                248.5 ms     3.0 queries
  recent-rejections       145.8 ms     7.8 queries   <- N+1
  device-fingerprint       95.1 ms     1.0 queries
  payee-reputation         82.7 ms     1.0 queries
  high-frequency           75.7 ms     1.0 queries
  payee-velocity           75.2 ms     1.0 queries
  average-ticket           73.7 ms     1.0 queries   <- carrega o historico inteiro
  behavioral-hour          70.9 ms     1.0 queries   <- carrega o mesmo historico de novo
  external-provider        37.8 ms     0.0 queries   <- dorme com a conexao presa
  blacklist                26.2 ms     3.0 queries
  ml-scoring                0.5 ms     0.0 queries
```

`scripts/slow-queries.sh`:

```text
 calls | total_s | mean_ms | pct  | rows_per_call | query
-------+---------+---------+------+---------------+-------------------------------------------------------------
  1792 |   119.5 |   66.67 | 38.0 |            10 | SELECT * FROM payments WHERE payer_account_id = $1 AND created_at >= $2 ...
   896 |    55.6 |   62.01 | 17.7 |           103 | SELECT * FROM payments WHERE payer_account_id = $1 AND id <> $2
   448 |    39.0 |   86.97 | 12.4 |             1 | SELECT count(DISTINCT payer_account_id) FROM payments WHERE device_id = $1 ...
   448 |    33.1 |   73.84 | 10.5 |             1 | SELECT count(*) FROM payments WHERE payee_account_id = $1 AND status = $3 ...
   448 |    30.2 |   67.48 |  9.6 |             1 | SELECT count(*) FROM payments WHERE payer_account_id = $1 AND created_at >= $2 ...
   448 |    28.9 |   64.41 |  9.2 |             1 | SELECT count(DISTINCT payer_account_id) FROM payments WHERE payee_account_id = $1 ...

 relname   | seq_scan | seq_tup_read  | idx_scan | n_live_tup
-----------+----------+---------------+----------+------------
 payments  |    34333 | 3.767.155.875 |    35650 |     207171
```

As seis consultas mais caras somam **97% do tempo do banco**. Todas são de Fraud. Todas fazem sequential scan em `payments`: 34 mil varreduras completas, 3,7 bilhões de tuplas lidas, em 45 segundos.

Hipótese 1 descartada: `INSERT INTO ledger_entries` custa 0,56 ms. O banco não é lento. Ele está ocupado.
Hipótese 2 descartada: Payment sem Fraud faz cerca de 10 consultas baratas por chave primária.
Hipótese 3 confirmada.

## Conclusão

**Fraud é o hotspot.**

Não "o banco". Não "o monólito". Fraud. Mais especificamente: consultas de histórico sem índice, um N+1, dois carregamentos do histórico inteiro e uma espera de rede dentro da transação.

## Trade-offs

| Instrumentação | |
|---|---|
| + | decisões viram números |
| + | o custo é uma dependência (micrometer-prometheus) e um proxy no DataSource |
| - | um proxy no DataSource adiciona um custo pequeno por consulta |
| - | métricas com tag por regra crescem com o número de regras (cardinalidade) |

## Pergunta para discussão

> Se `acquire mean` é 2,9 s e `usage mean` é 1,0 s, aumentar o pool de 10 para 50 conexões resolve?

(Não. O banco já está a 400% de CPU. Mais conexões só significam mais consultas disputando o mesmo banco saturado. O pool pequeno está, na verdade, protegendo o banco.)

## Professor Notes

```text
Pergunta:
O que a saída do slow-queries prova?

Resposta:
Que 97% do tempo do banco está em seis consultas de Fraud fazendo seq scan.

Erro comum:
Pular a investigação e ir direto para "vamos extrair Fraud". A extração não
resolve seq scan. Um Fraud Service separado faria as mesmas consultas no mesmo banco.

Conceito:
Sintoma (banco a 85%) não é causa (consultas sem índice e N+1 em Fraud).

Transição:
Sabemos quem e por quê. Antes de mudar arquitetura, vamos otimizar dentro do monólito.
```

## Próximo problema

Fraud é o hotspot. A solução mais barata é otimizar Fraud onde ele está. [Lab 04](04-optimizing-fraud.md).
