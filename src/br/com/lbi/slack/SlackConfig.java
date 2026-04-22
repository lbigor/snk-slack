package br.com.lbi.slack;

import br.com.sankhya.modelcore.util.MGECoreParameter;

/**
 * Configuracao centralizada do cliente Slack.
 *
 * <p>Suporta dois modos de envio, resolvidos dinamicamente via
 * {@link MGECoreParameter} a cada chamada (sem cache):</p>
 *
 * <ol>
 *   <li><b>API Slack Web (recomendado)</b>: preferencias
 *       {@code LOGSLACK_TOKEN} (Bearer token, ex: {@code xoxb-...}) e
 *       {@code LOGSLACK_CHANNEL} (ID do canal, ex: {@code C0AU7JUDEF2}).
 *       Permite reaproveitar o mesmo bot token entre ferramentas
 *       (MCP, scripts auxiliares, etc.) sem criar webhook no Slack.</li>
 *   <li><b>Incoming Webhook (legado)</b>: preferencia
 *       {@code LOGSLACK_WEBHOOK} (URL <code>https://hooks.slack.com/services/...</code>).</li>
 * </ol>
 *
 * <p>{@link SlackLogger} prefere o modo API quando as duas preferencias
 * {@code LOGSLACK_TOKEN} e {@code LOGSLACK_CHANNEL} estao preenchidas;
 * caso contrario cai no webhook; caso contrario vira no-op.</p>
 */
public final class SlackConfig {

    /**
     * Preferencia Sankhya que armazena a URL do webhook (modo legado).
     *
     * <p>Nome limitado a 15 chars pela UI de preferencias do Sankhya W —
     * evita truncamento no cadastro.</p>
     */
    public static final String PREFERENCIA_WEBHOOK = "LOGSLACK_HOOK";

    /** Preferencia Sankhya que armazena o Bearer token do bot Slack (14 chars). */
    public static final String PREFERENCIA_TOKEN = "LOGSLACK_TOKEN";

    /**
     * Preferencia Sankhya que armazena o ID do canal Slack (ex: C0AU7JUDEF2).
     *
     * <p>Nome limitado a 15 chars pela UI de preferencias do Sankhya W.</p>
     */
    public static final String PREFERENCIA_CHANNEL = "LOGSLACK_CHAN";

    private SlackConfig() {}

    /**
     * Le a URL do webhook da preferencia {@link #PREFERENCIA_WEBHOOK}.
     *
     * @return URL trim-ada, ou {@code null} se ausente/vazia/erro.
     */
    public static String getWebhookUrl() {
        return readParam(PREFERENCIA_WEBHOOK);
    }

    /**
     * Le o Bearer token da preferencia {@link #PREFERENCIA_TOKEN}.
     *
     * @return token trim-ado, ou {@code null} se ausente/vazio/erro.
     */
    public static String getToken() {
        return readParam(PREFERENCIA_TOKEN);
    }

    /**
     * Le o ID do canal da preferencia {@link #PREFERENCIA_CHANNEL}.
     *
     * @return ID trim-ado, ou {@code null} se ausente/vazio/erro.
     */
    public static String getChannel() {
        return readParam(PREFERENCIA_CHANNEL);
    }

    private static String readParam(String key) {
        try {
            String v = MGECoreParameter.getParameterAsString(key);
            if (v == null) return null;
            String trimmed = v.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Exception e) {
            return null;
        }
    }
}
