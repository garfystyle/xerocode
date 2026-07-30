package com.xerocode;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Blocks {
    public record Entry(String id, String name, String category, ItemStack icon) {}

    public static final String NO_ITEM = "Без предмета", OTHER = "Прочее";

    private static final List<Entry> ALL = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static final Map<String, String> NAMES = new HashMap<>();
    private static Object built;

    public static void refresh() {
        ClientWorld world = MinecraftClient.getInstance().world;
        Object token = world == null ? null : world.getRegistryManager();
        if (!ALL.isEmpty() && token == built) return;

        ALL.clear();
        BY_ID.clear();
        NAMES.clear();
        built = token;

        Stacks.refresh();
        Map<String, Integer> tabIndex = new HashMap<>();
        Map<String, String> tabName = new HashMap<>();
        List<Stacks.Tab> tabs = Stacks.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            for (Stacks.Entry e : tabs.get(i).entries()) {
                tabIndex.putIfAbsent(e.id(), i);
                tabName.putIfAbsent(e.id(), tabs.get(i).name());
            }
        }

        List<Integer> order = new ArrayList<>();
        try {
            for (Block block : Registries.BLOCK) {
                Identifier id = Registries.BLOCK.getId(block);
                if (id == null) continue;
                Item item = block.asItem();
                String itemId = item == Items.AIR ? "" : Registries.ITEM.getId(item).toString();
                ItemStack icon = itemId.isEmpty() ? Catalog.stackOf("minecraft:paper")
                        : new ItemStack(item);
                String category = itemId.isEmpty() ? NO_ITEM : tabName.getOrDefault(itemId, OTHER);
                Entry entry = new Entry(id.toString(), name(block, id), category, icon);
                ALL.add(entry);
                order.add(itemId.isEmpty() ? tabs.size() + 1
                        : tabIndex.getOrDefault(itemId, tabs.size()));
            }
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] реестр блоков не прочитался", e);
        }

        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < ALL.size(); i++) rank.put(ALL.get(i).id(), order.get(i));
        ALL.sort((a, b) -> {
            int byTab = Integer.compare(rank.get(a.id()), rank.get(b.id()));
            return byTab != 0 ? byTab : a.name().compareToIgnoreCase(b.name());
        });
        for (Entry e : ALL) {
            BY_ID.put(e.id(), e);
            NAMES.put(e.id(), e.name());
        }
    }

    private static String name(Block block, Identifier id) {
        try {
            String name = block.getName().getString();
            if (!name.isBlank()) return name;
        } catch (Throwable ignored) { }
        return pretty(id.getPath());
    }

    private static String pretty(String path) {
        String[] words = path.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)))
               .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? path : out.toString();
    }

    public static List<Entry> all() {
        refresh();
        return ALL;
    }

    public static Entry entry(String id) {
        if (id == null || id.isEmpty()) return null;
        refresh();
        return BY_ID.get(id);
    }

    public static String name(String id) {
        if (id == null || id.isEmpty()) return "";
        String cached = NAMES.get(id);
        if (cached != null) return cached;
        String name = id;
        try {
            Identifier ident = Identifier.tryParse(id);
            Block block = ident == null ? null
                    : Registries.BLOCK.getOptionalValue(ident).orElse(null);
            if (block != null) name = name(block, ident);
            else if (ident != null) name = pretty(ident.getPath());
        } catch (Throwable ignored) { }
        NAMES.put(id, name);
        return name;
    }

    public static boolean known(String id) {
        if (id == null || id.isEmpty()) return false;
        try {
            Identifier ident = Identifier.tryParse(id);
            return ident != null && Registries.BLOCK.getOptionalValue(ident).isPresent();
        } catch (Throwable e) {
            return false;
        }
    }

    public static ItemStack stack(String id) {
        Entry e = entry(id);
        if (e != null) return e.icon();
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        try {
            Identifier ident = Identifier.tryParse(id);
            Block block = ident == null ? null
                    : Registries.BLOCK.getOptionalValue(ident).orElse(null);
            Item item = block == null ? Items.AIR : block.asItem();
            if (item != Items.AIR) return new ItemStack(item);
        } catch (Throwable ignored) { }
        return known(id) ? Catalog.stackOf("minecraft:paper") : ItemStack.EMPTY;
    }

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        Identifier id = Registries.BLOCK.getId(item.getBlock());
        return id == null ? null : id.toString();
    }

    private Blocks() {}
}
