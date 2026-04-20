package br.com.lbi.slack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Cliente HTTP minimo para POST em webhook Slack.
 * Usa HttpURLConnection nativo — sem dependencias externas.
 * Nunca lanca excecao — falhas sao logadas em System.out.
 */
class SlackWebhookClient {

    private SlackWebhookClient() {}

    /**
     * Envia payload JSON para o webhook Slack.
     * @return HTTP status code ou -1 em caso de erro de I/O.
     */
    static int send(String webhookUrl, String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            byte[] bytes = jsonPayload.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();

            int status = conn.getResponseCode();
            if (status != 200) {
                System.out.println("[SlackLogger] HTTP " + status + " ao enviar webhook");
            }
            return status;
        } catch (Exception e) {
            System.out.println("[SlackLogger] ERRO HTTP: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
