# SKILL — Orquestrador Claude Code

> Instrucoes pro Claude Code quando o usuario pedir
> **"adiciona o log Slack nesse projeto"** (ou equivalente) em um projeto Java Sankhya.

## Pre-condicoes que o usuario cumpre

1. Tem workspace Slack + webhook criado (ver [INSTALACAO.md](INSTALACAO.md) passos 1-5).
2. Tem a URL do webhook em maos.
3. Tem acesso administrativo ao Sankhya W pra criar preferencia.

## Procedimento passo-a-passo

### 1. Detectar se a lib ja existe

Procurar por `src/br/com/lbi/slack/SlackLogger.java` no projeto atual. Se existir, pular para passo 4 (instrumentacao). Se nao existir, continuar.

### 2. Copiar os 6 arquivos da lib

De `src/br/com/lbi/slack/` desse repo (snk-slack), copiar para o projeto alvo **mantendo o mesmo caminho**:

- `LogEntry.java`
- `SlackConfig.java`
- `SlackLogger.java`
- `SlackMessage.java`
- `SlackWebhookClient.java`
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

1. Criar a preferencia `LOGSLACK_WEBHOOK` no Sankhya W (ver [scripts/criar-preferencia-logslack.md](scripts/criar-preferencia-logslack.md)).
2. Buildar o projeto na IDE (JAR ou deploy direto).
3. Rodar teste de fumaca acionando o entry point instrumentado.
4. Conferir que a mensagem chegou em `#logsankhya` (ou canal configurado).

### 8. Nao fazer

- **Nao executar `javac` automaticamente** — classpath depende de JARs do iCloud em path absoluto.
- **Nao criar a preferencia via SQL** — isso e UI do Sankhya W, usuario faz.
- **Nao hardcode URL do webhook** — sempre `SlackLogger.create(null)` em producao.
- **Nao instrumentar services sem perguntar.**

## Regra de instrumentacao obrigatoria em cada ramo

**Depois que a `snk-slack` esta ativada num projeto, toda edicao subsequente
em `.java` que tenha `SlackLogger slack` no escopo precisa instrumentar
cada ramo novo ou alterado.** Nao basta logar o caminho feliz — tem que
cobrir:

1. **Ramos de bypass/atalho** (ex: `if (flag == "S") return;`): emitir
   `slack.debug(TAG, "<NOME>_BYPASS motivo=..."),` **antes** do return, com
   contexto suficiente pra entender por que pulou.
2. **Leituras de `MGECoreParameter`**: logar 3 casos separados —
   - valor lido com sucesso: `slack.debug("CONFIG", "<PARAM>=<valor> (lido de MGECoreParameter)")`
   - ausente/vazio/null: `slack.debug("CONFIG", "<PARAM> ausente/vazio — usando default <X>")`
   - exception: `slack.debug("CONFIG", "<PARAM> erro ao ler ("+e.getClass().getSimpleName()+": "+e.getMessage()+") — usando default <X>")`
3. **`catch (Exception ignored)` e proibido.** Sempre logar
   `e.getClass().getSimpleName() + ": " + e.getMessage()`. Se for esperado
   que o catch silencie o erro, logar mesmo assim (nivel `debug` ou `warn`),
   nunca `ignored`.
4. **Antes de loops grandes**, emitir um `slack.debug` de resumo com:
   quantidade de itens, limites em uso, parametros lidos. Ajuda a
   correlacionar com os logs individuais que vem depois.
5. **Metodos auxiliares novos** que rodam em contexto com `SlackLogger`
   **devem receber** o `SlackLogger` como parametro. Nao cair em
   `SlackLogger.NOOP` internamente — isso esconde diagnostico em producao.
6. **Reutilizar as tags convencionadas** em [BOAS_PRATICAS.md](BOAS_PRATICAS.md#tags-convencionadas)
   (ex: `CONFIG`, `FILTRO`, `SKIP`, `COND`, `RESUMO`). Inventar tag nova so
   quando nenhuma cabe — e documentar ali.

**Por que e obrigatorio:** sem esses debugs, quando um lote/item e cortado
silenciosamente em producao nao da pra saber se foi pela parametrizacao
global, pelo flag do parceiro, ou por falha de leitura. O projeto so emite
o log do caminho feliz + catch final, e isso nao basta pra diagnosticar.

**Exemplo canonico** (trecho real de `Utils.filtrarPorValidade` apos instrumentacao):

```java
public static List<ItemEstoque> filtrarPorValidade(
        List<ItemEstoque> itens, ItemEmpenho item, SlackLogger slack) {
    if ("S".equals(item.getAceitaTrocaValidade())) {
        slack.debug("FILTRO", "VALIDADE_BYPASS CODPARC=" + item.getCodParc()
            + " (parceiro aceita validade inferior)");
        return itens;
    }
    int minMeses = getValidadeMinimaMeses(slack);  // <-- slack propagado
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MONTH, minMeses);
    Timestamp limite = new Timestamp(cal.getTimeInMillis());
    slack.debug("FILTRO", "VALIDADE_APLICANDO MIN_MESES=" + minMeses
        + " LIMITE=" + limite + " QTD_LOTES=" + itens.size());
    // ... loop com APROVADO/VALIDADE_INSUF por item ...
}

private static int getValidadeMinimaMeses(SlackLogger slack) {
    try {
        String v = MGECoreParameter.getParameterAsString("EMPVALMIN");
        if (v != null && !v.trim().isEmpty()) {
            int meses = Integer.parseInt(v.trim());
            slack.debug("CONFIG", "EMPVALMIN=" + meses + " (lido de MGECoreParameter)");
            return meses;
        }
        slack.debug("CONFIG", "EMPVALMIN ausente/vazio — usando default 12 meses");
    } catch (Exception e) {
        slack.debug("CONFIG", "EMPVALMIN erro ao ler MGECoreParameter ("
            + e.getClass().getSimpleName() + ": " + e.getMessage()
            + ") — usando default 12 meses");
    }
    return 12;
}
```

A `snk-doctor` tem um caso `kb/log-silencioso.md` que detecta violacoes
desta regra em code review.

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
