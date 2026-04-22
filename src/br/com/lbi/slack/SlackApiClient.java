package br.com.lbi.slack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Cliente HTTP para a Slack Web API (<code>chat.postMessage</code>).
 *
 * <p>Usa Bearer token em vez de webhook URL. Isso permite reaproveitar o
 * mesmo token do MCP/scripts auxiliares e elimina o passo de criar
 * Incoming Webhook no painel do Slack.</p>
 *
 * <p>Sem dependencias externas (HttpURLConnection nativo). Nunca lanca
 * excecao — falhas sao logadas em {@code System.out} como
 * <code>[SlackLogger]</code>.</p>
 */
class SlackApiClient {

    private static final String ENDPOINT = "https://slack.com/api/chat.postMessage";

    private SlackApiClient() {}

    /**
     * POST em <code>chat.postMessage</code>.
     *
     * <p>O {@code jsonPayload} ja deve conter o campo {@code channel} (injetado
     * em {@link SlackMessage#toJsonList}). Header {@code Authorization: Bearer &lt;token&gt;}
     * e adicionado aqui.</p>
     *
     * <p>A Slack Web API sempre responde HTTP 200 quando o token eh valido;
     * o sucesso real esta em {@code "ok": true} no body. Este cliente parseia
     * o body manualmente (sem Gson) so pra logar erros de aplicacao como
     * {@code channel_not_found}, {@code invalid_auth}, {@code not_in_channel}.</p>
     *
     * @return HTTP status code, ou -1 em caso de erro de I/O.
     */
    static int postMessage(String token, String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
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
            String body = readBody(conn, status);

            if (status != 200) {
                System.out.println("[SlackLogger] HTTP " + status + " na Slack API: " + body);
                return status;
            }

            String err = extractError(body);
            if (err != null) {
                System.out.println("[SlackLogger] Slack API erro: " + err);
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

    private static String readBody(HttpURLConnection conn, int status) {
        try {
            java.io.InputStream is = status >= 400
                ? conn.getErrorStream()
                : conn.getInputStream();
            if (is == null) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracao manual de <code>"ok":false,"error":"&lt;codigo&gt;"</code> do body.
     * Retorna o codigo do erro, ou {@code null} se a resposta foi ok.
     *
     * <p>Nao usa Gson aqui pra nao acoplar este client a uma dependencia
     * de parsing — o payload de resposta do chat.postMessage e minusculo
     * e o formato do {@code error} eh estavel.</p>
     */
    private static String extractError(String body) {
        if (body == null) return null;
        if (body.contains("\"ok\":true")) return null;
        int errIdx = body.indexOf("\"error\"");
        if (errIdx < 0) return "resposta sem campo error: " + truncate(body, 200);
        int quoteStart = body.indexOf('"', errIdx + 7);  // pula "error"
        if (quoteStart < 0) return null;
        quoteStart = body.indexOf('"', quoteStart + 1);  // abertura do valor
        if (quoteStart < 0) return null;
        int quoteEnd = body.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return body.substring(quoteStart + 1, quoteEnd);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
