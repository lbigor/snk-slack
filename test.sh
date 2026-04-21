#!/usr/bin/env bash
# test.sh — valida estrutura e compila Java localmente.
# Usado em CI e pode rodar local antes de commitar.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

fail=0

echo "==> [1/6] Validando presenca dos arquivos Java"
for f in LogEntry SlackConfig SlackLogger SlackMessage SlackWebhookClient DeployManifest; do
    if [ ! -f "src/br/com/lbi/slack/$f.java" ]; then
        red "  FALTA: src/br/com/lbi/slack/$f.java"
        fail=1
    fi
done
[ "$fail" -eq 0 ] && green "  OK"

echo "==> [2/6] Validando pacote br.com.lbi.slack em todos os .java"
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

echo "==> [3/6] Validando ausencia de HTTP clients externos"
if grep -rE "okhttp3|com\.squareup|org\.apache\.http|retrofit" src/ 2>/dev/null; then
    red "  Encontrado HTTP client nao permitido. Use apenas java.net.HttpURLConnection."
    fail=1
else
    green "  OK (apenas HttpURLConnection)"
fi

echo "==> [4/6] Validando presenca dos 4 docs principais"
for md in README.md INSTALACAO.md BOAS_PRATICAS.md SKILL.md LICENSE; do
    if [ ! -f "$md" ]; then
        red "  FALTA: $md"
        fail=1
    fi
done
[ "$fail" -eq 0 ] && green "  OK"

echo "==> [5/6] Compile check Java (baixa Gson se necessario)"
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

echo "==> [6/6] Smoke test DeployManifest (com e sem manifest no classpath)"
# Gera Main de teste que roda 2 cenarios e imprime os resultados.
TEST_DIR="target/test-src"
TEST_CLASSES="target/test-classes"
mkdir -p "$TEST_DIR" "$TEST_CLASSES"
cat > "$TEST_DIR/DeployManifestTest.java" <<'EOF'
import br.com.lbi.slack.DeployManifest;
import br.com.lbi.slack.SlackLogger;

public class DeployManifestTest {
    public static void main(String[] args) throws Exception {
        DeployManifest m = DeployManifest.get();
        String mode = System.getProperty("scenario", "absent");
        System.out.println("scenario=" + mode);
        System.out.println("present=" + m.isPresent());
        System.out.println("footer=[" + m.toFooter() + "]");
        if ("absent".equals(mode)) {
            if (m.isPresent()) { System.out.println("FAIL: deveria estar ausente"); System.exit(1); }
            if (!m.toFooter().isEmpty()) { System.out.println("FAIL: footer nao-vazio"); System.exit(1); }
        } else {
            if (!m.isPresent()) { System.out.println("FAIL: deveria estar presente"); System.exit(1); }
            String f = m.toFooter();
            if (!f.contains("abc12345") || !f.contains("feat/x") || !f.contains("PR #1")) {
                System.out.println("FAIL: footer inesperado: " + f); System.exit(1);
            }
        }
        // Smoke-check do SlackLogger (builder + withReleaseTracking)
        SlackLogger noop = SlackLogger.create("").modulo("M").header("H")
            .withReleaseTracking(true).build();
        if (!noop.isDisabled()) { System.out.println("FAIL: logger deveria estar disabled sem URL"); System.exit(1); }
        System.out.println("OK");
    }
}
EOF
javac -cp "$GSON_JAR:target/classes" -d "$TEST_CLASSES" "$TEST_DIR/DeployManifestTest.java"

# Cenario A: sem manifest no classpath
SCEN_A_OUT=$(java -cp "$GSON_JAR:target/classes:$TEST_CLASSES" -Dscenario=absent DeployManifestTest)
echo "  cenario A (sem manifest):"
echo "$SCEN_A_OUT" | sed 's/^/    /'
if ! echo "$SCEN_A_OUT" | grep -q "^OK$"; then
    red "  Cenario A falhou"; fail=1
fi

# Cenario B: com manifest mockado
MANIFEST_DIR="$TEST_CLASSES/META-INF/snk-deploy"
mkdir -p "$MANIFEST_DIR"
cat > "$MANIFEST_DIR/manifest.json" <<'EOF'
{
  "hash": "abc12345",
  "branch": "feat/x",
  "commit": "abc12345deadbeef",
  "commitShort": "abc1234",
  "prNumber": "1",
  "prUrl": "https://github.com/lbigor/snk-slack/pull/1",
  "prTitle": "feat: x",
  "author": "lbigor",
  "builtAt": "2026-04-21T00:00:00Z",
  "project": "snk-slack"
}
EOF
SCEN_B_OUT=$(java -cp "$GSON_JAR:target/classes:$TEST_CLASSES" -Dscenario=present DeployManifestTest)
echo "  cenario B (com manifest):"
echo "$SCEN_B_OUT" | sed 's/^/    /'
if ! echo "$SCEN_B_OUT" | grep -q "^OK$"; then
    red "  Cenario B falhou"; fail=1
fi
# Limpa manifest pra nao contaminar proxima rodada
rm -f "$MANIFEST_DIR/manifest.json"
[ "$fail" -eq 0 ] && green "  OK"

echo ""
if [ "$fail" -eq 0 ]; then
    green "==> TODOS OS TESTES PASSARAM"
    exit 0
else
    red "==> FALHOU"
    exit 1
fi
