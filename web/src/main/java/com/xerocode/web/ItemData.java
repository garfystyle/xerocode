package com.xerocode.web;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ItemData {
    public record Entry(String id, String components, String name) {}

    public record Tab(String name, String icon, List<Entry> entries) {}

    public record Ench(String id, String name, int max) {}

    public static final DefaultedRegistry<Item> ITEMS = new DefaultedRegistry<>(Identifier.withDefaultNamespace("air"), Item::id);
    public static final DefaultedRegistry<Block> BLOCKS = new DefaultedRegistry<>(Identifier.withDefaultNamespace("air"), Block::id);
    public static final Registry<MobEffect> EFFECTS = new Registry<>();

    private static final List<Tab> TABS = new ArrayList<>();
    private static final List<Entry> SEARCH = new ArrayList<>();
    private static final List<Ench> ENCHANTS = new ArrayList<>();
    private static final Map<String, Integer> POTION_COLORS = new HashMap<>();
    private static final Map<String, Integer> EFFECT_COLORS = new HashMap<>();
    private static final Map<String, Integer> ICONS = new HashMap<>();
    private static final Map<String, Integer> OVERLAYS = new HashMap<>();
    private static final Map<String, Integer> OVERLAY_TINTS = new HashMap<>();
    private static final java.util.Set<String> COMPONENTS = new java.util.HashSet<>();
    public static final int CUBE = 1 << 20;
    private static final int[] ATLAS_W = {1, 1}, ATLAS_H = {1, 1}, CELL = {16, 48};
    private static int dataVersion = 4671;
    private static boolean loaded;

    private ItemData() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Map<Block, String> blockItem = new HashMap<>();
        try (InputStream in = ItemData.class.getResourceAsStream("/web/items.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            List<Entry> current = null;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "atlas" -> {
                        int k = "c".equals(f[1]) ? 1 : 0;
                        ATLAS_W[k] = Integer.parseInt(f[2]);
                        ATLAS_H[k] = Integer.parseInt(f[3]);
                        CELL[k] = Integer.parseInt(f[4]);
                    }
                    case "icon" -> ICONS.put(f[1], Integer.parseInt(f[2]) + ("c".equals(f[3]) ? CUBE : 0));
                    case "overlay" -> {
                        OVERLAYS.put(f[1], Integer.parseInt(f[2]));
                        OVERLAY_TINTS.put(f[1], (int) Long.parseLong(f[3], 16));
                    }
                    case "dataversion" -> dataVersion = Integer.parseInt(f[1]);
                    case "component" -> COMPONENTS.add(f[1]);
                    case "lang" -> Lang.put(f[1], f[2]);
                    case "block" -> {
                        Block b = new Block(Identifier.parse(f[1]), f[2]);
                        BLOCKS.register(b);
                        if (!f[3].isEmpty()) blockItem.put(b, f[3]);
                    }
                    case "item" -> {
                        Identifier id = Identifier.parse(f[1]);
                        int icon = ICONS.getOrDefault(f[1], -1);
                        int max = Integer.parseInt(f[3]);
                        String blockId = f[4];
                        Item item;
                        if (!blockId.isEmpty()) {
                            Block b = BLOCKS.getValue(Identifier.parse(blockId));
                            item = new BlockItem(id, f[2], icon, max, b);
                        } else {
                            item = new Item(id, f[2], icon, max, false);
                        }
                        ITEMS.register(item);
                    }
                    case "tab" -> {
                        current = new ArrayList<>();
                        TABS.add(new Tab(f[1], f[2], current));
                    }
                    case "search" -> current = SEARCH;
                    case "in" -> {
                        if (current != null) current.add(new Entry(f[1], f[2], f[3]));
                    }
                    case "effect" -> {
                        int color = (int) Long.parseLong(f[3], 16);
                        EFFECTS.register(Identifier.parse(f[1]), new MobEffect(f[2], color));
                        EFFECT_COLORS.put(f[1], color);
                    }
                    case "potion" -> POTION_COLORS.put(f[1], (int) Long.parseLong(f[2], 16));
                    case "ench" -> ENCHANTS.add(new Ench(f[1], f[2], Integer.parseInt(f[3])));
                    default -> { }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("items.txt", e);
        }
        for (Map.Entry<Block, String> e : blockItem.entrySet())
            ITEMS.getOptional(Identifier.parse(e.getValue())).ifPresent(e.getKey()::item);
        if (!ITEMS.containsKey(Identifier.withDefaultNamespace("air")))
            ITEMS.register(new Item(Identifier.withDefaultNamespace("air"), "Воздух", -1, 64, false));
    }

    public static List<Tab> tabs() { return TABS; }
    public static List<Entry> search() { return SEARCH; }
    public static List<Ench> enchants() { return ENCHANTS; }
    public static Integer potionColor(String id) { return POTION_COLORS.get(id); }
    public static Integer effectColor(String id) { return EFFECT_COLORS.get(id); }
    public static int dataVersion() { return dataVersion; }
    public static boolean component(String id) { return COMPONENTS.isEmpty() || COMPONENTS.contains(id); }

    public static List<String> components() {
        List<String> out = new ArrayList<>(COMPONENTS);
        out.sort(String::compareTo);
        return out;
    }
    public static int overlay(String itemId) { return OVERLAYS.getOrDefault(itemId, -1); }
    public static int overlayTint(String itemId) { return OVERLAY_TINTS.getOrDefault(itemId, 0xFFFFFF); }
    public static int icon(String itemId) { return ICONS.getOrDefault(itemId, -1); }
    public static int atlasW(int kind) { return ATLAS_W[kind]; }
    public static int atlasH(int kind) { return ATLAS_H[kind]; }
    public static int cell(int kind) { return CELL[kind]; }
}
