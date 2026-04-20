package br.com.lbi.slack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Converte uma lista de LogEntry em payloads JSON Block Kit para o Slack.
 * Compativel com Gson 2.1 (sem JsonArray.add(String)).
 */
class SlackMessage {

    private static final int MAX_SECTION_CHARS = 2800;
    private static final int MAX_BLOCKS_PER_MSG = 45;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private SlackMessage() {}

    /**
     * Gera um ou mais payloads JSON para enviar ao Slack.
     * Divide automaticamente se o conteudo exceder os limites.
     */
    static List<String> toJsonList(String modulo, String header, Map<String, String> contextMap,
                                   List<LogEntry> entries, long startTime) {

        List<JsonObject> allBlocks = new ArrayList<JsonObject>();

        // Header block
        allBlocks.add(headerBlock(modulo, header));

        // Context block (rodada, timestamp, etc.)
        allBlocks.add(contextBlock(modulo, contextMap, startTime));

        // Section blocks com as entradas de log
        List<String> chunks = splitEntries(entries);
        for (String chunk : chunks) {
            allBlocks.add(sectionBlock(chunk));
        }

        // Footer block
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        String footerText = ":clock1: Duracao: " + elapsed + "s | " + entries.size() + " entradas";
        allBlocks.add(contextBlockSimple(footerText));

        // Divide em multiplas mensagens se necessario
        List<String> payloads = new ArrayList<String>();
        int blockIndex = 0;
        while (blockIndex < allBlocks.size()) {
            JsonArray blocks = new JsonArray();
            int count = 0;
            while (blockIndex < allBlocks.size() && count < MAX_BLOCKS_PER_MSG) {
                blocks.add(allBlocks.get(blockIndex));
                blockIndex++;
                count++;
            }
            payloads.add(buildPayload(blocks));
        }

        return payloads;
    }

    private static String buildPayload(JsonArray blocks) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Log Sankhya");
        payload.addProperty("icon_emoji", ":gear:");
        payload.add("blocks", blocks);
        return payload.toString();
    }

    private static JsonObject headerBlock(String modulo, String header) {
        String title = modulo != null && !modulo.isEmpty()
            ? "[" + modulo + "] " + header
            : header;
        JsonObject text = new JsonObject();
        text.addProperty("type", "plain_text");
        text.addProperty("text", truncate(title, 150));

        JsonObject block = new JsonObject();
        block.addProperty("type", "header");
        block.add("text", text);
        return block;
    }

    private static JsonObject contextBlock(String modulo, Map<String, String> contextMap, long startTime) {
        StringBuilder sb = new StringBuilder();
        if (modulo != null && !modulo.isEmpty()) {
            sb.append(":package: *Modulo:* ").append(modulo).append("  |  ");
        }
        String ts;
        synchronized (SDF) {
            ts = SDF.format(new Date(startTime));
        }
        sb.append(":calendar: ").append(ts);
        for (Map.Entry<String, String> entry : contextMap.entrySet()) {
            sb.append("  |  *").append(entry.getKey()).append(":* ").append(entry.getValue());
        }

        JsonObject elem = new JsonObject();
        elem.addProperty("type", "mrkdwn");
        elem.addProperty("text", sb.toString());

        JsonArray elements = new JsonArray();
        elements.add(elem);

        JsonObject block = new JsonObject();
        block.addProperty("type", "context");
        block.add("elements", elements);
        return block;
    }

    private static JsonObject contextBlockSimple(String text) {
        JsonObject elem = new JsonObject();
        elem.addProperty("type", "mrkdwn");
        elem.addProperty("text", text);

        JsonArray elements = new JsonArray();
        elements.add(elem);

        JsonObject block = new JsonObject();
        block.addProperty("type", "context");
        block.add("elements", elements);
        return block;
    }

    private static JsonObject sectionBlock(String mrkdwn) {
        JsonObject text = new JsonObject();
        text.addProperty("type", "mrkdwn");
        text.addProperty("text", mrkdwn);

        JsonObject block = new JsonObject();
        block.addProperty("type", "section");
        block.add("text", text);
        return block;
    }

    /**
     * Formata as entradas e divide em chunks de ate MAX_SECTION_CHARS.
     */
    private static List<String> splitEntries(List<LogEntry> entries) {
        List<String> chunks = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();

        for (LogEntry entry : entries) {
            String line = formatEntry(entry);

            if (sb.length() + line.length() + 1 > MAX_SECTION_CHARS) {
                if (sb.length() > 0) {
                    chunks.add(sb.toString());
                    sb.setLength(0);
                }
            }

            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);

            // Stack trace em code block
            if (entry.cause != null) {
                String trace = formatStackTrace(entry.cause);
                if (sb.length() + trace.length() + 1 > MAX_SECTION_CHARS) {
                    chunks.add(sb.toString());
                    sb.setLength(0);
                }
                sb.append("\n").append(trace);
            }
        }

        if (sb.length() > 0) {
            chunks.add(sb.toString());
        }

        return chunks;
    }

    private static String formatEntry(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.emoji());
        sb.append(" *[").append(entry.tag).append("]* ");
        sb.append(entry.text);
        return sb.toString();
    }

    private static String formatStackTrace(Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("```");
        sb.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
        StackTraceElement[] stack = cause.getStackTrace();
        int count = 0;
        for (StackTraceElement el : stack) {
            if (count >= 8) {
                sb.append("\n  ... ").append(stack.length - count).append(" mais");
                break;
            }
            sb.append("\n  at ").append(el.toString());
            count++;
        }
        sb.append("```");
        return sb.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
}
