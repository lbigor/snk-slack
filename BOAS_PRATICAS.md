# BOAS PRATICAS

## Tags convencionadas

Use tags curtas e padronizadas. Facilita filtro no Slack e grep de logs.

| Tag | Uso |
|---|---|
| `INICIO` | Primeiro log de uma rodada/execucao |
| `FIM` | Ultimo log antes do flush de sucesso |
| `RODADA` | Identificacao do lote processado |
| `ESTOQUE` | Snapshot de estoque carregado |
| `ITEM` | Iteracao individual de item/produto |
| `SKIP` | Item/cenario pulado (com motivo) |
| `COND` | Condicao de negocio avaliada |
| `FILTRO` | Criterio aplicado na query |
| `EMP` | Confirmacao de empenho |
| `PEDIDO` | Criacao/alteracao de pedido |
| `TRANSF` | Transferencia entre filiais |
| `CONFIRM` | Confirmacao de operacao |
| `RESUMO` | Totais finais antes do FIM |
| `DEBUG` | Detalhes de desenvolvimento (usar `slack.debug`) |
| `ERRO` | Erro recuperavel |
| `FATAL` | Erro que aborta a rodada |

## Padrao try/flush/catch FATAL/flush/throw

Sempre que instrumentar um entry point, siga:

```java
SlackLogger slack = SlackLogger.create(null)
    .modulo("Modulo X")
    .header("Acao Y")
    .build();
try {
    slack.info("INICIO", "=== INICIO ===");
    // ... logica ...
    slack.success("FIM", "=== FIM ===");
    slack.flush();  // <-- flush no caminho feliz
} catch (Exception e) {
    slack.error("FATAL", "Erro ao processar Y", e);
    slack.flush();  // <-- flush no caminho de erro
    throw e;
}
```

**Por que flush nos dois caminhos:** se o servico Sankhya for derrubado ou a exception propagar, o buffer em memoria e perdido. Sem flush voce perde justamente o log do erro.

## Pitfall do flush com dump grande

Se voce acumula centenas de entradas (`slack.info`) e chama `flush()` ao final, o envio sequencial respeitando rate-limit (1 msg/s) pode demorar varios segundos. Se o Sankhya finalizar a rotina antes do flush terminar, mensagens pendentes se perdem.

**Solucao:** flush intermediario dentro de loops grandes.

```java
int contador = 0;
for (Item item : itens) {
    slack.info("ITEM", "Processando " + item.getId());
    // ... logica ...
    contador++;
    if (contador % 100 == 0) {
        slack.flush();  // flush a cada 100 itens
    }
}
slack.flush();  // flush final
```

## Rate limit

Slack aceita 1 mensagem por segundo por webhook. A lib ja respeita com `Thread.sleep(1100)` entre payloads. Um flush que gera 10 paginas leva ~10s — planeje nas operacoes criticas.

## Nivel `info` vs `warn` vs `debug`

- **`info`**: eventos normais do fluxo (inicio, fim, resumo, confirmacao).
- **`success`**: marcos positivos (empenho confirmado, pedido criado).
- **`warn`**: condicao anormal mas recuperavel (item sem estoque, filial inexistente).
- **`error`**: erro que nao aborta a rodada (falha em item isolado com `continue`).
- **`error(tag, text, Throwable)`**: com stack trace — usar em catches.
- **`debug`**: detalhes de desenvolvimento. Desligar em producao via flag do chamador (a lib nao filtra).

## Nao faca

- Nao logue senhas, tokens ou dados sensiveis (LGPD).
- Nao logue BLOBs ou strings gigantes — a lib divide em paginas mas ocupa rate-limit.
- Nao chame `new SlackLogger` — use `SlackLogger.create(...)` sempre.
- Nao compartilhe a URL do webhook publicamente — e uma credencial.
