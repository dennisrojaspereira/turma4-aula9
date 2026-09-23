#!/usr/bin/env bash
# Navega pelos momentos da arquitetura.
#   scripts/step.sh            -> lista as tags
#   scripts/step.sh 4          -> checkout da etapa 4 (aula07-step-04-optimized)
#   scripts/step.sh main       -> volta para o fim da historia
#   scripts/step.sh diff 3 4   -> o que mudou entre as etapas 3 e 4
set -euo pipefail
cd "$(dirname "$0")/.."
tag_of() { git tag --list "aula07-step-$(printf '%02d' "$1")-*" | head -1; }
case "${1:-list}" in
  list) git tag --list 'aula07-step-*' | sort ;;
  main) git checkout -q main && echo "main: a historia completa" ;;
  diff) git diff --stat "$(tag_of "$2")" "$(tag_of "$3")" ;;
  *)    T=$(tag_of "$1"); [ -n "$T" ] || { echo "etapa $1 nao existe"; exit 1; }
        git checkout -q "$T" && echo "agora em $T (git checkout main para voltar)" ;;
esac
