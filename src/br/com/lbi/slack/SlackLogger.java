package br.com.lbi.slack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logger que acumula entradas e envia para o Slack via webhook.
 *
 * <p>A URL do webhook pode ser:</p>
 * <ul>
 *   <li><b>null</b> (recomendado): le dinamicamente da preferencia
 *       Sankhya <code>LOGSLACK_WEBHOOK</code> via {@link SlackConfig#getWebhookUrl()}.</li>
 *   <li><b>explicita</b>: usa a string fornecida (util em testes).</li>
 * </ul>
 *
 * <p>Uso tipico:</p>
 *
 * <pre>
 * SlackLogger slack = SlackLogger.create(null)  // null = le MGECoreParameter
 *     .modulo("Empenho Automatico")
 *     .header("Gerar Pedidos")
 *     .context("Rodada", "105")
 *     .build();
 * try {
 *     slack.info("INICIO", "=== INICIO ===");
 *     slack.success("FIM", "=== FIM ===");
 *     slack.flush();
 * } catch (Exception e) {
 *     slack.error("FATAL", "Erro ao gerar pedidos", e);
 *     slack.flush();
 *     throw e;
 * }
 * </pre>
 *
 * <p>Se a URL resolvida for null ou vazia, todos os metodos viram no-ops.
 * Falhas de envio ao Slack sao logadas em System.out e nunca lancam
 * excecao — o processo principal nunca e afetado.</p>
 */
public class SlackLogger {

    /** Instancia no-op: todos os metodos sao ignorados. Usar em vez de null. */
    public static final SlackLogger NOOP = new SlackLogger();

    private final String webhookUrl;
    private final String modulo;
    private final String header;
    private final Map<String, String> contextMap;
    private final List<LogEntry> entries;
    private final long startTime;
    private final boolean disabled;

    /** Construtor no-op (para NOOP). */
    private SlackLogger() {
        this.webhookUrl = null;
        this.modulo = "";
        this.header = "";
        this.contextMap = new LinkedHashMap<String, String>();
        this.entries = new ArrayList<LogEntry>();
        this.startTime = System.currentTimeMillis();
        this.disabled = true;
    }

    private SlackLogger(Builder builder) {
        // Resolve webhook: se explicitUrl == null, le da preferencia Sankhya.
        String resolvedUrl = builder.explicitUrl != null
            ? builder.explicitUrl
            : SlackConfig.getWebhookUrl();

        this.webhookUrl = resolvedUrl;
        this.modulo = builder.modulo;
        this.header = builder.header;
        this.contextMap = builder.contextMap;
        this.entries = new ArrayList<LogEntry>();
        this.startTime = System.currentTimeMillis();
        this.disabled = resolvedUrl == null || resolvedUrl.trim().isEmpty();
    }

    // ---- Factory ----

    /**
     * Cria um builder.
     *
     * @param webhookUrl URL explicita do webhook, ou <code>null</code> para
     *                   ler dinamicamente da preferencia <code>LOGSLACK_WEBHOOK</code>
     *                   (recomendado em producao).
     */
    public static Builder create(String webhookUrl) {
        return new Builder(webhookUrl);
    }

    // ---- Metodos de log ----

    public void info(String tag, String text) {
        log(LogEntry.INFO, tag, text, null);
    }

    public void success(String tag, String text) {
        log(LogEntry.SUCCESS, tag, text, null);
    }

    public void warn(String tag, String text) {
        log(LogEntry.WARN, tag, text, null);
    }

    public void error(String tag, String text) {
        log(LogEntry.ERROR, tag, text, null);
    }

    public void error(String tag, String text, Throwable cause) {
        log(LogEntry.ERROR, tag, text, cause);
    }

    public void debug(String tag, String text) {
        log(LogEntry.DEBUG, tag, text, null);
    }

    // ---- Controle ----

    /**
     * Envia todas as entradas acumuladas para o Slack e limpa o buffer.
     * Se nao houver entradas, nao envia nada.
     * Nunca lanca excecao.
     */
    public void flush() {
        if (disabled || entries.isEmpty()) {
            return;
        }
        try {
            List<LogEntry> snapshot = new ArrayList<LogEntry>(entries);
            entries.clear();

            List<String> payloads = SlackMessage.toJsonList(modulo, header, contextMap, snapshot, startTime);
            for (int i = 0; i < payloads.size(); i++) {
                if (i > 0) {
                    Thread.sleep(1100); // rate limit Slack: 1 msg/s
                }
                SlackWebhookClient.send(webhookUrl, payloads.get(i));
            }
        } catch (Exception e) {
            System.out.println("[SlackLogger] Erro ao enviar flush: " + e.getMessage());
        }
    }

    /** Adiciona contexto apos o build (ex: Rodada so conhecida em runtime). */
    public void context(String key, String value) {
        contextMap.put(key, value);
    }

    /** Numero de entradas acumuladas desde o ultimo flush. */
    public int size() {
        return entries.size();
    }

    /** True se o Slack esta desabilitado (URL vazia/null). */
    public boolean isDisabled() {
        return disabled;
    }

    // ---- Interno ----

    private void log(String level, String tag, String text, Throwable cause) {
        if (!disabled) {
            entries.add(new LogEntry(level, tag, text, cause));
        }
    }

    // ---- Builder ----

    public static class Builder {
        /** URL explicita passada em create(); null = ler da preferencia. */
        private final String explicitUrl;
        private String modulo = "";
        private String header = "Log";
        private final Map<String, String> contextMap = new LinkedHashMap<String, String>();

        Builder(String explicitUrl) {
            this.explicitUrl = explicitUrl;
        }

        /** Nome do modulo/projeto (ex: "Empenho Automatico", "NFS-e"). Aparece em todas as mensagens. */
        public Builder modulo(String modulo) {
            this.modulo = modulo;
            return this;
        }

        /** Titulo principal da mensagem (ex: "Gerar Pedidos"). */
        public Builder header(String header) {
            this.header = header;
            return this;
        }

        /** Adiciona campo de contexto (ex: "Rodada", "105"). */
        public Builder context(String key, String value) {
            this.contextMap.put(key, value);
            return this;
        }

        public SlackLogger build() {
            return new SlackLogger(this);
        }
    }
}
