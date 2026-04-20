# Criar preferencia `LOGSLACK_WEBHOOK` no Sankhya W

Passo-a-passo de UI.

## 1. Abrir o modulo de Preferencias

No Sankhya W, navegue para:

**Administracao &rarr; Preferencias**

(Tambem pode ser acessado via busca: digite "Preferencias" na barra superior.)

## 2. Criar nova preferencia

Clique em **Novo** (icone de folha em branco na barra de acoes).

## 3. Preencher os campos

| Campo | Valor |
|---|---|
| **Codigo / Nome** | `LOGSLACK_WEBHOOK` |
| **Descricao** | `URL do Incoming Webhook Slack para logs (projeto snk-slack)` |
| **Tipo de dado** | `TEXTO` |
| **Tamanho** | `500` |
| **Valor** | cole a URL copiada do Slack (formato `https://hooks.slack.com/services/T.../B.../...`) |
| **Permite alterar** | `Sim` (admins precisam poder trocar sem redeploy) |

## 4. Salvar

Clique em **Salvar** (disquete / Ctrl+S).

## 5. Conferir

Volte para o grid de preferencias, filtre por `LOGSLACK_WEBHOOK` e confirme que o valor aparece integro (sem quebra de linha no meio da URL).

## Observacoes

- **Sem cache:** a lib `SlackLogger` le a preferencia a cada `SlackLogger.create(null)`. Trocar o valor reflete na proxima execucao — sem restart de servico.
- **Trocar canal:** gere novo Incoming Webhook no Slack e atualize o valor dessa preferencia. Sem deploy.
- **Desligar logs temporariamente:** apague o valor (deixe vazio). A lib vira no-op silenciosamente.
- **Seguranca:** a URL do webhook e uma credencial. Restrinja quem pode ler/editar essa preferencia no controle de acesso do Sankhya W.
