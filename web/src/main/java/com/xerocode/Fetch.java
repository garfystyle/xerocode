package com.xerocode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class Fetch {
    public record Got(int status, byte[] body, boolean viaStore) {}

    public static Got code(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(Endpoints.safe() + "justmc?u="
                        + URLEncoder.encode(url, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(150)).GET().build();
        HttpResponse<byte[]> answer = Net.client(Duration.ofSeconds(10), true)
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new Got(answer.statusCode(), answer.body(), true);
    }

    private Fetch() {}
}
