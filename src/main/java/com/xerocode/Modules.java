package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.ui.McText;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class Modules {
    private static final String BASE = "https://api.creative.justmc.io/public/creative/modules";
    private static final String CACHE = "xerocode/modules-cache.json";
    private static final int PAGE = 100, PAGES = 60, KEEP_CODE = 12;
    private static final int LIST_BUDGET = 17, CODE_BUDGET = 20, RETRIES = 2;
    private static final long FRESH = 30 * 60_000L, WINDOW = 61_000L, QUIET = 60_000L;
    private static final long CODE_WAIT = 20_000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private static final String BUSY = "JustMC просит подождать — каталог обновится через минуту";
    private static final String CLOSED = "JustMC не пускает к каталогу (403)";
    private static final String GATHERING = "сайт ещё собирает каталог JustMC — открой через минуту";
    private static final Net.Who WHO = new Net.Who("каталог JustMC не найден — проверь интернет",
            "каталог JustMC молчит", "каталог JustMC не ответил вовремя");

    public enum Sort {
        INSTALLS("По установкам", Comparator.comparingInt((Module m) -> m.installs).reversed()
                .thenComparing(Comparator.comparingInt((Module m) -> m.stars).reversed())),
        STARS("По звёздам", Comparator.comparingInt((Module m) -> m.stars).reversed()
                .thenComparing(Comparator.comparingInt((Module m) -> m.installs).reversed())),
        NEW("Сначала новые", Comparator.comparing((Module m) -> m.created).reversed()),
        UPDATED("Недавно обновлённые", Comparator.comparing((Module m) -> m.updated).reversed());

        public final String label;
        private final Comparator<Module> order;

        Sort(String label, Comparator<Module> order) {
            this.label = label;
            this.order = order;
        }
    }

    public record Category(String id, String label, String item) {}

    public static final List<Category> CATEGORIES = List.of(
            new Category("TEXT", "Работа с текстом", "minecraft:writable_book"),
            new Category("GAME_MECHANICS", "Игровые механики", "minecraft:diamond_sword"),
            new Category("COMMANDS", "Команды", "minecraft:command_block"),
            new Category("INSTRUMENTS", "Инструментарий", "minecraft:iron_pickaxe"),
            new Category("GENERATIONS", "Генерация", "minecraft:grass_block"),
            new Category("SINGS", "Музыка и звуки", "minecraft:note_block"),
            new Category("VARIABLE_MANIPULATIONS", "Работа с переменными", "minecraft:magma_cream"),
            new Category("OTHER", "Разное", "minecraft:chest"));

    public static final class Module {
        public final String id, author, name, title, label, summary, category, version, item;
        public final String created, updated;
        public final List<String> about;
        public final boolean approved, open;
        public final int stars, installs;
        public final long createdAt, updatedAt;
        private final String key;

        private Module(JsonObject o, String id) {
            this.id = id;
            author = Json.str(o, "author");
            name = Json.str(o, "name");
            category = Json.str(o, "category");
            version = Json.str(o, "version");
            item = Json.str(o, "displayItem");
            approved = Json.flag(o, "isApproved");
            open = Json.flag(o, "codeVisible");
            stars = Json.num(o, "stars");
            installs = Json.num(o, "history");
            created = Json.str(o, "createdAt");
            updated = Json.str(o, "updatedAt");
            createdAt = millis(created);
            updatedAt = millis(updated);
            List<String> lines = lines(Json.str(o, "displayName"));
            String head = lines.isEmpty() ? "" : lines.get(0);
            String plain = McText.plain(head, McText.LEGACY).trim();
            title = plain.isEmpty() ? name : head;
            label = plain.isEmpty() ? name : plain;
            List<String> rest = new ArrayList<>(lines.subList(Math.min(1, lines.size()), lines.size()));
            if (!rest.isEmpty() && rest.get(0).isEmpty()) rest.remove(0);
            about = List.copyOf(rest);
            StringBuilder said = new StringBuilder();
            for (String line : about) {
                if (line.isEmpty()) continue;
                if (said.length() > 0) said.append(' ');
                said.append(McText.plain(line, McText.LEGACY).trim());
            }
            summary = said.toString();
            key = fold(label + " " + name);
        }

        static Module of(JsonObject o) {
            String id = Json.str(o, "id");
            return safeId(id) ? new Module(o, id) : null;
        }

        public String categoryLabel() { return Modules.category(category).label(); }

        public String when() { return Backpack.when(updatedAt); }

        private boolean matches(String[] words) {
            for (String w : words) if (!w.isEmpty() && !key.contains(w)) return false;
            return true;
        }
    }

    private record Snapshot(List<Module> modules, long at) {}

    private static final class Refused extends Exception {
        Refused(String said) { super(said); }
    }

    private static final ExecutorService LIST =
            Executors.newSingleThreadExecutor(Net.threads("xerocode-modules"));
    private static final ExecutorService CODE =
            Executors.newSingleThreadExecutor(Net.threads("xerocode-module-code"));
    private static final ArrayDeque<Long> SENT = new ArrayDeque<>();
    private static final Map<String, JsonArray> CODES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonArray> eldest) {
            return size() > KEEP_CODE;
        }
    };

    private static List<Module> all = List.of();
    private static long fetched, quietUntil;
    private static boolean loading, cacheRead;
    private static int got, version;
    private static String note = "";

    public static List<Module> all() { return all; }
    public static boolean loading() { return loading; }
    public static String note() { return note; }
    public static long fetched() { return fetched; }
    public static int got() { return got; }
    public static int version() { return version; }

    public static Category category(String id) {
        for (Category c : CATEGORIES) if (c.id().equals(id)) return c;
        return new Category(id, id.isEmpty() ? "Разное" : id.toLowerCase(Locale.ROOT), "minecraft:chest");
    }

    public static void open() {
        if (loading) return;
        if (!cacheRead) {
            cacheRead = true;
            begin(true);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - fetched >= FRESH && now >= quietUntil) begin(false);
    }

    public static boolean refresh() {
        if (loading) return false;
        begin(false);
        return true;
    }

    public static List<Module> pick(List<Module> from, Sort sort, String category,
                                    boolean approvedOnly, String query) {
        String[] words = fold(query == null ? "" : query).trim().split("\\s+");
        List<Module> out = new ArrayList<>();
        for (Module m : from) {
            if (!category.isEmpty() && !category.equals(m.category)) continue;
            if (approvedOnly && !m.approved) continue;
            if (m.matches(words)) out.add(m);
        }
        out.sort(sort.order);
        return out;
    }

    public static void code(String id, Consumer<JsonArray> ok, Consumer<String> bad) {
        JsonArray have = CODES.get(id);
        if (have != null) {
            ok.accept(have);
            return;
        }
        if (!safeId(id)) {
            bad.accept("у модуля странный номер");
            return;
        }
        CODE.execute(() -> {
            try {
                String body = get(base() + "/" + id + "/code", CODE_BUDGET, CODE_WAIT);
                JsonArray handlers = Json.arr(JsonParser.parseString(body).getAsJsonObject(), "handlers");
                if (handlers == null) {
                    back(() -> bad.accept("в ответе нет кода"));
                    return;
                }
                back(() -> {
                    CODES.put(id, handlers);
                    ok.accept(handlers);
                });
            } catch (Exception e) {
                String why = reason(e);
                back(() -> bad.accept(why));
            }
        });
    }

    private static void begin(boolean cache) {
        loading = true;
        got = 0;
        boolean shown = !all.isEmpty();
        LIST.execute(() -> load(cache, shown));
    }

    private static void load(boolean cache, boolean shown) {
        if (cache) {
            Snapshot kept = readCache();
            if (kept != null) {
                shown = true;
                back(() -> show(kept.modules(), kept.at()));
                long age = System.currentTimeMillis() - kept.at();
                if (age >= 0 && age < FRESH) {
                    back(() -> loading = false);
                    return;
                }
            }
        }
        JsonArray raw = new JsonArray();
        Map<String, Module> fresh = new LinkedHashMap<>();
        try {
            for (int page = 0; page < (Env.browser() ? 1 : PAGES); page++) {
                String body = get(Env.browser() ? base()
                        : BASE + "?offset=" + page * PAGE + "&limit=" + PAGE, LIST_BUDGET, 0);
                JsonArray part = JsonParser.parseString(body).getAsJsonArray();
                for (JsonElement e : part) {
                    if (!e.isJsonObject()) continue;
                    Module m = Module.of(e.getAsJsonObject());
                    if (m == null || fresh.containsKey(m.id)) continue;
                    fresh.put(m.id, m);
                    raw.add(e);
                }
                int count = fresh.size();
                List<Module> sofar = shown ? null : List.copyOf(fresh.values());
                back(() -> {
                    got = count;
                    if (sofar != null) show(sofar, 0);
                });
                if (Env.browser() || part.size() < PAGE) break;
            }
        } catch (Exception e) {
            String why = reason(e);
            back(() -> {
                loading = false;
                note = why;
                quietUntil = System.currentTimeMillis() + QUIET;
            });
            return;
        }
        long at = System.currentTimeMillis();
        writeCache(raw, at);
        List<Module> done = List.copyOf(fresh.values());
        back(() -> {
            show(done, at);
            loading = false;
            note = "";
        });
    }

    private static void show(List<Module> modules, long at) {
        all = modules;
        fetched = at;
        version++;
    }

    private static String get(String url, int budget, long maxWait) throws Exception {
        for (int tries = 0; ; tries++) {
            pace(budget, maxWait);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> answer = Net.client(TIMEOUT, true)
                    .send(request, HttpResponse.BodyHandlers.ofString());
            int code = answer.statusCode();
            if (code == 200) return answer.body();
            if (code == 429 && maxWait == 0 && tries < RETRIES) {
                back(() -> note = BUSY);
                Thread.sleep(WINDOW);
                back(() -> note = "");
                continue;
            }
            if (code == 503 && Env.browser()) throw new Refused(GATHERING);
            throw new Refused(code == 429 ? BUSY : code == 403 ? CLOSED
                    : code == 404 ? "JustMC не нашёл модуль" : "JustMC ответил " + code);
        }
    }

    private static void pace(int budget, long maxWait) throws Exception {
        while (true) {
            long wait;
            synchronized (SENT) {
                long now = System.currentTimeMillis();
                while (!SENT.isEmpty() && now - SENT.peekFirst() >= WINDOW) SENT.pollFirst();
                if (SENT.size() < budget) {
                    SENT.addLast(now);
                    return;
                }
                wait = SENT.peekFirst() + WINDOW - now;
            }
            long seconds = (wait + 999) / 1000;
            if (maxWait > 0 && wait > maxWait)
                throw new Refused("JustMC даёт 20 запросов в минуту — попробуй через " + seconds + " с");
            if (maxWait == 0) back(() -> note = "JustMC даёт 20 запросов в минуту — догружаю через "
                    + seconds + " с");
            Thread.sleep(Math.max(50, wait));
            if (maxWait == 0) back(() -> note = "");
        }
    }

    private static String reason(Exception e) {
        return e instanceof Refused ? e.getMessage() : Net.reason(e, WHO);
    }

    private static void back(Runnable what) { Minecraft.getInstance().execute(what); }

    private static String base() { return Env.browser() ? Endpoints.safe() + "modules" : BASE; }

    private static Path cacheFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(CACHE);
    }

    private static Snapshot readCache() {
        try {
            Path file = cacheFile();
            if (!Files.exists(file)) return null;
            JsonObject root = Json.read(file);
            JsonArray items = Json.arr(root, "modules");
            if (items == null) return null;
            List<Module> out = new ArrayList<>();
            for (JsonElement e : items) {
                Module m = e.isJsonObject() ? Module.of(e.getAsJsonObject()) : null;
                if (m != null) out.add(m);
            }
            return out.isEmpty() ? null : new Snapshot(List.copyOf(out), Json.big(root, "at"));
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] кэш модулей не читается: {}", e.toString());
            return null;
        }
    }

    private static void writeCache(JsonArray raw, long at) {
        JsonObject root = new JsonObject();
        root.addProperty("at", at);
        root.add("modules", raw);
        try {
            Json.write(cacheFile(), root);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] кэш модулей не записался: {}", e.toString());
        }
    }

    private static List<String> lines(String raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw.replace("\r", "").replace("\\n", "\n").split("\n", -1)) {
            String s = line.trim();
            boolean blank = McText.plain(s, McText.LEGACY).isBlank();
            if (blank && (out.isEmpty() || out.get(out.size() - 1).isEmpty())) continue;
            out.add(blank ? "" : s);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static boolean safeId(String id) {
        if (id.length() != 36) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c == '-' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
                return false;
        }
        return true;
    }

    private static long millis(String iso) {
        try {
            return LocalDateTime.of(Integer.parseInt(iso.substring(0, 4)),
                    Integer.parseInt(iso.substring(5, 7)), Integer.parseInt(iso.substring(8, 10)),
                    Integer.parseInt(iso.substring(11, 13)), Integer.parseInt(iso.substring(14, 16)),
                    Integer.parseInt(iso.substring(17, 19))).toEpochSecond(ZoneOffset.UTC) * 1000L;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Modules() {}
}
