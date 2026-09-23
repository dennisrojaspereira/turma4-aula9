# Lab 14 — Parallel Run

**Slide suportado:** 19 — Parallel Run
**Tag:** `aula07-step-09-feature-flag-parallel-run`

## Problema

O Fraud Service tem as mesmas 13 regras do legado. Em teoria decide igual. Na prática, ninguém sabe até comparar em produção, com dados reais, por tempo suficiente.

Como comparar sem arriscar nenhum pagamento?

## O que observar

```text
scripts/parallel-run-report.sh

  comparacoes         quantos pagamentos foram avaliados pelos dois
  match rate          % em que score e decisao foram iguais
  decision_mismatch   o novo decidiria DIFERENTE do legado  <- o numero que importa
  score_mismatch      mesma decisao, score diferente         <- investigar, menos grave
  error / timeout     o novo falhou ou demorou               <- ele aguenta a carga?
  legacy/new latency  quanto cada um leva
  lastDivergences     os casos concretos, com as regras que dispararam em cada lado
```

Métricas: `fraud.parallel.comparisons{outcome}`, `fraud.parallel.score.difference`, `fraud.parallel.latency{impl}`.

## Mudança

```text
                 +--> FraudService (legado) -------+   <- decide (thread da requisicao)
Payment -------->|                                  |--> ShadowComparator
                 +--> RemoteFraudEvaluator (novo) --+   <- shadow (pool separado, timeout)
```

[ParallelRunEvaluator.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/ParallelRunEvaluator.java): o shadow parte primeiro, em outro pool; o legado decide na thread da requisição; a comparação acontece quando o shadow termina, sem segurar a resposta.

[ShadowExecutorConfig.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/ShadowExecutorConfig.java): pool próprio, fila pequena, política de descarte. Se o Fraud Service ficar lento, o shadow é descartado e vira `skipped`; as threads HTTP nunca esperam.

[ShadowComparator.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/ShadowComparator.java): classifica cada comparação e guarda as últimas 50 divergências.

Somente o legado produz efeito oficial. O Fraud Service grava sua avaliação em `fraud_evaluations` (é a tabela dele), mas nenhum saldo muda por causa dele.

## Como executar

```bash
scripts/start-fraud-service.sh        # terminal 1
scripts/start.sh                      # terminal 2
scripts/enable-parallel-run.sh        # terminal 3
scripts/load-test.sh custom 10 30s
scripts/parallel-run-report.sh
```

### Divergência intencional ao vivo

O legado está em `SIMPLE` (5 regras); o Fraud Service tem 13. Para contas novas, o serviço dispara `new-account` (+20) e o legado não:

```bash
scripts/fraud-profile.sh SIMPLE
scripts/enable-parallel-run.sh
scripts/demo-payment.sh               # contas recem-criadas
scripts/parallel-run-report.sh        # score_mismatch: legacy=0 new=20
scripts/fraud-profile.sh HEAVY_OPTIMIZED
scripts/demo-payment.sh
scripts/parallel-run-report.sh        # match
```

Isso ensina algo importante: o Parallel Run não diz quem está certo. Diz onde os dois **diferem**. Decidir quem está certo é trabalho de gente.

## Como testar

`ParallelRunIT`, com o Fraud Service falso respondendo o que o teste manda:

| Teste | Divergência intencional | O que prova |
|---|---|---|
| `legacyDecidesAndShadowIsComparedAsMatch` | nenhuma | `match` = 1, remoto chamado 1 vez |
| `decisionMismatchIsRecordedButLegacyStillWins` | novo rejeita (95), legado aprova (0) | cliente recebe APPROVED; `decision_mismatch` = 1; relatório mostra 0 vs 95 |
| `scoreMismatchWithSameDecisionIsAMilderDivergence` | novo 20, legado 0, ambos aprovam | `score_mismatch` = 1 |
| `shadowErrorNeverAffectsThePayment` | novo responde 500 | pagamento 201; `error` = 1 |
| `slowShadowDoesNotSlowThePaymentAndIsRecordedAsTimeout` | novo demora 1,5 s | pagamento em < 1 s; `timeout` = 1 |

## Resultado esperado

Depois de uma carga em `PARALLEL` com os dois lados em 13 regras:

```text
== Parallel Run
  comparacoes:        1842
  match rate:         99.8%
    match              1838
    score_mismatch     4
    decision_mismatch  0
    error              0
    timeout            0
  legacy latency ms:  58.1
  new latency ms:     71.4
```

Os `score_mismatch` restantes são pagamentos avaliados em momentos ligeiramente diferentes pelos dois lados (o shadow parte antes; um pagamento concorrente pode entrar na janela de velocity de um e não do outro). Isso é consistência eventual aparecendo pela primeira vez, ainda dentro de um processo.

## Critério de saída

Só avançar para o Canary quando, por um período representativo (dias, não minutos):

```text
decision_mismatch  = 0 (ou cada caso explicado e aceito)
error + timeout    < 0.1%
new latency p95    dentro do orcamento de Payment
```

## Trade-offs

```text
Parallel Run
+ compara em producao, com dados reais, sem risco para o cliente
+ mede latencia e erro do novo sob carga real
+ divergencias viram lista concreta, nao opiniao

- o Fraud Service recebe 100% da carga sem produzir nada: custo dobrado durante o periodo
- o banco compartilhado recebe as consultas dos DOIS lados
- shadow que muda estado (grava fraud_evaluations) precisa ser idempotente ou isolado
- o shadow parte antes do legado: leituras em momentos diferentes geram falsos mismatches
```

## Pergunta para discussão

> O Parallel Run mostrou 4 `score_mismatch` em 1.842. Quem está certo: o legado ou o novo? Como você descobriria?

## Professor Notes

```text
Pergunta:
Por que o shadow roda em outro pool de threads?

Resposta:
Para que um Fraud Service lento nunca segure uma thread HTTP. Shadow descartado e estatistica.

Erro comum:
Rodar o shadow sincrono, "so para comparar". A latencia do novo passa a ser a latencia do cliente.

Conceito:
Uma implementacao produz efeito; a outra produz dados. O comparador transforma dados em decisao.

Transicao:
match rate alto por dias. Agora podemos mandar trafego REAL para o novo. Quanto?
```

## Próximo problema

O novo decide igual e aguenta a carga em shadow. Está na hora de deixá-lo decidir de verdade, para poucos. [Lab 15](15-canary.md).
