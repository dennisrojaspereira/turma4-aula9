# Lab 10 — Service Discovery

**Slide suportado:** 15 — Service Discovery
**Tag:** `aula07-step-08-kubernetes`

## Problema

Três Pods de Fraud, cada um com um IP. Os IPs mudam a cada reinício, escala ou deploy. Se Payment conhecesse um IP, cada morte de Pod seria um incidente em Payment.

## O que observar

```bash
kubectl -n techpix get configmap monolith-config -o jsonpath='{.data.TECHPIX_FRAUD_SERVICE_URL}'
# http://fraud-service:8081          <- o que Payment conhece: um NOME

kubectl -n techpix get endpoints fraud-service
# 10.244.0.7:8081,10.244.0.8:8081,10.244.0.9:8081   <- o que o Service conhece: os Pods READY

kubectl -n techpix exec deploy/monolith -- nslookup fraud-service
# fraud-service.techpix.svc.cluster.local -> ClusterIP
```

## Hipóteses

> Se Payment chama um nome e o Service mantém a lista de Pods prontos, podemos apagar um Pod no meio de uma sequência de pagamentos e nenhum deve falhar.

## Mudança

Nenhuma. O Service já existe desde o lab 09. Este lab é uma demonstração.

## Como executar

```bash
scripts/k8s-discovery-demo.sh
```

O script: mostra os endpoints, faz 20 pagamentos em modo `NEW`, apaga um Pod de Fraud no quinto, e mostra os endpoints de novo.

## Resultado (medido)

```text
== Endpoints do Service fraud-service:
10.244.0.9
10.244.0.8
10.244.0.7

== O que o monolito conhece:
http://fraud-service:8081

== 20 pagamentos; no 5o, o Pod fraud-service-867797b66d-dhgnd sera apagado
   pagamento  1 -> HTTP 201
   ...
   (pod fraud-service-867797b66d-dhgnd apagado)
   pagamento  5 -> HTTP 201
   ...
   pagamento 20 -> HTTP 201

== resultado: ok=20 falhas=0

== Endpoints agora (repare: IPs diferentes, mesmo nome):
10.244.0.7
10.244.0.8
NAME                             READY   STATUS    RESTARTS   AGE
fraud-service-867797b66d-jmtdw   1/1     Running   0          117s
fraud-service-867797b66d-nttj8   1/1     Running   0          117s
fraud-service-867797b66d-nvvvh   0/1     Running   0          14s     <- o substituto, ainda aquecendo
```

Um Pod morreu. Um Pod nasceu. O substituto está `Running` mas não `READY`, e por isso **não está nos endpoints**. Payment não soube de nada.

O que o Service faz:

```text
1. DNS       fraud-service -> ClusterIP (estavel)
2. Endpoints ClusterIP -> lista de Pods cujo readinessProbe passa
3. Balanceamento  cada conexao nova vai para um Pod da lista
```

## Trade-offs

```text
Service Discovery via Kubernetes Service
+ Payment nunca conhece IP
+ Pods entram e saem da lista automaticamente
+ funciona igual com 3 ou 30 Pods

- balanceamento por conexao, nao por requisicao: com keep-alive, um cliente pode ficar
  preso a um Pod. Para balanceamento por requisicao: service mesh ou client-side LB.
- o DNS do cluster e mais um componente que pode falhar
- "fraud-service" e um nome local ao namespace. Cross-namespace precisa do FQDN.
```

## Pergunta para discussão

> O Pod novo levou 20 s para ficar READY. Durante esse tempo, quem atendeu? E se os outros dois também tivessem sido apagados?

## Professor Notes

```text
Pergunta:
Por que nenhum dos 20 pagamentos falhou?

Resposta:
Porque Payment chama um nome; o Service tirou o Pod morto da lista antes
de a proxima requisicao chegar, e os outros dois Pods estavam READY.

Erro comum:
Colocar o IP do Pod em configuracao "so para testar". Funciona ate o primeiro restart.

Conceito:
Endpoint estavel + descoberta + balanceamento = Service.

Transicao:
Se Fraud precisar de 30 Pods e Payment de 5, como fazemos isso sem escalar tudo junto?
```

## Próximo problema

Agora que Fraud é descoberto pelo nome, podemos ter quantas cópias quisermos. Quantas? Com base em quê? [Lab 11](11-scaling.md).
