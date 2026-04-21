# snk-slack

[![Discussions](https://img.shields.io/github/discussions/lbigor/snk-slack)](https://github.com/lbigor/snk-slack/discussions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> Observabilidade Sankhya no Slack em 10 minutos. Logs estruturados, tags padronizadas, buffer inteligente.

**Problema:** rodada quebra em producao, voce descobre 3 dias depois.
**Solucao:** lib Java plug-and-play + guia pra configurar seu Slack.
**Voce faz:** `"Claude, adiciona o log Slack nesse projeto"` — o resto e automatico.

## Como funciona

1. Voce cria um workspace Slack e um Incoming Webhook (ver [INSTALACAO.md](INSTALACAO.md)).
2. Salva a URL do webhook em uma preferencia do Sankhya W chamada `LOGSLACK_WEBHOOK`.
3. A lib Java (`br.com.lbi.slack`) le a preferencia em cada chamada — sem deploy pra trocar de canal.
4. Os entry points do seu projeto Sankhya emitem logs com tags convencionadas (`INICIO`, `FIM`, `FATAL`, etc).
5. Se um servico quebra, voce tem timeline completa no Slack em segundos.

## Instalacao em 1 comando

```bash
curl -fsSL https://raw.githubusercontent.com/lbigor/snk-slack/main/install.sh | bash
```

Isso instala a **skill Claude Code**. Depois, em um projeto Sankhya, peca ao Claude:

> "Adiciona o log Slack nesse projeto."

## Exemplo de uso

```java
SlackLogger slack = SlackLogger.create(null)  // null = le MGECoreParameter.LOGSLACK_WEBHOOK
    .modulo("Empenho Automatico")
    .header("Gerar Pedidos")
    .context("Rodada", "105")
    .build();
try {
    slack.info("INICIO", "=== INICIO ===");
    // ... sua logica ...
    slack.success("FIM", "=== FIM ===");
    slack.flush();
} catch (Exception e) {
    slack.error("FATAL", "Erro ao processar", e);
    slack.flush();
    throw e;
}
```

## Documentacao

- [INSTALACAO.md](INSTALACAO.md) — passo-a-passo pra criar Slack + instalar lib
- [BOAS_PRATICAS.md](BOAS_PRATICAS.md) — tags convencionadas, rate-limit, pitfall do flush
- [SKILL.md](SKILL.md) — orquestrador Claude Code
- [CONTRIBUTING.md](CONTRIBUTING.md) — como contribuir

## Requisitos

- Java 8+ (projeto Sankhya tipico)
- Gson 2.x no classpath (ja vem em todo projeto Sankhya)
- Acesso de administrador ao Sankhya W (pra criar a preferencia)
- Workspace Slack + Incoming Webhook

## Licenca

MIT (c) 2026 Igor Lima — [LICENSE](LICENSE)

## Contato

lbigor@icloud.com
