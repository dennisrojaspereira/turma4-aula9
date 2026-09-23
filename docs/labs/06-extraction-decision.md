# Lab 06 — A dor persiste: decidir extrair

**Slides suportados:** 8 — A dor persiste · 9 — Decision Framework · 10 — Boundary antes do serviço
**Tag:** `aula07-step-06-strangler` (compartilhada com o lab 07)

## Problema

Fraud está otimizado. O banco não é mais o gargalo. Mas Fraud e Payment continuam no mesmo processo, e têm perfis operacionais diferentes:

```text
Payment                      Fraud
latencia critica             tolerante a dezenas de ms
carga previsivel             bursts, modelo pesado
pouco processamento          CPU intensive, memory intensive
muda pouco                   muda toda semana
```

A pergunta desta etapa não é "Fraud está lento?". É: **"o que acontece com Payment quando Fraud precisa de mais CPU?"**

## O que observar

O gerador de carga agora faz 20% de `GET /accounts/{id}`: uma rota leve que **não chama Fraud**. Se a latência dela subir quando só Fraud ficar mais caro, é porque os dois disputam o mesmo processo.

```text
account_read_http_ms    rota sem Fraud
payment_http_ms         rota com Fraud
fraud_duration_ms       so Fraud
```

## Hipóteses

1. Fraud mais caro afeta só `POST /payments`. (Então `account_read_http_ms` ficaria estável.)
2. Fraud mais caro afeta tudo no processo. (Então `account_read_http_ms` subiria junto.)

## Mudança

Nenhuma no código de produção. Só o gerador de carga e o custo do modelo por variável de ambiente.

## Como executar

```bash
# Fraud barato
TECHPIX_FRAUD_PROFILE=HEAVY_OPTIMIZED TECHPIX_FRAUD_ML_ITERATIONS=200000 ./mvnw -pl monolith spring-boot:run
scripts/load-test.sh custom 40 40s

# Fraud caro (so o modelo mudou)
TECHPIX_FRAUD_PROFILE=HEAVY_OPTIMIZED TECHPIX_FRAUD_ML_ITERATIONS=20000000 ./mvnw -pl monolith spring-boot:run
scripts/load-test.sh custom 40 40s
```

No PowerShell, `$env:TECHPIX_FRAUD_ML_ITERATIONS = "20000000"` antes de `.\mvnw.cmd`.

## Resultado (medido)

| | ML 200k | ML 20M |
|---|---|---|
| `GET /accounts/{id}` p50 | 3,8 ms | **30 ms** (8x) |
| `GET /accounts/{id}` p99 | 195 ms | 404 ms |
| `POST /payments` p95 | 462 ms | 919 ms |
| Throughput | 171 req/s | 98 req/s |

Hipótese 2 confirmada. Uma rota que não tem nada a ver com Fraud ficou 8x mais lenta.

Para devolver a latência de `GET /accounts` ao normal, a única opção dentro do monólito é escalar o monólito inteiro. Cada réplica traz Account, Ledger e Notification, e mais um pool de conexões no mesmo banco.

> Por que escalar Payment 30 vezes porque Fraud precisa de mais CPU?

## Decision Framework

Ver [decision-framework.md](../architecture/decision-framework.md). Sete de nove critérios apontam para perfis distintos. O único contra é **dados**: Fraud precisa do histórico de Payment. Não vamos ignorá-lo; vamos tratá-lo explicitamente mais adiante.

## Boundary antes do serviço

A decisão de extrair só é segura porque o lab 05 já definiu o que é Fraud: tudo em `com.techpix.fraud`, exposto por `FraudEvaluator`. Ver [boundaries.md](../architecture/boundaries.md).

Fraud é uma **Business Capability**: "avaliar risco de um pagamento". Não vamos criar `FraudControllerService`, `FraudRepositoryService` ou `FraudRulesService`. Vamos criar **um** serviço que responde **uma** pergunta.

## Trade-offs

A extração vai trazer (ver [ADR 003](../adr/003-extract-fraud-service.md)):

```text
+ escala independente
+ deploy independente
+ isolamento de falha

- rede: latencia, timeout, retry
- dois deploys, contrato versionado
- observabilidade em dois processos
- dados compartilhados (por enquanto)
- onde Fraud roda? quem reinicia? quem descobre?
```

Cada item negativo vai virar um lab.

## Pergunta para discussão

> Se Fraud fosse barato em CPU e mudasse uma vez por ano, a extração ainda valeria a pena?

(Não. O framework daria empate ou apontaria contra. Extrair seria custo sem contrapartida.)

## Professor Notes

```text
Pergunta:
Qual metrica justifica a extracao?

Resposta:
A latencia de uma rota que NAO usa Fraud subiu 8x quando so Fraud mudou.

Erro comum:
Justificar a extracao com "microservicos escalam melhor". Escalam
diferente. So vale a pena quando os perfis sao diferentes, e isso se mede.

Conceito:
Decision Framework: criterios com evidencia, nao preferencia.

Transicao:
Decidimos extrair. Como fazer isso sem parar o sistema e com rollback simples?
```

## Próximo problema

Decidimos extrair. Não vamos fazer big-bang. [Lab 07](07-strangler.md) introduz a fachada que permite trocar a implementação em runtime.
