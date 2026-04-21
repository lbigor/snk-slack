package br.com.lbi.slack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Le o manifest embutido pelo snk-deploy em
 * <code>META-INF/snk-deploy/manifest.json</code>.
 *
 * <p>Se o JAR foi empacotado com snk-deploy, disponibiliza metadados do
 * build (hash, branch, PR, commit) para anexar aos logs Slack. Se o
 * manifest nao existir ou estiver malformado, <code>isPresent()</code>
 * retorna <code>false</code> e <code>toFooter()</code> retorna string
 * vazia — qualquer falha de leitura e silenciosa, nunca quebra o logger.</p>
 *
 * <p>Zero dependencias alem da Gson (ja usada em {@link SlackMessage}).</p>
 *
 * <p><b>Thread-safe:</b> carregado uma unica vez no primeiro acesso via
 * double-checked locking.</p>
 */
public final class DeployManifest {

    private static final String RESOURCE_PATH = "/META-INF/snk-deploy/manifest.json";

    private static volatile DeployManifest instance;
    private static final Object LOCK = new Object();

    private final boolean present;
    private final String hash;
    private final String branch;
    private final String commit;
    private final String commitShort;
    private final String author;
    private final String builtAt;
    private final String prNumber;
    private final String prUrl;
    private final String prTitle;
    private final String project;

    /** Construtor "ausente". */
    private DeployManifest() {
        this.present = false;
        this.hash = null;
        this.branch = null;
        this.commit = null;
        this.commitShort = null;
        this.author = null;
        this.builtAt = null;
        this.prNumber = null;
        this.prUrl = null;
        this.prTitle = null;
        this.project = null;
    }

    /** Construtor a partir de manifest parseado. */
    private DeployManifest(JsonObject root) {
        this.present = true;
        this.hash = readString(root, "hash");
        this.branch = readString(root, "branch");
        this.commit = readString(root, "commit");
        this.commitShort = readString(root, "commitShort");
        this.author = readString(root, "author");
        this.builtAt = readString(root, "builtAt");
        this.prNumber = readString(root, "prNumber");
        this.prUrl = readString(root, "prUrl");
        this.prTitle = readString(root, "prTitle");
        this.project = readString(root, "project");
    }

    /**
     * Retorna a instancia cacheada (carregada uma unica vez).
     * Nunca retorna <code>null</code>.
     */
    public static DeployManifest get() {
        DeployManifest local = instance;
        if (local == null) {
            synchronized (LOCK) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Reseta o cache. Uso: testes. Nao chamar em producao. */
    static void resetForTests() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    private static DeployManifest load() {
        InputStream in = null;
        try {
            in = DeployManifest.class.getResourceAsStream(RESOURCE_PATH);
            if (in == null) {
                return new DeployManifest();
            }
            String json = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
            if (json.trim().isEmpty()) {
                return new DeployManifest();
            }
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            if (root == null) {
                return new DeployManifest();
            }
            return new DeployManifest(root);
        } catch (Exception e) {
            // Qualquer erro vira "nao presente" — nunca quebra o logger.
            return new DeployManifest();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static String readString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        try {
            String v = el.getAsString();
            return (v == null || v.isEmpty()) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isPresent()       { return present; }
    public String getHash()          { return hash; }
    public String getBranch()        { return branch; }
    public String getCommit()        { return commit; }
    public String getCommitShort()   { return commitShort; }
    public String getPrNumber()      { return prNumber; }
    public String getPrUrl()         { return prUrl; }
    public String getPrTitle()       { return prTitle; }
    public String getAuthor()        { return author; }
    public String getBuiltAt()       { return builtAt; }
    public String getProject()       { return project; }

    /**
     * Retorna uma string curta pro footer de log, no formato:
     * <pre>v: abc12345 · feat/fix-estoque · PR #42</pre>
     * ou string vazia se o manifest nao esta presente.
     *
     * <p>Nao inclui link clicavel — isso e responsabilidade de
     * {@link SlackMessage} (Block Kit usa sintaxe propria de link).</p>
     */
    public String toFooter() {
        if (!present) return "";
        StringBuilder sb = new StringBuilder("v: ");
        sb.append(hash != null ? hash : "?");
        if (branch != null) {
            sb.append(" \u00b7 ").append(branch);
        }
        if (prNumber != null) {
            sb.append(" \u00b7 PR #").append(prNumber);
        }
        return sb.toString();
    }
}
