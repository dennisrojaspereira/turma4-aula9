# Lab 11 — Escala independente

**Slide suportado:** 16 — Escala independente
**Tag:** `aula07-step-08-kubernetes`

## Problema

No lab 06, a pergunta era: "por que escalar Payment 30 vezes porque Fraud precisa de mais CPU?". Agora Fraud tem seu próprio Deployment. Vamos escalar só ele.

E depois: quem decide quantas cópias? Com base em quê?

## O que observar

```bash
kubectl -n techpix get deploy          # replicas de cada um
kubectl -n techpix top pods            # CPU e memoria reais (metrics-server)
kubectl -n techpix get hpa             # utilizacao vs alvo, replicas atuais
```

## Parte 1: requests e limits

Em [fraud-service/deployment.yaml](../../kubernetes/fraud-service/deployment.yaml):

```yaml
resources:
  requests:
    cpu: 250m        # o scheduler RESERVA isso; e a base do calculo do HPA
    memory: 256Mi
  limits:
    cpu: "1"         # acima disso: throttling
    memory: 512Mi    # acima disso: OOMKilled
```

`request` é o que o Pod ganha garantido. `limit` é o teto. A diferença entre eles é o que o Pod pode "pegar emprestado" se o node tiver sobra.

## Parte 2: escala manual

```bash
scripts/k8s-scale.sh 2 5       # monolith=2, fraud-service=5
```

```text
NAME            READY   UP-TO-DATE   AVAILABLE
fraud-service   5/5     5            5
monolith        2/2     2            2
```

Payment continua com 2. Fraud foi para 5. Antes da extração, isso era impossível: eram o mesmo processo.

No laboratório usamos 2 e 5 em vez de 5 e 30. A proporção é o que importa; o notebook não é.

## Parte 3: escala automática, com a métrica explícita

Não aplique o HPA antes de responder: **qual métrica?**

```text
workload           avaliacoes de fraude por segundo
   |
metrica            CPU do Pod (o modelo e CPU-bound; e o que o lab 06 mediu)
   |
threshold          60% do request (250m) = 150m por Pod, em media
   |
scaling decision   media acima -> mais Pods, ate 6. Media abaixo por 60s -> menos Pods, ate 2.
```

[fraud-service/hpa.yaml](../../kubernetes/hpa.yaml):

```bash
scripts/k8s-scale.sh hpa
```

```text
NAME            REFERENCE                  TARGETS       MINPODS   MAXPODS   REPLICAS
fraud-service   Deployment/fraud-service   cpu: 2%/60%   2         6         5
```

`2%/60%`: sem carga, 2% do request. Abaixo do alvo por mais de 60 s, o HPA reduz para `minReplicas: 2`. Com carga (`TECHPIX_URL=http://localhost:8090 scripts/load-test.sh custom 30 120s`, em modo `NEW`, depois de `scripts/seed.sh`), a CPU sobe e o HPA adiciona réplicas.

Por que CPU e não requisições por segundo? Porque o lab 06 mostrou que o custo de Fraud é CPU. Se fosse I/O, CPU seria a métrica errada: o Pod ficaria esperando o banco com CPU baixa e o HPA não escalaria. **A métrica vem da evidência, não do default.**

## Trade-offs

```text
Autoscaling
+ replicas seguem a carga sem intervencao
+ a metrica e explicita e revisavel

- HPA reage com atraso (coleta a cada 15s, decisao a cada 15s, Pod novo leva 20s+ para ficar READY)
- picos curtos passam antes de o HPA agir; picos longos podem esgotar maxReplicas
- CPU e proxy: se o gargalo virar o banco, mais Pods so pioram
- escalar Fraud nao escala o PostgreSQL compartilhado (lab 18)
- requests baixos demais: OOMKill e throttling. Altos demais: cluster ocioso e caro
```

## Pergunta para discussão

> O HPA adicionou 3 Pods de Fraud. Todos apontam para o mesmo PostgreSQL. O que acontece com o banco?

## Professor Notes

```text
Pergunta:
Por que nao aplicar o HPA no lab 09, junto com o Deployment?

Resposta:
Porque HPA sem metrica justificada e um botao magico. A metrica (CPU) vem do lab 06.

Erro comum:
Copiar um HPA de CPU 80% de um tutorial sem saber se o servico e CPU-bound.

Conceito:
workload -> metrica -> threshold -> decisao. Nessa ordem.

Transicao:
Um Pod novo leva 20 s para ficar pronto. O que acontece se o Service mandar trafego antes?
```

## Próximo problema

Escalar cria Pods novos. Um Pod novo está `Running` em segundos, mas só sabe responder depois de aquecer. [Lab 12](12-health-checks.md).
