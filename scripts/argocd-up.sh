#!/usr/bin/env bash
# Instala o Argo CD no cluster kind "techpix" e registra as Applications dev/qa/prod.
#
# Pre-requisito: este repositorio precisa estar em um Git remoto que o Argo CD consiga ler
# (GitHub, GitLab, Gitea...). O Argo CD roda DENTRO do cluster e clona o repositorio de la.
#
#   TECHPIX_GIT_URL=https://github.com/SEU-USUARIO/tech-pix.git scripts/argocd-up.sh
#
# Nao e obrigatorio para nada alem do lab 17. Os testes Java nao dependem disto.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${TECHPIX_GIT_URL:?defina TECHPIX_GIT_URL com a URL do seu repositorio Git (ex.: https://github.com/voce/tech-pix.git)}"

kubectl config use-context kind-techpix >/dev/null

echo "== instalando Argo CD (namespace argocd)"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml >/dev/null
echo "== aguardando componentes (pode levar alguns minutos na primeira vez)"
kubectl -n argocd rollout status deployment/argocd-server --timeout=600s
kubectl -n argocd rollout status deployment/argocd-repo-server --timeout=600s
kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=600s

echo "== registrando Applications apontando para $TECHPIX_GIT_URL"
for env in dev qa prod; do
  sed "s#\${TECHPIX_GIT_URL}#$TECHPIX_GIT_URL#" "argocd/applications/$env.yaml" | kubectl apply -f -
done

PASS=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)
echo
echo "Argo CD instalado."
echo "  UI:      kubectl -n argocd port-forward svc/argocd-server 8443:443   ->  https://localhost:8443"
echo "  usuario: admin"
echo "  senha:   $PASS"
echo
echo "  Applications: kubectl -n argocd get applications"
echo "  Drift demo:   scripts/argocd-drift-demo.sh"
