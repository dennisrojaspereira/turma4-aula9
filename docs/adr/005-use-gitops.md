# ADR 005 — GitOps com Kustomize e Argo CD

**Status:** aceito
**Data:** 2026-09-23
**Etapa:** 11

## Context

Depois dos labs 13 a 15, a Tech Pix tem configuração que muda o comportamento do sistema sem deploy: modo da flag (`LEGACY`, `PARALLEL`, `CANARY`, `NEW`), percentual do canário, réplicas, warm-up, timeouts. E tem três ambientes (dev, qa, prod).

O que aconteceu na prática:

```text
DEV   = configuracao A (PARALLEL, 2 replicas)
QA    = configuracao B (CANARY 10%, 3 replicas)
PROD  = alguem rodou kubectl scale e PUT /admin/fraud/canary as 14h e nao anotou
```

Ninguém sabe mais qual é o estado **desejado** de PROD. Só o estado atual, e só olhando o cluster. Isso tem nome: configuration drift.

Agravante: a feature flag vive na memória de cada réplica. `PUT /admin/fraud/mode` muda a réplica que atendeu a chamada. As outras ficam como estavam.

## Decision

1. **Git é o desired state.** Toda configuração de runtime (réplicas, ConfigMaps, flags, HPA) vive em `gitops/`, versionada. Nada é alterado no cluster por `kubectl edit`, `kubectl scale` ou endpoint administrativo em QA e PROD. Esses caminhos continuam existindo para DEV e para o laboratório.
2. **Kustomize** para expressar o que é comum (`base/`) e o que é específico (`overlays/dev|qa|prod`). Sem templates: patches sobre YAML válido.
3. **Argo CD** reconcilia: compara o Git com o cluster, mostra drift, e (em dev/qa) desfaz automaticamente. Em PROD a reconciliação é manual: o drift é detectado, alguém aprova.

```text
Git (desired state)
  |
  v
Argo CD (reconciliation loop)
  |
  v
Kubernetes (actual state)
```

## Alternatives

- **`kubectl apply` em um pipeline de CI.** Push-based. Funciona, mas o pipeline precisa de credenciais do cluster, não detecta drift entre execuções, e "o que está em PROD" é "o que o último pipeline aplicou", não "o que o Git diz".
- **Helm.** Templates. Poderoso, mas para três ambientes com meia dúzia de diferenças, patches são mais legíveis do que `{{ .Values }}`. Se o número de ambientes ou de serviços crescer, reavaliar.
- **Flux.** Equivalente ao Argo CD para este propósito. Escolhemos Argo CD pela interface visual, que ajuda a aula a **ver** o drift.
- **Não fazer nada e disciplinar as pessoas.** Rejeitado. Disciplina não escala e não deixa auditoria.

## Consequences

Positivas:

- `git log gitops/overlays/prod` responde "quem mudou o quê, quando, e por quê" (se a mensagem de commit for boa).
- Rollback de configuração é `git revert`.
- O drift aparece na interface antes de virar incidente.
- Ambientes deixam de divergir silenciosamente.

Negativas:

- **Mais um componente crítico.** Argo CD parado significa que nada mais é reconciliado. Precisa de monitoramento próprio.
- **Latência da mudança.** Um commit leva o intervalo de polling (padrão 3 min) para chegar ao cluster, a menos que haja webhook. A troca de flag "em milissegundos" do lab 13 vira "em minutos". É o preço de ter fonte única de verdade.
- **Segredos.** `secret.yaml` em texto no Git é inaceitável fora do laboratório. Exige Sealed Secrets, External Secrets ou similar. Deixamos isso explícito e não resolvemos.
- **O endpoint administrativo vira um problema.** Em ambientes reconciliados, `PUT /admin/fraud/mode` cria drift que o Argo CD desfaz no próximo ciclo (se o valor vier de ConfigMap com restart) ou que **não** desfaz (porque o Argo CD não sabe do estado em memória). Regra: fora de DEV, a flag muda por commit.
- **Selo-heal em PROD é uma decisão de risco.** Escolhemos manual. Alguns times escolhem automático. Não há resposta certa; há uma decisão que precisa estar escrita.
