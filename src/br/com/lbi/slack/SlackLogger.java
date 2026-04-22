package br.com.lbi.slack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logger que acumula entradas e envia para o Slack.
 *
 * <p>Resolve o transporte em ordem de preferencia:</p>
 * <ol>
 *   <li><b>Slack Web API</b> via {@code chat.postMessage}: usa as preferencias
 *       Sankhya {@code LOGSLACK_TOKEN} (Bearer) e {@code LOGSLACK_CHANNEL}
 *       (ID do canal). <i>Recomendado</i> — reaproveita o mesmo bot token
 *       usado em outras ferramentas (MCP, scripts) e elimina a criacao de
 *       Incoming Webhook.</li>
 *   <li><b>Incoming Webhook (legado)</b>: usa a preferencia
 *       {@code LOGSLACK_WEBHOOK}. Ativado automaticamente quando TOKEN/CHANNEL
 *       nao estao configurados.</li>
 *   <li><b>NOOP</b>: se nenhum dos dois resolver, todos os metodos viram
 *       no-ops e o processo principal nunca e afetado.</li>
 * </ol>
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
 * <p>Falhas de envio sao logadas em {@code System.out} e nunca lancam
 * excecao.</p>
 */
public class SlackLogger {

    /** Instancia no-op: todos os metodos sao ignorados. Usar em vez de null. */
    public static final SlackLogger NOOP = new SlackLogger();

    /** Modo de envio resolvido na construcao. */
    private enum Transport { NOOP, API, WEBHOOK }

    private final Transport transport;
    private final String webhookUrl;
    private final String apiToken;
    private final String apiChannel;
    private final String modulo;
    private final String header;
    private final Map<String, String> contextMap;
    private final List<LogEntry> entries;
    private final long startTime;
    private final boolean disabled;
    private final boolean releaseTracking;
    private final String username;
    private final String iconEmoji;

    /** Construtor no-op (para NOOP). */
    private SlackLogger() {
        this.transport = Transport.NOOP;
        this.webhookUrl = null;
        this.apiToken = null;
        this.apiChannel = null;
        this.modulo = "";
        this.header = "";
        this.contextMap = new LinkedHashMap<String, String>();
        this.entries = new ArrayList<LogEntry>();
        this.startTime = System.currentTimeMillis();
        this.disabled = true;
        this.releaseTracking = false;
        this.username = null;
        this.iconEmoji = null;
    }

    private SlackLogger(Builder builder) {
        // Resolucao do transport: API (token+channel) > webhook > NOOP.
        // Se o chamador passou URL explicita, preserva o comportamento antigo (webhook only).
        String resolvedUrl = null;
        String resolvedToken = null;
        String resolvedChannel = null;
        Transport resolvedTransport;

        if (builder.explicitUrl != null) {
            resolvedUrl = builder.explicitUrl;
            resolvedTransport = isNonEmpty(resolvedUrl) ? Transport.WEBHOOK : Transport.NOOP;
        } else {
            String t = SlackConfig.getToken();
            String c = SlackConfig.getChannel();
            if (isNonEmpty(t) && isNonEmpty(c)) {
                resolvedToken = t;
                resolvedChannel = c;
                resolvedTransport = Transport.API;
            } else {
                resolvedUrl = SlackConfig.getWebhookUrl();
                resolvedTransport = isNonEmpty(resolvedUrl) ? Transport.WEBHOOK : Transport.NOOP;
            }
        }

        this.transport = resolvedTransport;
        this.webhookUrl = resolvedUrl;
        this.apiToken = resolvedToken;
        this.apiChannel = resolvedChannel;
        this.modulo = builder.modulo;
        this.header = builder.header;
        this.contextMap = builder.contextMap;
        this.entries = new ArrayList<LogEntry>();
        this.startTime = System.currentTimeMillis();
        this.disabled = resolvedTransport == Transport.NOOP;
        this.releaseTracking = builder.releaseTracking;
        this.username = builder.username;
        this.iconEmoji = builder.iconEmoji;
    }

    private static boolean isNonEmpty(String s) {
        return s != null && !s.trim().isEmpty();
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

            // Quando transport=API, SlackMessage injeta "channel" no payload.
            String channelForPayload = transport == Transport.API ? apiChannel : null;
            List<String> payloads = SlackMessage.toJsonList(modulo, header, contextMap, snapshot, startTime,
                releaseTracking, username, iconEmoji, channelForPayload);

            for (int i = 0; i < payloads.size(); i++) {
                if (i > 0) {
                    Thread.sleep(1100); // rate limit Slack: 1 msg/s
                }
                if (transport == Transport.API) {
                    SlackApiClient.postMessage(apiToken, payloads.get(i));
                } else {
                    SlackWebhookClient.send(webhookUrl, payloads.get(i));
                }
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
        private boolean releaseTracking = true;
        private String username = null;
        private String iconEmoji = null;

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

        /**
         * Liga/desliga o footer automatico com metadados de build lidos do
         * <code>META-INF/snk-deploy/manifest.json</code>. Default: <b>true</b>.
         *
         * <p>Desligue apenas se o manifest estiver gerando ruido indesejado
         * nas mensagens. Em producao, o padrao e manter ligado — permite que
         * <a href="https://github.com/lbigor/snk-doctor">snk-doctor</a>
         * rastreie erros ate o PR que os causou.</p>
         */
        public Builder withReleaseTracking(boolean enabled) {
            this.releaseTracking = enabled;
            return this;
        }

        /**
         * Nome que aparece como autor da mensagem no Slack. Se null ou vazio,
         * o Slack usa o nome configurado no app (recomendado para multi-cliente).
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Emoji que aparece como icone da mensagem (ex: ":gear:", ":robot_face:").
         * Se null ou vazio, o Slack usa o icone configurado no app.
         */
        public Builder icon(String iconEmoji) {
            this.iconEmoji = iconEmoji;
            return this;
        }

        public SlackLogger build() {
            return new SlackLogger(this);
        }
    }
}
