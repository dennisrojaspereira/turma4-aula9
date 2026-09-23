#!/usr/bin/env bash
# Cria o cluster kind "techpix", carrega as imagens locais e aplica os manifests.
#   scripts/k8s-up.sh            -> usa kubernetes/ (labs 09-12)
#   scripts/k8s-up.sh gitops dev -> usa gitops/overlays/dev (lab 16)
set -euo pipefail
cd "$(dirname "$0")/.."
SOURCE="${1:-raw}"
OVERLAY="${2:-dev}"

if ! kind get clusters 2>/dev/null | grep -qx techpix; then
  echo "== criando cluster kind 'techpix'"
  kind create cluster --config kubernetes/kind-config.yaml --wait 60s
else
  echo "== cluster kind 'techpix' ja existe"
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
  raw)    kubectl apply -k kubernetes/ ;;
  gitops) kubectl apply -k "gitops/overlays/$OVERLAY" ;;
  *) echo "uso: $0 [raw|gitops [dev|qa|prod]]"; exit 1 ;;
esac

echo "== aguardando rollout"
kubectl -n techpix rollout status deployment/postgres --timeout=120s
kubectl -n techpix rollout status deployment/monolith --timeout=300s
kubectl -n techpix rollout status deployment/fraud-service --timeout=300s

echo
kubectl -n techpix get pods -o wide
echo
echo "Monolito:      http://localhost:8090   (TECHPIX_URL=http://localhost:8090 scripts/demo-payment.sh)"
echo "Fraud Service: http://localhost:8091/actuator/health/readiness"
