# Lab 15 — Canary

**Slide suportado:** 20 — Canary
**Tag:** `aula07-step-10-canary`

## Problema

O Parallel Run mostrou que o Fraud Service decide igual ao legado e aguenta a carga. Mas em shadow ele nunca **decidiu de verdade**. Nenhum cliente teve um pagamento rejeitado por ele.

Ir de "0% real" para "100% real" em um passo é apostar tudo em uma vez. Canary é a técnica de apostar pouco, medir, e apostar um pouco mais.

## O que observar

```bash
scripts/canary-status.sh

  fraud.canary.routed{target=new}      quantos foram para o canario
  fraud.canary.routed{target=legacy}   quantos ficaram no legado
  fraud.canary.fallback                canario falhou, legado decidiu   <- error rate
  fraud.remote.roundtrip               latencia da chamada remota (p95, p99)
```

## Mudança

`fraud.mode=CANARY` mais `techpix.fraud.canary.percentage`:

```text
                     paymentId
                         |
                    CanaryRouter          hash(paymentId) % 100 < percentage ?
                    /          \
                  sim           nao
                   |             |
           Fraud Service      FraudService
            (decide!)          (legado)
                   |
            falhou? -> fallback para o legado (contado)
```

- [CanaryRouter.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/CanaryRouter.java): determinístico por `paymentId`. O mesmo pagamento cai sempre do mesmo lado, em qualquer réplica. Isso permite responder depois "esse pagamento foi pelo novo?".
- [CanaryEvaluator.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/CanaryEvaluator.java): se o canário falhar, o legado decide e `fraud.canary.fallback` incrementa. Fallback alto é motivo para **não** subir o degrau.
- `PUT /admin/fraud/canary {"percentage": 10}`.

A diferença para o Parallel Run está em uma linha: no canary, o resultado do novo é **retornado**. Ele vale.

## Progressão

```text
1%  ---> 10% ---> 50% ---> 100%
 |        |        |         |
 medir    medir    medir     = NEW
```

Cada degrau só é subido se **todos** os critérios passarem por um período representativo:

| Critério | Métrica | Limite sugerido |
|---|---|---|
| error rate | `fraud.canary.fallback / fraud.canary.routed{new}` | < 0,5% |
| p95 | `fraud.remote.roundtrip` p95 | dentro do orçamento de latência de Payment |
| p99 | `fraud.remote.roundtrip` p99 | sem cauda longa (ex.: < 3x p95) |
| decision mismatch | do Parallel Run anterior | 0, ou cada caso explicado |
| timeouts | `fraud.parallel.comparisons{outcome=timeout}` no período anterior | 0 |

Canary é uma técnica de **redução de risco**, não uma configuração de infraestrutura. O que reduz o risco não é o roteamento; é medir em cada degrau e ter rollback simples.

## Rollback

```bash
scripts/rollback.sh        # = mode LEGACY. Uma chamada HTTP. Zero deploy.
```

Ou `scripts/canary.sh 0`.

## Como executar

```bash
scripts/start-fraud-service.sh     # terminal 1
scripts/start.sh                   # terminal 2
scripts/canary-10.sh               # terminal 3
scripts/load-test.sh custom 10 30s
scripts/canary-status.sh
scripts/canary-50.sh
scripts/load-test.sh custom 10 30s
scripts/canary-status.sh
scripts/canary-100.sh
scripts/rollback.sh
```

Para ver o fallback funcionando: pare o Fraud Service no meio da carga em 50%. Os pagamentos continuam em 201; `fallback` sobe.

## Como testar

| Teste | O que prova |
|---|---|
| `CanaryRouterTest` | 0% roteia nada, 100% roteia tudo, 10% roteia ~10%, mesmo id sempre do mesmo lado |
| `CanaryIT.atHundredPercentTheCanaryDecidesEverything` | a decisão do novo **vale** (REJECTED chega ao cliente) |
| `CanaryIT.canaryFailureFallsBackToLegacyAndIsCounted` | remoto 503, pagamento 201 pelo legado, fallback contado |
| `CanaryIT.percentageIsAdjustableThroughTheAdminEndpoint` | o degrau muda sem deploy |

## Trade-offs

```text
Canary
+ risco proporcional ao percentual
+ rollback instantaneo
+ o mesmo pagamento e reproduzivel (roteamento deterministico)

- durante a transicao, dois comportamentos coexistem em producao: suporte precisa saber qual valeu
- fallback silencioso pode mascarar um servico quebrado se ninguem olhar a metrica
- o percentual vive na memoria de cada replica (lab 13): com varias replicas, cada uma pode estar
  em um degrau diferente. A fonte de verdade precisa sair do endpoint e ir para o Git (lab 16).
- canary por hash de id nao e canary por usuario: o mesmo cliente pode cair nos dois lados
  em pagamentos diferentes. Para experiencia consistente, o hash seria por conta.
```

## Pergunta para discussão

> Você está em 50% e o fallback rate sobe para 3% às 14h. O que faz primeiro: rollback ou investigação?

(Rollback. Investigar com o sistema saudável. Canary existe para que essa ordem seja possível.)

## Professor Notes

```text
Pergunta:
Qual a diferenca entre Parallel Run e Canary em uma frase?

Resposta:
No Parallel Run o novo produz dados; no Canary o novo produz efeito.

Erro comum:
Tratar canary como "10% do trafego" e esquecer os criterios. Sem criterio, e so um deploy lento.

Conceito:
Canary reduz risco porque cada degrau e uma medicao com rollback barato.

Transicao:
Modo, percentual, replicas, warm-up: tudo isso e configuracao. Em DEV e de um jeito, em QA de outro,
em PROD alguem mudou na mao. Como saber qual e o estado certo?
```

## Próximo problema

Feature flag, canary, réplicas, timeouts: cada ambiente tem valores diferentes, e alguns foram alterados por endpoint ou `kubectl edit`. Ninguém sabe mais qual é o estado desejado. [Lab 16](16-gitops.md).
