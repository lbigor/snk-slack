# Lições aprendidas — snk-slack

Aprendizados consolidados do teste piloto de 2026-04-21 com o primeiro
cliente real (projeto `snk-fabmed-empenho-automatico`). Cada lição
virou código ou doc — este arquivo é a trilha de audit.

## 1. Webhook NÃO pode ser constante hardcoded

**Sintoma:** `SlackConfig.WEBHOOK_URL = "https://hooks.slack.com/services/..."` foi
comitado no repo privado do cliente. Quando o histórico vira público (fork, erro
de visibilidade, colaborador), token vaza.

**Fix:** `SlackConfig.getWebhookUrl()` lê dinamicamente de `MGECoreParameter.LOGSLACK_WEBHOOK`.
Sem cache. Trocar canal = editar preferência no Sankhya W, sem deploy.

**Regra:** qualquer URL/token/secret precisa vir de fonte de runtime
(`MGECoreParameter`, env var, arquivo `.env` gitignored). Nunca literal em `.java`/`.properties`.

## 2. Username do bot NÃO pode ser hardcoded

**Sintoma:** `SlackMessage.java` tinha `payload.addProperty("username", "Log Sankhya")`.
Consequência: todo cliente que instalava a lib via mensagem aparecer como "Log Sankhya"
(nome do app do autor) em vez do próprio app.

**Fix:** `SlackLogger.Builder.username(String)` e `.icon(String)`, default `null` =
Slack usa o nome configurado no app daquele cliente. Comportamento correto multi-tenant.

## 3. Canal privado exige bot como membro

**Sintoma:** `curl` no webhook retorna 404 `channel_not_found`, mesmo com webhook
válido, porque o bot não é membro do canal privado.

**Fix no doc:** passo explícito `/invite @<nome-do-seu-bot>` no canal ANTES de
criar o webhook. Canal público não precisa.

**No troubleshooting:** erro `channel_not_found` → volta ao passo do /invite.

## 4. Ordem dos passos importa no Slack API

**Sintoma:** instrução original dizia "criar canal privado, depois criar app".
Resultado: quando o usuário chegava no `/invite`, o bot ainda não existia.

**Fix:** ordem nova é `workspace → app (cria bot user) → canal → /invite → webhook`.

## 5. `pbpaste` em pipeline tem pegadinha

**Sintoma:** `pbpaste > arquivo` executado quando o clipboard perdeu foco / foi
sobrescrito resulta em arquivo de 0 bytes, sem erro aparente.

**Mitigação no doc:** passo de verificação obrigatório `head -c 30 arquivo` pra
confirmar que começa com `https://hooks.slack.com/services/T`.

## 6. Manifest do snk-deploy é auto-descoberto

**Decisão:** `DeployManifest.java` é singleton thread-safe que lê
`/META-INF/snk-deploy/manifest.json` do classpath na primeira chamada. Se o
JAR não foi empacotado com snk-deploy, `isPresent() == false` — logger
funciona normal sem footer de versão.

**Regra:** lib snk-slack NUNCA falha por falta do manifest. Release tracking
é feature opcional, não requisito.

## 7. Canal privado requer `groups:*` escopos, não `channels:*`

**Para MCP ler logs** (usado pelo snk-doctor), precisa token com:
- Canal público: `channels:read`, `channels:history`
- Canal privado: `groups:read`, `groups:history`
- Busca global: `search:read` (útil pra `find-by-hash`)

Configuração única: marcar todos esses escopos no app Slack uma vez só,
antes de criar webhook. Assim não precisa reinstalar depois.

---

## Incidentes conhecidos durante o piloto

| Data | Incidente | Resolução |
|---|---|---|
| 2026-04-21 08:00 | Webhook vazou no repo privado `snk-fabmed-empenho-automatico` via auto-init do snk-deploy | `git filter-branch` + force-push + substituição da lib por versão dinâmica. Security gate adicionado ao snk-deploy/build.sh (PR #7) |
| 2026-04-21 07:15 | `/invite @log_sankhya` sugerido em doc genérica assumindo nome fixo | INSTALACAO.md atualizado pra usar `@<nome-do-seu-bot>` |
| 2026-04-21 07:30 | `pbpaste` salvou arquivo com 0 bytes porque clipboard perdeu conteúdo entre /copy e executar | Passo de verificação `head -c 30` adicionado |

---

## Próximas melhorias planejadas

- [ ] Adicionar `INSTALACAO.pdf` gerado via pandoc+typst (padrão do docs-user).
- [ ] Capturar prints restantes do passo-a-passo (passos 5 em diante).
- [ ] Adicionar workflow GitHub Actions com `gitleaks` pra scanner de secret em PRs.
- [ ] Documentar fluxo de "mudar de canal sem redeploy" em `BOAS_PRATICAS.md`.
- [ ] Teste e2e: JAR instalado no Sankhya W real → botão disparado → log chega no canal.
