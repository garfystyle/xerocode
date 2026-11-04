package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.Minecraft;

public final class Collab {

    private static final long DIFF_EVERY = 200;
    private static final long LIVE_EVERY = 100;
    private static final long LIVE_BEAT = 2000;
    private static final long RESUME_WITHIN = 300_000;
    private static final int CHUNK = 300_000;
    private static final int PUSH_AT_ONCE = 10;
    private static final long WAIT_LIMIT = 15_000;
    private static final long[] BACKOFF = {1000, 2000, 4000, 8000, 15_000};
    private static final int PARKED_TRIES = 3;

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
    private static boolean greeted, joined, merging, resuming;
    private static String plot = "";
    private static String seat = "";

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
    private static List<Script.Root> parked;
    private static Script parkedIn;
    private static final List<JsonObject> early = new ArrayList<>();
    private static long retryAt;
    private static int attempts;

    private static int changes;

    public static int changes() { return changes; }

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
            String name = Minecraft.getInstance().getUser().getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Throwable ignored) {
        }
        return "Игрок";
    }

    public static void host() { begin(CollabCrypto.freshSecret(), "", false, false); }

    public static boolean guest(String code) {
        byte[] raw = CollabCrypto.fromCode(code);
        if (raw == null) {
            note = "код приглашения не подходит";
            stage = Stage.BROKEN;
            return false;
        }
        begin(raw, "", false, true);
        return true;
    }

    public static String session() {
        if (secret == null || stage == Stage.BROKEN) return "";
        JsonObject out = new JsonObject();
        out.addProperty("code", code());
        out.addProperty("seat", seat);
        out.addProperty("plot", plot);
        out.addProperty("at", System.currentTimeMillis());
        return out.toString();
    }

    public static boolean resume(String saved) {
        if (saved == null || saved.isBlank() || on()) return false;
        try {
            JsonObject in = JsonParser.parseString(saved).getAsJsonObject();
            long age = System.currentTimeMillis() - in.get("at").getAsLong();
            if (age < 0 || age > RESUME_WITHIN) return false;
            if (!Json.str(in, "plot").equals(XeroCode.script().plot)) return false;
            byte[] raw = CollabCrypto.fromCode(Json.str(in, "code"));
            if (raw == null) return false;
            begin(raw, Json.str(in, "seat"), true, true);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void begin(byte[] raw, String seatToken, boolean again, boolean guest) {
        stop();
        if (guest) park(XeroCode.script());
        secret = raw;
        enc = CollabCrypto.encKey(raw);
        auth = CollabCrypto.authKey(raw);
        roomId = CollabCrypto.roomId(raw);
        plot = XeroCode.script().plot;
        seat = seatToken;
        resuming = again;
        joined = false;
        merging = false;
        attempts = 0;
        clock = 0;
        note = again ? "возвращаюсь в комнату…" : "подключаюсь…";
        connect();
    }

    private static void connect() {
        stage = Stage.CONNECTING;
        greeted = false;
        taking = null;
        net = new CollabNet();
        net.connect(Endpoints.relay());
    }

    public static void stop() {
        unpark();
        early.clear();
        if (net != null) net.leave();
        net = null;
        stage = Stage.OFF;
        note = "";
        me = 0;
        seat = "";
        merging = false;
        resuming = false;
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
        retryAt = 0;
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
            taking = null;
            unpark();
            base("никто не ответил — общим стало ваше полотно");
            settled(script);
            return;
        }
        if (stage != Stage.LIVE || paused()) return;
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
        String why = net == null || net.why().isEmpty() ? "связь потеряна" : net.why();
        if (parked != null && !joined && attempts >= PARKED_TRIES) {
            fail("не удалось войти в комнату: " + why);
            return;
        }
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
                if (!seat.isEmpty()) answer.addProperty("seat", seat);
                net.send(answer);
            }
            case "ok" -> {
                me = message.get("you").getAsInt();
                seat = Json.str(message, "seat");
                attempts = 0;
                retryAt = 0;
                Map<Integer, Peer> known = new HashMap<>(peers);
                peers.clear();
                if (message.has("peers"))
                    for (JsonElement e : message.getAsJsonArray("peers")) {
                        int id = e.getAsInt();
                        Peer was = known.get(id);
                        if (was != null && id != me) peers.put(id, was);
                        else peer(id);
                    }
                lastBeat = 0;
                boolean first = message.has("first") && message.get("first").getAsBoolean();
                merging = joined && !first;
                if (first) {
                    unpark();
                    base(joined ? "связь восстановлена" : resuming ? "снова в комнате" : "комната создана");
                    settled(script);
                } else {
                    stage = Stage.WAITING;
                    waitingSince = System.currentTimeMillis();
                    note = merging ? "сверяю полотно с комнатой…" : "забираю полотно комнаты…";
                    JsonObject pull = new JsonObject();
                    pull.addProperty("t", "pull");
                    net.send(pull);
                }
                joined = true;
            }
            case "empty" -> {
                unpark();
                base("вы первый в комнате");
                settled(script);
            }
            case "want" -> { if (live()) giveTo(message.get("who").getAsInt(), script); }
            case "give" -> {
                if (stage != Stage.WAITING) break;
                waitingSince = System.currentTimeMillis();
                if (taking == null) taking = new StringBuilder();
                if (taking.length() < 8 << 20) taking.append(Json.str(message, "b"));
                if (!message.has("more") || !message.get("more").getAsBoolean()) {
                    String whole = taking.toString();
                    taking = null;
                    if (merging) merge(script, whole);
                    else adopt(script, whole);
                }
            }
            case "put" -> { if (stage == Stage.WAITING) early.add(message); else put(message, script); }
            case "del" -> { if (stage == Stage.WAITING) early.add(message); else del(message, script); }
            case "live" -> incoming(message);
            case "join" -> {
                peer(message.get("who").getAsInt());
                lastBeat = 0;
            }
            case "left" -> peers.remove(message.get("who").getAsInt());
            case "moved" -> {
                seat = "";
                if (net != null) net.close("вы вошли в комнату ещё раз");
            }
            case "err" -> fail(Json.str(message, "why"));
            default -> { }
        }
    }

    private static void park(Script script) {
        parked = new ArrayList<>(script.roots);
        parkedIn = script;
        script.roots.clear();
        changes++;
    }

    private static void unpark() {
        if (parked == null) return;
        parkedIn.roots.clear();
        parkedIn.roots.addAll(parked);
        parked = null;
        parkedIn = null;
        changes++;
    }

    public static List<Script.Root> parked(Script script) {
        return parked != null && script == parkedIn ? parked : null;
    }

    private static void fail(String why) {
        stage = Stage.BROKEN;
        note = why;
        taking = null;
        if (net != null) net.close();
        net = null;
        unpark();
        early.clear();
    }

    private static void settled(Script script) {
        List<JsonObject> late = new ArrayList<>(early);
        early.clear();
        for (JsonObject message : late) handle(message, script);
    }

    private static void base(String said) {
        mirror.clear();
        merging = false;
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
        JsonObject known = new JsonObject();
        for (Map.Entry<String, Long> entry : versions.entrySet())
            known.addProperty(entry.getKey(), entry.getValue());
        JsonObject payload = new JsonObject();
        payload.add("roots", arr);
        payload.add("v", known);
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

    private static JsonObject opened(String sealed) {
        String body = CollabCrypto.open(enc, sealed, "give");
        JsonObject payload = null;
        try {
            if (body != null) payload = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException ignored) {
        }
        if (payload == null || !payload.has("roots")) {
            fail("не удалось прочитать полотно комнаты");
            return null;
        }
        return payload;
    }

    private static Map<String, Long> theirVersions(JsonObject payload) {
        if (!payload.has("v") || !payload.get("v").isJsonObject()) return null;
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : payload.getAsJsonObject("v").entrySet()) {
            try {
                out.put(entry.getKey(), entry.getValue().getAsLong());
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static void learn(Map<String, Long> theirs) {
        if (theirs == null) return;
        for (Map.Entry<String, Long> entry : theirs.entrySet()) {
            versions.merge(entry.getKey(), entry.getValue(), Math::max);
            clock = Math.max(clock, entry.getValue());
        }
    }

    private static void adopt(Script script, String sealed) {
        JsonObject payload = opened(sealed);
        if (payload == null) return;
        List<Script.Root> before = parked(script);
        if (!resuming) backup(before != null ? before : script.roots);
        parked = null;
        parkedIn = null;
        script.roots.clear();
        mirror.clear();
        versions.clear();
        slotToRoot.clear();
        remote.clear();
        mine.clear();
        for (JsonElement e : payload.getAsJsonArray("roots")) {
            Script.Root root = readRoot(e.getAsJsonObject());
            if (root == null) continue;
            script.roots.add(root);
            mirror.put(root.id, Script.rootHash(root));
            slotToRoot.put(slotFor(root.id), root.id);
            remote.put(root.id, e.getAsJsonObject());
        }
        learn(theirVersions(payload));
        if (!resuming) script.fitOnOpen = true;
        script.save();
        History.clear(script);
        changes++;
        stage = Stage.LIVE;
        note = resuming ? "снова в комнате" : "полотно комнаты загружено";
        settled(script);
    }

    private static void merge(Script script, String sealed) {
        JsonObject payload = opened(sealed);
        if (payload == null) return;
        Map<String, Long> theirVersions = theirVersions(payload);
        Map<String, JsonObject> theirs = new LinkedHashMap<>();
        for (JsonElement e : payload.getAsJsonArray("roots")) {
            JsonObject root = e.getAsJsonObject();
            String id = Json.str(root, "id");
            if (!id.isEmpty()) theirs.put(id, root);
        }
        boolean moved = false;
        for (Script.Root local : new ArrayList<>(script.roots)) {
            JsonObject other = theirs.remove(local.id);
            Integer synced = mirror.get(local.id);
            boolean edited = synced == null || synced != Script.rootHash(local) || local.id.equals(held);
            if (edited) continue;
            if (mineNewer(slotFor(local.id), theirVersions)) {
                mirror.remove(local.id);
                continue;
            }
            if (other == null) {
                script.roots.remove(local);
                forget(local.id);
                moved = true;
            } else if (take(script, local, other)) moved = true;
        }
        for (JsonObject other : theirs.values()) {
            String id = Json.str(other, "id");
            if (mirror.containsKey(id)) continue;
            if (mineNewer(slotFor(id), theirVersions)) {
                Script.Root gone = readRoot(other);
                if (gone != null) mirror.put(id, Script.rootHash(gone));
                continue;
            }
            if (take(script, null, other)) moved = true;
        }
        learn(theirVersions);
        if (moved) changes++;
        merging = false;
        stage = Stage.LIVE;
        note = "связь восстановлена";
        lastDiff = 0;
        settled(script);
    }

    private static boolean mineNewer(String slot, Map<String, Long> theirVersions) {
        Long ours = versions.get(slot);
        if (ours == null || theirVersions == null) return false;
        Long room = theirVersions.get(slot);
        return room == null || ours > room;
    }

    private static boolean take(Script script, Script.Root local, JsonObject other) {
        Script.Root fresh = readRoot(other);
        if (fresh == null) return false;
        int hash = Script.rootHash(fresh);
        mirror.put(fresh.id, hash);
        slotToRoot.put(slotFor(fresh.id), fresh.id);
        remote.put(fresh.id, other);
        mine.remove(fresh.id);
        if (local == null) {
            script.roots.add(fresh);
            return true;
        }
        if (Script.rootHash(local) == hash) return false;
        fresh.folded = local.folded;
        script.roots.set(script.roots.indexOf(local), fresh);
        return true;
    }

    private static void forget(String id) {
        mirror.remove(id);
        remote.remove(id);
        mine.remove(id);
    }

    private static void backup(List<Script.Root> roots) {
        try {
            if (roots.isEmpty()) return;
            Script copy = new Script();
            copy.roots.addAll(roots);
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode");
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH.mm.ss"));
            Json.write(dir.resolve("before-collab-" + stamp + ".json"), copy.toJson());
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
        int hash = Script.rootHash(fresh);
        mirror.put(id, hash);
        if (was != null && Script.rootHash(was) == hash) return;
        if (was == null) script.roots.add(fresh);
        else {
            fresh.folded = was.folded;
            script.roots.set(script.roots.indexOf(was), fresh);
        }
        changes++;
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
        if (script.roots.removeIf(root -> root.id.equals(gone))) changes++;
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
            else {
                fresh.folded = was.folded;
                script.roots.set(script.roots.indexOf(was), fresh);
            }
            mirror.put(fresh.id, Script.rootHash(fresh));
            changes++;
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
