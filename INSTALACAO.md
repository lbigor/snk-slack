# INSTALACAO

Guia passo-a-passo pra quem nunca usou Slack. Leva ~10 minutos.

> Nos exemplos abaixo usamos os nomes `Log Sankhya` (app) e `#logsankhya`
> (canal), mas voce pode escolher qualquer nome. A skill nao depende desses
> nomes em lugar nenhum — e tudo configuravel.

## Parte 1 — Criar o workspace e o canal

### 1. Criar workspace Slack (se ainda nao tem)

Acesse [slack.com/create](https://slack.com/create) e crie um workspace com o e-mail da sua empresa. Nao precisa convidar ninguem agora.

### 2. Criar o canal de logs

No workspace, clique em **Adicionar canal → Criar canal**. Escolha:

- **Nome:** o que preferir (ex: `logsankhya`, `logs-customizacoes`, `sankhya-alerts`).
- **Privacidade:** privado se quer esconder os logs de outros membros; publico se qualquer pessoa do workspace pode ver.

Guarde o nome escolhido — vai usar nos proximos passos.

## Parte 2 — Criar o app Slack e o webhook

### 3. Criar app Slack

Acesse [api.slack.com/apps](https://api.slack.com/apps), clique em **Create New App → From scratch**. Escolha:

- **Nome do app:** o que preferir (ex: `Log Sankhya`, `Meu Logger`, `Sankhya Bot`).
- **Workspace:** o criado no passo 1.

Esse nome vai aparecer como autor das mensagens no canal (a menos que voce customize via `SlackLogger.Builder.username(...)`).

### 4. Ativar Incoming Webhooks

No menu lateral do app, clique em **Incoming Webhooks**. Ative o toggle **Activate Incoming Webhooks**. Role pra baixo e clique em **Add New Webhook to Workspace**. Escolha o canal criado no passo 2. Clique em **Allow**.

### 5. Se o canal for PRIVADO: adicionar o bot ao canal

Canal privado no Slack nao permite que o webhook poste nele sem que o bot seja membro. Na caixa de mensagem do canal, envie:

```
/invite @<nome-do-seu-bot>
```

Substitua `<nome-do-seu-bot>` pelo nome exato do app criado no passo 3 (o Slack autocompleta quando voce comeca a digitar). Apos o convite aparecer a mensagem "X adicionou Y ao canal", o webhook ja funciona.

> Canal publico nao precisa deste passo.

### 6. Copiar Webhook URL

Volte pra tela **Incoming Webhooks**. Ao lado do webhook criado, clique em **Copy**. Formato: `https://hooks.slack.com/services/T.../B.../...`. Essa URL e **secreta** — trate como senha.

## Parte 3 — Instalar a lib no projeto Sankhya

### 7. Peca ao Claude pra instalar

Dentro do projeto Sankhya Java, peca ao Claude:

> "Adiciona o log Slack nesse projeto."

A skill Claude Code copia os 5 arquivos Java da lib para `src/br/com/lbi/slack/`, valida o Gson no classpath e instrumenta o entry point principal.

Se preferir fazer manualmente: copie os 5 arquivos de `src/br/com/lbi/slack/` desse repo para o seu projeto. Confira que o Gson esta no `.classpath`.

### 8. Salvar o webhook no Sankhya W

Em **Administracao → Preferencias**, crie uma nova preferencia:

- **Nome:** `LOGSLACK_WEBHOOK`
- **Tipo:** TEXTO
- **Tamanho:** 500
- **Descricao:** `URL do Incoming Webhook Slack para logs (veja snk-slack)`
- **Valor:** cole a URL copiada no passo 6

Ver instrucoes detalhadas de UI em [scripts/criar-preferencia-logslack.md](scripts/criar-preferencia-logslack.md).

### 9. Testar

Acione o botao/rotina instrumentada no Sankhya. Em segundos uma mensagem aparece no canal configurado com o header do modulo e as entradas de log.

## Personalizacao opcional

### Mudar o nome que aparece como autor das mensagens

Por padrao, a lib nao sobrescreve o nome configurado no app Slack — o que faz com que cada cliente veja seu proprio nome. Se quiser forcar um nome especifico:

```java
SlackLogger slack = SlackLogger.create(null)
    .modulo("Meu Modulo")
    .header("Minha Acao")
    .username("Sankhya Bot")   // sobrescreve o nome do app no Slack
    .icon(":warning:")          // sobrescreve o icone
    .build();
```

## Troubleshooting

- **Nao aparece nada no Slack:** confira que `LOGSLACK_WEBHOOK` existe como preferencia e o valor nao esta vazio. A lib vira no-op silenciosamente se a URL for null/vazia.
- **HTTP 404 no log (channel_not_found):** canal privado e o bot nao foi adicionado ao canal. Volte ao passo 5.
- **HTTP 404 generico:** URL do webhook errada ou revogada. Gere novo webhook e atualize a preferencia.
- **Processo terminou mas log truncado:** falta `flush()` no caminho de erro. Ver [BOAS_PRATICAS.md](BOAS_PRATICAS.md) (pitfall do flush).
- **Quer mudar de canal:** altere apenas o valor da preferencia `LOGSLACK_WEBHOOK` no Sankhya W. Sem deploy.
