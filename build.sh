#!/usr/bin/env bash
# build.sh — compila os 5 arquivos Java em um JAR distribuivel.
#
# Requer Gson 2.x no classpath. Tenta baixar gson-2.10.1 se nao achar localmente.
# Saida: target/snk-slack.jar

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mkdir -p target/classes

GSON_JAR="target/gson-2.10.1.jar"
if [ ! -f "$GSON_JAR" ]; then
    echo "==> Baixando Gson 2.10.1 para compilacao"
    curl -fsSL https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar -o "$GSON_JAR"
fi

# Stub de MGECoreParameter para permitir compile fora do Sankhya.
# Em producao o classpath do Sankhya prove a classe real.
STUB_DIR="target/stubs/br/com/sankhya/modelcore/util"
mkdir -p "$STUB_DIR"
cat > "$STUB_DIR/MGECoreParameter.java" <<'EOF'
package br.com.sankhya.modelcore.util;

/**
 * STUB para compilacao fora do Sankhya. Em runtime, a classe real do
 * modelcore do Sankhya e resolvida no classpath.
 */
public class MGECoreParameter {
    public static String getParameterAsString(String nome) throws Exception {
        return null;
    }
}
EOF

echo "==> Compilando stub"
javac -d target/classes "$STUB_DIR/MGECoreParameter.java"

echo "==> Compilando src/br/com/lbi/slack"
javac -cp "$GSON_JAR:target/classes" -d target/classes src/br/com/lbi/slack/*.java

echo "==> Empacotando snk-slack.jar (somente br/com/lbi/slack)"
(cd target/classes && jar cf "../snk-slack.jar" br/com/lbi/slack)

echo ""
echo "OK: target/snk-slack.jar"
ls -la target/snk-slack.jar
