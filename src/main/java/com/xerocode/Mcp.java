package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.util.Util;

public final class Mcp {
    public record Tool(String id, String title, boolean found, boolean connected, String note, String error) {}

    public record State(String version, String root, List<Tool> tools, List<String> skills, boolean modules) {}

    public enum Job { NONE, CHECK, INSTALL, CONNECT, REMOVE, MODULES }

    private static final String RAW = "https://raw.githubusercontent.com/garfystyle/justmc-mcp/main/";
    private static final String NODE_PAGE = "https://nodejs.org";
    private static final String STALE = "Установлен старый MCP: нажми «Установить MCP», чтобы обновить";
    private static final String BROKEN = "MCP установлен не до конца: нажми «Установить MCP» ещё раз";
    private static final String GAVE_UP = "justmc-mcp не установлен";
    private static final String BANNER = "JustMCP ";
    private static final String AGENT = "justmc.agent.Agent";
    private static final String LIVE = "justmc.agent.live";
    private static final int KEEP = 4, HISTORY = 40;
    private static final long FRESH = 60_000;
    private static final long CHECK_WAIT = 120, INSTALL_WAIT = 900, SETUP_WAIT = 300, MODULES_WAIT = 7200;
    private static final Pattern COLORS = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static volatile State state;
    private static volatile String node;
    private static volatile Job job = Job.NONE;
    private static volatile long checkedAt;
    private static volatile String failure = "";
    private static volatile String trouble = "";
    private static volatile List<String> searched = List.of();
    private static final ArrayDeque<String> tail = new ArrayDeque<>();

    private Mcp() {}

    public static void openNodePage() { Util.getPlatform().openUri(NODE_PAGE); }

    public static State state() { return state; }

    public static String node() { return node; }

    public static Job job() { return job; }

    public static boolean busy() { return job != Job.NONE; }

    public static String failure() { return failure.isEmpty() ? trouble : failure; }

    public static boolean agentLive() { return System.getProperties().get(LIVE) != null; }

    public static List<String> tail() {
        synchronized (tail) {
            List<String> all = List.copyOf(tail);
            return all.subList(Math.max(0, all.size() - KEEP), all.size());
        }
    }

    public static void look() {
        if (busy() || System.currentTimeMillis() - checkedAt < FRESH) return;
        start(Job.CHECK);
    }

    public static void install() { start(Job.INSTALL); }

    public static void connect() { start(Job.CONNECT); }

    public static void remove() { start(Job.REMOVE); }

    public static void modules() { start(Job.MODULES); }

    public static String startAgent(Path jar, String work, String token) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, Mcp.class.getClassLoader());
        try {
            Class<?> type = Class.forName(AGENT, true, loader);
            return (String) type.getMethod("start", String.class, String.class).invoke(null, work, token);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IOException(cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
    }

    private static synchronized void start(Job what) {
        if (busy()) return;
        job = what;
        if (what != Job.CHECK) {
            failure = "";
            synchronized (tail) {
                tail.clear();
            }
        }
        Thread worker = new Thread(() -> {
            try {
                work(what);
            } catch (Throwable e) {
                failure = String.valueOf(e.getMessage());
                XeroCode.LOG.warn("[xerocode] MCP: {}", e.toString());
            } finally {
                checkedAt = System.currentTimeMillis();
                job = Job.NONE;
            }
        }, "xerocode-mcp");
        worker.setDaemon(true);
        worker.start();
    }

    private static void work(Job what) throws Exception {
        searched = searchPath();
        if (what == Job.INSTALL) {
            if (run(installer(), INSTALL_WAIT, null) != 0) failure = reason("установка не прошла");
        } else if (what != Job.CHECK) {
            Path setup = setup();
            if (setup == null) throw new IOException("MCP не установлен");
            List<String> command = new ArrayList<>(List.of(nodeCommand(), setup.toString()));
            if (what == Job.REMOVE) command.add("--remove");
            if (what == Job.MODULES) command.add("--modules");
            if (run(command, what == Job.MODULES ? MODULES_WAIT : SETUP_WAIT, null) != 0) failure = last("не получилось");
        }
        check();
    }

    private static void check() throws Exception {
        StringBuilder said = new StringBuilder();
        node = run(List.of(nodeCommand(), "--version"), 20, said) == 0 ? said.toString().trim() : "";
        Path setup = setup();
        if (setup == null || node.isEmpty()) {
            state = null;
            trouble = "";
            return;
        }
        StringBuilder out = new StringBuilder();
        run(List.of(nodeCommand(), setup.toString(), "--dry", "--json"), CHECK_WAIT, out);
        JsonObject root = report(out.toString());
        if (root == null) {
            state = null;
            trouble = out.toString().lines().anyMatch(line -> line.strip().startsWith(BANNER)) ? STALE : BROKEN;
            return;
        }
        trouble = "";
        List<Tool> tools = new ArrayList<>();
        JsonArray list = Json.arr(root, "tools");
        if (list != null) for (JsonElement el : list) {
            JsonObject t = el.getAsJsonObject();
            tools.add(new Tool(Json.str(t, "id"), Json.str(t, "title"), Json.flag(t, "found"), Json.flag(t, "connected"),
                    Json.str(t, "note"), Json.str(t, "error")));
        }
        List<String> skills = new ArrayList<>();
        JsonArray dirs = Json.arr(root, "skills");
        if (dirs != null) for (JsonElement el : dirs) skills.add(el.getAsString());
        state = new State(Json.str(root, "version"), Json.str(root, "root"), List.copyOf(tools), List.copyOf(skills),
                Json.flag(root, "modules"));
    }

    private static JsonObject report(String text) {
        String[] lines = text.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = COLORS.matcher(lines[i]).replaceAll("").strip();
            if (!line.startsWith("{") || !line.endsWith("}")) continue;
            try {
                JsonElement got = JsonParser.parseString(line);
                if (got.isJsonObject() && got.getAsJsonObject().has("tools")) return got.getAsJsonObject();
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static String last(String fallback) {
        synchronized (tail) {
            return tail.isEmpty() ? fallback : tail.peekLast();
        }
    }

    private static String reason(String fallback) {
        synchronized (tail) {
            List<String> lines = List.copyOf(tail);
            int end = lines.lastIndexOf(GAVE_UP);
            if (end > 0) return lines.get(end - 1);
            return lines.isEmpty() || end == 0 ? fallback : lines.getLast();
        }
    }

    private static Path workspace() {
        String set = System.getenv("JUSTMC_WORKSPACE");
        return set != null && !set.isBlank() ? Path.of(set) : Path.of(System.getProperty("user.home"), ".justmc-mcp");
    }

    private static JsonObject installed() {
        Path file = workspace().resolve("install.json");
        try {
            return Files.isRegularFile(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Path setup() {
        JsonObject marker = installed();
        if (marker != null) {
            Path own = Path.of(Json.str(marker, "root"), "scripts", "setup.mjs");
            if (Files.isRegularFile(own)) return own;
        }
        Path app = workspace().resolve("app").resolve("scripts").resolve("setup.mjs");
        return Files.isRegularFile(app) ? app : null;
    }

    private static String nodeCommand() {
        JsonObject marker = installed();
        String exe = marker == null ? "" : Json.str(marker, "node");
        if (!exe.isEmpty() && Files.isRegularFile(Path.of(exe))) return exe;
        for (String dir : searched) {
            try {
                Path found = Path.of(dir, WINDOWS ? "node.exe" : "node");
                if (Files.isRegularFile(found)) return found.toString();
            } catch (InvalidPathException ignored) {
            }
        }
        return "node";
    }

    static List<String> searchPath() {
        Set<String> dirs = new LinkedHashSet<>();
        addAll(dirs, System.getenv("PATH"));
        String home = System.getProperty("user.home", "");
        if (WINDOWS) {
            addAll(dirs, freshWindowsPath());
            for (String root : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramW6432"), System.getenv("ProgramFiles(x86)")})
                if (root != null) dirs.add(Path.of(root, "nodejs").toString());
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                dirs.add(Path.of(local, "Programs", "nodejs").toString());
                dirs.add(Path.of(local, "Volta", "bin").toString());
            }
            String symlink = System.getenv("NVM_SYMLINK");
            if (symlink != null && !symlink.isBlank()) dirs.add(symlink);
            dirs.add(Path.of(home, "scoop", "apps", "nodejs", "current").toString());
            dirs.add(Path.of(home, "scoop", "shims").toString());
        } else {
            for (String dir : new String[]{"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin"}) dirs.add(dir);
            dirs.add(home + "/.volta/bin");
            dirs.add(home + "/.local/bin");
            String nvm = newestNvm(Path.of(home, ".nvm", "versions", "node"));
            if (nvm != null) dirs.add(nvm);
        }
        return List.copyOf(dirs);
    }

    private static void addAll(Set<String> dirs, String path) {
        if (path == null) return;
        for (String dir : path.split(WINDOWS ? ";" : ":")) {
            String clean = dir.strip().replace("\"", "");
            if (!clean.isEmpty()) dirs.add(clean);
        }
    }

    private static String freshWindowsPath() {
        StringBuilder out = new StringBuilder();
        try {
            run(List.of("powershell.exe", "-NoProfile", "-Command",
                    "[Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')"),
                    20, out, List.of());
        } catch (Exception e) {
            return "";
        }
        return out.toString().strip();
    }

    private static String newestNvm(Path versions) {
        try (Stream<Path> list = Files.list(versions)) {
            return list.filter(Files::isDirectory)
                    .max(Comparator.comparing(Mcp::versionKey))
                    .map(p -> p.resolve("bin").toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String versionKey(Path dir) {
        StringBuilder key = new StringBuilder();
        for (String part : dir.getFileName().toString().replaceFirst("^v", "").split("\\.")) {
            String digits = part.replaceAll("\\D", "");
            key.append(String.format(Locale.ROOT, "%08d", digits.isEmpty() ? 0 : Long.parseLong(digits.substring(0, Math.min(8, digits.length())))));
        }
        return key.toString();
    }

    private static List<String> installer() {
        return WINDOWS
                ? List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "irm " + RAW + "install.ps1 | iex")
                : List.of("/bin/sh", "-c", "curl -fsSL " + RAW + "install.sh | sh");
    }

    private static int run(List<String> command, long seconds, StringBuilder into) throws Exception {
        return run(command, seconds, into, searched);
    }

    private static int run(List<String> command, long seconds, StringBuilder into, List<String> path) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (!path.isEmpty()) builder.environment().put("PATH", String.join(WINDOWS ? ";" : ":", path));
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            if (into == null) throw e;
            return -1;
        }
        process.getOutputStream().close();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    String clean = COLORS.matcher(line).replaceAll("").strip();
                    if (into != null) into.append(line).append('\n');
                    else if (!clean.isEmpty()) {
                        synchronized (tail) {
                            tail.addLast(clean);
                            while (tail.size() > HISTORY) tail.removeFirst();
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }, "xerocode-mcp-out");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            reader.join(2000);
            throw new IOException("не дождался конца: " + String.join(" ", command.subList(0, Math.min(2, command.size()))));
        }
        reader.join(5000);
        return process.exitValue();
    }
}
