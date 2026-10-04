# Incident Report

> Preencha durante a investigação. Toda afirmação precisa apontar para uma evidência (log, métrica, trace ou reconciliação).

## Impacto

<!-- O que o cliente sentiu? Quantos clientes? Quanto dinheiro ficou em estado incerto? -->

## Transaction ID

## Correlation ID

## Trace ID

## Timeline

<!-- hora a hora, do início do incidente até a recuperação. Inclua o deploy, o alerta e a sua atuação. -->

| Hora | Evento |
|---|---|
|  |  |

## Evidências

### Logs

<!-- linhas relevantes (serviço, hora, mensagem). O que cada uma prova? -->

### Metrics

<!-- quais métricas mudaram, a partir de que horário, e o que ficou normal (isso também é evidência). -->

### Traces

<!-- span dominante, durações, onde o tempo foi gasto. -->

## Hipóteses

<!-- pelo menos 2, cada uma com evidências a favor e contra. Correlação temporal não é prova. -->

1.
2.

## Causa provável

<!-- sintoma ≠ impacto ≠ causa intermediária ≠ causa raiz. Nomeie os quatro níveis. -->

| Nível | Resposta |
|---|---|
| Sintoma | |
| Impacto | |
| Causa intermediária | |
| Causa raiz | |

## Recuperação

<!-- o que foi feito para o cliente (não para o gráfico). -->

## Risco de retry

<!-- o que aconteceria com um retry cego? Por quê? -->

## Idempotência

<!-- havia proteção? O que ela garante e o que ela NÃO garante? -->

## Reconciliação

<!-- fontes consultadas, o que cada uma dizia, decisão tomada e com qual evidência. -->

## SLI/SLO impactado

<!-- SLI, valor medido no período, SLO, dentro ou fora. -->

## MTTD

## MTTR

## Ações preventivas

<!-- o que impediria a recorrência: config review/canary, alerta melhor, teste, limite de pool, etc. -->

1.
2.
3.
