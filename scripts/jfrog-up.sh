#!/usr/bin/env bash
# Sobe o JFrog Artifactory OSS em container (mesmo padrao do Gitea/SonarQube) e
# prepara a pipeline para publicar artefatos nele:
#   - container techpix-jfrog em http://localhost:8082 (admin / password)
#   - repositorio generico "techpix-libs"
#   - Secret "artifactory" no namespace techpix-ci (url + credenciais)
#   - aplica a task publish-artifactory e a pipeline atualizada
#   scripts/jfrog-up.sh
# Pre-requisito para a pipeline publicar: cluster kind "techpix" com Tekton (scripts/tekton-up.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

JFROG_NAME=techpix-jfrog
JFROG_PORT=8082
JFROG_USER=admin
JFROG_PASS=password
JFROG_URL="http://localhost:$JFROG_PORT/artifactory"

# O Artifactory OSS atual exige PostgreSQL (nao tem mais banco embutido).
# Usamos o techpix-postgres que ja existe, com um banco "artifactory" dedicado.
echo "== garantindo o PostgreSQL e o banco artifactory"
docker compose up -d postgres >/dev/null 2>&1 || true
for i in $(seq 1 24); do
  docker exec techpix-postgres pg_isready -U techpix >/dev/null 2>&1 && break
  sleep 5
done
docker exec techpix-postgres psql -U techpix -d techpix -tAc \
  "SELECT 1 FROM pg_roles WHERE rolname='artifactory'" | grep -q 1 || \
  docker exec techpix-postgres psql -U techpix -d techpix -c \
    "CREATE ROLE artifactory LOGIN PASSWORD 'artifactory'" >/dev/null
docker exec techpix-postgres psql -U techpix -d techpix -tAc \
  "SELECT 1 FROM pg_database WHERE datname='artifactory'" | grep -q 1 || \
  docker exec techpix-postgres psql -U techpix -d techpix -c \
    "CREATE DATABASE artifactory OWNER artifactory" >/dev/null

if docker ps --format '{{.Names}}' | grep -qx "$JFROG_NAME"; then
  echo "== Artifactory ja esta rodando (container $JFROG_NAME)"
elif docker ps -a --format '{{.Names}}' | grep -qx "$JFROG_NAME"; then
  echo "== religando o container $JFROG_NAME"
  docker start "$JFROG_NAME" >/dev/null
else
  echo "== subindo Artifactory OSS em http://localhost:$JFROG_PORT (container $JFROG_NAME)"
  echo "   (primeira vez baixa ~1.5GB de imagem e a subida leva alguns minutos)"
  docker run -d --name "$JFROG_NAME" -p "$JFROG_PORT:8082" \
    --add-host host.docker.internal:host-gateway \
    -e JF_SHARED_DATABASE_TYPE=postgresql \
    -e JF_SHARED_DATABASE_DRIVER=org.postgresql.Driver \
    -e JF_SHARED_DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/artifactory" \
    -e JF_SHARED_DATABASE_USERNAME=artifactory \
    -e JF_SHARED_DATABASE_PASSWORD=artifactory \
    -v techpix-jfrog-data:/var/opt/jfrog/artifactory \
    releases-docker.jfrog.io/jfrog/artifactory-oss:latest >/dev/null
fi

echo "== aguardando o Artifactory responder (ate ~420s; a primeira subida e lenta)"
UP=false
for i in $(seq 1 84); do
  if curl -sf "$JFROG_URL/api/system/ping" 2>/dev/null | grep -q OK; then
    UP=true; break
  fi
  echo "   ... aguardando ($((i*5))s)"
  sleep 5
done
if [ "$UP" != true ]; then
  echo "ERRO: Artifactory nao ficou pronto. Veja: docker logs $JFROG_NAME" >&2
  exit 1
fi

# Criar repositorio via REST e recurso Pro; o OSS ja vem com o repositorio
# generico "example-repo-local" - e o que usamos (os caminhos ficam em tech-pix/...).
JFROG_REPO=example-repo-local
echo "== verificando o repositorio generico $JFROG_REPO"
if curl -sf -u "$JFROG_USER:$JFROG_PASS" "$JFROG_URL/api/repositories" | grep -q "\"$JFROG_REPO\""; then
  echo "   $JFROG_REPO disponivel"
else
  echo "ERRO: repositorio $JFROG_REPO nao encontrado no Artifactory." >&2
  echo "Crie um repositorio Generic local na UI (http://localhost:$JFROG_PORT) e rode de novo." >&2
  exit 1
fi

# a pipeline roda DENTRO do cluster e alcanca o Artifactory do host via host.docker.internal
if kubectl config use-context kind-techpix >/dev/null 2>&1 \
   && kubectl get namespace techpix-ci >/dev/null 2>&1; then
  echo "== criando Secret artifactory no namespace techpix-ci"
  kubectl -n techpix-ci create secret generic artifactory \
    --from-literal=url="http://host.docker.internal:$JFROG_PORT/artifactory" \
    --from-literal=repo="$JFROG_REPO" \
    --from-literal=user="$JFROG_USER" \
    --from-literal=pass="$JFROG_PASS" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "== aplicando task publish-artifactory e pipeline atualizada"
  kubectl apply -f tekton/tasks/publish-artifactory.yaml
  kubectl apply -f tekton/pipeline.yaml
else
  echo "AVISO: cluster/namespace techpix-ci nao encontrado; rode scripts/tekton-up.sh e depois"
  echo "       scripts/jfrog-up.sh de novo para instalar a etapa de publicacao."
fi

echo
echo "Artifactory pronto."
echo "  UI:          http://localhost:$JFROG_PORT  (usuario $JFROG_USER, senha $JFROG_PASS)"
echo "  Repositorio: $JFROG_URL/$JFROG_REPO/tech-pix/"
echo "  Para publicar: scripts/simulate-commit.sh  (commit simulado + pipeline completa)"
