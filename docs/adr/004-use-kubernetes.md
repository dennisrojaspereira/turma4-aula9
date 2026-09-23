# ADR 004 — Executar o Fraud Service em Kubernetes

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 8

## Context

Fraud virou um processo independente (ADR 003). Isso criou perguntas que não existiam quando Fraud era um pacote dentro do monólito:

- **Onde roda?** Em qual máquina? Quantas cópias?
- **Quem reinicia** quando o processo morre?
- **Quem escala** quando o modelo fica mais caro? Com base em quê?
- **Como Payment encontra Fraud** se os processos nascem e morrem com IPs diferentes?
- **Como saber se uma cópia está pronta** para receber tráfego, e não apenas "rodando"?

Docker Compose responde "onde roda" para uma máquina. Não responde as outras.

## Decision

O Fraud Service (e, para o laboratório, o monólito e o PostgreSQL) roda em Kubernetes. Começamos com o mínimo:

| Recurso | Pergunta que responde |
|---|---|
| `Deployment` | quantas cópias, qual imagem, como atualizar sem derrubar |
| `Pod` | a unidade que roda; efêmera, com IP próprio que ninguém deve conhecer |
| `Service` | o nome estável (`fraud-service`) que resolve para os Pods prontos |
| `ConfigMap` / `Secret` | configuração e senhas fora da imagem |
| probes (startup, readiness, liveness) | "já iniciou?", "pode receber tráfego?", "está vivo?" |
| `resources.requests/limits` | quanto reservar, qual o teto |
| `HorizontalPodAutoscaler` | escalar por uma métrica explícita (CPU sobre o request) |

Não usamos, por ora: Ingress, StatefulSet, operadores, service mesh, Helm. Cada um resolve um problema que a Tech Pix ainda não tem.

Ambiente local: kind, um node, imagens carregadas direto (sem registry).

## Alternatives

- **Docker Compose em produção.** Rejeitado. Uma máquina, sem reinício automático entre hosts, sem escala por métrica, sem readiness.
- **VMs com systemd e um load balancer.** Funciona. Rejeitado pelo custo de reproduzir manualmente o que Deployment, Service e probes dão de graça. Seria uma escolha legítima para uma equipe sem experiência em Kubernetes e com poucos serviços.
- **Serverless / FaaS para Fraud.** Considerado. Fraud tem warm-up (cache de blacklist) e latência sensível; cold start seria um problema. Rejeitado para este caso.
- **Manter Fraud no Compose e só o monólito em VM.** Não resolve descoberta nem escala.

## Consequences

Positivas:

- Reinício, escala e descoberta viram declaração em YAML, não runbook.
- Payment chama `http://fraud-service:8081`. Pods podem morrer e nascer; o nome não muda.
- Readiness impede tráfego para um Pod que ainda não aqueceu. Rolling update com `maxUnavailable: 0` dá zero-downtime.

Negativas:

- **Curva de aprendizado.** Pod, Deployment, Service, probe, request, limit, HPA, kustomize: cada um é um conceito novo para a equipe.
- **Um cluster para operar.** Upgrades, certificados, rede, storage. No laboratório o kind esconde isso; em produção não.
- **YAML como código.** Sem revisão e sem fonte única de verdade, o YAML vira drift (ADR 005).
- **Custo de recursos.** Cada Pod reserva CPU e memória; um cluster pequeno ocioso ainda paga.
- **Debug mais distante.** `kubectl logs`, `kubectl exec`, port-forward. Nada de anexar o debugger na porta local.
- **Restarts silenciosos.** O monólito reiniciou duas vezes até o PostgreSQL ficar pronto. Kubernetes "resolveu"; em produção isso aparece como `RESTARTS: 2` e precisa de explicação.
