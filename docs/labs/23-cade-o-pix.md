# Lab 23 — Cadê o Pix? (Aula 9)

**Aula:** 9 — Observabilidade e Resiliência em Sistemas Financeiros
**Formato:** investigação de incidente, ao final da aula (30–40 min)
**Entregável:** [Incident Report](aula09-incident-report-template.md)

> Você está de plantão. Um cliente afirma que o dinheiro sumiu.
> Descubra o que aconteceu.

Este lab não pede que você implemente nada. Ele entrega um sistema **aparentemente saudável** e uma transação que ninguém sabe explicar. Seu trabalho é o de um engenheiro de plantão: seguir evidências até conseguir responder, com provas, **onde está o dinheiro**.

Não leia o [guia do professor](aula09-professor-guide.md). Ele entrega as respostas.

## Preparação (1 comando)

```bash
scripts/lab9-up.sh          # Windows: scripts/lab9-up.ps1
# ou, para o ambiente inteiro (dashboards, prometheus, synthetic): python scripts/sobe-tudo.py
```

Isso sobe o "ambiente de produção" do lab na porta 8080 e reinicia o estado do incidente. Não precisa do cluster, do Kafka nem do monólito rodando — se o stack da Aula 7/8 estiver no ar, derrube-o antes (`docker compose --profile app down`) ou use `TECHPIX_LAB9_PORT=8086`.

**Deixe estas abas abertas** (você vai clicar nelas durante a investigação):

| Aba | Link |
|---|---|
| Painel do instrutor (botões de cada fase) | <http://localhost:8099> |
| Dashboard do incidente — "Aula 9 — Cadê o Pix?" | <http://localhost:3000/d/aula9> |
| Visão Geral (todos os dashboards + synthetic) | <http://localhost:3000/d/techpix-home> |
| Health do sistema | <http://localhost:8080/actuator/health> |

No Windows, todos os comandos `scripts/*.sh` abaixo têm um equivalente `.ps1` (ou rodam no Git Bash).

---

## O chamado

É quinta-feira, 21:08. O plantão recebe esta mensagem do suporte:

> "Cliente informa que realizou um Pix de R$ 250,00. O valor saiu da conta, mas o destinatário não recebeu."

Tudo o que você tem:

```text
transaction_id = PIX-928371
horário aproximado = 21:03
valor = 250.00
```

Ninguém vai te dizer a causa. A pergunta que você precisa responder no final é uma só:

> **Onde está o Pix?**

Abra o [template do Incident Report](aula09-incident-report-template.md) agora e preencha-o conforme avança. Cada fase te dá uma peça.

---

## Fase 1 — "Está tudo verde"

**Contexto.** Primeiro reflexo de quem está de plantão: o sistema caiu?

**Desafio.** Verifique a saúde do sistema como o time de operação faria.

```bash
curl localhost:8080/actuator/health
```

(ou clique: <http://localhost:8080/actuator/health> — e olhe também os targets verdes em <http://localhost:9090/targets>)

Painel da operação neste momento:

```text
Pods: UP          Health checks: UP      CPU: normal
Memory: normal    PostgreSQL: UP         Kafka lag: 0
API: respondendo
```

**Perguntas.**

- O sistema está saudável?
- O health check prova que a operação financeira do cliente está correta?
- Qual é a diferença entre disponibilidade técnica e saúde do negócio?

**Registre no report:** o que o monitoramento estava dizendo às 21:08.

**Próximo passo.** Se tudo está verde e o cliente está sem R$ 250,00, "verde" não é resposta. Vá atrás da transação.

---

## Fase 2 — Procurar a transação nos logs

**Contexto.** Você tem um `transaction_id`. Logs são o primeiro lugar onde uma transação específica deixa rastro.

**Desafio.** Encontre todos os registros desta transação, em todos os serviços.

```bash
scripts/investigate-logs.sh PIX-928371
```

**Perguntas.**

- `transaction_id` e `correlation_id` são a mesma coisa? Qual identifica o negócio e qual identifica a jornada técnica?
- Por que logs estruturados (JSON) ajudam aqui? Imagine fazer isso com `grep` em texto livre.
- Olhe cada campo dos logs com olhos de LGPD: **quais dados não deveriam estar ali?**

**Registre no report:** o `correlation_id` e o `trace_id` da transação, e o último status visto.

**Próximo passo.** Você achou um `correlation_id`. Use-o para ver a jornada inteira, entre serviços.

---

## Fase 3 — Seguir o correlation ID

**Contexto.** Cada serviço loga o seu pedaço. O `correlation_id` costura os pedaços na ordem em que aconteceram.

**Desafio.** Reconstrua a linha do tempo da transação.

```bash
scripts/find-correlation.sh abc123
```

**Perguntas.**

- Em que ordem os serviços participaram? Onde a jornada parou?
- **Encontramos o problema?** Cuidado antes de responder sim.
- O que a diferença de ~5 segundos entre dois eventos consecutivos sugere?

**Registre no report:** a timeline (hora a hora) na seção Timeline.

**Próximo passo.** Os logs mostram **onde o sintoma apareceu**, mas não mostram a anatomia da requisição. Para isso existe tracing.

---

## Fase 4 — Distributed tracing

**Contexto.** Um trace mostra a requisição inteira como uma árvore de spans, com a duração de cada passo — inclusive os passos internos que os logs não contam.

**Desafio.** Abra o trace da transação e descubra onde os 5 segundos foram gastos.

```bash
scripts/investigate-trace.sh PIX-928371
```

**Perguntas.**

- Qual span concentrou o tempo? E **dentro** dele, o que consumiu quase 3 segundos antes mesmo da chamada HTTP sair?
- Timeout significa necessariamente que o PSP **não** processou a transferência?
- Qual a diferença entre `correlation_id` e `trace_id`? Por que o OpenTelemetry propaga contexto entre serviços?

**Registre no report:** o span dominante e suas durações, na seção Traces.

**Próximo passo.** Um trace é uma transação. Para saber se o problema é geral ou pontual, você precisa de agregados: métricas.

---

## Fase 5 — Olhar as métricas

**Contexto.** Métricas respondem "quantos" e "desde quando". É aqui que RED, USE e os Golden Signals viram ferramenta, não slide.

**Desafio.** Abra o dashboard e descubra desde quando o sistema está assim — e o que mudou nesse horário.

```bash
scripts/investigate-metrics.sh
```

E ao vivo, no Grafana: **<http://localhost:3000/d/aula9>** — o incidente é reencenado em loop de 20 min; os 4 Golden Signals estão na primeira linha, e o painel "Deploy no ar" avisa quando o v1.13.4 entra.

**Perguntas.**

- Qual métrica realmente indica **impacto de negócio**? CPU detectaria este incidente?
- Pelo método **RED** (Rate, Errors, Duration): o que está normal e o que degradou?
- Pelo método **USE** (Utilization, Saturation, Errors): algum recurso está saturado? Qual? (Dica: saturação é fila.)
- Quais dos quatro **Golden Signals** (latency, traffic, errors, saturation) mudaram?
- O que aconteceu às 21:00?

**Registre no report:** as métricas que mudaram e o horário exato, na seção Metrics.

**Próximo passo.** Antes de seguir: por que você achou a transação nos logs e no trace, mas o dashboard não tem um painel "PIX-928371"?

---

## Fase 6 — Cardinalidade: por que a métrica não tem o seu ID

**Contexto.** Alguém do time sugere "criar uma métrica por transação" para a próxima vez:

```text
pix_status{transaction_id="PIX-928371"}
```

**Desafio.** Decida: devemos colocar `transaction_id` como label do Prometheus?

**Perguntas.**

- Quantas séries temporais essa label criaria por dia, com ~16 Pix/s?
- Onde cada tipo de dado deve morar?

```text
Métricas (agregadas, baixa cardinalidade):  status, provider, operation
Logs e traces (por evento, alta cardinalidade): transaction_id, correlation_id, trace_id
```

**Registre no report:** nada — mas use a regra na seção Evidências: métricas para tendência, logs/traces para o caso individual.

Repare que o [dashboard](http://localhost:3000/d/aula9) tem a tabela **"Do dashboard ao trace"**: os IDs do Pix investigado como *info-metric* de 2 exemplares fixos — o papel dos *exemplars* do OpenTelemetry, não uma label por transação.

**Próximo passo.** Você já sabe **o que** degradou e **quando**. Falta a pergunta do cliente: em que estado está o Pix dele?

---

## Fase 7 — O estado da transação

**Desafio.** Pergunte ao sistema.

```bash
curl localhost:8080/payments/PIX-928371
```

(ou clique: <http://localhost:8080/payments/PIX-928371>)

```json
{ "status": "UNKNOWN" }
```

**Perguntas.**

- `UNKNOWN` significa `FAILED`?
- O que o sistema está honestamente admitindo com esse estado?
- Por que marcar como `FAILED` (ou `APPROVED`) sem evidência seria pior do que admitir `UNKNOWN`?

**Registre no report:** o estado e o que ele significa (e o que **não** significa).

**Próximo passo.** O suporte pergunta: "posso reprocessar?". Há um botão para isso. Cuidado.

---

## Fase 8 — A armadilha do retry

**Contexto.** Existe um script de reprocessamento. O suporte usa para "destravar" pagamentos.

```bash
scripts/retry-payment.sh PIX-928371
```

Rode assim, sem flags: ele ainda não executa nada — primeiro te confronta com as perguntas certas.

**Desafio.** Decida: **você executaria o retry agora?** Discuta com a turma antes de qualquer flag.

Depois da discussão, veja o que aconteceria, em ambiente controlado:

```bash
scripts/retry-payment.sh PIX-928371 --executar
```

**Perguntas.**

- O que aconteceu com o dinheiro do cliente nesse cenário simulado?
- Por que o PSP processou duas vezes? Ele tinha como saber que era a mesma operação?

> **Retry de operação financeira não é igual a retry de leitura HTTP.**

**Registre no report:** a seção "Risco de retry".

**Próximo passo.** Existe um jeito de repetir com segurança.

---

## Fase 9 — Idempotência

**Contexto.** Uma chave de idempotência dá à operação uma identidade que sobrevive ao timeout: quem recebe a repetição reconhece "já vi essa" e devolve o resultado anterior.

**Desafio.** Repita a operação do jeito certo.

```bash
scripts/retry-payment.sh PIX-928371 --idempotency-key PIX-928371
```

**Perguntas.**

- Houve nova transferência?
- A idempotência resolveu **todo** o problema? Agora sabemos se a primeira operação foi concluída?

**Registre no report:** a seção "Idempotência".

**Próximo passo.** Idempotência evita o dano. Mas o cliente continua sem resposta. Para descobrir a verdade, pergunte a quem sabe: o ledger e o PSP.

---

## Fase 10 — Reconciliação

**Contexto.** Quando o nosso sistema não sabe, as fontes externas sabem. Reconciliar é comparar as versões da história e decidir com evidência.

**Desafio.** Reconcilie a transação.

```bash
scripts/reconcile.sh PIX-928371
```

**Perguntas.**

- O que cada fonte (Tech Pix, ledger, PSP) dizia?
- Qual evidência permitiu promover `UNKNOWN -> APPROVED`?
- O dinheiro estava perdido?

> O dinheiro não estava perdido. O sistema tinha perdido, temporariamente, a **certeza** sobre o estado da transação.

**Registre no report:** a seção "Reconciliação" — e agora você já pode responder "Onde está o Pix?".

**Próximo passo.** A reconciliação publicou um evento de mudança de estado. Nem tudo correu bem com ele.

---

## Fase 11 — DLQ: o incidente dentro do incidente

**Contexto.** O evento `PaymentStatusChanged` foi publicado no Kafka. Um dos consumers não conseguiu processá-lo.

```text
Kafka -> consumer -> retry 1 -> retry 2 -> retry 3 -> DLQ
```

**Desafio.** Olhe a Dead Letter Queue.

```bash
scripts/dlq-show.sh
```

**Perguntas.**

- A DLQ **resolveu** o erro, ou só evitou que ele travasse a fila inteira?
- Podemos fazer replay sem pensar? O que precisa ser corrigido antes?
- Por que idempotência importa no replay? (E se a mensagem já tivesse sido meio-processada?)

Quando souber responder, reprocesse:

```bash
scripts/dlq-show.sh replay
```

**Registre no report:** o segundo defeito encontrado e por que o replay foi seguro.

**Próximo passo.** Incidente entendido. Agora meça o estrago — e o seu próprio tempo de resposta.

---

## Fase 12 — SLI e SLO

**Contexto.** Dados do período do incidente:

```text
10.000 Pix no período
 9.975 concluídos em <= 5s
    25 acima de 5s (ou sem conclusão)
```

**Desafio.** Com a turma:

1. Escreva o **SLI**: `% de Pix concluídos em <= 5s`.
2. O **SLO** é `99,9%`. Calcule o valor do SLI no período. **Estamos dentro do SLO?** (confira depois no gauge "SLI" do [dashboard](http://localhost:3000/d/aula9))
3. Agora considere um segundo SLI: `% de Pix em UNKNOWN por mais de 5 min`.

**Pergunta.**

- Qual dos dois SLIs é mais importante para um sistema financeiro? Por quê?

**Registre no report:** a seção "SLI/SLO impactado", com o cálculo.

---

## Fase 13 — MTTD, MTTA, MTTR

**Contexto.** A timeline do plantão:

```text
21:03  incidente começa
21:05  alerta dispara
21:07  engenheiro assume
21:12  transação reconciliada
```

**Desafio.** Calcule **MTTD**, **MTTA** e **MTTR**. Neste lab, MTTR = início do incidente até a transação reconciliada (recuperação do ponto de vista do cliente).

**Perguntas.**

- Qual parte do tempo dependeu de ferramenta (alerta) e qual dependeu de gente?
- O que neste lab reduziu o tempo de diagnóstico: mais dashboards, ou logs/trace/métricas que se cruzavam?

> O objetivo de observabilidade não é produzir mais dashboards. É reduzir tempo de diagnóstico e recuperação.

**Registre no report:** MTTD e MTTR.

---

## Fase 14 — RCA: sintoma não é causa

**Contexto.** Evidências na mesa:

```text
PSP p95 aumentou (21:00 em diante)
UNKNOWN aumentou (mesmo período)
CPU normal, Kafka normal, banco normal
deploy v1.13.4 concluído às 21:00
psp-adapter: connection pool caiu de 20 para 2 no deploy
```

**Desafio.** Separe os níveis — sem misturar:

| Nível | Sua resposta |
|---|---|
| Sintoma | ? |
| Impacto no negócio | ? |
| Causa intermediária | ? |
| Causa raiz | ? |

**Perguntas.**

- "O deploy aconteceu às 21:00 e a latência subiu às 21:00" já é **prova** de causalidade, ou ainda é correlação? O que falta para afirmar causa? (diff da configuração, reprodução, rollback que melhora…)
- Que verificação adicional você pediria antes de escrever o RCA?

**Registre no report:** as seções Hipóteses e Causa provável, citando evidências.

---

## Fase 15 — IA como assistente de incidente

**Contexto.** Todo o contexto do incidente está consolidado em um arquivo:

```text
scripts/aula9/incident-context.json
```

**Desafio.** Entregue o arquivo a uma IA (Claude, por exemplo) com este prompt:

```text
Analise as evidências deste incidente.

1. Reconstrua a timeline.
2. Liste 3 hipóteses.
3. Mostre evidências a favor e contra cada hipótese.
4. Não confunda correlação com causalidade.
5. Sugira verificações adicionais.
6. Não altere o estado de nenhuma transação.
```

**Perguntas.**

- A hipótese mais forte da IA bate com a sua? As evidências citadas são reais (estão no arquivo) ou inventadas?
- A IA pode marcar todos os pagamentos `UNKNOWN` como `APPROVED`?

> **AI proposes hypotheses. Evidence decides.**

A IA acelera a investigação; quem decide sobre dinheiro é um humano com evidência de reconciliação.

---

## Desafio final — agora é com você

Ative o segundo incidente. Desta vez, ninguém te dá a causa — nem as fases.

```bash
scripts/lab9-incident2.sh
```

Você recebe um novo chamado (leia a saída do comando) e as mesmas ferramentas:

```text
logs            scripts/investigate-logs.sh <txid>
correlation     scripts/find-correlation.sh <id>
traces          scripts/investigate-trace.sh <txid>
métricas        scripts/investigate-metrics.sh
estado          curl localhost:8080/payments/<txid>
DLQ             scripts/dlq-show.sh
reconciliação   scripts/reconcile.sh <txid>
```

Entregue um [Incident Report](aula09-incident-report-template.md) completo, com:

```text
1. impacto            5. causa provável
2. timeline           6. ação de recuperação
3. hipótese           7. ação preventiva
4. evidências
```

O [dashboard](http://localhost:3000/d/aula9) também muda de história: com o incidente 2 ativo, as mesmas séries passam a contar o novo caso (olhe o CPU do fraud-service, o Kafka lag e o backlog de reconciliação).

Para voltar ao primeiro incidente: `scripts/lab9-incident2.sh off`. Para zerar tudo: `scripts/lab9-reset.sh`.

---

## Epílogo — o plantão que não dorme (synthetic monitoring)

Tudo que você fez até aqui foi **reativo**: o cliente reclamou, você investigou. E às 3h da manhã, sem clientes acordados?

Ligue o robô que refaz a jornada do Pix de tempos em tempos (contra o monólito real da Aula 7, em <http://localhost:8090>):

```bash
python scripts/synthetic-monitor.py start     # a cada 2 min
python scripts/synthetic-monitor.py run       # ou uma vez, agora
```

Acompanhe no painel **"Synthetic: a jornada do Pix funciona AGORA?"** da [Visão Geral](http://localhost:3000/d/techpix-home). Para ver o robô descobrir um problema antes de qualquer cliente:

```bash
scripts/chaos.sh errors 0.9      # 90% das avaliações de fraude falham
# aguarde até 2 min -> o stat fica VERMELHO sem nenhum chamado aberto
scripts/chaos.sh off
```

**Perguntas.**

- O que o synthetic verifica que o health check da Fase 1 não verifica?
- Por que o check é "o dinheiro chegou na conta" e não "a API respondeu 200"?

---

---

## O princípio que este lab queria provar

> Observabilidade não é coletar dados. É conseguir explicar o comportamento do sistema.

E, no contexto financeiro:

> **Observabilidade é conseguir explicar onde está o dinheiro.**

Você começou com "está tudo verde" e um cliente sem R$ 250,00. Terminou com uma frase que explica tudo, sustentada por logs, trace, métricas e reconciliação. Essa é a diferença entre monitorar e observar.
