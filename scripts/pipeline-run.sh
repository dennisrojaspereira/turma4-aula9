#!/usr/bin/env bash
# Dispara uma execucao da security-pipeline (lab 21) e acompanha ate o fim.
#   scripts/pipeline-run.sh
# Pre-requisitos: scripts/tekton-up.sh, scripts/local-git-server.sh, scripts/k8s-up.sh.
# Opcional: TECHPIX_DAST_URL para apontar o DAST para outro alvo.
set -euo pipefail
cd "$(dirname "$0")/.."

kubectl config use-context kind-techpix >/dev/null

# garante que a pipeline analisa o codigo mais recente, nao o do ultimo push
if docker ps --format '{{.Names}}' | grep -qx techpix-gitea; then
  echo "== push do codigo atual para o Gitea local"
  git push -q local main --force
else
  echo "ERRO: o Gitea local nao esta rodando. Rode scripts/local-git-server.sh primeiro." >&2
  exit 1
fi

# alvo do DAST: a aplicacao rodando no cluster (raw -> techpix, gitops -> techpix-dev)
if [ -n "${TECHPIX_DAST_URL:-}" ]; then
  TARGET="$TECHPIX_DAST_URL"
elif kubectl get namespace techpix >/dev/null 2>&1; then
  TARGET="http://monolith.techpix.svc.cluster.local:8080"
elif kubectl get namespace techpix-dev >/dev/null 2>&1; then
  TARGET="http://monolith.techpix-dev.svc.cluster.local:8080"
else
  echo "ERRO: nenhuma aplicacao rodando no cluster (namespace techpix ou techpix-dev)." >&2
  echo "Rode scripts/k8s-up.sh primeiro, ou defina TECHPIX_DAST_URL." >&2
  exit 1
fi
echo "== alvo do DAST: $TARGET"

echo "== criando PipelineRun"
# workspace "source": um PVC novo por execucao (volumeClaimTemplate)
# workspace "maven-cache": o MESMO PVC em todas as execucoes (cache de dependencias)
RUN=$(kubectl create -f - -o name <<EOF
apiVersion: tekton.dev/v1
kind: PipelineRun
metadata:
  generateName: security-pipeline-
  namespace: techpix-ci
spec:
  pipelineRef:
    name: security-pipeline
  params:
    - name: revision
      value: main
    - name: target-url
      value: $TARGET
  workspaces:
    - name: source
      volumeClaimTemplate:
        spec:
          accessModes:
            - ReadWriteOnce
          resources:
            requests:
              storage: 1Gi
    - name: maven-cache
      persistentVolumeClaim:
        claimName: maven-cache
EOF
)
RUN_NAME="${RUN##*/}"
echo "== $RUN_NAME criado"

if command -v tkn >/dev/null 2>&1; then
  # com o cli do Tekton: logs em tempo real (|| true: o veredicto sai do kubectl abaixo)
  tkn -n techpix-ci pipelinerun logs -f "$RUN_NAME" || true
  STATUS=$(kubectl -n techpix-ci get pipelinerun "$RUN_NAME" \
    -o jsonpath='{.status.conditions[?(@.type=="Succeeded")].status}')
else
  # sem o cli: acompanha a condicao e despeja os logs no final
  echo "== acompanhando (instale o cli 'tkn' para logs em tempo real)"
  while true; do
    REASON=$(kubectl -n techpix-ci get pipelinerun "$RUN_NAME" \
      -o jsonpath='{.status.conditions[?(@.type=="Succeeded")].reason}' 2>/dev/null || true)
    STATUS=$(kubectl -n techpix-ci get pipelinerun "$RUN_NAME" \
      -o jsonpath='{.status.conditions[?(@.type=="Succeeded")].status}' 2>/dev/null || true)
    echo "   status=$STATUS reason=${REASON:-aguardando}"
    if [ "$STATUS" = "True" ] || [ "$STATUS" = "False" ]; then
      break
    fi
    sleep 5
  done
  echo
  echo "== logs das tasks"
  for POD in $(kubectl -n techpix-ci get pods -l "tekton.dev/pipelineRun=$RUN_NAME" \
      -o jsonpath='{.items[*].metadata.name}'); do
    echo "----- $POD -----"
    kubectl -n techpix-ci logs "$POD" --all-containers || true
  done
fi

echo
echo "Resultados:"
echo "  SAST: http://localhost:9000/dashboard?id=tech-pix"
echo "  DAST: relatorio ZAP nos logs da task dast (acima)"

if [ "$STATUS" != "True" ]; then
  echo "PIPELINE FALHOU (quality gate reprovado, FAIL do ZAP ou erro de build)." >&2
  exit 1
fi
