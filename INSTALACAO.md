# INSTALACAO

Guia passo-a-passo pra quem nunca usou Slack. Leva ~10 minutos.

## Parte 1 — Criar o workspace e o canal

### 1. Criar workspace Slack (se ainda nao tem)

Acesse [slack.com/create](https://slack.com/create) e crie um workspace com o e-mail da sua empresa. Nao precisa convidar ninguem agora.

### 2. Criar canal `#logsankhya`

No workspace, clique em **Adicionar canal &rarr; Criar canal**. Use nome `logsankhya` e privacidade a sua escolha. Esse sera o canal que recebe os logs.

## Parte 2 — Criar o app Slack e o webhook

### 3. Criar app Slack

Acesse [api.slack.com/apps](https://api.slack.com/apps), clique em **Create New App &rarr; From scratch**. Nome sugerido: `Log Sankhya`. Selecione o workspace criado no passo 1.

### 4. Ativar Incoming Webhooks

No menu lateral do app, clique em **Incoming Webhooks**. Ative o toggle **Activate Incoming Webhooks**. Role pra baixo e clique em **Add New Webhook to Workspace**. Escolha o canal `#logsankhya` criado no passo 2. Clique em **Allow**.

### 5. Copiar Webhook URL

Copie a URL gerada. Formato: `https://hooks.slack.com/services/T.../B.../...`. Essa URL e secreta — trate como senha.

## Parte 3 — Instalar a lib no projeto Sankhya

### 6. Peca ao Claude pra instalar

Dentro do projeto Sankhya Java, peca ao Claude:

> "Adiciona o log Slack nesse projeto."

A skill Claude Code copia os 5 arquivos Java da lib para `src/br/com/lbi/slack/`, valida o Gson no classpath e instrumenta o entry point principal.

Se preferir fazer manualmente: copie os 5 arquivos de `src/br/com/lbi/slack/` desse repo para o seu projeto. Confira que o Gson esta no `.classpath`.

### 7. Salvar o webhook no Sankhya W

Em **Administracao &rarr; Preferencias**, crie uma nova preferencia:

- **Nome:** `LOGSLACK_WEBHOOK`
- **Tipo:** TEXTO
- **Tamanho:** 500
- **Descricao:** `URL do Incoming Webhook Slack para logs (veja snk-slack)`
- **Valor:** cole a URL copiada no passo 5

Ver instrucoes detalhadas de UI em [scripts/criar-preferencia-logslack.md](scripts/criar-preferencia-logslack.md).

### 8. Testar

Acione o botao/rotina instrumentada no Sankhya. Em segundos uma mensagem aparece em `#logsankhya` com o header do modulo e as entradas de log.

## Troubleshooting

- **Nao aparece nada no Slack:** confira que `LOGSLACK_WEBHOOK` existe como preferencia e o valor nao esta vazio. A lib vira no-op silenciosamente se a URL for null/vazia.
- **HTTP 404 no log:** URL errada. Gere novo webhook e atualize a preferencia.
- **Processo terminou mas log truncado:** falta `flush()` no caminho de erro. Ver [BOAS_PRATICAS.md](BOAS_PRATICAS.md) (pitfall do flush).
- **Quer mudar de canal:** altere apenas o valor da preferencia `LOGSLACK_WEBHOOK` no Sankhya W. Sem deploy.
