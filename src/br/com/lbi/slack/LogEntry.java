package br.com.lbi.slack;

/**
 * Entrada de log individual. Acumula timestamp, nivel, tag e texto.
 * Usado internamente pelo SlackLogger.
 */
class LogEntry {

    static final String INFO    = "INFO";
    static final String SUCCESS = "SUCCESS";
    static final String WARN    = "WARN";
    static final String ERROR   = "ERROR";
    static final String DEBUG   = "DEBUG";

    final long timestamp;
    final String level;
    final String tag;
    final String text;
    final Throwable cause;

    LogEntry(String level, String tag, String text, Throwable cause) {
        this.timestamp = System.currentTimeMillis();
        this.level = level;
        this.tag = tag;
        this.text = text;
        this.cause = cause;
    }

    String emoji() {
        if (SUCCESS.equals(level)) return ":white_check_mark:";
        if (WARN.equals(level))    return ":warning:";
        if (ERROR.equals(level))   return ":x:";
        if (DEBUG.equals(level))   return ":mag:";
        return ":information_source:";
    }
}
