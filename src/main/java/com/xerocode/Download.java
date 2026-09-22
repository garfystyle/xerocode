package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.tree.CommandNode;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class Download {
    public enum State { WAITING, FETCHING, DONE, FAILED }

    private static final String COMMAND = "editor download";
    private static final String HOST = "api.creative.justmc.io";
    private static final int WAIT_TICKS = 200;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String[] REFUSED = {
            "недостаточно прав", "нет прав", "нет доступа", "meteor", "метеор",
            "подождите", "недоступно"};

    private static final ExecutorService POOL =
            Executors.newSingleThreadExecutor(Net.threads("xerocode-download"));

    private static final Net.Who WHO = new Net.Who(
            "сервер кода JustMC не найден — проверь интернет",
            "сервер кода JustMC молчит",
            "сервер кода JustMC не ответил вовремя");

    private static volatile Download current;

    public volatile State state = State.WAITING;
    public volatile String error = "";
    public volatile JsonArray handlers;
    private int timer = WAIT_TICKS;

    private Download() {}

    public static Download start(Minecraft client) {
        ClientPacketListener net = client == null ? null : client.getConnection();
        if (net == null || !offered(net)) return null;
        Download asked = new Download();
        current = asked;
        net.sendCommand(COMMAND);
        return asked;
    }

    private static boolean offered(ClientPacketListener net) {
        CommandNode<?> editor = net.getCommands().getRoot().getChild("editor");
        return editor != null && editor.getChild("download") != null;
    }

    public void tick() {
        if (state != State.WAITING) return;
        if (--timer > 0) return;
        fail("сервер не прислал ссылку");
    }

    public void cancel() {
        if (current == this) current = null;
        if (state == State.WAITING || state == State.FETCHING) state = State.FAILED;
    }

    public static boolean heard(Component message) {
        Download asked = current;
        if (asked == null || asked.state != State.WAITING) return false;
        URI link = link(message);
        if (link != null) {
            asked.fetch(link);
            return true;
        }
        if (refusal(message.getString())) asked.fail("сервер отказал: " + message.getString());
        return false;
    }

    private static boolean refusal(String said) {
        String text = said.toLowerCase(Locale.ROOT);
        for (String no : REFUSED) if (text.contains(no)) return true;
        return false;
    }

    private static URI link(Component message) {
        Style style = message.getStyle();
        if (style != null && style.getClickEvent() instanceof ClickEvent.OpenUrl open
                && HOST.equals(open.uri().getHost())) return open.uri();
        for (Component part : message.getSiblings()) {
            URI found = link(part);
            if (found != null) return found;
        }
        return null;
    }

    private void fetch(URI link) {
        state = State.FETCHING;
        URI secure = secure(link);
        POOL.execute(() -> {
            try {
                take(secure);
            } catch (Exception first) {
                try {
                    take(link);
                } catch (Exception second) {
                    fail(Net.reason(second, WHO));
                }
            }
        });
    }

    private void take(URI link) throws Exception {
        HttpClient http = Net.client(Duration.ofSeconds(10), true);
        HttpRequest request = HttpRequest.newBuilder(link).timeout(TIMEOUT).GET().build();
        HttpResponse<InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            fail("сервер ответил " + response.statusCode());
            return;
        }
        String body = read(response.body());
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("handlers")) {
            fail("в ответе нет кода");
            return;
        }
        handlers = root.getAsJsonArray("handlers");
        state = State.DONE;
        XeroCode.LOG.info("[xerocode] код мира получен командой: строк {}, байт {}",
                handlers.size(), body.length());
    }

    private static String read(InputStream stream) throws Exception {
        try (InputStream in = stream) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("код слишком велик");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static URI secure(URI link) {
        if (!"http".equalsIgnoreCase(link.getScheme())) return link;
        try {
            return new URI("https", link.getSchemeSpecificPart(), link.getFragment());
        } catch (Exception e) {
            return link;
        }
    }

    private void fail(String why) {
        error = why;
        state = State.FAILED;
        if (current == this) current = null;
    }
}
