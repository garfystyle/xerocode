package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MarketNet {
    public static final String HOST = "https://109-120-178-103.sslip.io:8793";
    public static final String BASE = HOST + "/market/v1/";
    private static final String KEY = "xm-9632d0a284e31d0efac55927aa109975fd0f";

    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_ANSWER = 4 << 20;

    private static final Net.Who WHO = new Net.Who("не найден адрес магазина", "магазин не отвечает",
            "магазин не ответил за 25 с — похоже, мешает VPN или прокси");

    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(6, Net.threads("xerocode-market"));

    private static final ConcurrentLinkedQueue<Runnable> DONE = new ConcurrentLinkedQueue<>();

    private static volatile HttpClient client;

    private static HttpClient client() {
        HttpClient made = client;
        if (made != null) return made;
        synchronized (MarketNet.class) {
            if (client == null) client = Net.client(TIMEOUT, false);
            return client;
        }
    }

    public static void pump() {
        for (Runnable done = DONE.poll(); done != null; done = DONE.poll()) {
            try {
                done.run();
            } catch (Throwable e) {
                XeroCode.LOG.error("[xerocode] ответ магазина не разобрался", e);
            }
        }
    }

    private static void later(Runnable what) { DONE.add(what); }

    public interface Fail { void apply(String message, String code); }

    public static void call(String op, JsonObject body, boolean signed,
                            Consumer<JsonObject> ok, Fail bad) {
        byte[] payload = (body == null ? new JsonObject() : body)
                .toString().getBytes(StandardCharsets.UTF_8);
        send(op, payload, signed, "application/json", null, ok, bad);
    }

    public static void image(byte[] png, String kind, Consumer<JsonObject> ok, Fail bad) {
        send("image", png, true, "image/png", kind, ok, bad);
    }

    private static void send(String op, byte[] payload, boolean signed, String type, String kind,
                             Consumer<JsonObject> ok, Fail bad) {
        POOL.execute(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + op))
                        .header("Content-Type", type)
                        .header("X-Key", KEY)
                        .timeout(TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
                if (kind != null) builder.header("X-Kind", kind);
                if (signed) {
                    Map<String, String> signature = MarketId.headers(op, payload);
                    if (signature.isEmpty()) {
                        later(() -> bad.apply("нет ключа аккаунта", "no_key"));
                        return;
                    }
                    signature.forEach(builder::header);
                }
                HttpResponse<String> answer = client()
                        .send(builder.build(), HttpResponse.BodyHandlers.ofString());
                String text = answer.body();
                if (text != null && text.length() > MAX_ANSWER) {
                    later(() -> bad.apply("сервер прислал слишком много", "big"));
                    return;
                }
                JsonObject json;
                try {
                    json = JsonParser.parseString(text).getAsJsonObject();
                } catch (Throwable e) {
                    later(() -> bad.apply("магазин ответил непонятно", "shape"));
                    return;
                }
                if (json.has("ok") && json.get("ok").getAsBoolean()) {
                    later(() -> ok.accept(json));
                    return;
                }
                String code = json.has("error") ? json.get("error").getAsString() : "нет";
                String said = json.has("message") ? json.get("message").getAsString()
                        : "магазин отказал (" + answer.statusCode() + ")";
                later(() -> bad.apply(said, code));
            } catch (Throwable e) {
                String said = reason(e);
                later(() -> bad.apply(said, "net"));
            }
        });
    }

    public static byte[] fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("X-Key", KEY)
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> answer = client()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (answer.statusCode() != 200)
            throw new java.io.IOException("HTTP " + answer.statusCode());
        return answer.body();
    }

    public static String reason(Throwable e) { return Net.reason(e, WHO); }

    public static void run(Runnable work) { POOL.execute(work); }

    public static void back(Runnable what) { later(what); }

    private MarketNet() {}
}
