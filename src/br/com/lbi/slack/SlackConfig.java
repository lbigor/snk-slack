package br.com.lbi.slack;

import br.com.sankhya.modelcore.util.MGECoreParameter;

/**
 * Configuracao centralizada do Slack webhook.
 *
 * <p>A URL do webhook e lida dinamicamente da preferencia Sankhya
 * <code>LOGSLACK_WEBHOOK</code> em cada chamada — sem cache — para permitir
 * que o administrador altere o canal no Sankhya W sem deploy de codigo.</p>
 *
 * <p>Se a preferencia estiver vazia ou inexistente, o {@link SlackLogger}
 * vira no-op e o processo principal nunca e afetado.</p>
 *
 * <p>Criar a preferencia em: Administracao &rarr; Preferencias &rarr;
 * nova preferencia <code>LOGSLACK_WEBHOOK</code> (TEXTO, 500) com a URL
 * gerada em api.slack.com/apps (Incoming Webhook).</p>
 */
public final class SlackConfig {

    /** Nome da preferencia Sankhya que armazena a URL do webhook. */
    public static final String PREFERENCIA_WEBHOOK = "LOGSLACK_WEBHOOK";

    private SlackConfig() {}

    /**
     * Le dinamicamente a URL do webhook do Sankhya W (MGECoreParameter).
     * Sem cache — mudanca na preferencia reflete na proxima chamada.
     *
     * @return URL trim-ada, ou <code>null</code> se preferencia vazia/ausente
     *         ou se o lookup falhar (logger vira no-op).
     */
    public static String getWebhookUrl() {
        try {
            String url = MGECoreParameter.getParameterAsString(PREFERENCIA_WEBHOOK);
            if (url == null) {
                return null;
            }
            String trimmed = url.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Exception e) {
            return null;
        }
    }
}
