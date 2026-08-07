package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xerocode.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Market {
    public static final String TAB_HOT = "hot", TAB_NEW = "new", TAB_TOP = "top",
            TAB_MINE = "mine", TAB_LIKED = "liked";
    public static final int PAGE = 24;

    public static final class Module {
        public String id = "", name = "", summary = "", descr = "", cat = "";
        public String icon = "", banner = "", owner = "", author = "", authorIcon = "";
        public boolean authorOk, mine, liked, hidden;
        public int blocks, roots, size, downloads, likes, version, reports;
        public long created, updated;
        public final List<String> tags = new ArrayList<>();

        static Module of(JsonObject o) {
            Module m = new Module();
            m.id = Json.str(o, "id");
            m.name = Json.str(o, "name");
            m.summary = Json.str(o, "summary");
            m.descr = Json.str(o, "descr");
            m.cat = Json.str(o, "cat");
            m.icon = Json.str(o, "icon");
            m.banner = Json.str(o, "banner");
            m.owner = Json.str(o, "owner");
            m.author = Json.str(o, "author");
            m.authorIcon = Json.str(o, "authorIcon");
            m.authorOk = Json.flag(o, "authorOk");
            m.mine = Json.flag(o, "mine");
            m.liked = Json.flag(o, "liked");
            m.hidden = Json.flag(o, "hidden");
            m.blocks = Json.num(o, "blocks");
            m.roots = Json.num(o, "roots");
            m.size = Json.num(o, "size");
            m.downloads = Json.num(o, "downloads");
            m.likes = Json.num(o, "likes");
            m.version = Json.num(o, "version");
            m.reports = Json.num(o, "reports");
            m.created = Json.big(o, "created");
            m.updated = Json.big(o, "updated");
            if (o.has("tags") && o.get("tags").isJsonArray())
                for (JsonElement e : o.getAsJsonArray("tags")) m.tags.add(e.getAsString());
            return m;
        }

        public String blocksText() { return Ui.plural(blocks, "блок", "блока", "блоков"); }

        public String sizeText() {
            return size < 1024 ? size + " Б" : (size / 1024) + " КБ";
        }

        public String when() { return Backpack.when(updated * 1000L); }
    }

    public static final class Me {
        public String id = "", name = "", bio = "", icon = "", uuid = "", path = "";
        public boolean verified, banned, admin;
        public long created;
        public int modules;
        public final Map<String, Integer> limits = new LinkedHashMap<>();
        public final Map<String, Integer> used = new LinkedHashMap<>();

        static Me of(JsonObject o) {
            Me me = new Me();
            me.id = Json.str(o, "id");
            me.name = Json.str(o, "name");
            me.bio = Json.str(o, "bio");
            me.icon = Json.str(o, "icon");
            me.uuid = Json.str(o, "uuid");
            me.path = Json.str(o, "path");
            me.verified = Json.flag(o, "verified");
            me.banned = Json.flag(o, "banned");
            me.admin = Json.flag(o, "admin");
            me.created = Json.big(o, "created");
            me.modules = Json.num(o, "modules");
            if (o.has("limits"))
                for (String k : o.getAsJsonObject("limits").keySet())
                    me.limits.put(k, o.getAsJsonObject("limits").get(k).getAsInt());
            if (o.has("usedToday"))
                for (String k : o.getAsJsonObject("usedToday").keySet())
                    me.used.put(k, o.getAsJsonObject("usedToday").get(k).getAsInt());
            return me;
        }

        public int limit(String key) { return limits.getOrDefault(key, 0); }
        public int spent(String key) { return used.getOrDefault(key, 0); }
        public int left(String limitKey, String usedKey) {
            return Math.max(0, limit(limitKey) - spent(usedKey));
        }

        public int publishLeft() { return left("publish_day", "publish"); }
        public int imageLeft() { return left("images_day", "image"); }
    }

    public static final class Page {
        public final List<Module> items = new ArrayList<>();
        public int total, offset;
        public boolean loading, more;
        public String trouble = "";
        public long stamp;
    }

    private static Me me;
    private static boolean metaAsked, metaOk;
    private static String metaTrouble = "";
    private static int powBits = 20;
    private static int publicModules;
    private static final List<String> CATEGORIES = new ArrayList<>();
    private static final List<String> REASONS = new ArrayList<>();
    private static boolean joining;
    private static String joinNote = "";

    public static final Page LIST = new Page();

    public static void tick() { MarketNet.pump(); }

    public static Me me() { return me; }
    public static boolean ready() { return metaOk; }
    public static String trouble() { return metaTrouble; }
    public static List<String> categories() { return CATEGORIES; }
    public static List<String> reasons() { return REASONS; }
    public static int publicModules() { return publicModules; }
    public static boolean joining() { return joining; }
    public static String joinNote() { return joinNote; }

    public static void start() {
        if (metaAsked && metaOk) {
            if (me == null && MarketId.have()) hello();
            return;
        }
        metaAsked = true;
        metaTrouble = "";
        MarketNet.call("meta", null, MarketId.have(), answer -> {
            metaOk = true;
            readMeta(answer);
            if (answer.has("me")) me = Me.of(answer.getAsJsonObject("me"));
            else if (MarketId.have()) hello();
        }, (said, code) -> {
            metaAsked = false;
            metaTrouble = said;
        });
    }

    private static void readMeta(JsonObject o) {
        CATEGORIES.clear();
        if (o.has("categories"))
            for (JsonElement e : o.getAsJsonArray("categories")) CATEGORIES.add(e.getAsString());
        REASONS.clear();
        if (o.has("reasons"))
            for (JsonElement e : o.getAsJsonArray("reasons")) REASONS.add(e.getAsString());
        if (o.has("powBits")) powBits = o.get("powBits").getAsInt();
        if (o.has("counts")) {
            publicModules = Json.num(o.getAsJsonObject("counts"), "modules");
        }
    }

    public static void hello() {
        if (joining) return;
        joining = true;
        joinNote = MarketId.have() ? "вход…" : "завожу аккаунт…";
        int bits = powBits;
        MarketNet.run(() -> {
            try {
                if (!MarketId.have()) MarketId.create(bits);
            } catch (Throwable e) {
                MarketNet.back(() -> {
                    joining = false;
                    joinNote = "не вышло сделать ключ: " + MarketNet.reason(e);
                });
                return;
            }
            MarketNet.back(Market::sayHello);
        });
    }

    private static void sayHello() {
        JsonObject body = new JsonObject();
        body.addProperty("name", playerName());
        body.addProperty("pow", MarketId.work());
        MarketNet.call("hello", body, true, answer -> {
            joining = false;
            joinNote = "";
            if (answer.has("me")) me = Me.of(answer.getAsJsonObject("me"));
        }, (said, code) -> {
            joining = false;
            joinNote = said;
        });
    }

    public static String playerName() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            String name = client.getSession() == null ? "" : client.getSession().getUsername();
            return name == null || name.isBlank() ? "игрок" : name;
        } catch (Throwable e) {
            return "игрок";
        }
    }

    public static void load(String tab, String query, String cat, int offset, Runnable after) {
        Page page = LIST;
        page.loading = true;
        page.trouble = "";
        long stamp = ++page.stamp;
        JsonObject body = new JsonObject();
        body.addProperty("tab", tab);
        body.addProperty("q", query == null ? "" : query);
        body.addProperty("cat", cat == null ? "" : cat);
        body.addProperty("offset", offset);
        body.addProperty("limit", PAGE);
        MarketNet.call("list", body, MarketId.have(), answer -> {
            if (page.stamp != stamp) return;
            page.loading = false;
            if (offset == 0) page.items.clear();
            page.offset = offset;
            page.total = Json.num(answer, "total");
            if (answer.has("items"))
                for (JsonElement e : answer.getAsJsonArray("items"))
                    page.items.add(Module.of(e.getAsJsonObject()));
            page.more = page.items.size() < page.total;
            if (after != null) after.run();
        }, (said, code) -> {
            if (page.stamp != stamp) return;
            page.loading = false;
            page.trouble = said;
            if (offset == 0) page.items.clear();
            if (after != null) after.run();
        });
    }

    public static void one(String id, Consumer<Module> ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        MarketNet.call("module", body, MarketId.have(),
                answer -> ok.accept(Module.of(answer)), bad);
    }

    public static void payload(String id, Consumer<JsonObject> ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        MarketNet.call("payload", body, MarketId.have(), answer -> {
            if (!answer.has("payload") || !answer.get("payload").isJsonObject())
                bad.apply("в ответе нет кода", "shape");
            else ok.accept(answer.getAsJsonObject("payload"));
        }, bad);
    }

    public static JsonObject payloadOf(List<Script.Root> roots) {
        JsonArray arr = new JsonArray();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (Script.Root r : roots) {
            minX = Math.min(minX, r.x);
            minY = Math.min(minY, r.y);
        }
        if (roots.isEmpty()) minX = minY = 0;
        for (Script.Root r : roots) {
            JsonObject o = new JsonObject();
            o.addProperty("x", Math.round((r.x - minX) * 100) / 100.0);
            o.addProperty("y", Math.round((r.y - minY) * 100) / 100.0);
            o.add("chain", Script.writeChain(r.chain));
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("version", 1);
        out.add("roots", arr);
        return out;
    }

    public static void publish(JsonObject form, Consumer<Module> ok, MarketNet.Fail bad) {
        MarketNet.call("publish", form, true, answer -> {
            Module made = Module.of(answer);
            if (me != null) {
                me.used.merge("publish", 1, Integer::sum);
                me.modules++;
            }
            ok.accept(made);
        }, bad);
    }

    public static void change(JsonObject form, Consumer<Module> ok, MarketNet.Fail bad) {
        MarketNet.call("update", form, true, answer -> {
            if (me != null && form.has("payload")) me.used.merge("publish", 1, Integer::sum);
            ok.accept(Module.of(answer));
        }, bad);
    }

    public static void drop(String id, Runnable ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        MarketNet.call("delete", body, true, answer -> {
            if (me != null) me.modules = Math.max(0, me.modules - 1);
            ok.run();
        }, bad);
    }

    public static void like(String id, boolean on, Consumer<JsonObject> ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("on", on);
        MarketNet.call("like", body, true, answer -> {
            if (me != null && on) me.used.merge("like", 1, Integer::sum);
            ok.accept(answer);
        }, bad);
    }

    public static void report(String id, String reason, Runnable ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("reason", reason);
        MarketNet.call("report", body, true, answer -> {
            if (me != null) me.used.merge("report", 1, Integer::sum);
            ok.run();
        }, bad);
    }

    public static void watch(Consumer<JsonObject> ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("what", "stats");
        MarketNet.call("admin", body, true, ok, bad);
    }

    public static boolean admin() { return me != null && me.admin; }

    public static void saveMe(String bio, String icon, Runnable ok, MarketNet.Fail bad) {
        JsonObject body = new JsonObject();
        body.addProperty("bio", bio);
        body.addProperty("icon", icon);
        MarketNet.call("account.update", body, true, answer -> {
            if (answer.has("me")) me = Me.of(answer.getAsJsonObject("me"));
            ok.run();
        }, bad);
    }

    public static void sendImage(byte[] png, boolean banner,
                                 Consumer<String> ok, MarketNet.Fail bad) {
        MarketNet.image(png, banner ? "banner" : "avatar", answer -> {
            if (me != null) me.used.merge("image", 1, Integer::sum);
            ok.accept("img:" + Json.str(answer, "hash"));
        }, bad);
    }

    private Market() {}
}
