package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionStage;

public final class CollabNet {
    private static final Duration CONNECT = Duration.ofSeconds(15);
    private static final int MAX_MESSAGE = 4 << 20;

    private static final Net.Who WHO = new Net.Who("не найден адрес сервера", "сервер не отвечает",
            "сервер не ответил вовремя");

    private static final Executor POOL =
            Executors.newCachedThreadPool(Net.threads("xerocode-collab"));

    private final Queue<JsonObject> inbox = new ConcurrentLinkedQueue<>();
    private final StringBuilder pending = new StringBuilder();
    private final Object sending = new Object();

    private volatile HttpClient client;
    private volatile WebSocket socket;
    private volatile boolean open;
    private volatile boolean dead;
    private volatile String why = "";
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    public boolean open() { return open && !dead; }
    public boolean dead() { return dead; }
    public String why() { return why; }
    public JsonObject take() { return inbox.poll(); }

    public void connect(String url) {
        client = HttpClient.newBuilder()
                .executor(POOL)
                .connectTimeout(CONNECT)
                .build();
        client.newWebSocketBuilder()
                .connectTimeout(CONNECT)
                .buildAsync(URI.create(url), new Ears())
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        fail(reason(error));
                        return;
                    }
                    socket = ws;
                    open = true;
                });
    }

    public void send(JsonObject message) {
        WebSocket ws = socket;
        if (ws == null || dead) return;
        String text = message.toString();
        synchronized (sending) {
            tail = tail.thenCompose(ignored -> ws.sendText(text, true))
                    .exceptionally(error -> {
                        fail(reason(error));
                        return null;
                    });
        }
    }

    public void close() {
        WebSocket ws = socket;
        open = false;
        dead = true;
        socket = null;
        if (ws == null) return;
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "пока").orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .exceptionally(error -> null);
        } catch (Throwable ignored) {
        }
        try {
            ws.abort();
        } catch (Throwable ignored) {
        }
    }

    private void fail(String reason) {
        if (dead) return;
        why = reason;
        dead = true;
        open = false;
    }

    private static String reason(Throwable error) { return Net.reason(error, WHO); }

    private final class Ears implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            socket = ws;
            open = true;
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (pending.length() + data.length() > MAX_MESSAGE) {
                pending.setLength(0);
                fail("сервер прислал слишком много");
                return null;
            }
            pending.append(data);
            if (last) {
                String whole = pending.toString();
                pending.setLength(0);
                try {
                    JsonObject json = JsonParser.parseString(whole).getAsJsonObject();
                    if (inbox.size() < 4096) inbox.add(json);
                } catch (Throwable ignored) {
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            fail(reason == null || reason.isBlank() ? "связь закрыта" : reason);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            fail(reason(error));
        }
    }
}
