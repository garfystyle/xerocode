package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Pickers {
    private static final Map<String, ItemStack> POTION_ITEMS = new HashMap<>();

    public static ItemStack potionStack(String effectId) {
        ItemStack cached = POTION_ITEMS.get(effectId);
        if (cached != null) return cached;
        ItemStack stack = new ItemStack(Items.POTION);
        try {
            Identifier id = Identifier.tryParse(effectId);
            RegistryEntry.Reference<StatusEffect> entry = id == null ? null
                    : Registries.STATUS_EFFECT.getEntry(id).orElse(null);
            if (entry != null)
                stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                        Optional.empty(), Optional.empty(),
                        List.of(new StatusEffectInstance(entry, 1)), Optional.empty()));
        } catch (RuntimeException ignored) {
        }
        POTION_ITEMS.put(effectId, stack);
        return stack;
    }

    public static final class Entry {
        public final String id, name, category, item, description;
        public final List<String> extras;
        Entry(String id, String name, String category, String item, String description,
              List<String> extras) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.item = item;
            this.description = description;
            this.extras = extras;
        }
        public boolean has(String extra) { return extras.contains(extra); }
    }

    public static final List<Entry> SOUNDS = new ArrayList<>();
    public static final List<Entry> PARTICLES = new ArrayList<>();
    public static final List<Entry> POTIONS = new ArrayList<>();

    public static final Map<String, Integer> SOUND_CATEGORIES = new LinkedHashMap<>();
    public static final Map<String, Integer> PARTICLE_CATEGORIES = new LinkedHashMap<>();
    public static final Map<String, Integer> POTION_CATEGORIES = new LinkedHashMap<>();

    private static final Map<String, Entry> BY_ID = new HashMap<>();

    public static final String MOTION = "Движение", COLOR = "Цвет", SIZE = "Размер",
            MATERIAL = "Материал", TO_COLOR = "Цвет перехода";

    public static final List<String> SOURCES = List.of(
            "MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS",
            "AMBIENT", "VOICE");

    public static String sourceName(String id) {
        return switch (id) {
            case "MASTER" -> "Общий";
            case "MUSIC" -> "Музыка";
            case "RECORDS" -> "Пластинки";
            case "WEATHER" -> "Погода";
            case "BLOCKS" -> "Блоки";
            case "HOSTILE" -> "Враждебные";
            case "NEUTRAL" -> "Мирные";
            case "PLAYERS" -> "Игроки";
            case "AMBIENT" -> "Окружение";
            case "VOICE" -> "Голос";
            default -> id;
        };
    }

    public static boolean loaded() { return !PARTICLES.isEmpty(); }

    public static void load() {
        if (loaded()) return;
        try (InputStream in = Pickers.class.getResourceAsStream("/assets/xerocode/pickers.json")) {
            if (in == null) { XeroCode.LOG.error("[xerocode] pickers.json not found in the jar"); return; }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            if (root.has("sounds")) {
                for (JsonElement ce : root.getAsJsonArray("sounds")) {
                    JsonObject co = ce.getAsJsonObject();
                    String cat = co.get("n").getAsString();
                    JsonArray list = co.getAsJsonArray("v");
                    SOUND_CATEGORIES.put(cat, list.size());
                    for (JsonElement se : list) {
                        String id = se.getAsString();
                        add(SOUNDS, new Entry("minecraft:" + id, id, cat, "minecraft:nautilus_shell",
                                "", List.of()));
                    }
                }
            }
            readList(root, "particles", PARTICLES, PARTICLE_CATEGORIES);
            readList(root, "potions", POTIONS, POTION_CATEGORIES);
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read pickers.json", e);
        }
    }

    private static void readList(JsonObject root, String key, List<Entry> out,
                                 Map<String, Integer> categories) {
        if (!root.has(key)) return;
        for (JsonElement e : root.getAsJsonArray(key)) {
            JsonObject o = e.getAsJsonObject();
            List<String> extras = new ArrayList<>();
            if (o.has("x")) for (JsonElement xe : o.getAsJsonArray("x")) extras.add(xe.getAsString());
            String cat = str(o, "c");
            Entry entry = new Entry(o.get("id").getAsString(), str(o, "n"), cat, str(o, "i"),
                    str(o, "d"), extras);
            add(out, entry);
            categories.merge(cat, 1, Integer::sum);
        }
    }

    private static final char SOUND = 's', PARTICLE = 'p', POTION = 'e';

    private static void add(List<Entry> list, Entry e) {
        list.add(e);
        BY_ID.put(key(list == SOUNDS ? SOUND : list == PARTICLES ? PARTICLE : POTION, e.id), e);
    }

    private static String key(char tag, String id) { return tag + "\0" + id; }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    public static Entry sound(String id)    { return BY_ID.get(key(SOUND, id)); }
    public static Entry particle(String id) { return BY_ID.get(key(PARTICLE, id)); }
    public static Entry potion(String id)   { return BY_ID.get(key(POTION, id)); }

    public static String soundName(String id) {
        Entry e = sound(id);
        return e != null ? e.name : (id == null ? "" : id.replace("minecraft:", ""));
    }

    public static String particleName(String id) {
        Entry e = particle(id);
        return e != null ? e.name : (id == null ? "" : id);
    }

    public static String potionName(String id) {
        Entry e = potion(id);
        return e != null ? e.name : (id == null ? "" : id.replace("minecraft:", ""));
    }

    public static List<Entry> search(List<Entry> pool, String query, int limit) {
        if (query.isBlank()) return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
        return Search.rank(pool, query, limit,
                e -> new Search.Fields(e.name, e.id, e.category, e.description));
    }

    private Pickers() {}
}
