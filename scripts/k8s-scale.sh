#!/usr/bin/env bash
# Escala independente: Payment e Fraud com numeros diferentes de replicas.
#   scripts/k8s-scale.sh 2 5        -> monolith=2, fraud-service=5
#   scripts/k8s-scale.sh hpa        -> aplica o HPA de Fraud e mostra
#   scripts/k8s-scale.sh watch      -> acompanha replicas e CPU
set -euo pipefail
cd "$(dirname "$0")/.."
NS="-n techpix"
case "${1:-}" in
  hpa)
    kubectl $NS apply -f kubernetes/hpa.yaml
    kubectl $NS get hpa
    ;;
  watch)
    watch -n 2 "kubectl $NS get hpa; echo; kubectl $NS get deploy; echo; kubectl $NS top pods 2>/dev/null || echo 'metrics-server ainda sem dados'"
    ;;
  *)
    MONO="${1:-2}"; FRAUD="${2:-3}"
    kubectl $NS scale deployment/monolith --replicas="$MONO"
    kubectl $NS scale deployment/fraud-service --replicas="$FRAUD"
    kubectl $NS get deploy
    ;;
esac
