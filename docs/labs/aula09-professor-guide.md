# Aula 9 — Guia do Professor: "Cadê o Pix?"

Gabarito e roteiro de condução do [Lab 23](23-cade-o-pix.md). **Não distribuir aos alunos.**

## Antes da aula (5 min de preparo)

```bash
python scripts/sobe-tudo.py   # ambiente completo: simulador, dashboards, prometheus, synthetic, painel
scripts/lab9-up.sh            # ou so o simulador na porta 8080 (zera o estado)
scripts/lab9-up.sh stop       # ao final
```

Abas para projetar: [painel](http://localhost:8099) · [dashboard do incidente](http://localhost:3000/d/aula9) · [Visão Geral](http://localhost:3000/d/techpix-home) · [health](http://localhost:8080/actuator/health). O dashboard reencena o incidente em **loop de 20 min** (deploy na metade do ciclo) — se quiser o "deploy ao vivo" durante a Fase 5, cronometre o `lab9-up` ~10 min antes de chegar lá.

- O lab **não depende** do cluster kind, do Kafka nem do monólito. O simulador (`scripts/aula9/lab9.py`, Python puro, sem dependências) faz o papel do payment-service e serve todas as evidências. Se o stack da Aula 7/8 estiver rodando na 8080, derrube-o ou use `TECHPIX_LAB9_PORT=8086`.
- Ensaie a sequência completa uma vez: `lab9-up` → fases 1–11 → `lab9-incident2` → `lab9-reset`. Leva ~5 min sozinho.
- A ordem importa em dois pontos: a **DLQ só tem mensagem depois da reconciliação** (fase 10 antes da 11), e o `lab9-up.sh` sempre zera o estado.
- Narrativa: a Tech Pix das aulas 7–8 "cresceu" — nesta aula ela aparece com `psp-adapter` e `notification-service` além do que existe no código. São serviços da ficção do incidente, simulados; se um aluno perguntar, é a deixa para dizer que a investigação independe de conhecer o código.

## Cronograma sugerido (35 min)

```text
 5 min   chamado + fase 1 (health)
 5 min   fases 2–3 (logs + correlation)
 5 min   fases 4–5 (trace + métricas)      fase 6 em 1 min, de passagem
 8 min   fases 7–9 (UNKNOWN + retry + idempotência)   <- o clímax; não apresse a discussão da fase 8
 5 min   fases 10–11 (reconciliação + DLQ)
 5 min   fases 12–13 (SLI/SLO + MTTD/MTTR)
 5 min   fases 14–15 (RCA + IA)
```

O desafio final (incidente 2) é para casa ou para os últimos 15 min se a turma estiver rápida.

## Mapa conceito → fase

| Conceito | Fase | Conceito | Fase |
|---|---|---|---|
| Monitoramento vs observabilidade | 1 | Estado UNKNOWN | 7 |
| Logs estruturados | 2 | Retry | 8 |
| Correlation ID | 2–3 | Idempotência | 9 |
| Distributed tracing / OpenTelemetry | 4 | Reconciliação | 10 |
| Métricas, RED, USE, Golden Signals | 5 | DLQ | 11 |
| Cardinalidade | 6 | SLI / SLO | 12 |
| MTTD / MTTR | 13 | RCA | 14 |
| IA na investigação | 15 | Tudo junto | Desafio final |

---

## Fase 1 — "Está tudo verde"

**Evidência esperada:** `curl localhost:8080/actuator/health` responde `UP` em todos os componentes. O painel da operação idem.

**Onde pausar:** depois que alguém disser "o sistema está ok".

```text
Pergunta para a turma:
  O health check diz UP. O cliente diz que perdeu R$ 250. Quem está mentindo?

Resposta esperada:
  Ninguém. O health check responde "o processo está vivo e responde?".
  Ele não responde "a transação do Carlos foi concluída?". Disponibilidade
  técnica != saúde do negócio. Monitoramento responde perguntas que você
  previu; observabilidade permite responder perguntas que você NÃO previu
  — como "cadê este Pix específico?".

Erro comum:
  Concluir que o cliente está errado porque os dashboards estão verdes.
  Ou o oposto: querer reiniciar pods "por via das dúvidas".

Conceito:
  Monitoramento vs observabilidade.

Transição para próxima fase:
  "Se as perguntas prontas não respondem, vamos interrogar o sistema.
   Temos um transaction_id — onde uma transação deixa rastro?"
```

**Se a demo falhar:** se o curl não responder, `scripts/lab9-up.sh` de novo (ele é idempotente). Porta ocupada → `TECHPIX_LAB9_PORT=8086 scripts/lab9-up.sh` e ajuste os curls.

---

## Fase 2 — Logs

**Evidência esperada:** `scripts/investigate-logs.sh PIX-928371` → 7 eventos JSON em 5 serviços: payment-service `PROCESSING`, fraud aprovado (40 ms), ledger `DEBITED`, psp-adapter `WARN pool esgotado (pool_max=2, wait 2874 ms)`, psp-adapter `ERROR ReadTimeout 5000 ms`, payment-service `UNKNOWN`, notification-service com **`cpf` e nome em claro**.

**Onde pausar:** no log do notification-service — deixe que eles achem o CPF.

```text
Pergunta para a turma:
  transaction_id e correlation_id são a mesma coisa? E o que NÃO deveria
  estar nesses logs?

Resposta esperada:
  transaction_id identifica a operação de NEGÓCIO (o Pix do Carlos);
  correlation_id identifica a JORNADA técnica e costura os serviços.
  Logs estruturados permitem filtrar por campo em segundos — em texto
  livre isso seria regex e sorte. E o log do notification-service vaza
  CPF e nome: dado pessoal não anonimizado em log é incidente de LGPD
  esperando para acontecer (logs são copiados, indexados, retidos).

Erro comum:
  Parar no primeiro ERROR e declarar causa ("é timeout do PSP!") sem ver
  o WARN do pool logo acima. E ninguém reparar no CPF até você apontar.

Conceito:
  Logs estruturados; correlation ID; higiene de dados em log.

Transição:
  "Os logs desta transação apontam para vários serviços. Vamos ver a
   jornada inteira na ordem — temos o correlation_id."
```

**Se a demo falhar:** o script não precisa do servidor; precisa só de Python. `python scripts/aula9/lab9.py logs PIX-928371` é o fallback direto (vale para todos os scripts de evidência).

---

## Fase 3 — Correlation ID

**Evidência esperada:** `scripts/find-correlation.sh abc123` → timeline 21:03:01 recebido → fraude ok → 21:03:02 débito → 21:03:03 espera por conexão → 21:03:08 timeout → UNKNOWN.

```text
Pergunta para a turma:
  Encontramos o problema?

Resposta esperada:
  Ainda não. Sabemos ONDE o sintoma apareceu (psp-adapter, timeout) e
  quando a jornada parou. Mas não sabemos O QUE aconteceu: o PSP
  processou e a resposta se perdeu? Nem POR QUE começou hoje. Sintoma
  localizado != causa encontrada.

Erro comum:
  "Achamos! O PSP caiu." O PSP não caiu — a transação ao lado (21:02,
  PIX-928366) foi confirmada pelo MESMO PSP, só que lenta. Mostre-a:
  scripts/find-correlation.sh x4t8w2

Conceito:
  Correlation ID como fio condutor entre serviços.

Transição:
  "Entre 21:03:03 e 21:03:08 há um buraco de 5 segundos. O que aconteceu
   DENTRO dele? Logs não mostram anatomia de requisição. Trace mostra."
```

---

## Fase 4 — Tracing

**Evidência esperada:** `scripts/investigate-trace.sh PIX-928371` → `POST /payments` 5212 ms; `psp.transfer` 5003 ms TIMEOUT, com filhos `pool.acquire` **2874 ms** e `http.post` 2101 ms sem resposta.

**Versão visual:** [dashboard "Trace de um Pix"](http://localhost:3000/d/aula9-trace) — cole o trace_id (`4f2a9c01d7e3b8a64f2a9c01d7e3b8a6`) e explore o waterfall no Tempo; clique no span `pool.acquire` para mostrar os atributos (`pool_max=2`, `pool_pending=14`). O do incidente 2 é `9b1cf3aa21e07c449b1cf3aa21e07c44`. Se o trace sumiu (retenção/reinício do Tempo): `python scripts/aula9/lab9.py traces` reingere em 1s.

**Onde pausar:** no `pool.acquire`. É a pista central do incidente.

```text
Pergunta para a turma:
  Qual span concentrou o tempo? E o que são esses 2874 ms de pool.acquire
  — o PSP estava lento?

Resposta esperada:
  psp.transfer domina (5.0s de 5.2s). Mas quase 3s foram gastos ANTES da
  chamada HTTP sair: esperando conexão livre no pool. O problema começa
  dentro de casa. E timeout NÃO prova que o PSP não processou — a
  requisição saiu; a resposta é que não voltou a tempo.
  correlation_id: identificador que NÓS criamos e logamos.
  trace_id: identificador propagado automaticamente (W3C traceparent /
  OpenTelemetry), com spans e durações. O OTel padroniza propagação e
  exportação (Tempo, Jaeger...).

Erro comum:
  Ler "TIMEOUT" como "falhou, pode reprocessar". Essa leitura é a
  armadilha da fase 8.

Conceito:
  Trace, span, context propagation, OpenTelemetry.

Transição:
  "Isso é UMA transação. Isso está acontecendo com todas? Desde quando?
   Pergunta de agregado = métrica."
```

---

## Fase 5 — Métricas (RED / USE / Golden Signals)

**Evidência esperada:** `scripts/investigate-metrics.sh` → p95 do PSP salta de ~200 ms para ~4900 ms às 21:05; `pool_pending` 0 → 14–16; `unknown_total` 0 → 25; **rate estável, 5xx baixo, CPU normal, Kafka lag 0**; annotation `deploy v1.13.4` às **21:00**. Projete também o [dashboard ao vivo](http://localhost:3000/d/aula9): os 4 Golden Signals na primeira linha e o stat "Deploy no ar" contam a mesma história em curvas.

**Onde pausar:** na linha das 21:00.

```text
Pergunta para a turma:
  CPU detectaria este incidente? O que mudou às 21:00? Classifiquem pelo
  RED, pelo USE e pelos Golden Signals.

Resposta esperada:
  RED:  Rate normal, Errors baixos (timeout vira UNKNOWN, não 500),
        Duration explodiu. Incidente invisível para quem só olha erro.
  USE:  CPU/memória/banco normais — MAS o pool do psp-adapter está
        saturado: pool_pending é FILA. Saturação sem utilização alta.
  Golden Signals: latency degradou e saturation subiu, sem derrubar
        traffic nem errors.
  Métrica de negócio que detecta: unknown_transactions_total (e o p95).
  E há um deploy às 21:00 exatamente no joelho da curva.

Erro comum:
  Procurar o vilão em CPU/memória; ignorar pool_pending por não saber
  que saturação se mede em fila, não em percentual.

Conceito:
  RED, USE, Golden Signals; métrica de negócio > métrica de máquina.

Transição:
  "Vocês acharam a transação em log e trace, mas não existe painel
   'PIX-928371'. Por quê?"
```

---

## Fase 6 — Cardinalidade (de passagem, 1–2 min)

```text
Pergunta para a turma:
  Devemos colocar transaction_id como label no Prometheus?

Resposta esperada:
  Não. Cada valor de label cria uma série temporal nova. A 16 Pix/s são
  ~1,4 milhão de séries por dia: explosão de cardinalidade, Prometheus
  morre. Métrica agrega com labels de baixa cardinalidade (status,
  provider, operation); IDs únicos vivem em logs e traces, que são
  feitos para alta cardinalidade.

Erro comum:
  "Mas seria tão útil!" — é útil, e é exatamente o papel do trace.

Conceito:
  Cardinalidade; qual pergunta pertence a qual sinal.

Transição:
  "Sabemos o quê e desde quando. Falta a pergunta do cliente: em que
   estado está O PIX DELE?"
```

---

## Fase 7 — UNKNOWN

**Evidência esperada:** `curl localhost:8080/payments/PIX-928371` → `"status": "UNKNOWN"`.

```text
Pergunta para a turma:
  UNKNOWN significa FAILED?

Resposta esperada:
  Não. UNKNOWN = "não tenho evidência suficiente para afirmar sucesso
  nem falha". O débito aconteceu; a confirmação não veio. Marcar FAILED
  sem evidência poderia estornar um Pix que o destinatário JÁ recebeu
  (perda financeira); marcar APPROVED sem evidência poderia dar baixa em
  dinheiro que não chegou. UNKNOWN é o sistema sendo honesto.

Erro comum:
  Tratar UNKNOWN como bug ("estado mal modelado"). É o contrário: é
  modelagem correta de incerteza em sistema distribuído.

Conceito:
  Estados de incerteza; two generals problem na prática.

Transição:
  "O suporte pergunta: 'posso reprocessar?'. Existe um botão."
```

---

## Fase 8 — Retry (o clímax — não apresse)

**Evidência esperada:** `scripts/retry-payment.sh PIX-928371` sem flag só confronta com 3 perguntas. Com `--executar`: simulação mostra PSP processando **duas vezes** → `DUPLICATE TRANSFER`.

**Onde pausar:** ANTES de qualquer flag. Pergunte nominalmente: "você apertaria?". Deixe a turma se dividir; idealmente faça uma votação.

```text
Pergunta para a turma:
  Você executaria o retry agora? Quem vota sim?

Resposta esperada (após a demo --executar):
  A primeira requisição FOI processada pelo PSP — só a resposta morreu
  no timeout. O retry criou uma segunda transferência: o cliente perderia
  R$ 500 em vez de R$ 250. O PSP não tem como saber que é a mesma
  operação sem uma identidade de operação.
  Retry de operação financeira não é igual a retry de leitura HTTP:
  GET é naturalmente idempotente; "transferir R$ 250" não é.

Erro comum:
  Metade da turma vota sim — ótimo, é o momento pedagógico. O erro
  técnico subjacente: assumir que timeout == não processado.

Conceito:
  Retry e efeitos colaterais; at-least-once na prática.

Transição:
  "Existe um jeito de repetir com segurança."
```

**Se a demo falhar:** a simulação não altera a transação real (roda em sandbox); pode executar quantas vezes quiser.

---

## Fase 9 — Idempotência

**Evidência esperada:** `--idempotency-key PIX-928371` → `idempotent_replay: true`, `psp_executions: 1`, mesmo resultado anterior, nenhuma transferência nova.

```text
Pergunta para a turma:
  Idempotência resolveu todo o problema? Agora sabemos se a primeira
  operação foi concluída?

Resposta esperada:
  Não. Idempotência evita o DANO do retry (duplicidade), mas não produz
  CONHECIMENTO: o status continua UNKNOWN. São problemas diferentes:
  segurança de repetição vs descoberta do resultado real.

Erro comum:
  Achar que a chave "destrava" a transação. Ela só torna a repetição
  inofensiva.

Conceito:
  Idempotency-Key; dedup por identidade de operação.

Transição:
  "Para descobrir a verdade, perguntamos a quem sabe: ledger e PSP."
```

---

## Fase 10 — Reconciliação

**Evidência esperada:** `scripts/reconcile.sh PIX-928371` → Tech Pix `UNKNOWN`, Ledger `DEBITED`, PSP `COMPLETED` (com `e2e_id`) → decisão `UNKNOWN -> APPROVED`, `Reconciliation completed payment=PIX-928371 status=APPROVED`.

**Onde pausar:** na linha do PSP `COMPLETED`. É a resposta do lab.

```text
Pergunta para a turma:
  Então... onde estava o Pix?

Resposta esperada:
  No destinatário, desde 21:03. O PSP processou; nossa resposta morreu
  no timeout. O dinheiro nunca esteve perdido — o sistema tinha perdido
  temporariamente a CERTEZA sobre o estado. Reconciliação é comparar as
  fontes da verdade (ledger interno, PSP externo) e decidir com
  evidência (o e2e_id), não com esperança.

Erro comum:
  "Então o retry teria dado certo!" — não: teria DUPLICADO, como visto
  na fase 8. A reconciliação é que resolve UNKNOWN, não o retry.

Conceito:
  Reconciliação; fontes da verdade.

Transição:
  "A reconciliação publicou um evento de mudança de estado. Nem todo
   consumer ficou feliz com ele."
```

**Atenção à ordem:** a reconciliação é o que coloca a mensagem na DLQ. Se rodarem `dlq-show` antes, a fila estará vazia (o script explica isso).

---

## Fase 11 — DLQ

**Evidência esperada:** `scripts/dlq-show.sh` → 1 mensagem: `PaymentStatusChanged` de PIX-928371 com `amount: "250,00"` (string com vírgula), `DeserializationException` no notification-service, 4 tentativas. `scripts/dlq-show.sh replay` → corrigido, aplicado 1x, duplicata ignorada por `processed_events`.

```text
Pergunta para a turma:
  A DLQ resolveu o erro? Podemos fazer replay sem pensar?

Resposta esperada:
  A DLQ não resolveu nada — ela PRESERVOU a mensagem venenosa sem travar
  a partição para os demais eventos. Replay sem corrigir a causa
  (amount como string "250,00") só devolve a mensagem para o mesmo erro.
  E replay significa reentrega: se o consumer não fosse idempotente,
  o cliente poderia receber a notificação N vezes — ou pior, um consumer
  financeiro aplicaria o efeito N vezes. Idempotência (processed_events,
  mesma ideia do fraud-service no lab 19/20) é o que torna replay seguro.

Erro comum:
  Tratar DLQ como lixeira ("deu erro, descarta") ou como fim do fluxo
  ("está na DLQ, resolvido"). DLQ é sala de espera com obrigação de
  revisita.

Conceito:
  Dead Letter Queue; poison message; replay idempotente.

Transição:
  "Incidente entendido e cliente atendido. Agora: quão ruim foi, e quão
   rápidos fomos?"
```

---

## Fase 12 — SLI / SLO

**Dados:** 10.000 Pix; 9.975 em ≤ 5s.

```text
Pergunta para a turma:
  Estamos dentro do SLO de 99,9%?

Resposta esperada:
  SLI = 9.975 / 10.000 = 99,75%. SLO = 99,9% permitia no máximo 10
  violações em 10.000 — tivemos 25. FORA do SLO: 2,5x o error budget do
  período consumido.
  Segundo SLI (% de Pix em UNKNOWN > 5 min) é possivelmente mais
  importante para uma fintech: latência irrita; dinheiro em estado
  desconhecido corrói confiança e gera passivo regulatório. Um Pix de
  6s concluído é melhor que um Pix "rápido" sem resposta.

Erro comum:
  Calcular 99,75% e arredondar para "basicamente 99,9%, estamos ok".
  Error budget não arredonda.

Conceito:
  SLI (medida), SLO (alvo), error budget.

Transição:
  "SLO mede o impacto no cliente. E o NOSSO desempenho no plantão?"
```

---

## Fase 13 — MTTD / MTTA / MTTR

**Timeline:** 21:03 início → 21:05 alerta → 21:07 engenheiro assume → 21:12 reconciliada.

```text
Pergunta para a turma:
  Calculem MTTD, MTTA e MTTR.

Resposta esperada:
  MTTD = 21:05 - 21:03 = 2 min   (detecção: alerta de unknown_total)
  MTTA = 21:07 - 21:05 = 2 min   (acknowledgement: humano assume)
  MTTR = 21:12 - 21:03 = 9 min   (neste lab: início -> transação
                                  reconciliada, recuperação do ponto de
                                  vista do CLIENTE)
  Deixe explícito que há definições alternativas de MTTR (até rollback,
  até fim do impacto agregado); o que importa é o time usar UMA.
  O que reduziu o MTTR aqui não foi quantidade de dashboards: foi
  logs/trace/métricas CORRELACIONÁVEIS (ids em comum) + um alerta de
  negócio (unknown_total) em vez de alerta de CPU.

Erro comum:
  MTTR como 21:12 - 21:07 (= 5 min), esquecendo que o cliente sofre
  desde 21:03. Vale aceitar se o aluno declarar a definição.

Conceito:
  MTTD, MTTA, MTTR; alerta de negócio vs alerta de infraestrutura.

Transição:
  "Falta a pergunta de gente grande: POR QUE aconteceu?"
```

---

## Fase 14 — RCA

**Gabarito da tabela:**

```text
Sintoma:             timeout nas chamadas ao PSP (p95 5s)
Impacto:             25 Pix em UNKNOWN; cliente sem confirmação; SLO estourado
Causa intermediária: psp-adapter saturado — pool de conexões com fila (pending 14+)
Causa raiz:          deploy v1.13.4 (21:00) reduziu o connection pool do
                     psp-adapter de 20 para 2 (configuração incorreta)
```

```text
Pergunta para a turma:
  Temos evidência suficiente para afirmar causalidade, ou só correlação
  temporal (deploy às 21:00, latência às 21:00)?

Resposta esperada:
  Correlação temporal sozinha NÃO basta. O que fecha o caso:
  1. mecanismo plausível e observado: pool.acquire de 2,9s no trace +
     WARN "pool esgotado, pool_max=2" nos logs + pool_pending nas métricas;
  2. o diff do deploy mostra a config PSP_POOL_SIZE 20 -> 2;
  3. teste de refutação: rollback (ou pool de volta a 20) deve normalizar
     o p95 — e normaliza.
  Correlação + mecanismo + diff + reversão = causalidade defensável.

Erro comum:
  Parar em "foi o deploy" (causa intermediária de processo) sem nomear
  O QUE no deploy. RCA que termina em "deploy ruim" não previne nada;
  "pool de 2 passou sem review/canary" gera ação preventiva concreta.

Conceito:
  Sintoma != impacto != causa intermediária != causa raiz; correlação
  vs causalidade.

Transição:
  "Vocês levaram X minutos. Vamos ver o que uma IA faz com as mesmas
   evidências — e o que ela NÃO pode fazer."
```

---

## Fase 15 — IA como assistente

**Evidência esperada:** aluno entrega `scripts/aula9/incident-context.json` à IA com o prompt do lab. Uma boa resposta aponta: hipótese mais forte = redução do pool no deploy v1.13.4; evidências = latência cresce imediatamente após o deploy, CPU normal (descarta exaustão de recurso computacional), span do PSP domina o trace com pool.acquire alto, UNKNOWN cresce no mesmo período; verificações sugeridas = diff de config, rollback, consulta ao PSP.

```text
Pergunta para a turma:
  A IA pode marcar todos os pagamentos UNKNOWN como APPROVED?

Resposta esperada:
  Não. A IA reconstrói timeline, gera e ranqueia hipóteses e sugere
  verificações — acelera o DIAGNÓSTICO. Mas mudar estado de dinheiro
  exige a evidência de reconciliação por transação (e2e_id do PSP),
  e a decisão é de um humano com essa evidência na mão.
  AI proposes hypotheses. Evidence decides.
  Checagem importante: peça para a turma verificar se cada evidência
  citada pela IA EXISTE no JSON — caça à alucinação é parte do exercício.

Erro comum:
  Aceitar a resposta da IA sem conferir as evidências; ou o oposto,
  descartá-la ("não confio") em vez de usá-la como geradora de hipóteses.

Conceito:
  IA na investigação de incidentes; humano no loop para ações com efeito.

Transição:
  "Agora vocês investigam sozinhos."
```

**Se a demo falhar:** sem acesso a uma IA em sala, projete o JSON e conduza a turma como "IA humana": timeline → 3 hipóteses → evidências a favor/contra.

---

## Desafio final — GABARITO (incidente 2)

`scripts/lab9-incident2.sh` ativa; `off` desativa; `lab9-reset.sh` zera tudo.

**Cenário:** 21:35, deploy **v1.13.5** corrige o pool do PSP (2 → 20, de verdade) **mas também** migra o fraud-service para `FRAUD_PROFILE=HEAVY` (`ml_iterations=2000000`).

**Cadeia causal que o aluno deve montar:**

```text
Causa raiz:          v1.13.5 ativa perfil HEAVY no fraud-service
                     (regra de ML com ml_iterations=2000000)
Causa intermediária: CPU do fraud-service em 93–95%; avaliação síncrona
                     sobe de ~60ms para ~2.6s; o MESMO processo consome
                     payment-events, então o consumer atrasa junto
Sintomas:            fraud_p95 2600ms; kafka lag 1800 e crescendo;
                     reconciliation backlog 340 e crescendo
Impacto:             Pix lentos (~4s, dentro do timeout: quase nada vira
                     UNKNOWN); local view do fraud defasada; fila de
                     reconciliação não drena
```

**Evidências plantadas:**

- `investigate-logs.sh PIX-554219` / `find-correlation.sh def456`: fraud `WARN avaliacao lenta, ml_iterations=2000000, duration_ms=2612`; PSP saudável (236 ms, pool 3/20); pagamento `APPROVED` em 3890 ms. Mais: WARN de consumer lag e do reconciliation-job.
- `investigate-trace.sh PIX-554219`: `fraud.check` 2612 ms domina, filho `rule.ml-risk` 2458 ms.
- `investigate-metrics.sh`: fraud_p95 62→2650 ms a partir de 21:35; cpu_fraud 35%→95% (**desta vez o USE acusa CPU** — contraste proposital com o incidente 1); kafka_lag 0→1800; reconc_backlog 4→340; PSP e 5xx normais; annotation do deploy v1.13.5 com o changelog entregando as duas mudanças.
- `curl localhost:8080/payments/PIX-554219`: `APPROVED` (lento ≠ perdido).
- `dlq-show.sh`: vazia — pista negativa deliberada.
- `reconcile.sh PIX-554219`: fontes consistentes; "o problema é atraso, não divergência".

**Armadilha pedagógica:** o deploy v1.13.5 é o que CORRIGIU o incidente 1 — quem decorar "deploy mexeu em pool" erra; o changelog tem duas mudanças e só uma é culpada. É o teste de "correlação não é causalidade" na prática.

**Report esperado (resumo):** impacto = latência + backlog, sem perda de dinheiro; recuperação = rollback do `FRAUD_PROFILE` (ou rollback do deploy) e drenar o lag/backlog; retry = desnecessário e inofensivo aqui (nada em UNKNOWN — bom aluno nota a diferença); preventivas = separar consumer da API de avaliação (recursos/processo), canary com SLO de p95 do fraud como critério de abortar, teste de carga do perfil HEAVY antes de produção, alerta de consumer lag e de backlog de reconciliação.

**Critérios de avaliação do report (sugestão):** timeline com o deploy; ≥2 hipóteses com evidência contra (ex.: "PSP de novo" refutado pelo trace); 4 níveis sintoma/impacto/causa intermediária/causa raiz distintos; ação de recuperação ≠ ação preventiva; nenhuma afirmação sem evidência citada.

---

## Dicas gerais caso algo falhe

| Problema | Saída |
|---|---|
| Porta 8080 ocupada | `docker compose --profile app down` ou `TECHPIX_LAB9_PORT=8086 scripts/lab9-up.sh` (ajuste os curls) |
| Servidor caiu no meio | `scripts/lab9-up.sh` — mas atenção: ele zera o estado; refaça reconcile se já tinha passado da fase 10 |
| Estado estranho (alguém adiantou fases) | `scripts/lab9-reset.sh` e repita só os comandos-chave (são rápidos) |
| Sem Python na máquina | qualquer Python 3 serve (`py -3` no Windows); o lab não tem dependências |
| Scripts de evidência | nunca precisam do servidor: `python scripts/aula9/lab9.py <logs|correlation|trace|metrics|dlq> ...` |
| Sem IA na sala (fase 15) | projete o `incident-context.json` e conduza a análise coletivamente |

## Epílogo — synthetic monitoring (gabarito)

Demo: `python scripts/synthetic-monitor.py start` com a [Visão Geral](http://localhost:3000/d/techpix-home) projetada; depois `scripts/chaos.sh errors 0.9` → em até 2 min o stat vira **FALHOU** (o k6 exige `APPROVED`, e o fail-closed do fraud rejeita) — *sem nenhum chamado aberto*. `scripts/chaos.sh off` normaliza no ciclo seguinte.

```text
Pergunta para a turma:
  O que o synthetic verifica que o health check da Fase 1 nao verifica?

Resposta esperada:
  O health check pergunta "o processo esta vivo?"; o synthetic executa a
  JORNADA DE NEGOCIO inteira e exige o resultado certo: APPROVED e o credito
  na conta do recebedor. Responder 200 nao basta; o dinheiro tem que chegar.
  E ele e PROATIVO: descobre as 3h da manha, sem trafego real sofrendo.

Erro comum:
  Confundir synthetic com teste de carga (e 1 VU, 1 iteracao) ou achar que
  substitui alertas sobre trafego real - sao complementares.

Conceito:
  Monitoramento sintetico; SLI de jornada; proativo vs reativo.

Transicao:
  Fecha o circulo com a Fase 1: comecamos com "esta tudo verde" enganando;
  terminamos com um robo que nao se deixa enganar.
```

## Fechamento da aula

Termine com as duas frases, nesta ordem, apontando para o que a turma acabou de fazer:

> Observabilidade não é coletar dados. É conseguir explicar o comportamento do sistema.

> **Observabilidade é conseguir explicar onde está o dinheiro.**

A turma começou com "está tudo verde" e terminou explicando, com evidências, onde estavam os R$ 250,00 do Carlos. Esse é o teste de que a aula funcionou.
