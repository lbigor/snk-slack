#!/usr/bin/env bash
# test.sh — valida estrutura e compila Java localmente.
# Usado em CI e pode rodar local antes de commitar.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

fail=0

echo "==> [1/5] Validando presenca dos 5 arquivos Java"
for f in LogEntry SlackConfig SlackLogger SlackMessage SlackWebhookClient; do
    if [ ! -f "src/br/com/lbi/slack/$f.java" ]; then
        red "  FALTA: src/br/com/lbi/slack/$f.java"
        fail=1
    fi
done
[ "$fail" -eq 0 ] && green "  OK"

echo "==> [2/5] Validando pacote br.com.lbi.slack em todos os .java"
bad_pkg=$(grep -l -r "^package " src/ | while read -r file; do
    if ! grep -q "^package br.com.lbi.slack;" "$file"; then
        echo "$file"
    fi
done || true)
if [ -n "$bad_pkg" ]; then
    red "  Arquivos com pacote errado:"
    echo "$bad_pkg"
    fail=1
else
    green "  OK"
fi

echo "==> [3/5] Validando ausencia de HTTP clients externos"
if grep -rE "okhttp3|com\.squareup|org\.apache\.http|retrofit" src/ 2>/dev/null; then
    red "  Encontrado HTTP client nao permitido. Use apenas java.net.HttpURLConnection."
    fail=1
else
    green "  OK (apenas HttpURLConnection)"
fi

echo "==> [4/5] Validando presenca dos 4 docs principais"
for md in README.md INSTALACAO.md BOAS_PRATICAS.md SKILL.md LICENSE; do
    if [ ! -f "$md" ]; then
        red "  FALTA: $md"
        fail=1
    fi
done
[ "$fail" -eq 0 ] && green "  OK"

echo "==> [5/5] Compile check Java (baixa Gson se necessario)"
mkdir -p target
GSON_JAR="target/gson-2.10.1.jar"
if [ ! -f "$GSON_JAR" ]; then
    curl -fsSL https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar -o "$GSON_JAR"
fi

STUB_DIR="target/stubs/br/com/sankhya/modelcore/util"
mkdir -p "$STUB_DIR" target/classes
cat > "$STUB_DIR/MGECoreParameter.java" <<'EOF'
package br.com.sankhya.modelcore.util;
public class MGECoreParameter {
    public static String getParameterAsString(String nome) throws Exception { return null; }
}
EOF
javac -d target/classes "$STUB_DIR/MGECoreParameter.java"
if javac -cp "$GSON_JAR:target/classes" -d target/classes src/br/com/lbi/slack/*.java 2>&1; then
    green "  OK"
else
    red "  Erro de compilacao"
    fail=1
fi

echo ""
if [ "$fail" -eq 0 ]; then
    green "==> TODOS OS TESTES PASSARAM"
    exit 0
else
    red "==> FALHOU"
    exit 1
fi
