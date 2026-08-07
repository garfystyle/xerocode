package com.xerocode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Net {
    public record Who(String address, String silent, String slow) {}

    public static ThreadFactory threads(String name) {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public static HttpClient client(Duration timeout, boolean follow) {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(follow ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
                .build();
    }

    public static String reason(Throwable error, Who who) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getMessage() == null) cause = cause.getCause();
        String text = cause.getMessage();
        if (text == null || text.isBlank()) text = cause.getClass().getSimpleName();
        if (cause instanceof java.net.UnknownHostException) return who.address();
        if (cause instanceof java.net.ConnectException) return who.silent();
        if (cause instanceof java.net.http.HttpTimeoutException) return who.slow();
        String low = text.toLowerCase(Locale.ROOT);
        if (low.contains("timed out") || low.contains("timeout")) return who.slow();
        if (low.contains("handshake") || low.contains("certificate") || low.contains("ssl")
                || low.contains("reset") || low.contains("eof"))
            return "связь оборвалась — так делают VPN, прокси и антивирус";
        return text.length() <= 70 ? text : text.substring(0, 70) + "…";
    }

    private Net() {}
}
