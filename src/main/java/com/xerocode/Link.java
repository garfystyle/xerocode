package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class Link {
    public static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;

        Refused(int status, String why) {
            super(why, null, false, false);
            this.status = status;
        }
    }

    private static final int MAX_BODY = 16 * 1024 * 1024;
    private static final int MAX_LINE = 8192;
    private static final int READ_TIMEOUT = 15_000;
    private static final long SEND_WAIT = 40_000;
    private static final long ANSWER_WAIT = 90_000;
    private static final long ANSWER_SETTLE = 400;
    private static final long READ_WAIT = 600_000;
    private static final long LINE_GAP = 1_500;
    private static final long LINES_WAIT = 300_000;
    private static final long CONNECT_WAIT = 30;
    private static final long AGENT_WAIT = 20;
    private static final int LINES_PER_FLOOR = (Codespace.LAST_Z - Codespace.FIRST_Z) / Codespace.LINE_STEP + 1;

    private static volatile ServerSocket socket;
    private static volatile String token;
    private static volatile Codespace.Scan scan;
    private static volatile CompletableFuture<JsonArray> reading;

    private record Line(int position, JsonObject fresh, JsonObject was) {}

    private static final ArrayDeque<Line> queue = new ArrayDeque<>();
    private static volatile CompletableFuture<JsonArray> placing;
    private static JsonArray placed;
    private static Line active;
    private static long nextAt;

    public static Path file() {
        return Path.of(System.getProperty("user.home"), ".xerocode", "link.json");
    }

    public static synchronized void start() {
        if (socket != null) return;
        try {
            byte[] secret = new byte[16];
            new SecureRandom().nextBytes(secret);
            String made = HexFormat.of().formatHex(secret);
            ServerSocket opened = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            JsonObject o = new JsonObject();
            o.addProperty("port", opened.getLocalPort());
            o.addProperty("token", made);
            o.addProperty("version", Swap.version());
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o + "\n", StandardCharsets.UTF_8);
            token = made;
            socket = opened;
            Thread accept = new Thread(() -> accept(opened), "xerocode-link");
            accept.setDaemon(true);
            accept.start();
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] связь с редактором не поднялась", e);
        }
    }

    public static synchronized void stop() {
        ServerSocket opened = socket;
        socket = null;
        if (opened == null) return;
        try {
            opened.close();
        } catch (IOException ignored) {
        }
        try {
            String said = Files.readString(file(), StandardCharsets.UTF_8);
            if (said.contains(token)) Files.deleteIfExists(file());
        } catch (IOException ignored) {
        }
    }

    private static void placeNext() {
        CompletableFuture<JsonArray> asked = placing;
        if (asked == null || LinePut.busy()) return;
        if (active != null) {
            report(active, !LinePut.lastBad(), LinePut.lastSaid());
            active = null;
            nextAt = System.currentTimeMillis() + LINE_GAP;
        }
        if (System.currentTimeMillis() < nextAt) return;
        Line next = queue.poll();
        if (next == null) {
            placing = null;
            asked.complete(placed);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LineEdit.Where at = where(mc.level, next.position());
        String refused = at == null ? "на этом месте строки быть не может" : LinePut.start(mc, at, next.fresh(), next.was());
        if (refused == null) active = next;
        else report(next, false, refused);
    }

    private static void report(Line line, boolean ok, String said) {
        JsonObject o = new JsonObject();
        o.addProperty("position", line.position());
        o.addProperty("ok", ok);
        o.addProperty("said", said);
        placed.add(o);
    }

    private static LineEdit.Where where(ClientLevel level, int position) {
        if (level == null || position < 0) return null;
        int floor = position / LINES_PER_FLOOR;
        int y = Codespace.FIRST_Y + floor * Codespace.FLOOR_H;
        int z = Codespace.FIRST_Z + position % LINES_PER_FLOOR * Codespace.LINE_STEP;
        if (level.getBlockState(new BlockPos(Codespace.LINE_X, y - 1, Codespace.LINE_X)).isAir()) return null;
        return LineEdit.where(new BlockPos(Codespace.LINE_X, y, z));
    }

    public static void tick() {
        placeNext();
        Codespace.Scan running = scan;
        CompletableFuture<JsonArray> asked = reading;
        if (running == null || asked == null) return;
        running.tick();
        if (running.state == Codespace.State.RUNNING) return;
        scan = null;
        reading = null;
        if (running.state == Codespace.State.DONE) asked.complete(running.handlers());
        else asked.completeExceptionally(new Refused(409, running.error.isEmpty() ? "чтение мира прервано" : running.error));
    }

    private static void accept(ServerSocket opened) {
        while (socket == opened) {
            try {
                Socket client = opened.accept();
                Thread serve = new Thread(() -> serve(client), "xerocode-link-request");
                serve.setDaemon(true);
                serve.start();
            } catch (IOException e) {
                if (socket == opened) XeroCode.LOG.warn("[xerocode] связь с редактором оборвалась", e);
            }
        }
    }

    private record Request(String method, String path, String query, Map<String, String> headers, byte[] body) {}

    private static void serve(Socket client) {
        try (client) {
            client.setSoTimeout(READ_TIMEOUT);
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            int status = 200;
            String body;
            try {
                body = route(read(in)).toString();
            } catch (Refused r) {
                status = r.status;
                body = failure(r.getMessage());
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] запрос редактора не выполнен", e);
                status = 500;
                body = failure(String.valueOf(e.getMessage()));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static String failure(String why) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", why);
        return o.toString();
    }

    private static Request read(InputStream in) throws IOException {
        String[] head = line(in).split(" ");
        if (head.length < 2) throw new Refused(400, "непонятный запрос");
        Map<String, String> headers = new HashMap<>();
        for (String h = line(in); !h.isEmpty(); h = line(in)) {
            int colon = h.indexOf(':');
            if (colon > 0) headers.put(h.substring(0, colon).trim().toLowerCase(Locale.ROOT), h.substring(colon + 1).trim());
        }
        if (headers.containsKey("origin")) throw new Refused(403, "из браузера нельзя");
        String sent = headers.get("x-xero-token");
        if (sent == null || !MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new Refused(403, "неверный ключ связи");
        int length;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            throw new Refused(400, "непонятная длина");
        }
        if (length < 0 || length > MAX_BODY) throw new Refused(413, "слишком большой код");
        byte[] body = in.readNBytes(length);
        if (body.length != length) throw new Refused(400, "запрос оборвался");
        int question = head[1].indexOf('?');
        return new Request(head[0], question < 0 ? head[1] : head[1].substring(0, question),
                question < 0 ? "" : head[1].substring(question + 1), headers, body);
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0 || b == '\n') break;
            if (b != '\r') bytes.write(b);
            if (bytes.size() > MAX_LINE) throw new Refused(400, "слишком длинная строка");
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static JsonObject route(Request r) throws Exception {
        String key = r.method() + " " + r.path();
        return switch (key) {
            case "GET /status" -> status();
            case "POST /upload" -> upload(r);
            case "GET /world" -> world();
            case "POST /lines" -> lines(r);
            case "POST /connect" -> connect(r);
            case "GET /log" -> log(r);
            case "POST /agent" -> agent(r);
            default -> throw new Refused(404, "нет такого запроса: " + key);
        };
    }

    private static <T> T onClient(Supplier<T> work) throws Exception {
        return onClient(work, 5);
    }

    private static <T> T onClient(Supplier<T> work, long seconds) throws Exception {
        CompletableFuture<T> out = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                out.complete(work.get());
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        try {
            return out.get(seconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Refused refused) throw refused;
            throw e;
        }
    }

    private static JsonObject agent(Request r) throws Exception {
        if (!Settings.get().mcpAgent) throw new Refused(403, "в настройках XeroCode нейросети запрещено управлять игрой");
        JsonObject ask = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        String jar = Json.str(ask, "jar"), work = Json.str(ask, "work"), key = Json.str(ask, "token");
        if (jar.isEmpty() || !Files.isRegularFile(Path.of(jar))) throw new Refused(400, "нет файла агента: " + jar);
        if (work.isEmpty() || key.isEmpty()) throw new Refused(400, "нет папки или ключа агента");
        String started = onClient(() -> {
            try {
                return Mcp.startAgent(Path.of(jar), work, key);
            } catch (Exception e) {
                throw new Refused(500, "агент не запустился: " + e.getMessage());
            }
        }, AGENT_WAIT);
        JsonObject o = JsonParser.parseString(started).getAsJsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject status() throws Exception {
        return onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("version", Swap.version());
            o.addProperty("online", mc.getConnection() != null);
            o.addProperty("dev", Codespace.inDev(level));
            if (level != null && Codespace.inDev(level)) o.addProperty("world", Codespace.worldId(level));
            return o;
        });
    }

    private static ClientLevel dev() {
        ClientLevel level = Minecraft.getInstance().level;
        if (!Codespace.inDev(level)) throw new Refused(409, "игрок не в /dev своего мира");
        return level;
    }

    private static JsonObject upload(Request r) throws Exception {
        boolean replace = !r.query().contains("mode=add");
        JsonObject code = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!code.has("handlers")) throw new Refused(400, "в теле нет кода");
        Publish.Job job = onClient(() -> Publish.start(code, Codespace.worldId(dev()), replace));
        long until = System.currentTimeMillis() + SEND_WAIT;
        while (job.state == Publish.State.UPLOADING && System.currentTimeMillis() < until) Thread.sleep(50);
        if (job.state == Publish.State.FAILED) throw new Refused(502, job.error);
        if (job.state != Publish.State.SENT) {
            job.cancel();
            throw new Refused(504, "код не успел залиться");
        }
        List<String> answers = answers(job);
        if (job.state == Publish.State.FAILED) throw new Refused(502, job.error);
        if (job.answered() && !job.worked()) throw new Refused(502, answers.getLast());
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("done", job.worked());
        o.addProperty("url", job.url);
        o.addProperty("confirmed", !job.asked.isEmpty());
        if (!job.note.isEmpty()) o.addProperty("note", job.note);
        JsonArray said = new JsonArray();
        for (String a : answers) said.add(a);
        o.add("answers", said);
        return o;
    }

    private static List<String> answers(Publish.Job job) throws InterruptedException {
        long until = System.currentTimeMillis() + ANSWER_WAIT;
        while (System.currentTimeMillis() < until && !job.answered()) Thread.sleep(50);
        Thread.sleep(ANSWER_SETTLE);
        return Publish.answersSince(job.firstSentAt);
    }

    private static JsonObject lines(Request r) throws Exception {
        JsonArray rows = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("lines");
        if (rows == null) throw new Refused(400, "в теле нет строк");
        ArrayDeque<Line> wanted = new ArrayDeque<>();
        for (var row : rows) {
            JsonObject o = row.getAsJsonObject();
            wanted.add(new Line(o.get("position").getAsInt(), o.getAsJsonObject("handler"), o.has("was") ? o.getAsJsonObject("was") : null));
        }
        CompletableFuture<JsonArray> asked = new CompletableFuture<>();
        onClient(() -> {
            if (placing != null) throw new Refused(409, "строки уже ставятся");
            dev();
            queue.clear();
            queue.addAll(wanted);
            placed = new JsonArray();
            active = null;
            nextAt = 0;
            placing = asked;
            return true;
        });
        JsonArray done;
        try {
            done = asked.get(LINES_WAIT, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            Minecraft.getInstance().execute(() -> {
                queue.clear();
                placing = null;
            });
            throw new Refused(504, "строки не успели встать");
        }
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.add("lines", done);
        return o;
    }

    private static JsonObject connect(Request r) throws Exception {
        JsonObject entry = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        Modules.Module module = Modules.Module.of(entry);
        if (module == null) throw new Refused(400, "непонятный модуль");
        CompletableFuture<String> answer = new CompletableFuture<>();
        onClient(() -> {
            dev();
            ModuleActions.install(module, null, (ok, said) -> {
                if (ok) answer.complete(said);
                else answer.completeExceptionally(new Refused(409, said));
            });
            return true;
        });
        try {
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("said", answer.get(CONNECT_WAIT, TimeUnit.SECONDS));
            return o;
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Refused refused) throw refused;
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            throw new Refused(504, "меню /module не ответило");
        }
    }

    private static JsonObject log(Request r) {
        long since = 0;
        for (String part : r.query().split("&"))
            if (part.startsWith("since=")) {
                try {
                    since = Long.parseLong(part.substring("since=".length()));
                } catch (NumberFormatException e) {
                    throw new Refused(400, "непонятное время");
                }
            }
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("now", System.currentTimeMillis());
        JsonArray lines = new JsonArray();
        for (String line : Publish.answersSince(since)) lines.add(line);
        o.add("lines", lines);
        return o;
    }

    private static JsonObject world() throws Exception {
        CompletableFuture<JsonArray> asked = new CompletableFuture<>();
        onClient(() -> {
            if (reading != null) throw new Refused(409, "мир уже читается");
            ClientLevel level = dev();
            if (!Codespace.chunksReady(level)) throw new Refused(409, "строки мира ещё не загрузились — подойди к ним в /dev");
            scan = Codespace.start(level, Codespace.lines(level), Codespace.Memo.fresh(Codespace.worldId(level), null));
            reading = asked;
            return true;
        });
        JsonArray handlers;
        try {
            handlers = asked.get(READ_WAIT, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Refused refused) throw refused;
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            Minecraft.getInstance().execute(() -> {
                Codespace.Scan running = scan;
                if (running != null) running.cancel();
                scan = null;
                reading = null;
            });
            throw new Refused(504, "мир не прочитался за 10 минут");
        }
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.add("handlers", handlers);
        return o;
    }

    private Link() {}
}
