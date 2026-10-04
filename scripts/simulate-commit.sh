#!/usr/bin/env bash
# Imita o fluxo de CI de verdade: um "dev" faz um commit -> a pipeline roda ->
# o artefato aprovado vai para o Artifactory.
#   scripts/simulate-commit.sh                commit inofensivo + pipeline completa
#   scripts/simulate-commit.sh vulneravel     commit com codigo VULNERAVEL + pipeline
#                                             (o quality gate do SonarQube deve REPROVAR)
#   scripts/simulate-commit.sh corrigir       remove a vulnerabilidade + pipeline (volta a passar)
#   scripts/simulate-commit.sh so-commit      apenas o commit inofensivo, sem pipeline
# O commit acontece num CLONE TEMPORARIO do Gitea local e e empurrado para la;
# o seu repositorio de trabalho nao e tocado.
# Pre-requisitos: scripts/local-git-server.sh, scripts/tekton-up.sh, scripts/jfrog-up.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

GITEA_URL="http://techpix:techpix123@localhost:3001/techpix/tech-pix.git"
VULN_FILE="monolith/src/main/java/com/techpix/shared/LegacyPixExporter.java"
MODE="${1:-normal}"

case "$MODE" in
  normal|so-commit|vulneravel|corrigir) ;;
  *) echo "uso: $0 [vulneravel|corrigir|so-commit]"; exit 1 ;;
esac

if ! docker ps --format '{{.Names}}' | grep -qx techpix-gitea; then
  echo "ERRO: o Gitea local nao esta rodando. Rode scripts/local-git-server.sh primeiro." >&2
  exit 1
fi

TMP=$(mktemp -d)
# No Git Bash do Windows com MSYS_NO_PATHCONV=1 (painel), o git.exe nao entende
# /tmp/...; cygpath -m devolve um caminho (C:/...) que bash e git.exe entendem.
command -v cygpath >/dev/null 2>&1 && TMP=$(cygpath -m "$TMP")
trap 'rm -rf "$TMP"' EXIT

echo "== clonando o repositorio do Gitea (clone temporario)"
git clone -q --depth 1 -b main "$GITEA_URL" "$TMP"

if [ "$MODE" = "vulneravel" ]; then
  # Codigo que um dev apressado commitaria numa sexta-feira: compila normalmente,
  # mas o SonarQube encontra credencial hardcoded (S2068), DES/ECB (S5547/S5542),
  # MD5 (S4790) e java.util.Random para token (S2245). Quality gate: REPROVADO.
  MSG="feat: exportador legado de chaves Pix (integracao com banco parceiro)"
  mkdir -p "$TMP/$(dirname "$VULN_FILE")"
  cat > "$TMP/$VULN_FILE" <<'EOF'
package com.techpix.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

/**
 * Exportador "temporario" de chaves Pix para o banco parceiro.
 * TODO: mover credenciais para o vault depois do piloto.
 */
public class LegacyPixExporter {

    private static final String PARTNER_DB_URL = "jdbc:postgresql://parceiro.interno:5432/pix";
    private static final String PARTNER_DB_USER = "pix_export";
    private static final String PARTNER_DB_PASSWORD = "Sup3rS3creta!2024";

    public String exportToken(String accountId) {
        Random random = new Random();
        long nonce = random.nextLong();
        return accountId + "-" + Long.toHexString(nonce);
    }

    public byte[] encryptPayload(byte[] payload) throws Exception {
        DESKeySpec keySpec = new DESKeySpec("8bytekey".getBytes(StandardCharsets.UTF_8));
        SecretKey key = SecretKeyFactory.getInstance("DES").generateSecret(keySpec);
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(payload);
    }

    public String fingerprint(String document) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(document.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    public String connectionString() {
        return PARTNER_DB_URL + "?user=" + PARTNER_DB_USER + "&password=" + PARTNER_DB_PASSWORD;
    }
}
EOF
  git -C "$TMP" add "$VULN_FILE"
elif [ "$MODE" = "corrigir" ]; then
  if [ ! -f "$TMP/$VULN_FILE" ]; then
    echo "Nada a corrigir: $VULN_FILE nao existe no Gitea (rode 'vulneravel' antes)."
    exit 0
  fi
  MSG="fix: remove exportador legado (credencial hardcoded e criptografia fraca)"
  git -C "$TMP" rm -q "$VULN_FILE"
else
  MSGS=(
    "feat: valida limite diario por conta antes do debito"
    "fix: arredondamento do valor na confirmacao do Pix"
    "refactor: extrai validacao de chave Pix para o dominio"
    "chore: ajusta timeout do cliente HTTP do Fraud"
    "feat: mensagem de notificacao com nome do recebedor"
    "fix: trata resposta vazia do provider externo de risco"
  )
  MSG="${MSGS[$((RANDOM % ${#MSGS[@]}))]}"
  mkdir -p "$TMP/docs"
  echo "- $(date '+%Y-%m-%d %H:%M:%S')  $MSG" >> "$TMP/docs/demo-commits.md"
  git -C "$TMP" add docs/demo-commits.md
fi

echo "== simulando o trabalho do dev: $MSG"
git -C "$TMP" -c user.name="Aluno FintechDev" -c user.email="aluno@fintechdev.local" \
  commit -q -m "$MSG"
SHA=$(git -C "$TMP" rev-parse --short HEAD)

echo "== push para o Gitea (commit $SHA)"
git -C "$TMP" push -q origin main

echo
echo "Commit simulado no Gitea: $SHA  \"$MSG\""
echo "  http://localhost:3001/techpix/tech-pix/commits/branch/main"

if [ "$MODE" = "so-commit" ]; then
  echo "(pipeline nao disparada; rode scripts/pipeline-run.sh quando quiser)"
  exit 0
fi

echo
if [ "$MODE" = "vulneravel" ]; then
  echo "== disparando a pipeline para o commit $SHA"
  echo "   EXPECTATIVA: a task sast REPROVA no quality gate do SonarQube"
  echo "   (credencial hardcoded, DES/ECB, MD5, Random previsivel em codigo novo)."
  echo "   O DAST e o publish nem chegam a rodar: artefato vulneravel nao e publicado."
elif [ "$MODE" = "corrigir" ]; then
  echo "== disparando a pipeline para o commit $SHA (a correcao deve passar nos gates)"
else
  echo "== disparando a pipeline para o commit $SHA (clone -> SAST -> DAST -> publish)"
fi
TECHPIX_SKIP_PUSH=1 exec bash scripts/pipeline-run.sh
