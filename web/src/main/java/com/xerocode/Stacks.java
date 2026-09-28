package com.xerocode;

import com.xerocode.ui.McText;
import com.xerocode.web.ItemData;
import com.xerocode.web.Lang;
import com.xerocode.web.Nbt;
import com.xerocode.web.NbtText;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class Stacks {
    public record Entry(ItemStack stack, String id, String name) {
        static Entry of(ItemStack stack) {
            return new Entry(stack, idOf(stack), stack.getHoverName().getString());
        }
    }

    public record Tab(String name, ItemStack icon, List<Entry> entries) {}

    public record Ench(String id, String name, String description, int max) {}

    private static final String NAME = "minecraft:custom_name", LORE = "minecraft:lore",
            ENCHANTS = "minecraft:enchantments", UNBREAKABLE = "minecraft:unbreakable",
            DAMAGE = "minecraft:damage", MODEL = "minecraft:custom_model_data",
            GLINT = "minecraft:enchantment_glint_override", TOOLTIP = "minecraft:tooltip_display";

    private static final List<String> MODELLED = List.of(NAME, LORE, ENCHANTS, UNBREAKABLE, DAMAGE, MODEL, GLINT, TOOLTIP);

    private static final List<Tab> TABS = new ArrayList<>();
    private static final List<Entry> ALL = new ArrayList<>();
    private static final List<Ench> ENCHANTS_LIST = new ArrayList<>();
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static int serverDataVersion;

    private Stacks() {}

    public static void refresh() {
        if (!TABS.isEmpty()) return;
        for (ItemData.Tab t : ItemData.tabs()) {
            List<Entry> entries = new ArrayList<>();
            for (ItemData.Entry e : t.entries()) {
                ItemStack st = make(e);
                if (!st.isEmpty()) entries.add(new Entry(st, e.id(), e.name()));
            }
            if (!entries.isEmpty()) TABS.add(new Tab(t.name(), stack(t.icon()), entries));
        }
        for (ItemData.Entry e : ItemData.search()) {
            ItemStack st = make(e);
            if (!st.isEmpty()) ALL.add(new Entry(st, e.id(), e.name()));
        }
        if (ALL.isEmpty()) for (Tab t : TABS) ALL.addAll(t.entries());
    }

    private static ItemStack make(ItemData.Entry e) {
        ItemStack st = stack(e.id());
        if (st.isEmpty()) return st;
        if (!e.components().isEmpty()) {
            try {
                st.setComponentsPatch(new DataComponentPatch(Nbt.parseCompound(e.components())));
            } catch (Nbt.SyntaxError ignored) {
            }
        }
        st.setDisplayName(e.name());
        return st;
    }

    public static List<Tab> tabs() { return TABS; }

    public static List<Entry> all() { return ALL; }

    public static List<Entry> inventory() { return new ArrayList<>(); }

    public static List<Entry> search(List<Entry> pool, String query, int limit) {
        if (query.isBlank()) return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
        return Search.rank(pool, query, limit, e -> Search.Fields.of(e.name(), e.id()));
    }

    public static List<Ench> enchantments() {
        if (!ENCHANTS_LIST.isEmpty()) return ENCHANTS_LIST;
        for (ItemData.Ench e : ItemData.enchants())
            ENCHANTS_LIST.add(new Ench(e.id(), e.name(), "максимальный уровень " + e.max(), e.max()));
        ENCHANTS_LIST.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return ENCHANTS_LIST;
    }

    public static Ench ench(String id) {
        for (Ench e : enchantments()) if (e.id().equals(id)) return e;
        return null;
    }

    public static String enchLabel(String id, int level) {
        Ench e = ench(id);
        String name = e == null ? id : e.name();
        if (e != null && level == 1 && e.max() == 1) return name;
        String roman = Lang.get("enchantment.level." + level);
        return name + " " + (roman == null ? String.valueOf(level) : roman);
    }

    public static ItemStack fromServer(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            boolean zeros = true;
            for (byte b : raw) if (b != 0) { zeros = false; break; }
            if (zeros) return null;
            Nbt.Compound nbt = Nbt.readCompressed(raw);
            int seen = nbt.getInt("DataVersion", 0);
            if (seen > 0) serverDataVersion = seen;
            ItemStack st = stack(nbt.getString("id"));
            if (st.isEmpty()) return null;
            st.setCount(Math.max(1, nbt.getInt("count", 1)));
            Nbt.Compound comps = nbt.getCompound("components");
            if (comps != null && !comps.isEmpty()) st.setComponentsPatch(new DataComponentPatch(comps.copy()));
            return st;
        } catch (Exception e) {
            return null;
        }
    }

    public static Value valueFromServer(String encoded) {
        try {
            ItemStack stack = fromServer(encoded);
            if (stack == null || stack.isEmpty()) return null;
            Value v = new Value(Value.ITEM);
            read(v, stack);
            v.itemRaw = encoded;
            v.itemRawHash = v.hash();
            return v;
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] предмет с сервера не разобрался", e);
            return null;
        }
    }

    public static String toServer(Value v) {
        if (v == null || v.itemId.isEmpty()) return null;
        if (!v.itemRaw.isEmpty() && v.itemRawHash == v.hash()) return v.itemRaw;
        try {
            ItemStack stack = build(v);
            if (stack.isEmpty()) return null;
            Nbt.Compound nbt = new Nbt.Compound();
            int mine = ItemData.dataVersion();
            nbt.put("DataVersion", Nbt.ofInt(serverDataVersion > 0 ? Math.min(mine, serverDataVersion) : mine));
            nbt.put("id", new Nbt.Str(idOf(stack)));
            nbt.put("count", Nbt.ofInt(stack.getCount()));
            Nbt.Compound comps = stack.getComponentsPatch().nbt();
            if (!comps.isEmpty()) nbt.put("components", comps.copy());
            return Base64.getEncoder().encodeToString(Nbt.writeCompressed(nbt));
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] предмет {} не сериализовался", v.itemId, e);
            return null;
        }
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static ItemStack stack(String id) {
        Identifier ident = id == null || id.isEmpty() ? null : Identifier.tryParse(id);
        if (ident == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(ident).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static String itemName(String id) {
        String cached = NAMES.get(id);
        if (cached != null) return cached;
        ItemStack st = stack(id);
        String name = st.isEmpty() ? id : st.getHoverName().getString();
        NAMES.put(id, name);
        return name;
    }

    public static String plainName(Value v) {
        if (!v.itemName.isEmpty()) {
            String plain = McText.plain(v.itemName, v.itemParsing);
            if (!plain.isEmpty()) return plain;
        }
        return itemName(v.itemId);
    }

    public static ItemStack build(Value v) {
        ItemStack st = stack(v.itemId);
        if (st.isEmpty()) return st;
        st.setCount(Math.max(1, Math.min(99, v.itemCount)));
        Nbt.Compound c = extrasNbt(v.components);
        if (c == null) c = new Nbt.Compound();
        if (!v.itemName.isEmpty()) c.put(NAME, NbtText.of(styled(v.itemName, v.itemParsing, false)));
        if (!v.lore.isEmpty()) {
            Nbt.ListTag lines = new Nbt.ListTag();
            for (String line : v.lore) lines.items.add(NbtText.of(styled(line, v.itemParsing, true)));
            c.put(LORE, lines);
        }
        if (!v.enchants.isEmpty()) {
            Nbt.Compound levels = new Nbt.Compound();
            for (Value.Ench e : v.enchants) if (ench(e.id) != null) levels.put(e.id, Nbt.ofInt(Math.max(1, e.level)));
            if (!levels.isEmpty()) c.put(ENCHANTS, levels);
        }
        if (v.unbreakable) c.put(UNBREAKABLE, new Nbt.Compound());
        if (v.itemDamage > 0) c.put(DAMAGE, Nbt.ofInt(v.itemDamage));
        if (v.modelData >= 0) {
            Nbt.Compound model = new Nbt.Compound();
            Nbt.ListTag floats = new Nbt.ListTag();
            floats.items.add(new Nbt.Num(Nbt.FLOAT, (float) v.modelData));
            model.put("floats", floats);
            c.put(MODEL, model);
        }
        if (v.glint != 0) c.put(GLINT, Nbt.ofByte(v.glint == 1 ? 1 : 0));
        if (v.hideTooltip || !v.hidden.isEmpty()) {
            Nbt.Compound tip = new Nbt.Compound();
            if (v.hideTooltip) tip.put("hide_tooltip", Nbt.ofByte(1));
            if (!v.hidden.isEmpty()) {
                Nbt.ListTag hidden = new Nbt.ListTag();
                for (String id : v.hidden) if (ItemData.component(id)) hidden.items.add(new Nbt.Str(id));
                tip.put("hidden_components", hidden);
            }
            c.put(TOOLTIP, tip);
        }
        st.setComponentsPatch(new DataComponentPatch(c));
        return st;
    }

    private static Component styled(String raw, String parsing, boolean lore) {
        Style style = Style.EMPTY.withItalic(false);
        if (lore) style = style.withColor(ChatFormatting.GRAY);
        MutableComponent out = Component.empty().setStyle(style);
        for (McText.Run run : McText.runs(raw, parsing))
            out.append(Component.literal(run.text()).setStyle(run.style()));
        return out;
    }

    public static void read(Value v, ItemStack stack) {
        v.itemId = idOf(stack);
        v.itemCount = Math.max(1, stack.getCount());
        Nbt.Compound nbt = stack.getComponentsPatch().nbt();
        apply(v, nbt);
        v.components = nbt.isEmpty() ? "" : nbt.toString();
    }

    public static void readText(Value v) {
        Nbt.Compound nbt = compound(v.components);
        if (nbt == null) return;
        Nbt.Compound mine = new Nbt.Compound();
        for (String key : MODELLED) {
            Nbt.Tag el = nbt.get(key);
            if (el != null) mine.put(key, el);
        }
        apply(v, mine);
    }

    private static void apply(Value v, Nbt.Compound c) {
        v.itemName = "";
        v.lore.clear();
        v.enchants.clear();
        v.unbreakable = false;
        v.itemDamage = 0;
        v.modelData = -1;
        v.glint = 0;
        v.hideTooltip = false;
        v.hidden.clear();
        if (c.isEmpty()) return;
        Component name = NbtText.component(c.get(NAME));
        if (name != null) v.itemName = McText.from(name, v.itemParsing);
        if (c.get(LORE) instanceof Nbt.ListTag lines) {
            for (Nbt.Tag line : lines.items) {
                Component t = NbtText.component(line);
                if (t != null) v.lore.add(McText.from(t, v.itemParsing));
            }
        }
        if (c.get(ENCHANTS) instanceof Nbt.Compound ench) {
            Nbt.Compound levels = ench.get("levels") instanceof Nbt.Compound l ? l : ench;
            for (Map.Entry<String, Nbt.Tag> e : levels.map.entrySet())
                if (e.getValue() instanceof Nbt.Num n) v.enchants.add(new Value.Ench(e.getKey(), n.asInt()));
        }
        v.unbreakable = c.contains(UNBREAKABLE);
        if (c.get(DAMAGE) instanceof Nbt.Num d) v.itemDamage = d.asInt();
        if (c.get(MODEL) instanceof Nbt.Compound model) {
            if (model.get("floats") instanceof Nbt.ListTag f && f.items.size() == 1 && model.size() == 1
                    && f.items.get(0) instanceof Nbt.Num n)
                v.modelData = Math.max(0, (int) n.asDouble());
        }
        if (c.get(GLINT) instanceof Nbt.Num g) v.glint = g.asInt() != 0 ? 1 : 2;
        if (c.get(TOOLTIP) instanceof Nbt.Compound tip) {
            v.hideTooltip = tip.get("hide_tooltip") instanceof Nbt.Num h && h.asInt() != 0;
            if (tip.get("hidden_components") instanceof Nbt.ListTag hidden)
                for (Nbt.Tag t : hidden.items) if (t instanceof Nbt.Str s) v.hidden.add(s.value);
        }
    }

    public static List<Component> tooltip(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<Component> out = new ArrayList<>();
        Nbt.Compound c = stack.getComponentsPatch().nbt();
        Component custom = NbtText.component(c.get(NAME));
        out.add(custom != null ? Component.empty().withStyle(Style.EMPTY.withItalic(true)).append(custom)
                : stack.getHoverName());
        if (c.get(ENCHANTS) instanceof Nbt.Compound ench) {
            Nbt.Compound levels = ench.get("levels") instanceof Nbt.Compound l ? l : ench;
            for (Map.Entry<String, Nbt.Tag> e : levels.map.entrySet()) {
                int level = e.getValue() instanceof Nbt.Num n ? n.asInt() : 1;
                boolean curse = e.getKey().contains("curse");
                out.add(Component.literal(enchLabel(e.getKey(), level))
                        .withStyle(curse ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }
        if (c.get(LORE) instanceof Nbt.ListTag lines) {
            for (Nbt.Tag line : lines.items) {
                Component t = NbtText.component(line);
                if (t != null) out.add(Component.empty().withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE)
                        .withItalic(true)).append(t));
            }
        }
        if (c.contains(UNBREAKABLE))
            out.add(Component.literal("Неразрушимый").withStyle(ChatFormatting.BLUE));
        return out;
    }

    public static String summary(Value v) {
        List<String> parts = new ArrayList<>();
        if (!v.itemName.isEmpty()) parts.add("название");
        if (!v.lore.isEmpty()) parts.add("описание " + v.lore.size());
        if (!v.enchants.isEmpty()) parts.add("чары " + v.enchants.size());
        if (v.unbreakable) parts.add("неразрушимый");
        if (v.itemDamage > 0) parts.add("прочность " + v.itemDamage);
        if (v.modelData >= 0) parts.add("модель " + v.modelData);
        if (v.glint == 1) parts.add("блеск");
        if (v.glint == 2) parts.add("без блеска");
        if (v.hideTooltip) parts.add("без подсказки");
        if (!v.hidden.isEmpty()) parts.add("скрыто " + v.hidden.size());
        int extra = extraCount(v.components);
        if (extra > 0) parts.add("компонентов " + extra);
        return String.join(" · ", parts);
    }

    public static DataComponentPatch components(String snbt) {
        if (snbt == null || snbt.isBlank()) return DataComponentPatch.EMPTY;
        Nbt.Compound nbt;
        try {
            nbt = Nbt.parseCompound(snbt);
        } catch (Nbt.SyntaxError e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        for (String key : nbt.map.keySet()) {
            String id = key.startsWith("!") ? key.substring(1) : key;
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!ItemData.component(id)) throw new IllegalArgumentException("неизвестный компонент " + key);
        }
        return new DataComponentPatch(nbt);
    }

    private static Nbt.Compound compound(String snbt) {
        if (snbt == null || snbt.isBlank()) return new Nbt.Compound();
        try {
            return Nbt.parseCompound(snbt);
        } catch (Nbt.SyntaxError e) {
            return null;
        }
    }

    private static Nbt.Compound extrasNbt(String snbt) {
        Nbt.Compound nbt = compound(snbt);
        if (nbt == null) return null;
        for (String key : MODELLED) nbt.remove(key);
        return nbt;
    }

    public static DataComponentPatch extras(String snbt) {
        Nbt.Compound nbt = extrasNbt(snbt);
        return nbt == null ? DataComponentPatch.EMPTY : new DataComponentPatch(nbt);
    }

    public static String print(Value v) {
        Nbt.Compound nbt = build(v).getComponentsPatch().nbt();
        return nbt.isEmpty() ? "" : nbt.toString();
    }

    private static String memoText = "\0";
    private static String memoError;
    private static int memoCount;

    private static void memo(String snbt) {
        if (Objects.equals(memoText, snbt)) return;
        memoText = snbt;
        memoError = null;
        memoCount = 0;
        if (snbt == null || snbt.isBlank()) return;
        try {
            memoCount = Nbt.parseCompound(snbt).size();
            components(snbt);
        } catch (Nbt.SyntaxError e) {
            memoError = e.getMessage();
        } catch (RuntimeException e) {
            memoError = e.getMessage() == null || e.getMessage().isBlank() ? "не разобрано" : e.getMessage();
        }
    }

    public static String error(String snbt) {
        memo(snbt);
        return memoError;
    }

    public static int componentCount(String snbt) {
        memo(snbt);
        return memoCount;
    }

    public static int extraCount(String snbt) {
        Nbt.Compound nbt = extrasNbt(snbt);
        return nbt == null ? 0 : nbt.size();
    }

    public static ItemStack preview(Value v) {
        if (Value.BLOCK.equals(v.type)) return Blocks.stack(v.block);
        int hash = v.hash();
        ItemStack cached = PREVIEW.get(hash);
        if (cached != null) return cached;
        ItemStack built = build(v);
        PREVIEW.put(hash, built);
        return built;
    }

    private static final Map<Integer, ItemStack> PREVIEW = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ItemStack> eldest) {
            return size() > 64;
        }
    };

    public static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final String BUKKIT = "PublicBukkitValues";
    public static final String TAG_PREFIX = "justcreativeplus:";

    private static Nbt.Compound child(Nbt.Compound c, String key) {
        Nbt.Compound got = c == null ? null : c.getCompound(key);
        return got == null ? new Nbt.Compound() : got;
    }

    public static List<String[]> tags(String snbt) {
        List<String[]> out = new ArrayList<>();
        Nbt.Compound nbt = compound(snbt);
        if (nbt == null) return out;
        Nbt.Compound pbv = child(child(nbt, CUSTOM_DATA), BUKKIT);
        for (Map.Entry<String, Nbt.Tag> e : pbv.map.entrySet()) {
            if (!e.getKey().startsWith(TAG_PREFIX)) continue;
            String value = e.getValue() instanceof Nbt.Str str ? str.value : e.getValue().toString();
            out.add(new String[]{e.getKey().substring(TAG_PREFIX.length()), value});
        }
        out.sort((a, b) -> a[0].compareTo(b[0]));
        return out;
    }

    private static Nbt.Compound ours(Nbt.Compound nbt) {
        Nbt.Compound out = new Nbt.Compound();
        for (Map.Entry<String, Nbt.Tag> e : child(child(nbt, CUSTOM_DATA), BUKKIT).map.entrySet())
            if (e.getKey().startsWith(TAG_PREFIX)) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    private static void mergeTags(Nbt.Compound nbt, Nbt.Compound tags) {
        Nbt.Compound data = child(nbt, CUSTOM_DATA).copy();
        Nbt.Compound pbv = child(data, BUKKIT).copy();
        pbv.map.keySet().removeIf(k -> k.startsWith(TAG_PREFIX));
        pbv.map.putAll(tags.map);
        if (pbv.isEmpty()) data.remove(BUKKIT); else data.put(BUKKIT, pbv);
        if (data.isEmpty()) nbt.remove(CUSTOM_DATA); else nbt.put(CUSTOM_DATA, data);
    }

    public static String withTags(String snbt, List<String[]> tags) {
        Nbt.Compound nbt = compound(snbt);
        if (nbt == null) return snbt;
        Nbt.Compound mine = new Nbt.Compound();
        for (String[] t : tags) {
            String key = t[0].trim();
            if (!key.isEmpty()) mine.put(TAG_PREFIX + key, new Nbt.Str(t[1]));
        }
        mergeTags(nbt, mine);
        return nbt.isEmpty() ? "" : nbt.toString();
    }

    public static List<String[]> extraRows(String snbt) {
        List<String[]> out = new ArrayList<>();
        Nbt.Compound nbt = compound(snbt);
        if (nbt == null) return out;
        for (Map.Entry<String, Nbt.Tag> e : nbt.map.entrySet()) {
            if (MODELLED.contains(e.getKey())) continue;
            Nbt.Tag value = e.getValue();
            if (CUSTOM_DATA.equals(e.getKey()) && value instanceof Nbt.Compound data) {
                Nbt.Compound rest = data.copy();
                Nbt.Compound pbv = child(rest, BUKKIT).copy();
                pbv.map.keySet().removeIf(k -> k.startsWith(TAG_PREFIX));
                if (pbv.isEmpty()) rest.remove(BUKKIT); else rest.put(BUKKIT, pbv);
                if (rest.isEmpty()) continue;
                value = rest;
            }
            out.add(new String[]{e.getKey(), value.toString()});
        }
        return out;
    }

    private static Nbt.Tag parseTag(String snbt) {
        try {
            return Nbt.parse(snbt.trim());
        } catch (Nbt.SyntaxError | RuntimeException e) {
            return null;
        }
    }

    public static String componentId(String id) {
        String s = id.trim();
        boolean gone = s.startsWith("!");
        if (gone) s = s.substring(1);
        if (!s.isEmpty() && s.indexOf(':') < 0) s = "minecraft:" + s;
        return (gone ? "!" : "") + s;
    }

    public static String withRows(String snbt, List<String[]> rows) {
        Nbt.Compound old = compound(snbt);
        if (old == null) old = new Nbt.Compound();
        Nbt.Compound out = new Nbt.Compound();
        for (String key : MODELLED) if (old.contains(key)) out.put(key, old.get(key));
        for (String[] r : rows) {
            String id = componentId(r[0]);
            Nbt.Tag tag = id.isEmpty() ? null : parseTag(r[1]);
            if (tag != null) out.put(id, tag);
        }
        mergeTags(out, ours(old));
        return out.isEmpty() ? "" : out.toString();
    }

    public static String rowError(String id, String value) {
        String key = componentId(id);
        if (key.isEmpty()) return "нет имени компонента";
        String bare = key.startsWith("!") ? key.substring(1) : key;
        if (!ItemData.component(bare)) return "нет такого компонента";
        return parseTag(value) == null ? "значение не разбирается как SNBT" : null;
    }

    public static String hint(String id) {
        String key = componentId(id);
        if (key.startsWith("!")) return "снять компонент · значение {}";
        Components.Info info = Components.of(key);
        return info == null ? "" : info.name() + " · " + info.example();
    }

    public static List<String> componentIds() { return ItemData.components(); }

    public static String pretty(String snbt) { return indent(snbt); }

    public static String compact(String snbt) {
        if (snbt == null || snbt.isBlank()) return "";
        Nbt.Compound nbt = compound(snbt);
        return nbt == null ? snbt : nbt.isEmpty() ? "" : nbt.toString();
    }

    public static String indent(String snbt) {
        if (snbt == null || snbt.isBlank()) return snbt;
        Nbt.Compound before = compound(snbt);
        if (before == null) return snbt;
        String flat = snbt.trim();
        if (!flat.startsWith("{") || !flat.endsWith("}")) return snbt;
        StringBuilder out = new StringBuilder("{\n");
        int depth = 0;
        boolean quoted = false, escape = false;
        char quote = 0;
        StringBuilder line = new StringBuilder();
        for (int i = 1; i < flat.length() - 1; i++) {
            char ch = flat.charAt(i);
            if (quoted) {
                line.append(ch);
                if (escape) escape = false;
                else if (ch == '\\') escape = true;
                else if (ch == quote) quoted = false;
                continue;
            }
            switch (ch) {
                case '"', '\'' -> { quoted = true; quote = ch; line.append(ch); }
                case '{', '[' -> { depth++; line.append(ch); }
                case '}', ']' -> { depth--; line.append(ch); }
                case ',' -> {
                    if (depth == 0) { flush(out, line); out.append(",\n"); } else line.append(ch);
                }
                case '\n', '\r', '\t' -> line.append(' ');
                default -> line.append(ch);
            }
        }
        flush(out, line);
        out.append("\n}");
        String result = out.toString();
        Nbt.Compound after = compound(result);
        return after != null && after.equals(before) ? result : snbt;
    }

    private static void flush(StringBuilder out, StringBuilder line) {
        String s = line.toString().trim();
        if (!s.isEmpty()) out.append("    ").append(s);
        line.setLength(0);
    }
}
