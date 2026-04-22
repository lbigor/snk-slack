# SKILL — Orquestrador Claude Code

> Instrucoes pro Claude Code quando o usuario pedir
> **"adiciona o log Slack nesse projeto"** (ou equivalente) em um projeto Java Sankhya.

## Transport: API (recomendado) ou Webhook (legado)

A lib resolve o transporte dinamicamente das preferencias Sankhya:

| Modo | Preferencias | Quando usar |
|---|---|---|
| **API** (Slack Web API `chat.postMessage`) | `LOGSLACK_TOKEN` + `LOGSLACK_CHANNEL` | **Recomendado** — reaproveita o mesmo Bearer token (`xoxb-...`) que ja existe pra MCP/scripts auxiliares. Nao precisa criar Incoming Webhook no painel do Slack. |
| **Webhook** (Incoming Webhook) | `LOGSLACK_WEBHOOK` | Legado — use apenas se o workspace nao permite bot tokens ou se o projeto ja esta configurado com webhook. |

Se as tres preferencias existirem, a API ganha. A escolha e feita em tempo
de execucao — nao precisa rebuildar a lib pra trocar.

## Pre-condicoes que o usuario cumpre

1. Tem workspace Slack + bot app criado (ver [INSTALACAO.md](INSTALACAO.md) partes 1-2).
2. Para **modo API**: Bearer token (`xoxb-...`) do bot + ID do canal (`C...`).
3. Para **modo Webhook**: URL do Incoming Webhook.
4. Tem acesso administrativo ao Sankhya W pra criar preferencias.

## Procedimento passo-a-passo

### 1. Detectar se a lib ja existe

Procurar por `src/br/com/lbi/slack/SlackLogger.java` no projeto atual. Se existir, pular para passo 4 (instrumentacao). Se nao existir, continuar.

### 2. Copiar os 7 arquivos da lib

De `src/br/com/lbi/slack/` desse repo (snk-slack), copiar para o projeto alvo **mantendo o mesmo caminho**:

- `LogEntry.java`
- `SlackConfig.java`
- `SlackLogger.java`
- `SlackMessage.java`
- `SlackWebhookClient.java` (transport webhook legado)
- `SlackApiClient.java` (transport API — `chat.postMessage`)
- `DeployManifest.java`

Nao editar conteudo. Confirme que o pacote e `br.com.lbi.slack` em todos.

### 3. Validar Gson no classpath

Confira o `.classpath` do projeto. Deve existir entrada para `gson-*.jar`. Todo projeto Sankhya tipico ja tem. Se faltar, avisar o usuario — nao tentar baixar nem adicionar automaticamente.

### 4. Identificar entry points

Rodar Grep por classes que extendam/implementem os entry points Sankhya:

```
grep -rE "implements AcaoRotinaJava|implements EventoProgramavelJava" src/
```

Listar os arquivos encontrados ao usuario. Perguntar qual deve ser instrumentado primeiro. **Nao instrumentar todos em massa.**

### 5. Instrumentar um por vez

No entry point escolhido, envolver o metodo principal com o padrao:

```java
SlackLogger slack = SlackLogger.create(null)
    .modulo("<nome do modulo>")
    .header("<titulo da acao>")
    .build();
try {
    slack.info("INICIO", "=== INICIO ===");
    // ... codigo original ...
    slack.success("FIM", "=== FIM ===");
    slack.flush();
} catch (Exception e) {
    slack.error("FATAL", "Erro em <acao>", e);
    slack.flush();
    throw e;
}
```

Ajustar o nome do modulo e header para o contexto. Preservar toda a logica de negocio original.

### 6. Perguntar antes de propagar para services

Se o usuario quiser logs dentro de classes de service chamadas pelo entry point, perguntar caso a caso antes de passar o `slack` como parametro. Nao propagar por conta propria.

### 7. Orientar o usuario a:

1. Criar as preferencias no Sankhya W conforme o modo escolhido:
   - **Modo API (recomendado):** `LOGSLACK_TOKEN` (TEXTO, 80) com o Bearer token `xoxb-...` + `LOGSLACK_CHANNEL` (TEXTO, 30) com o ID do canal (`C...`).
   - **Modo Webhook (legado):** `LOGSLACK_WEBHOOK` (TEXTO, 500) com a URL — ver [scripts/criar-preferencia-logslack.md](scripts/criar-preferencia-logslack.md).
   - Em qualquer caso, tambem criar `LOGSLACK` (Logico) como ligado — sem isso o entry point nao instancia o logger.
2. Buildar o projeto na IDE (JAR ou deploy direto).
3. Rodar teste de fumaca acionando o entry point instrumentado.
4. Conferir que a mensagem chegou em `#logsankhya` (ou canal configurado).

### 8. Nao fazer

- **Nao executar `javac` automaticamente** — classpath depende de JARs do iCloud em path absoluto.
- **Nao criar preferencia via SQL** — isso e UI do Sankhya W, usuario faz.
- **Nao hardcode URL/token** — sempre `SlackLogger.create(null)` em producao. Token e URL vivem apenas em `MGECoreParameter`.
- **Nao instrumentar services sem perguntar.**

## Release tracking automatico

Se o JAR foi empacotado pelo [snk-deploy](https://github.com/lbigor/snk-deploy),
um `META-INF/snk-deploy/manifest.json` esta presente no classpath. O
`SlackLogger` detecta automaticamente via `DeployManifest.get()` e anexa um
footer em **cada mensagem Slack** com:

- Hash curto do build (ex: `abc12345`)
- Branch (ex: `feat/fix-estoque`)
- Numero do PR (ex: `PR #42`, link clicavel se `prUrl` estiver disponivel)

Isso permite que o [snk-doctor](https://github.com/lbigor/snk-doctor) rastreie
qualquer erro ate o PR que o causou — **sem configuracao adicional**.

- Se o manifest **nao existir** no classpath (build local, fora do snk-deploy),
  o footer simplesmente nao aparece e nada quebra.
- Para **desligar** intencionalmente:

  ```java
  SlackLogger.create(null)
      .modulo("X").header("Y")
      .withReleaseTracking(false)
      .build();
  ```

Exemplo do footer gerado:

```text
v: abc12345 · feat/fix-estoque · PR #42
```

## Referencia global

O arquivo `~/Documents/Claude/sankhya_slack.md` tem historico completo da lib (versao hardcoded anterior). Esse repo (snk-slack) e a versao publica/generica com webhook lido via preferencia Sankhya.
