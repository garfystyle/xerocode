package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Backpack {
    public static final int MAX = 300;
    public static final int NAME_MAX = 48;

    public static final class Part {
        public final double x, y;
        public final List<Script.Node> chain = new ArrayList<>();

        public Part(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Item {
        public final String id;
        public String name;
        public long at;
        public final List<Part> parts = new ArrayList<>();

        Item(String id, String name, long at) {
            this.id = id;
            this.name = name;
            this.at = at;
        }

        public int pieces() { return parts.size(); }

        public Script.Node head() {
            for (Part p : parts) if (!p.chain.isEmpty()) return p.chain.get(0);
            return null;
        }

        public boolean empty() {
            for (Part p : parts) if (!p.chain.isEmpty()) return false;
            return true;
        }

        public int blocks() {
            int n = 0;
            for (Part p : parts) n += Script.blocks(p.chain);
            return n;
        }

        public String category() {
            Script.Node h = head();
            return h == null || h.action.category == null ? "" : h.action.category.name;
        }

        public int color() {
            Script.Node h = head();
            return h == null || h.action.category == null ? 0x7A7A7A : h.action.category.color;
        }

        public ItemStack icon() {
            Script.Node h = head();
            if (h == null) return ItemStack.EMPTY;
            Value own = h.declares() ? Functions.iconOf(h) : null;
            return own != null ? Stacks.preview(own) : h.action.icon();
        }

        public String subtitle() {
            String cat = category();
            return blocksText() + (cat.isEmpty() ? "" : " · " + cat);
        }

        public String blocksText() {
            String said = Ui.plural(blocks(), "блок", "блока", "блоков");
            return parts.size() < 2 ? said
                    : Ui.plural(parts.size(), "стопка", "стопки", "стопок") + " · " + said;
        }

        public List<Script.Node> copy() {
            List<Script.Node> out = new ArrayList<>();
            for (Part p : parts) for (Script.Node n : p.chain) out.add(n.copy());
            return out;
        }

        public List<Script.Root> roots() {
            List<Script.Root> out = new ArrayList<>(parts.size());
            for (Part p : parts) {
                if (p.chain.isEmpty()) continue;
                Script.Root r = new Script.Root(p.x, p.y);
                for (Script.Node n : p.chain) r.chain.add(n.copy());
                out.add(r);
            }
            return out;
        }

        public String searchText() {
            StringBuilder sb = new StringBuilder(name);
            for (Part p : parts) for (Script.Node n : p.chain) names(n, sb);
            return sb.toString();
        }

        private static void names(Script.Node n, StringBuilder sb) {
            sb.append(' ').append(n.action.name);
            for (Script.Node c : n.body) names(c, sb);
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static boolean read;

    public static List<Item> all() {
        if (!read) { read = true; load(); }
        return ITEMS;
    }

    public static int count() { return all().size(); }

    public static Item byId(String id) {
        for (Item i : all()) if (i.id.equals(id)) return i;
        return null;
    }

    public static Map<String, Integer> categories() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Item i : all()) {
            String c = i.category();
            if (!c.isEmpty()) out.merge(c, 1, Integer::sum);
        }
        return out;
    }

    public static Item put(String name, List<Script.Node> chain) {
        if (chain.isEmpty()) return null;
        Script.Root one = new Script.Root(0, 0);
        one.chain.addAll(chain);
        return putAll(name, List.of(one));
    }

    public static Item putAll(String name, List<Script.Root> roots) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE;
        for (Script.Root r : roots) {
            if (r.chain.isEmpty()) continue;
            x0 = Math.min(x0, r.x);
            y0 = Math.min(y0, r.y);
        }
        if (x0 == Double.MAX_VALUE) return null;
        Item item = new Item(nextId(), trim(name), System.currentTimeMillis());
        for (Script.Root r : roots) {
            if (r.chain.isEmpty()) continue;
            Part part = new Part(r.x - x0, r.y - y0);
            for (Script.Node n : r.chain) part.chain.add(n.copy());
            item.parts.add(part);
        }
        if (item.parts.isEmpty()) return null;
        all().add(0, item);
        while (ITEMS.size() > MAX) ITEMS.remove(ITEMS.size() - 1);
        save();
        return item;
    }

    public static void rename(Item item, String name) {
        if (item == null) return;
        item.name = trim(name);
        save();
    }

    public static void remove(Item item) {
        if (item == null) return;
        all().remove(item);
        save();
    }

    public static String suggestAll(List<Script.Root> roots) {
        List<Script.Node> first = List.of();
        int live = 0;
        for (Script.Root r : roots) {
            if (r.chain.isEmpty()) continue;
            if (live == 0) first = r.chain;
            live++;
        }
        if (live == 0) return "кусок кода";
        String name = suggest(first);
        return live == 1 ? name
                : name + " и ещё " + Ui.plural(live - 1, "стопка", "стопки", "стопок");
    }

    public static String suggest(List<Script.Node> chain) {
        if (chain.isEmpty()) return "кусок кода";
        Script.Node h = chain.get(0);
        if (h.declares()) {
            String own = Functions.nameOf(h);
            return own.isBlank() ? h.action.name : own;
        }
        if (h.invokes()) {
            String own = Functions.targetOf(h);
            return own.isBlank() ? h.action.name : h.action.name + ": " + own;
        }
        return h.action.name;
    }

    private static String trim(String name) {
        String s = name == null ? "" : name.trim();
        if (s.isEmpty()) s = "кусок кода";
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX) : s;
    }

    private static int seq;

    private static String nextId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" + (seq++);
    }

    private static final String[] MONTHS = {"янв", "фев", "мар", "апр", "мая", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"};

    public static String when(long at) {
        if (at <= 0) return "";
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault());
        LocalDate day = t.toLocalDate(), today = LocalDate.now();
        String clock = String.format("%02d:%02d", t.getHour(), t.getMinute());
        if (day.equals(today)) return "сегодня, " + clock;
        if (day.equals(today.minusDays(1))) return "вчера, " + clock;
        String date = day.getDayOfMonth() + " " + MONTHS[day.getMonthValue() - 1];
        return day.getYear() == today.getYear() ? date : date + " " + day.getYear();
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("xerocode/backpack.json");
    }

    private static void keepOld(Path p) {
        try {
            Path copy = p.getParent().resolve("backpack-v1.json");
            if (!Files.exists(copy)) Files.copy(p, copy);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось сохранить копию старого рюкзака", e);
        }
    }

    private static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (!root.has("items")) return;
            if (!root.has("version") || root.get("version").getAsInt() < 2) keepOld(p);
            for (JsonElement e : root.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                Item item = new Item(
                        o.has("id") ? o.get("id").getAsString() : nextId(),
                        o.has("name") ? o.get("name").getAsString() : "кусок кода",
                        o.has("at") ? o.get("at").getAsLong() : 0);
                if (o.has("parts")) {
                    for (JsonElement pe : o.getAsJsonArray("parts")) {
                        JsonObject po = pe.getAsJsonObject();
                        Part part = new Part(po.get("x").getAsDouble(), po.get("y").getAsDouble());
                        part.chain.addAll(Script.readChain(po.getAsJsonArray("chain")));
                        if (!part.chain.isEmpty()) item.parts.add(part);
                    }
                } else if (o.has("chain")) {
                    Part part = new Part(0, 0);
                    part.chain.addAll(Script.readChain(o.getAsJsonArray("chain")));
                    if (!part.chain.isEmpty()) item.parts.add(part);
                }
                if (!item.empty()) ITEMS.add(item);
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] рюкзак не читается", e);
        }
    }

    public static void save() {
        JsonArray items = new JsonArray();
        for (Item i : ITEMS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", i.id);
            o.addProperty("name", i.name);
            o.addProperty("at", i.at);
            JsonArray parts = new JsonArray();
            for (Part p : i.parts) {
                JsonObject po = new JsonObject();
                po.addProperty("x", p.x);
                po.addProperty("y", p.y);
                po.add("chain", Script.writeChain(p.chain));
                parts.add(po);
            }
            o.add("parts", parts);
            if (i.parts.size() == 1) o.add("chain", Script.writeChain(i.parts.get(0).chain));
            items.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.add("items", items);
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] рюкзак не записался", e);
        }
    }

    private Backpack() {}
}
