package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class Publish {
    private static final String KEY = "xc-0d0e58158f364a5719205d2b652b2651b9d5";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public enum State { UPLOADING, SENT, FAILED }

    private record Line(long at, String text) {}

    private static final java.util.ArrayDeque<Line> CHAT = new java.util.ArrayDeque<>();

    public static synchronized void noteChat(String text) {
        if (text == null || text.isBlank()) return;
        CHAT.addLast(new Line(System.currentTimeMillis(), text.replace('\n', ' ').trim()));
        while (CHAT.size() > 60) CHAT.removeFirst();
    }

    private static boolean looksLikeChatter(String text) {
        return text.startsWith("[!]") || text.startsWith("⏵") || text.startsWith("⏴")
                || text.contains(" приглашает поиграть");
    }

    public static synchronized List<String> answersSince(long since) {
        List<String> out = new ArrayList<>();
        for (Line line : CHAT)
            if (line.at() >= since && !looksLikeChatter(line.text())) out.add(line.text());
        return out;
    }

    public static final class Job {
        private final Minecraft client = Minecraft.getInstance();
        private final String body;
        private final boolean replace;
        private final long startedAt = System.currentTimeMillis();
        private Thread worker;

        public volatile State state = State.UPLOADING;
        public volatile String error = "";
        public volatile String url = "";
        public volatile String asked = "";
        public Path file;
        public long millis;
        public long sentAt;

        Job(JsonObject code, boolean replace, Path file) {
            this.body = code.toString();
            this.replace = replace;
            this.file = file;
        }

        public int bytes() { return body.getBytes(StandardCharsets.UTF_8).length; }

        void start() {
            worker = new Thread(() -> {
                try {
                    String link = upload(body);
                    synchronized (this) { url = link; }
                } catch (Throwable e) {
                    synchronized (this) {
                        error = Net.reason(e, STORE);
                        state = State.FAILED;
                    }
                }
            }, "xerocode-upload");
            worker.setDaemon(true);
            worker.start();
        }

        public synchronized void tick() {
            if (dropped || state != State.UPLOADING || url.isEmpty()) return;
            ClientPacketListener net = client.getConnection();
            if (net == null) {
                error = "нет соединения с сервером";
                state = State.FAILED;
                return;
            }
            String command = "module loadUrl " + (replace ? "force " : "") + url;
            sentAt = System.currentTimeMillis();
            net.sendCommand(command);
            millis = sentAt - startedAt;
            state = State.SENT;
            if (replace) awaiting = this;
        }

        void confirmed(String question) {
            asked = question;
            if (awaiting == this) awaiting = null;
        }

        public volatile boolean dropped;

        public void cancel() {
            dropped = true;
            if (worker != null) worker.interrupt();
        }
    }

    private static final Net.Who STORE = new Net.Who("не найден адрес хранилища",
            "не достучаться до хранилища", "хранилище не ответило за 10 с — похоже, мешает VPN или прокси");

    private static String upload(String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        IOException last = null;
        for (String where : new String[]{Endpoints.safe() + "upload.php?key=" + KEY,
                Endpoints.plain() + "upload.php?key=" + KEY}) {
            for (boolean plain : new boolean[]{false, true}) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("отменено");
                try {
                    String answer = plain ? viaUrlConnection(where, payload) : viaHttpClient(where, payload);
                    return Endpoints.plain() + "code/" + idOf(answer) + ".json";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("отменено");
                } catch (Exception e) {
                    last = e instanceof IOException io ? io : new IOException(e);
                }
            }
        }
        throw last == null ? new IOException("загрузка не удалась") : last;
    }

    private static String idOf(String answer) throws IOException {
        JsonObject json = JsonParser.parseString(answer).getAsJsonObject();
        if (!json.has("id")) throw new IOException("в ответе нет id: " + cut(answer));
        return json.get("id").getAsString();
    }

    private static String viaHttpClient(String where, byte[] payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(where))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .timeout(TIMEOUT)
                .build();
        HttpResponse<String> response =
                Net.client(TIMEOUT, true).send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IOException("HTTP " + response.statusCode() + ": " + cut(response.body()));
        return response.body();
    }

    private static String viaUrlConnection(String where, byte[] payload) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(where).toURL().openConnection();
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout((int) TIMEOUT.toMillis());
            c.setReadTimeout((int) TIMEOUT.toMillis());
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(payload);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String answer = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code != 200) throw new IOException("HTTP " + code + ": " + cut(answer));
            return answer;
        } finally {
            c.disconnect();
        }
    }

    private static String cut(String s) {
        String one = s == null ? "" : s.replace('\n', ' ').trim();
        return one.length() <= 80 ? one : one.substring(0, 80) + "…";
    }

    private static Path write(JsonObject code, String worldId) {
        try {
            Path dir = Codespace.savedDir();
            String stem = "export_" + worldId + "-"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            Path path = dir.resolve(stem + ".json");
            for (int i = 2; Files.exists(path) && i < 100; i++)
                path = dir.resolve(stem + "-" + i + ".json");
            Json.write(path, code);
            return path;
        } catch (IOException e) {
            XeroCode.LOG.error("[xerocode] не удалось записать экспорт файлом", e);
            return null;
        }
    }

    private static volatile Job current;

    public static Job start(JsonObject code, String worldId, boolean replace) {
        Job job = new Job(code, replace, write(code, worldId));
        current = job;
        job.start();
        return job;
    }

    private static final long CONFIRM_WINDOW = 30_000;

    private static volatile Job awaiting;

    static Job awaitingConfirm() {
        Job job = awaiting;
        if (job == null || job.dropped) return null;
        if (System.currentTimeMillis() - job.sentAt > CONFIRM_WINDOW) {
            awaiting = null;
            return null;
        }
        return job;
    }

    public static void tick() {
        Job job = current;
        if (job == null) return;
        job.tick();
        if (job.state != State.UPLOADING) current = null;
    }

    private Publish() {}
}
