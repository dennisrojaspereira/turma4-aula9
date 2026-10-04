#!/usr/bin/env bash
# Instala a pipeline de seguranca (lab 21): Tekton no cluster kind "techpix",
# SonarQube em container Docker no host (mesmo padrao do Gitea) e os manifests tekton/.
#   scripts/tekton-up.sh
# Pre-requisito: cluster kind "techpix" (scripts/k8s-up.sh).
# Opcional: SONAR_ADMIN_PASS se a senha do admin do SonarQube nao for mais "admin".
set -euo pipefail
cd "$(dirname "$0")/.."

SONAR_NAME=techpix-sonarqube
SONAR_PORT=9000
SONAR_ADMIN_PASS="${SONAR_ADMIN_PASS:-admin}"

if ! kubectl config use-context kind-techpix >/dev/null 2>&1; then
  echo "ERRO: contexto kind-techpix nao existe. Rode scripts/k8s-up.sh primeiro." >&2
  exit 1
fi

echo "== instalando Tekton Pipelines (namespace tekton-pipelines)"
kubectl apply -f https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml >/dev/null
echo "== aguardando componentes do Tekton (primeira vez baixa imagens, pode demorar)"
kubectl -n tekton-pipelines rollout status deployment/tekton-pipelines-controller --timeout=600s
kubectl -n tekton-pipelines rollout status deployment/tekton-pipelines-webhook --timeout=600s

echo "== instalando Tekton Dashboard (UI web)"
kubectl apply -f https://storage.googleapis.com/tekton-releases/dashboard/latest/release.yaml >/dev/null
kubectl -n tekton-pipelines rollout status deployment/tekton-dashboard --timeout=300s

# A task maven-sonar monta DOIS PVCs (source e maven-cache). Com o affinity assistant
# ligado (padrao), o Tekton se recusa a agendar uma task com mais de um PVC.
# Desabilitar e seguro aqui: o cluster kind tem um unico node, entao todos os Pods
# enxergam os mesmos volumes de qualquer jeito.
echo "== desabilitando affinity assistant (task com dois PVCs)"
kubectl -n tekton-pipelines patch configmap feature-flags \
  -p '{"data":{"disable-affinity-assistant":"true","coschedule":"disabled"}}' >/dev/null

if docker ps --format '{{.Names}}' | grep -qx "$SONAR_NAME"; then
  echo "== SonarQube ja esta rodando (container $SONAR_NAME)"
else
  echo "== subindo SonarQube em http://localhost:$SONAR_PORT (container $SONAR_NAME)"
  # SONAR_ES_BOOTSTRAP_CHECKS_DISABLE: pula os checks do Elasticsearch embutido
  # (vm.max_map_count etc.), que nao fazem sentido em um laboratorio local.
  docker run -d --name "$SONAR_NAME" -p "$SONAR_PORT:9000" \
    -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
    sonarqube:community >/dev/null
fi

echo "== aguardando SonarQube responder (ate ~300s; a primeira subida e lenta)"
UP=false
for i in $(seq 1 60); do
  if curl -sf "http://localhost:$SONAR_PORT/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
    UP=true; break
  fi
  echo "   ... aguardando ($((i*5))s)"
  sleep 5
done
if [ "$UP" != true ]; then
  echo "ERRO: SonarQube nao ficou pronto. Veja: docker logs $SONAR_NAME" >&2
  exit 1
fi

echo "== gerando token de acesso no SonarQube"
TOKEN=$(curl -s -u "admin:$SONAR_ADMIN_PASS" -X POST \
  "http://localhost:$SONAR_PORT/api/user_tokens/generate" \
  -d "name=tekton-$(date +%s)" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "ERRO: nao consegui gerar o token no SonarQube." >&2
  echo "A senha do admin mudou? Rode com SONAR_ADMIN_PASS=sua-senha scripts/tekton-up.sh" >&2
  exit 1
fi

# Gate proprio: reprova qualquer violacao ou hotspot nao revisado em CODIGO NOVO.
# O "Sonar way" exige 80% de cobertura no codigo novo, mas a pipeline pula testes
# (Testcontainers precisa de Docker); sem isso qualquer commit Java reprovaria.
echo "== criando quality gate techpix-security e associando ao projeto"
curl -s -u "admin:$SONAR_ADMIN_PASS" -X POST \
  "http://localhost:$SONAR_PORT/api/qualitygates/create?name=techpix-security" >/dev/null || true
curl -s -u "admin:$SONAR_ADMIN_PASS" -X POST \
  "http://localhost:$SONAR_PORT/api/qualitygates/create_condition" \
  -d "gateName=techpix-security&metric=new_violations&op=GT&error=0" >/dev/null || true
curl -s -u "admin:$SONAR_ADMIN_PASS" -X POST \
  "http://localhost:$SONAR_PORT/api/qualitygates/create_condition" \
  -d "gateName=techpix-security&metric=new_security_hotspots_reviewed&op=LT&error=100" >/dev/null || true
curl -s -u "admin:$SONAR_ADMIN_PASS" -X POST \
  "http://localhost:$SONAR_PORT/api/qualitygates/select" \
  -d "gateName=techpix-security&projectKey=tech-pix" >/dev/null || true

echo "== aplicando namespace techpix-ci e cache Maven"
kubectl apply -f tekton/namespace.yaml

# host.docker.internal: o scanner roda DENTRO do cluster e precisa alcancar o
# SonarQube que roda no Docker do host (mesmo truque do Gitea no lab 17).
echo "== criando Secret sonarqube (host + token)"
kubectl -n techpix-ci create secret generic sonarqube \
  --from-literal=host="http://host.docker.internal:$SONAR_PORT" \
  --from-literal=token="$TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "== aplicando tasks e pipeline"
kubectl apply -f tekton/tasks/git-clone.yaml
kubectl apply -f tekton/tasks/maven-sonar.yaml
kubectl apply -f tekton/tasks/zap-baseline.yaml
kubectl apply -f tekton/pipeline.yaml

echo
echo "Pipeline de seguranca instalada."
echo "  SonarQube UI:     http://localhost:$SONAR_PORT  (usuario admin, senha admin)"
echo "  Tekton Dashboard: kubectl -n tekton-pipelines port-forward svc/tekton-dashboard 9097:9097"
echo "                    -> http://localhost:9097  (ou o botao no painel: python scripts/painel.py)"
echo
echo "Pre-requisitos para rodar a pipeline:"
echo "  - scripts/local-git-server.sh   (o codigo precisa estar no Gitea para o clone)"
echo "  - scripts/k8s-up.sh             (a aplicacao precisa estar rodando para o DAST)"
echo
echo "Para disparar uma execucao: scripts/pipeline-run.sh"
