package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
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
import java.util.Locale;

public final class Publish {
    private static final String HOST = "http://31.59.39.212:8791/jcode/";
    private static final String UPLOAD = HOST + "upload.php?key=jcode-garf";
    private static final String BASE = HOST + "code/";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
        private final MinecraftClient client = MinecraftClient.getInstance();
        private final String body;
        private final boolean replace;
        private final long startedAt = System.currentTimeMillis();
        private Thread worker;

        public volatile State state = State.UPLOADING;
        public volatile String error = "";
        public volatile String url = "";
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
                        error = reason(e);
                        state = State.FAILED;
                    }
                }
            }, "xerocode-upload");
            worker.setDaemon(true);
            worker.start();
        }

        public synchronized void tick() {
            if (state != State.UPLOADING || url.isEmpty()) return;
            ClientPlayNetworkHandler net = client.getNetworkHandler();
            if (net == null) {
                error = "нет соединения с сервером";
                state = State.FAILED;
                return;
            }
            String command = "module loadUrl " + (replace ? "force " : "") + url;
            sentAt = System.currentTimeMillis();
            net.sendChatCommand(command);
            millis = sentAt - startedAt;
            state = State.SENT;
        }

        public void cancel() {
            if (worker != null) worker.interrupt();
        }
    }

    private static String reason(Throwable e) {
        String message = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
        if (e instanceof java.net.http.HttpTimeoutException || e instanceof SocketTimeoutException)
            return "сервер не ответил за 30 с — похоже, мешает VPN или прокси";
        if (e instanceof java.net.UnknownHostException) return "не найден адрес хранилища";
        if (e instanceof java.net.ConnectException) return "не достучаться до хранилища";
        String low = message.toLowerCase(Locale.ROOT);
        if (low.contains("reset") || low.contains("end of file") || low.contains("handshake")
                || low.contains("eof"))
            return "связь оборвалась — так делают VPN, прокси и антивирус";
        return message.length() <= 90 ? message : message.substring(0, 90) + "…";
    }

    private static String upload(String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        IOException last = null;
        for (String way : new String[]{"HTTP/2", "HTTP/1.1", "HttpURLConnection"}) {
            try {
                String answer = switch (way) {
                    case "HTTP/2" -> viaHttpClient(payload, null);
                    case "HTTP/1.1" -> viaHttpClient(payload, HttpClient.Version.HTTP_1_1);
                    default -> viaUrlConnection(payload);
                };
                return BASE + idOf(answer) + ".json";
            } catch (Exception e) {
                last = e instanceof IOException io ? io : new IOException(e);
            }
        }
        throw last == null ? new IOException("загрузка не удалась") : last;
    }

    private static String idOf(String answer) throws IOException {
        JsonObject json = JsonParser.parseString(answer).getAsJsonObject();
        if (!json.has("id")) throw new IOException("в ответе нет id: " + cut(answer));
        return json.get("id").getAsString();
    }

    private static String viaHttpClient(byte[] payload, HttpClient.Version version)
            throws IOException, InterruptedException {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (version != null) builder.version(version);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPLOAD))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .timeout(TIMEOUT)
                .build();
        HttpResponse<String> response =
                builder.build().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IOException("HTTP " + response.statusCode() + ": " + cut(response.body()));
        return response.body();
    }

    private static String viaUrlConnection(byte[] payload) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(UPLOAD).toURL().openConnection();
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
            Files.createDirectories(dir);
            String stem = "export_" + worldId + "-"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            Path path = dir.resolve(stem + ".json");
            for (int i = 2; Files.exists(path) && i < 100; i++)
                path = dir.resolve(stem + "-" + i + ".json");
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                w.write(code.toString());
            }
            return path;
        } catch (IOException e) {
            XeroCode.LOG.error("[xerocode] не удалось записать экспорт файлом", e);
            return null;
        }
    }

    public static Job start(JsonObject code, String worldId, boolean replace) {
        Job job = new Job(code, replace, write(code, worldId));
        job.start();
        return job;
    }

    private Publish() {}
}
