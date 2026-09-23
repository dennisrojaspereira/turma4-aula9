#!/usr/bin/env bash
# Cria o cluster kind "techpix", carrega as imagens locais e aplica os manifests.
#   scripts/k8s-up.sh            -> usa kubernetes/ (labs 09-12)
#   scripts/k8s-up.sh gitops dev -> usa gitops/overlays/dev (lab 16)
set -euo pipefail
cd "$(dirname "$0")/.."
SOURCE="${1:-raw}"
OVERLAY="${2:-dev}"

EXISTED=false
if ! kind get clusters 2>/dev/null | grep -qx techpix; then
  echo "== criando cluster kind 'techpix'"
  kind create cluster --config kubernetes/kind-config.yaml --wait 60s
else
  echo "== cluster kind 'techpix' ja existe"
  EXISTED=true
fi
kubectl config use-context kind-techpix >/dev/null

echo "== construindo imagens"
docker compose --profile app build -q monolith fraud-service

echo "== carregando imagens no cluster (sem registry)"
kind load docker-image techpix/monolith:local techpix/fraud-service:local --name techpix

echo "== metrics-server (necessario para o HPA e para kubectl top)"
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml >/dev/null
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' >/dev/null 2>&1 || true

echo "== aplicando manifests ($SOURCE)"
case "$SOURCE" in
  raw)    kubectl apply -k kubernetes/; NS=techpix; PORT=8090 ;;
  gitops) kubectl apply -k "gitops/overlays/$OVERLAY"; NS="techpix-$OVERLAY"
          case "$OVERLAY" in dev) PORT=8090 ;; qa) PORT=8092 ;; prod) PORT=8094 ;; esac ;;
  *) echo "uso: $0 [raw|gitops [dev|qa|prod]]"; exit 1 ;;
esac

if [ "$EXISTED" = true ]; then
  # A tag da imagem nao muda (":local"), entao um Pod antigo nao pega a imagem nova sozinho.
  # Reciclar os Pods da aplicacao garante imagem nova e Flyway rodando de novo se o Postgres foi recriado.
  echo "== reciclando Pods da aplicacao (imagem nova, mesma tag)"
  kubectl -n "$NS" delete pod -l 'app in (monolith,fraud-service)' --wait=false >/dev/null 2>&1 || true
fi

echo "== aguardando rollout (namespace $NS)"
kubectl -n "$NS" rollout status deployment/postgres --timeout=120s
kubectl -n "$NS" rollout status deployment/monolith --timeout=300s
kubectl -n "$NS" rollout status deployment/fraud-service --timeout=300s

echo
kubectl -n "$NS" get pods -o wide
echo
echo "Monolito:      http://localhost:$PORT   (TECHPIX_URL=http://localhost:$PORT scripts/demo-payment.sh)"
echo "Fraud Service: http://localhost:$((PORT+1))/actuator/health/readiness"
