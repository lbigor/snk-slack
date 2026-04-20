#!/usr/bin/env bash
# install.sh — instala a skill snk-slack no diretorio de skills do Claude Code do usuario.
#
# A skill cobre o procedimento de adicionar a lib Java br.com.lbi.slack em um
# projeto Sankhya Java. NAO instala a lib Java — isso e feito pela skill dentro
# do projeto alvo (copia os 5 arquivos de src/br/com/lbi/slack/).
#
# Uso:
#   curl -fsSL https://raw.githubusercontent.com/lbigor/snk-slack/main/install.sh | bash

set -euo pipefail

REPO_URL="https://github.com/lbigor/snk-slack.git"
RAW_URL="https://raw.githubusercontent.com/lbigor/snk-slack/main"
SKILLS_DIR="${CLAUDE_SKILLS_DIR:-$HOME/.claude/skills}"
SKILL_DIR="$SKILLS_DIR/snk-slack"

echo "==> Instalando skill snk-slack em: $SKILL_DIR"

mkdir -p "$SKILL_DIR"

# Baixa os documentos da skill (SKILL.md e apoios).
for file in SKILL.md INSTALACAO.md BOAS_PRATICAS.md scripts/criar-preferencia-logslack.md; do
    target="$SKILL_DIR/$file"
    mkdir -p "$(dirname "$target")"
    echo "  - $file"
    curl -fsSL "$RAW_URL/$file" -o "$target"
done

# Baixa os 5 arquivos da lib Java (usados como gabarito pela skill).
mkdir -p "$SKILL_DIR/src/br/com/lbi/slack"
for java in LogEntry.java SlackConfig.java SlackLogger.java SlackMessage.java SlackWebhookClient.java; do
    echo "  - src/br/com/lbi/slack/$java"
    curl -fsSL "$RAW_URL/src/br/com/lbi/slack/$java" -o "$SKILL_DIR/src/br/com/lbi/slack/$java"
done

echo ""
echo "Skill instalada."
echo ""
echo "Proximo passo: em um projeto Sankhya Java, peca ao Claude Code:"
echo "  \"Adiciona o log Slack nesse projeto.\""
echo ""
echo "Leia $SKILL_DIR/INSTALACAO.md para criar o webhook Slack e a preferencia LOGSLACK_WEBHOOK no Sankhya W."
