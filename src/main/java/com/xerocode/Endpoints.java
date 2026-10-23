package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.minecraft.client.Minecraft;

public final class Endpoints {
    private static final String REMOTE =
            "https://raw.githubusercontent.com/garfystyle/xerocode/HEAD/endpoints.json";
    private static final String SAFE = "https://2-27-249-33.sslip.io/xerocode/";
    private static final String PLAIN = "http://2.27.249.33:8791/xerocode/";
    private static final String RELAY = "wss://2-27-249-33.sslip.io:8792/xerocode/collab";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BYTES = 4096;

    private static volatile String safe = SAFE, plain = PLAIN, relay = RELAY;

    private Endpoints() {}

    public static String safe() { return safe; }

    public static String plain() { return plain; }

    public static String relay() { return relay; }

    static void load() {
        apply(read(cacheFile()));
        apply(read(localFile()));
        Net.threads("xerocode-endpoints").newThread(Endpoints::refresh).start();
    }

    private static void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(REMOTE))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "xerocode")
                    .GET()
                    .build();
            HttpResponse<String> answer = Net.client(TIMEOUT, true)
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (answer.statusCode() != 200 || answer.body().length() > MAX_BYTES) return;
            JsonObject remote = JsonParser.parseString(answer.body()).getAsJsonObject();
            if (!apply(remote)) return;
            apply(read(localFile()));
            Json.write(cacheFile(), remote);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] адреса серверов не обновились: {}", e.toString());
        }
    }

    private static boolean apply(JsonObject o) {
        if (o == null) return false;
        boolean any = false;
        String s = Json.str(o, "safe"), p = Json.str(o, "plain"), r = Json.str(o, "relay");
        if (s.startsWith("https://") && s.endsWith("/")) { safe = s; any = true; }
        if (p.startsWith("http://") && p.endsWith("/")) { plain = p; any = true; }
        if (r.startsWith("wss://")) { relay = r; any = true; }
        return any;
    }

    private static JsonObject read(Path file) {
        try {
            return Files.exists(file) ? Json.read(file) : null;
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] {} не читается", file.getFileName());
            return null;
        }
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode");
    }

    private static Path cacheFile() { return dir().resolve("endpoints-cache.json"); }

    private static Path localFile() { return dir().resolve("endpoints.json"); }
}
