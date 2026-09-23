# Lab 13 — Branch by Abstraction + Feature Flag

**Slide suportado:** 18 — Feature Flag
**Tag:** `aula07-step-09-feature-flag-parallel-run`

## Problema

`fraud.mode=NEW` manda 100% dos pagamentos para o Fraud Service de uma vez. Se o novo decidir diferente do legado em 2% dos casos, são 2% dos clientes recebendo uma decisão diferente da que receberiam ontem, e ninguém saberia até a reclamação chegar.

Precisamos de mais estados entre "tudo no legado" e "tudo no novo".

## O que observar

```bash
scripts/fraud-mode.sh            # o valor atual da flag
curl localhost:8080/actuator/metrics/fraud.parallel.comparisons
```

## Branch by Abstraction

O lab 05 criou a abstração (`FraudEvaluator`). O lab 07 colocou uma fachada atrás dela. Este lab acrescenta estados à fachada:

```text
fraud.mode = LEGACY     -> FraudService (in-process)
fraud.mode = PARALLEL   -> legado decide, novo em shadow, comparador conta      (lab 14)
fraud.mode = NEW        -> RemoteFraudEvaluator (Fraud Service)
```

É um `switch` em [FraudFacade.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/FraudFacade.java). Payment não mudou uma linha desde o lab 05.

## A flag

[FraudMode.java](../../monolith/src/main/java/com/techpix/fraud/FraudMode.java) é um enum. [FraudModeConfig.java](../../monolith/src/main/java/com/techpix/fraud/internal/strangler/FraudModeConfig.java) é um `AtomicReference`. `PUT /admin/fraud/mode` troca o valor. Sem SaaS, sem SDK, sem dependência.

Valor inicial: propriedade `techpix.fraud.mode` (variável `TECHPIX_FRAUD_MODE` no Compose e no ConfigMap do Kubernetes). Troca ao vivo: o endpoint.

## Como executar

```bash
scripts/fraud-mode.sh LEGACY
scripts/fraud-mode.sh PARALLEL
scripts/fraud-mode.sh NEW
scripts/rollback.sh              # = LEGACY
```

## Como testar

`StranglerIT.rollbackIsAConfigurationChangeNotADeploy`: remoto quebrado, 503; troca a flag; 201.

## Trade-offs

```text
Feature Flag (in-memory, por processo)
+ zero dependencia externa
+ troca em milissegundos, sem deploy
+ rollback e uma chamada HTTP

- o valor vive na memoria de CADA replica. Com 2 replicas do monolito em Kubernetes,
  PUT /admin/fraud/mode muda so a replica que atendeu a chamada. As outras continuam
  como estavam. Isso e um problema real e e o gancho do lab 16 (GitOps): a fonte de
  verdade precisa ser o ConfigMap, versionado, nao um endpoint.
- flags esquecidas viram codigo morto com dois caminhos. Toda flag precisa de data para morrer.
- o endpoint administrativo precisa de autenticacao em qualquer ambiente real.
```

## Pergunta para discussão

> Com 2 réplicas do monólito, você chama `PUT /admin/fraud/mode` uma vez. O que acontece?

## Professor Notes

```text
Pergunta:
Por que nao usar LaunchDarkly, Unleash ou similar?

Resposta:
Para o laboratorio, um enum e um endpoint ensinam o conceito sem esconder o mecanismo.
Em producao, uma ferramenta de flags resolve o problema das replicas e da auditoria.

Erro comum:
Confundir feature flag com configuracao de deploy. A flag existe para mudar comportamento
SEM deploy. Se toda troca exige redeploy, nao e flag.

Conceito:
Branch by Abstraction: as duas implementacoes coexistem atras de uma interface; a flag escolhe.

Transicao:
Temos um estado intermediario, PARALLEL. O que exatamente acontece nele?
```

## Próximo problema

`PARALLEL` existe. Precisamos saber o que ele mede e como ler o resultado. [Lab 14](14-parallel-run.md).
