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
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class Publish {
    private static final String KEY = "xc-0d0e58158f364a5719205d2b652b2651b9d5";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String OFFICIAL = "https://m.justmc.ru/api/";
    private static final Pattern OFFICIAL_ID = Pattern.compile("[0-9A-Za-z_-]{4,64}");
    private static final Duration OFFICIAL_WAIT = Duration.ofSeconds(60);
    private static final long RESCUE_WINDOW = 120_000;
    private static final long RESEND_GAP = 2_000;
    private static final String[] TIMED_OUT = {"не ответил на запрос импортирования",
            "did not respond to the import request", "не відповів на запит імпортування",
            "не найден или недоступен", "not found or not available"};
    private static final String[] COOLDOWN = {"Подождите перед установкой", "wait before reinstalling", "Зачекайте перед"};
    private static final long COOLDOWN_GAP = 5_000;
    private static final int COOLDOWN_TRIES = 4;
    private static final String[] WORKED = {"успешно импортирован", "imported successfully", "модуль загружен"};
    private static final String[] BROKE = {"поврежд", "is corrupted", "ответил ошибкой", "returned an error",
            "відповів помилкою", "Не хватает места", "not enough space", "приватн", "is private", "є приватним",
            "не может быть размером", "cannot be larger", "Подождите перед установкой", "wait before reinstalling",
            "Зачекайте перед"};
    private static final String RESCUING = "JustMC не успел скачать код из хранилища — заливаю на m.justmc.ru";
    private static final String RESCUED = "JustMC не успел скачать код из хранилища — повторил через m.justmc.ru";

    public enum State { UPLOADING, SENT, FAILED }

    private record Line(long at, String text) {}

    private static final java.util.ArrayDeque<Line> CHAT = new java.util.ArrayDeque<>();

    public static void noteChat(String text) {
        if (text == null || text.isBlank()) return;
        Job job = lastSent;
        if (job != null && timedOut(text)) job.rescue();
        if (job != null && cooldown(text)) job.again();
        synchronized (Publish.class) {
            CHAT.addLast(new Line(System.currentTimeMillis(), text.replace('\n', ' ').trim()));
            while (CHAT.size() > 60) CHAT.removeFirst();
        }
    }

    private static boolean cooldown(String text) {
        for (String marker : COOLDOWN) if (text.contains(marker)) return true;
        return false;
    }

    private static int verdict(String line) {
        for (String word : WORKED) if (line.contains(word)) return 1;
        for (String word : BROKE) if (line.contains(word)) return -1;
        return timedOut(line) ? -1 : 0;
    }

    private static boolean timedOut(String text) {
        for (String marker : TIMED_OUT) if (text.contains(marker)) return true;
        return false;
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
        public volatile String note = "";
        public Path file;
        public long millis;
        public volatile long sentAt, firstSentAt;
        private boolean rescued;
        private int waits;
        private long notBefore;

        Job(JsonObject code, boolean replace, Path file) {
            this.body = code.toString();
            this.replace = replace;
            this.file = file;
        }

        public int bytes() { return body.getBytes(StandardCharsets.UTF_8).length; }

        void start() {
            worker = new Thread(() -> {
                try {
                    String link = Settings.uploadToJustmc() ? viaJustmc() : upload(body);
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

        private String viaJustmc() throws IOException, InterruptedException {
            try {
                return official(body);
            } catch (IOException e) {
                String why = Net.reason(e, OFFICIAL_WHO);
                try {
                    String link = upload(body);
                    note = why + " — залил в хранилище XeroCode";
                    return link;
                } catch (IOException own) {
                    note = why;
                    throw own;
                }
            }
        }

        public synchronized void tick() {
            if (dropped || state != State.UPLOADING || url.isEmpty()) return;
            if (System.currentTimeMillis() - sentAt < RESEND_GAP || System.currentTimeMillis() < notBefore) return;
            ClientPacketListener net = client.getConnection();
            if (net == null) {
                error = "нет соединения с сервером";
                state = State.FAILED;
                return;
            }
            String command = "module loadUrl " + (replace ? "force " : "") + url;
            sentAt = System.currentTimeMillis();
            if (firstSentAt == 0) firstSentAt = sentAt;
            net.sendCommand(command);
            if (millis == 0) millis = sentAt - startedAt;
            state = State.SENT;
            lastSent = this;
            if (replace) awaiting = this;
        }

        synchronized void again() {
            if (waits >= COOLDOWN_TRIES || dropped || state != State.SENT) return;
            waits++;
            notBefore = System.currentTimeMillis() + COOLDOWN_GAP;
            state = State.UPLOADING;
            current = this;
        }

        synchronized void rescue() {
            if (rescued || dropped || state != State.SENT || url.startsWith(OFFICIAL)
                    || System.currentTimeMillis() - sentAt > RESCUE_WINDOW) return;
            rescued = true;
            state = State.UPLOADING;
            url = "";
            note = RESCUING;
            current = this;
            worker = new Thread(() -> {
                try {
                    String link = official(body);
                    synchronized (this) {
                        url = link;
                        note = RESCUED;
                    }
                } catch (Throwable e) {
                    synchronized (this) {
                        error = Net.reason(e, OFFICIAL_WHO);
                        state = State.FAILED;
                    }
                }
            }, "xerocode-upload-official");
            worker.setDaemon(true);
            worker.start();
        }

        private int verdict() {
            int out = 0;
            for (String line : answersSince(sentAt)) {
                int v = Publish.verdict(line);
                if (v != 0) out = v;
            }
            return out;
        }

        public boolean answered() {
            return state == State.FAILED || state == State.SENT && verdict() != 0;
        }

        public boolean worked() {
            return state == State.SENT && verdict() > 0;
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
    private static final Net.Who OFFICIAL_WHO = new Net.Who("m.justmc.ru не найден — проверь интернет",
            "m.justmc.ru не отвечает", "m.justmc.ru не ответил за минуту");

    private static String official(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(OFFICIAL + "upload"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "XeroCode")
                .timeout(OFFICIAL_WAIT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = Net.client(OFFICIAL_WAIT, true)
                .send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("m.justmc.ru ответил " + response.statusCode());
        }
        if (json.has("error"))
            throw new IOException("m.justmc.ru не принял код: " + cut(json.get("error").toString()));
        String id = json.has("id") && json.get("id").isJsonPrimitive() ? json.get("id").getAsString() : "";
        if (response.statusCode() != 200 || !OFFICIAL_ID.matcher(id).matches())
            throw new IOException("m.justmc.ru ответил " + response.statusCode());
        return OFFICIAL + id;
    }

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

    private static volatile Job current, lastSent;

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
