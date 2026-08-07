package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Collab {
    public static final String HOST = "wss://109-120-178-103.sslip.io:8792/xerocode/collab";

    private static final long DIFF_EVERY = 200;
    private static final long LIVE_EVERY = 100;
    private static final long LIVE_BEAT = 2000;
    private static final long PEER_GONE = 30_000;
    private static final int CHUNK = 300_000;
    private static final int PUSH_AT_ONCE = 10;
    private static final long WAIT_LIMIT = 15_000;
    private static final long[] BACKOFF = {1000, 2000, 4000, 8000, 15_000};

    private static final int[] INKS = {
            0x5AC8FA, 0xFF9F0A, 0x30D158, 0xFF6482,
            0xBF5AF2, 0xFFD60A, 0x64D2FF, 0xFF7A45
    };

    public enum Stage { OFF, CONNECTING, WAITING, LIVE, BROKEN }

    public static final class Peer {
        public final int id;
        public String name = "";
        public int ink;
        public double x, y, zoom = 1;
        public double drawX, drawY;
        public boolean drawn;
        public String holding = "";
        public long seen;

        Peer(int id) {
            this.id = id;
            this.ink = INKS[Math.floorMod(id, INKS.length)];
        }
    }

    private static CollabNet net;
    private static byte[] secret, enc, auth;
    private static String roomId = "";

    private static Stage stage = Stage.OFF;
    private static String note = "";
    private static int me;
    private static long clock;
    private static boolean greeted, joined;
    private static String plot = "";

    private static final Map<String, Integer> mirror = new HashMap<>();
    private static final Map<String, Long> versions = new HashMap<>();
    private static final Map<String, String> slotCache = new HashMap<>();
    private static final Map<String, String> slotToRoot = new HashMap<>();
    private static final Map<String, JsonObject> remote = new HashMap<>();
    private static final Set<String> mine = new HashSet<>();
    private static final Map<Integer, Peer> peers = new LinkedHashMap<>();

    private static String held = "";
    private static double myX, myY, myZoom = 1;
    private static long lastDiff, lastLive, lastBeat;
    private static String lastLiveText = "";

    private static StringBuilder taking;
    private static long waitingSince;
    private static long retryAt;
    private static int attempts;

    public static Stage stage() { return stage; }

    public static boolean on() { return stage != Stage.OFF; }

    public static boolean live() { return stage == Stage.LIVE && !paused(); }

    public static boolean paused() {
        return stage == Stage.LIVE && !plot.equals(XeroCode.script().plot);
    }

    public static String note() { return note; }

    public static String code() { return secret == null ? "" : CollabCrypto.code(secret); }

    public static int members() { return peers.size() + (stage == Stage.LIVE ? 1 : 0); }

    public static List<Peer> peers() { return new ArrayList<>(peers.values()); }

    public static int peerCount() { return peers.size(); }

    public static String myName() {
        String chosen = Settings.get().collabName;
        if (chosen != null && !chosen.isBlank()) return chosen.trim();
        try {
            String name = MinecraftClient.getInstance().getSession().getUsername();
            if (name != null && !name.isBlank()) return name;
        } catch (Throwable ignored) {
        }
        return "Игрок";
    }

    public static void host() { begin(CollabCrypto.freshSecret()); }

    public static boolean guest(String code) {
        byte[] raw = CollabCrypto.fromCode(code);
        if (raw == null) {
            note = "код приглашения не подходит";
            stage = Stage.BROKEN;
            return false;
        }
        begin(raw);
        return true;
    }

    private static void begin(byte[] raw) {
        stop();
        secret = raw;
        enc = CollabCrypto.encKey(raw);
        auth = CollabCrypto.authKey(raw);
        roomId = CollabCrypto.roomId(raw);
        plot = XeroCode.script().plot;
        joined = false;
        attempts = 0;
        clock = 0;
        note = "подключаюсь…";
        connect();
    }

    private static void connect() {
        stage = Stage.CONNECTING;
        greeted = false;
        taking = null;
        net = new CollabNet();
        net.connect(HOST);
    }

    public static void stop() {
        if (net != null) net.close();
        net = null;
        stage = Stage.OFF;
        note = "";
        me = 0;
        held = "";
        peers.clear();
        mirror.clear();
        versions.clear();
        slotCache.clear();
        slotToRoot.clear();
        remote.clear();
        mine.clear();
        taking = null;
        secret = null;
        enc = null;
        auth = null;
        roomId = "";
    }

    public static void hold(Script.Root root) {
        held = root == null ? "" : root.id;
    }

    public static void frame(Script.Root heldRoot, double x, double y, double zoom) {
        hold(heldRoot);
        myX = x;
        myY = y;
        myZoom = zoom;
    }

    public static void tick() {
        if (stage == Stage.OFF) return;
        Script script = XeroCode.script();
        if (net != null) drain(script);
        long now = System.currentTimeMillis();
        if (net == null || net.dead()) {
            if (stage != Stage.BROKEN) retry(now);
            return;
        }
        if (!net.open()) return;
        if (!greeted) {
            greeted = true;
            JsonObject hello = new JsonObject();
            hello.addProperty("t", "hello");
            hello.addProperty("room", roomId);
            net.send(hello);
            return;
        }
        if (stage == Stage.WAITING && now - waitingSince > WAIT_LIMIT) {
            base("никто не ответил — общим стало ваше полотно");
            return;
        }
        if (stage != Stage.LIVE || paused()) return;
        peers.values().removeIf(p -> now - p.seen > PEER_GONE);
        if (now - lastDiff >= DIFF_EVERY) {
            lastDiff = now;
            diff(script);
        }
        if (now - lastLive >= LIVE_EVERY) {
            lastLive = now;
            sendLive(now);
        }
    }

    private static void retry(long now) {
        String why = net == null ? "связь потеряна" : net.why();
        if (net != null) net.close();
        net = null;
        if (retryAt == 0) {
            retryAt = now + BACKOFF[Math.min(attempts, BACKOFF.length - 1)];
            attempts++;
            stage = Stage.CONNECTING;
            note = why + " · переподключаюсь…";
            return;
        }
        if (now < retryAt) return;
        retryAt = 0;
        connect();
    }

    private static void drain(Script script) {
        JsonObject message;
        while ((message = net.take()) != null) {
            try {
                handle(message, script);
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] не разобрал сообщение совместной работы", e);
            }
        }
    }

    private static void handle(JsonObject message, Script script) {
        String kind = Json.str(message, "t");
        switch (kind) {
            case "chal" -> {
                JsonObject answer = new JsonObject();
                answer.addProperty("t", "auth");
                answer.addProperty("p", CollabCrypto.proof(auth, roomId, Json.str(message, "n")));
                if (message.has("new") && message.get("new").getAsBoolean())
                    answer.addProperty("reg", Hex.of(auth));
                net.send(answer);
            }
            case "ok" -> {
                me = message.get("you").getAsInt();
                attempts = 0;
                retryAt = 0;
                peers.clear();
                if (message.has("peers"))
                    for (JsonElement e : message.getAsJsonArray("peers")) peer(e.getAsInt());
                boolean first = message.has("first") && message.get("first").getAsBoolean();
                if (first || joined) {
                    base(first ? "комната создана" : "связь восстановлена");
                } else {
                    stage = Stage.WAITING;
                    waitingSince = System.currentTimeMillis();
                    note = "забираю полотно комнаты…";
                    JsonObject pull = new JsonObject();
                    pull.addProperty("t", "pull");
                    net.send(pull);
                }
                joined = true;
            }
            case "empty" -> base("вы первый в комнате");
            case "want" -> { if (live()) giveTo(message.get("who").getAsInt(), script); }
            case "give" -> {
                if (taking == null) taking = new StringBuilder();
                if (taking.length() < 8 << 20) taking.append(Json.str(message, "b"));
                if (!message.has("more") || !message.get("more").getAsBoolean()) {
                    String whole = taking.toString();
                    taking = null;
                    adopt(script, whole);
                }
            }
            case "put" -> put(message, script);
            case "del" -> del(message, script);
            case "live" -> incoming(message);
            case "join" -> {
                peer(message.get("who").getAsInt());
                lastBeat = 0;
            }
            case "left" -> peers.remove(message.get("who").getAsInt());
            case "err" -> {
                stage = Stage.BROKEN;
                note = Json.str(message, "why");
                if (net != null) net.close();
                net = null;
            }
            default -> { }
        }
    }

    private static void base(String said) {
        mirror.clear();
        stage = Stage.LIVE;
        note = said;
        lastDiff = 0;
    }

    private static Peer peer(int id) {
        if (id == me) return null;
        return peers.computeIfAbsent(id, Peer::new);
    }

    private static void giveTo(int who, Script script) {
        JsonArray arr = new JsonArray();
        for (Script.Root r : script.roots) arr.add(rootJson(r));
        JsonObject payload = new JsonObject();
        payload.add("roots", arr);
        String sealed = CollabCrypto.seal(enc, payload.toString(), "give");
        for (int at = 0; at < sealed.length(); at += CHUNK) {
            int end = Math.min(sealed.length(), at + CHUNK);
            JsonObject piece = new JsonObject();
            piece.addProperty("t", "give");
            piece.addProperty("to", who);
            piece.addProperty("b", sealed.substring(at, end));
            piece.addProperty("more", end < sealed.length());
            net.send(piece);
        }
    }

    private static void adopt(Script script, String sealed) {
        String body = CollabCrypto.open(enc, sealed, "give");
        if (body == null) {
            stage = Stage.BROKEN;
            note = "не удалось расшифровать полотно комнаты";
            return;
        }
        backup(script);
        script.roots.clear();
        mirror.clear();
        versions.clear();
        slotToRoot.clear();
        remote.clear();
        mine.clear();
        JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
        for (JsonElement e : payload.getAsJsonArray("roots")) {
            Script.Root root = readRoot(e.getAsJsonObject());
            if (root == null) continue;
            script.roots.add(root);
            mirror.put(root.id, Script.rootHash(root));
            slotToRoot.put(slotFor(root.id), root.id);
            remote.put(root.id, e.getAsJsonObject());
        }
        script.fitOnOpen = true;
        script.save();
        History.clear();
        stage = Stage.LIVE;
        note = "полотно комнаты загружено";
    }

    private static void backup(Script script) {
        try {
            if (script.roots.isEmpty()) return;
            Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH.mm.ss"));
            Files.writeString(dir.resolve("before-collab-" + stamp + ".json"),
                    script.toJson().toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось сохранить полотно перед входом в комнату", e);
        }
    }

    private static void diff(Script script) {
        Set<String> alive = new HashSet<>();
        int sent = 0;
        for (Script.Root root : script.roots) {
            alive.add(root.id);
            if (sent >= PUSH_AT_ONCE) continue;
            int hash = Script.rootHash(root);
            Integer was = mirror.get(root.id);
            if (was != null && was == hash) continue;
            push(root, hash);
            sent++;
        }
        for (String id : new ArrayList<>(mirror.keySet())) {
            if (alive.contains(id)) continue;
            drop(id);
        }
    }

    private static long nextVersion() {
        clock = Math.max(clock + 1, System.currentTimeMillis());
        return clock;
    }

    private static void push(Script.Root root, int hash) {
        String slot = slotFor(root.id);
        long version = nextVersion();
        JsonObject out = new JsonObject();
        out.addProperty("t", "put");
        out.addProperty("slot", slot);
        out.addProperty("v", version);
        out.addProperty("b", CollabCrypto.seal(enc, rootJson(root).toString(), slot + ":" + version));
        net.send(out);
        mirror.put(root.id, hash);
        versions.put(slot, version);
        slotToRoot.put(slot, root.id);
        remote.remove(root.id);
        mine.add(root.id);
    }

    private static void drop(String id) {
        String slot = slotFor(id);
        long version = nextVersion();
        JsonObject out = new JsonObject();
        out.addProperty("t", "del");
        out.addProperty("slot", slot);
        out.addProperty("v", version);
        net.send(out);
        mirror.remove(id);
        versions.put(slot, version);
        remote.remove(id);
        mine.remove(id);
    }

    private static void put(JsonObject message, Script script) {
        String slot = Json.str(message, "slot");
        long version = message.get("v").getAsLong();
        clock = Math.max(clock, version);
        Long known = versions.get(slot);
        if (known != null && version <= known) return;
        String body = CollabCrypto.open(enc, Json.str(message, "b"), slot + ":" + version);
        if (body == null) return;
        JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
        String id = Json.str(payload, "id");
        if (id.isEmpty() || !slot.equals(slotFor(id))) return;
        versions.put(slot, version);
        slotToRoot.put(slot, id);
        remote.put(id, payload);
        mine.remove(id);
        if (id.equals(held)) {
            mirror.remove(id);
            return;
        }
        Script.Root fresh = readRoot(payload);
        if (fresh == null) return;
        Script.Root was = script.rootById(id);
        if (was == null) script.roots.add(fresh);
        else script.roots.set(script.roots.indexOf(was), fresh);
        mirror.put(id, Script.rootHash(fresh));
    }

    private static void del(JsonObject message, Script script) {
        String slot = Json.str(message, "slot");
        long version = message.get("v").getAsLong();
        clock = Math.max(clock, version);
        Long known = versions.get(slot);
        if (known != null && version <= known) return;
        versions.put(slot, version);
        String id = slotToRoot.get(slot);
        if (id == null)
            for (Script.Root root : script.roots)
                if (slot.equals(slotFor(root.id))) { id = root.id; break; }
        if (id == null) return;
        remote.remove(id);
        mine.remove(id);
        if (id.equals(held)) {
            mirror.remove(id);
            return;
        }
        String gone = id;
        script.roots.removeIf(root -> root.id.equals(gone));
        mirror.remove(id);
    }

    private static void sendLive(long now) {
        JsonObject payload = new JsonObject();
        payload.addProperty("x", Math.round(myX * 10) / 10.0);
        payload.addProperty("y", Math.round(myY * 10) / 10.0);
        payload.addProperty("z", Math.round(myZoom * 100) / 100.0);
        if (!held.isEmpty()) payload.addProperty("g", held);
        String body = payload.toString();
        boolean beat = now - lastBeat >= LIVE_BEAT;
        if (body.equals(lastLiveText) && !beat) return;
        lastLiveText = body;
        if (beat) {
            lastBeat = now;
            payload.addProperty("n", myName());
            body = payload.toString();
        }
        JsonObject out = new JsonObject();
        out.addProperty("t", "live");
        out.addProperty("b", CollabCrypto.seal(enc, body, "live"));
        net.send(out);
    }

    private static void incoming(JsonObject message) {
        if (!message.has("m")) return;
        JsonObject map = message.getAsJsonObject("m");
        long now = System.currentTimeMillis();
        for (String key : map.keySet()) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            if (id == me) continue;
            String body = CollabCrypto.open(enc, map.get(key).getAsString(), "live");
            if (body == null) continue;
            JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
            Peer p = peer(id);
            if (p == null) continue;
            if (payload.has("n")) p.name = cut(Json.str(payload, "n"));
            p.x = payload.has("x") ? payload.get("x").getAsDouble() : p.x;
            p.y = payload.has("y") ? payload.get("y").getAsDouble() : p.y;
            p.zoom = payload.has("z") ? payload.get("z").getAsDouble() : p.zoom;
            p.holding = Json.str(payload, "g");
            p.seen = now;
        }
    }

    public static void afterHistory(Script script) {
        if (!live()) return;
        for (Map.Entry<String, JsonObject> entry : remote.entrySet()) {
            if (mine.contains(entry.getKey())) continue;
            Script.Root fresh = readRoot(entry.getValue());
            if (fresh == null) continue;
            Script.Root was = script.rootById(fresh.id);
            if (was != null && Script.rootHash(was) == Script.rootHash(fresh)) continue;
            if (was == null) script.roots.add(fresh);
            else script.roots.set(script.roots.indexOf(was), fresh);
            mirror.put(fresh.id, Script.rootHash(fresh));
        }
    }

    private static String slotFor(String id) {
        return slotCache.computeIfAbsent(id, key -> CollabCrypto.slot(enc, key));
    }

    private static JsonObject rootJson(Script.Root root) {
        JsonObject out = new JsonObject();
        out.addProperty("id", root.id);
        out.addProperty("x", root.x);
        out.addProperty("y", root.y);
        out.add("chain", Script.writeChain(root.chain));
        return out;
    }

    private static Script.Root readRoot(JsonObject payload) {
        try {
            Script.Root root = new Script.Root(payload.get("x").getAsDouble(),
                    payload.get("y").getAsDouble(), payload.get("id").getAsString());
            root.chain.addAll(Script.readChain(payload.getAsJsonArray("chain")));
            return root;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String cut(String s) {
        String one = s.replace('\n', ' ').trim();
        return one.length() <= 16 ? one : one.substring(0, 16);
    }

    private Collab() {}
}
