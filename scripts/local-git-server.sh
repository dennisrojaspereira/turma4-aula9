#!/usr/bin/env bash
# Um servidor Git LOCAL (Gitea em container) para demonstrar o Argo CD sem GitHub e sem internet.
#   scripts/local-git-server.sh          -> sobe o Gitea, cria o repo, faz push de main e das tags
#   scripts/local-git-server.sh push     -> so faz push (depois de novos commits)
#   scripts/local-git-server.sh down     -> remove o container
#
# Depois: TECHPIX_GIT_URL=http://host.docker.internal:3001/techpix/tech-pix.git scripts/argocd-up.sh
set -euo pipefail
cd "$(dirname "$0")/.."
NAME=techpix-gitea
PORT=3001
USER=techpix
PASS=techpix123
LOCAL_URL="http://$USER:$PASS@localhost:$PORT/$USER/tech-pix.git"
CLUSTER_URL="http://host.docker.internal:$PORT/$USER/tech-pix.git"

case "${1:-up}" in
  down)
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    git remote remove local >/dev/null 2>&1 || true
    echo "Gitea removido."; exit 0 ;;
  push)
    git push -q local main --tags --force
    echo "push feito para $CLUSTER_URL"; exit 0 ;;
  up) ;;
  *) echo "uso: $0 [up|push|down]"; exit 1 ;;
esac

if ! docker ps --format '{{.Names}}' | grep -qx "$NAME"; then
  echo "== subindo Gitea em http://localhost:$PORT"
  docker run -d --name "$NAME" -p "$PORT:3000" \
    -e GITEA__security__INSTALL_LOCK=true \
    -e GITEA__service__DISABLE_REGISTRATION=true \
    -e GITEA__server__ROOT_URL="http://host.docker.internal:$PORT/" \
    -e GITEA__server__DOMAIN=host.docker.internal \
    gitea/gitea:1.22 >/dev/null
  for i in $(seq 1 60); do curl -sf "http://localhost:$PORT/api/healthz" >/dev/null && break; sleep 1; done
  docker exec -u git "$NAME" gitea admin user create --username "$USER" --password "$PASS" \
    --email "$USER@example.com" --admin --must-change-password=false >/dev/null
  curl -s -u "$USER:$PASS" -X POST "http://localhost:$PORT/api/v1/user/repos" \
    -H 'Content-Type: application/json' -d '{"name":"tech-pix","private":false}' >/dev/null
fi

git remote remove local >/dev/null 2>&1 || true
git remote add local "$LOCAL_URL"
git push -q local main --tags --force
echo
echo "Repositorio publicado."
echo "  na maquina:  http://localhost:$PORT/$USER/tech-pix   (usuario $USER, senha $PASS)"
echo "  no cluster:  $CLUSTER_URL"
echo
echo "Proximo passo: TECHPIX_GIT_URL=$CLUSTER_URL scripts/argocd-up.sh"
echo "Depois de novos commits: scripts/local-git-server.sh push"
