# Lab 12 — Readiness, Liveness e Startup

**Slide suportado:** 17 — Readiness/Liveness/Startup
**Tag:** `aula07-step-08-kubernetes`

## Problema

O Fraud Service carrega a blacklist em memória e aquece caches antes de poder decidir. O processo sobe em segundos; o serviço só está pronto depois.

Se o Service rotear tráfego para um Pod que está `Running` mas ainda não pronto, Payment recebe erro ou latência alta. Isso aparece exatamente durante deploys e escalas, que é quando menos queremos surpresas.

```text
Pod Running  !=  aplicacao pronta
```

## O que observar

```bash
kubectl -n techpix get pods -l app=fraud-service -w      # READY 0/1 -> 1/1
kubectl -n techpix get endpoints fraud-service           # so os READY
curl localhost:8091/actuator/health/readiness            # {"status":"DOWN"} durante o warm-up
curl localhost:8091/actuator/health/liveness             # {"status":"UP"} desde o inicio
```

## As três perguntas

| Probe | Pergunta | Se falhar | No Fraud Service |
|---|---|---|---|
| **startup** | "já terminou de inicializar?" | as outras probes esperam; passado o limite, reinicia | `/actuator/health/liveness`, até 120 s |
| **readiness** | "pode receber tráfego?" | sai dos endpoints do Service; **não reinicia** | `/actuator/health/readiness`: DOWN até blacklist carregada e warm-up concluído |
| **liveness** | "o processo está vivo?" | o kubelet **reinicia** o container | `/actuator/health/liveness` |

Implementação: [WarmupReadiness.java](../../fraud-service/src/main/java/com/techpix/fraudservice/health/WarmupReadiness.java) e os grupos de health em [application.yml](../../fraud-service/src/main/resources/application.yml):

```yaml
group:
  readiness:
    include: readinessState,db,warmup     # banco fora? nao mande trafego
  liveness:
    include: livenessState                # banco fora? NAO reinicie (nao resolveria nada)
```

A diferença entre os grupos é a lição mais importante deste lab. Banco indisponível é motivo para **não receber tráfego**, não para **reiniciar**. Se liveness incluísse o banco, uma queda do PostgreSQL derrubaria todos os Pods de Fraud em cascata, e eles voltariam a cair, em loop.

## Mudança

`FRAUD_WARMUP_SECONDS: "20"` no [ConfigMap](../../kubernetes/fraud-service/configmap.yaml). Didático: força 20 s de `readiness: DOWN` para que a aula veja o intervalo.

## Como executar

```bash
kubectl -n techpix rollout restart deployment/fraud-service
kubectl -n techpix get pods -l app=fraud-service -w
```

Em outro terminal, durante o restart:

```bash
kubectl -n techpix get endpoints fraud-service
```

## Resultado (medido)

Depois de apagar um Pod (lab 10):

```text
NAME                             READY   STATUS    RESTARTS   AGE
fraud-service-867797b66d-jmtdw   1/1     Running   0          2m7s
fraud-service-867797b66d-nttj8   1/1     Running   0          2m7s
fraud-service-867797b66d-nvvvh   0/1     Running   0          24s    <- Running ha 24s, READY nao

== endpoints (so os READY):
10.244.0.7 10.244.0.8                                              <- o terceiro nao esta aqui
```

`HealthProbesIT` prova o mesmo em teste: com warm-up de 3 s, liveness responde 200 e readiness responde 503 até o warm-up terminar.

## Zero-downtime deployment

Com `maxUnavailable: 0` e `maxSurge: 1` no Deployment:

```text
rollout restart
  -> cria 1 Pod novo (surge)
  -> espera ele ficar READY (readiness passa)
  -> so entao mata 1 Pod velho
  -> repete
```

Em nenhum momento há menos de 3 Pods READY. Sem readiness, o Kubernetes mataria o velho assim que o novo estivesse `Running`, e o Service rotearia para um Pod que ainda não sabe responder.

## Trade-offs

```text
Probes
+ trafego so para quem esta pronto
+ reinicio automatico de processos travados
+ zero-downtime em rolling update

- readiness que depende de algo externo (banco) tira TODOS os Pods do ar juntos quando o externo cai
- liveness agressiva demais reinicia Pods saudaveis sob carga (GC longo, thread pool cheio)
- startup curto demais mata JVMs lentas antes de terminarem de subir
- cada probe e uma requisicao HTTP periodica por Pod: custo pequeno, mas existe
```

## Pergunta para discussão

> Se a liveness do Fraud Service incluísse o banco, o que aconteceria quando o PostgreSQL caísse por 30 segundos?

## Professor Notes

```text
Pergunta:
Qual a diferenca pratica entre readiness DOWN e liveness DOWN?

Resposta:
Readiness DOWN: sai da lista do Service, continua rodando. Liveness DOWN: e reiniciado.

Erro comum:
Apontar as tres probes para o mesmo /health que inclui o banco.
Queda do banco vira restart em loop de todos os Pods.

Conceito:
Pod Running != aplicacao pronta. Readiness e o que faz o rolling update ser zero-downtime.

Transicao:
O runtime esta resolvido. Agora, quantos pagamentos mandamos para o Fraud Service? Todos de uma vez?
```

## Próximo problema

Fraud Service roda, escala, é descoberto e só recebe tráfego quando pronto. Mas `fraud.mode=NEW` manda 100% dos pagamentos para ele de uma vez. Precisamos de mais controle. [Lab 13](13-feature-flags.md).
