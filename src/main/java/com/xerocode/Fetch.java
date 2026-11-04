package com.xerocode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

public final class Fetch {
    public record Got(int status, byte[] body, boolean viaStore) {}

    private static final Duration DIRECT = Duration.ofSeconds(20);
    private static final Duration STORE = Duration.ofSeconds(150);
    private static final int MAX = 64 << 20;

    public static Got code(String url) throws Exception {
        try {
            return direct(url);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception direct) {
            XeroCode.LOG.info("[xerocode] код по ссылке не пришёл напрямую ({}), беру через хранилище",
                    direct.toString());
            try {
                return store(url);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception viaStore) {
                XeroCode.LOG.warn("[xerocode] и через хранилище не вышло", viaStore);
                throw direct;
            }
        }
    }

    private static Got direct(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(DIRECT)
                .header("User-Agent", "XeroCode").GET().build();
        HttpResponse<byte[]> answer = within(Net.client(DIRECT, true)
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()), DIRECT);
        return new Got(answer.statusCode(), answer.body(), false);
    }

    private static Got store(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(Endpoints.safe() + "justmc?u="
                        + URLEncoder.encode(url, StandardCharsets.UTF_8)))
                .timeout(STORE).header("Accept-Encoding", "gzip").GET().build();
        HttpResponse<byte[]> answer = within(Net.client(STORE, true)
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()), STORE);
        boolean packed = answer.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip");
        return new Got(answer.statusCode(), packed ? unpacked(answer.body()) : answer.body(), true);
    }

    private static <T> HttpResponse<T> within(CompletableFuture<HttpResponse<T>> pending, Duration limit)
            throws Exception {
        try {
            return pending.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new HttpTimeoutException("ответ шёл дольше " + limit.toSeconds() + " с");
        } catch (InterruptedException e) {
            pending.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private static byte[] unpacked(byte[] body) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            byte[] out = in.readNBytes(MAX + 1);
            if (out.length > MAX) throw new IOException("код больше " + (MAX >> 20) + " МБ");
            return out;
        }
    }

    private Fetch() {}
}
