#!/usr/bin/env bash
# Drift ao vivo: "alguem" altera o cluster na mao; o Argo CD percebe e desfaz.
#   Desired state (Git): fraud-service replicas=2 em techpix-dev
#   Alguem faz:          kubectl scale ... --replicas=5
#   Argo CD (selfHeal):  volta para 2
set -euo pipefail
NS=techpix-dev
DESIRED=$(kubectl -n $NS get deploy fraud-service -o jsonpath='{.spec.replicas}')
echo "== desired state (Git, via Argo CD): fraud-service replicas=$DESIRED"
echo "== alguem altera o cluster na mao:"
kubectl -n $NS scale deploy fraud-service --replicas=5
echo "== actual state agora:"
kubectl -n $NS get deploy fraud-service
echo
echo "== Argo CD: Desired != Actual -> drift -> reconciliation. Aguardando..."
for i in $(seq 1 90); do
  NOW=$(kubectl -n $NS get deploy fraud-service -o jsonpath='{.spec.replicas}')
  STATUS=$(kubectl -n argocd get application techpix-dev -o jsonpath='{.status.sync.status}' 2>/dev/null || echo "?")
  printf "   t=%3ds  replicas=%s  sync=%s\n" "$((i*2))" "$NOW" "$STATUS"
  if [ "$NOW" = "$DESIRED" ]; then
    echo "== reconciliado: o cluster voltou ao que o Git diz ($DESIRED replicas)."
    exit 0
  fi
  sleep 2
done
echo "== o Argo CD nao reconciliou em 180s. Verifique: kubectl -n argocd get application techpix-dev -o yaml"
exit 1
