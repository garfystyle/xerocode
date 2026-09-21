package com.xerocode;

import com.xerocode.ui.McText;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Stacks {
    public record Entry(ItemStack stack, String id, String name) {
        static Entry of(ItemStack stack) {
            return new Entry(stack, idOf(stack), stack.getHoverName().getString());
        }
    }

    public record Tab(String name, ItemStack icon, List<Entry> entries) {}

    public record Ench(String id, String name, String description, int max) {}

    private static final List<Tab> TABS = new ArrayList<>();
    private static final List<Entry> ALL = new ArrayList<>();
    private static final List<Ench> ENCHANTS = new ArrayList<>();
    private static Object built;

    private static final Map<String, String> NAMES = new HashMap<>();

    private static final List<String> MODELLED = List.of(
            "minecraft:custom_name", "minecraft:lore", "minecraft:enchantments",
            "minecraft:unbreakable", "minecraft:damage", "minecraft:custom_model_data",
            "minecraft:enchantment_glint_override", "minecraft:tooltip_display");

    public static void refresh() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        Object token = world == null ? null : world.registryAccess();
        if (!TABS.isEmpty() && token == built) return;

        TABS.clear();
        ALL.clear();
        ENCHANTS.clear();
        NAMES.clear();
        PREVIEW.clear();
        built = token;
        if (world == null) { fallback(); return; }

        try {
            CreativeModeTabs.tryRebuildTabContents(world.enabledFeatures(), true,
                    world.registryAccess());
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] could not update the creative display context", e);
        }

        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            if (group.getType() != CreativeModeTab.Type.CATEGORY) continue;
            List<Entry> entries = entries(stacksOf(group));
            if (!entries.isEmpty())
                TABS.add(new Tab(group.getDisplayName().getString(), group.getIconItem(), entries));
        }
        ALL.addAll(entries(stacksOf(CreativeModeTabs.searchTab())));
        if (ALL.isEmpty()) for (Tab t : TABS) ALL.addAll(t.entries());
        if (ALL.isEmpty()) fallback();
    }

    private static void fallback() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = item.getDefaultInstance();
            if (!st.isEmpty()) stacks.add(st);
        }
        ALL.addAll(entries(stacks));
        if (!ALL.isEmpty())
            TABS.add(new Tab("Все предметы", ALL.get(0).stack(), new ArrayList<>(ALL)));
    }

    private static Collection<ItemStack> stacksOf(CreativeModeTab group) {
        try {
            return group.getDisplayItems();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Entry> entries(Collection<ItemStack> stacks) {
        List<Entry> out = new ArrayList<>(stacks.size());
        for (ItemStack st : stacks) if (!st.isEmpty()) out.add(Entry.of(st.copy()));
        return out;
    }

    public static List<Tab> tabs() { return TABS; }

    public static List<Entry> all() { return ALL; }

    public static List<Entry> inventory() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        List<Entry> out = new ArrayList<>();
        if (player == null) return out;
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        stacks.add(player.getOffhandItem());
        for (ItemStack st : stacks) if (!st.isEmpty()) out.add(Entry.of(st.copy()));
        return out;
    }

    public static List<Entry> search(List<Entry> pool, String query, int limit) {
        if (query.isBlank()) return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
        return Search.rank(pool, query, limit, e -> Search.Fields.of(e.name(), e.id()));
    }

    public static List<Ench> enchantments() {
        if (!ENCHANTS.isEmpty()) return ENCHANTS;
        Registry<Enchantment> reg = registry(Registries.ENCHANTMENT);
        if (reg == null) return ENCHANTS;
        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> e : reg.entrySet()) {
            Enchantment ench = e.getValue();
            ENCHANTS.add(new Ench(e.getKey().identifier().toString(),
                    ench.description().getString(),
                    "максимальный уровень " + ench.getMaxLevel(), ench.getMaxLevel()));
        }
        ENCHANTS.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return ENCHANTS;
    }

    public static Ench ench(String id) {
        for (Ench e : enchantments()) if (e.id().equals(id)) return e;
        return null;
    }

    public static String enchLabel(String id, int level) {
        Holder<Enchantment> entry = enchEntry(id);
        if (entry == null) {
            Ench e = ench(id);
            return (e == null ? id : e.name()) + " " + level;
        }
        return Enchantment.getFullname(entry, level).getString();
    }

    private static Holder<Enchantment> enchEntry(String id) {
        Registry<Enchantment> reg = registry(Registries.ENCHANTMENT);
        Identifier ident = id == null ? null : Identifier.tryParse(id);
        if (reg == null || ident == null) return null;
        return reg.get(ident).orElse(null);
    }

    private static <T> Registry<T> registry(ResourceKey<? extends Registry<? extends T>> key) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return null;
        return world.registryAccess().lookup(key).orElse(null);
    }

    public static ItemStack fromServer(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client == null ? null : client.level;
        if (world == null) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            boolean zeros = true;
            for (byte b : raw) if (b != 0) { zeros = false; break; }
            if (zeros) return null;
            CompoundTag nbt = NbtIo.readCompressed(
                    new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, world.registryAccess());
            return ItemStack.CODEC.parse(ops, nbt).result().orElse(null);
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
            RegistryOps<Tag> ops = ops();
            if (ops == null) return null;
            Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (!(encoded instanceof CompoundTag nbt)) return null;
            nbt.putInt(SharedConstants.DATA_VERSION_TAG,
                    SharedConstants.getCurrentVersion().dataVersion().version());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
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

        try {
            DataComponentPatch extra = extras(v.components);
            if (!extra.isEmpty()) st.applyComponentsAndValidate(extra);
        } catch (RuntimeException ignored) {
        }

        if (!v.itemName.isEmpty())
            st.set(DataComponents.CUSTOM_NAME, styled(v.itemName, v.itemParsing, false));
        if (!v.lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : v.lore) lines.add(styled(line, v.itemParsing, true));
            st.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (!v.enchants.isEmpty()) {
            ItemEnchantments.Mutable b =
                    new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (Value.Ench e : v.enchants) {
                Holder<Enchantment> entry = enchEntry(e.id);
                if (entry != null) b.set(entry, Math.max(1, e.level));
            }
            ItemEnchantments comp = b.toImmutable();
            if (!comp.isEmpty()) st.set(DataComponents.ENCHANTMENTS, comp);
        }
        if (v.unbreakable) st.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        if (v.itemDamage > 0) st.set(DataComponents.DAMAGE, v.itemDamage);
        if (v.modelData >= 0)
            st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.of((float) v.modelData), List.of(), List.of(), List.of()));
        if (v.glint != 0) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, v.glint == 1);
        if (v.hideTooltip || !v.hidden.isEmpty()) {
            LinkedHashSet<DataComponentType<?>> hide = new LinkedHashSet<>();
            for (String id : v.hidden) {
                DataComponentType<?> type = componentType(id);
                if (type != null) hide.add(type);
            }
            st.set(DataComponents.TOOLTIP_DISPLAY,
                    new TooltipDisplay(v.hideTooltip, hide));
        }
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
        apply(v, stack.getComponentsPatch());
        CompoundTag nbt = encode(stack.getComponentsPatch());
        v.components = nbt == null || nbt.isEmpty() ? "" : nbt.toString();
    }

    public static void readText(Value v) {
        CompoundTag nbt = compound(v.components);
        if (nbt == null) return;
        CompoundTag mine = new CompoundTag();
        for (String key : MODELLED) {
            Tag el = nbt.get(key);
            if (el != null) mine.put(key, el);
        }
        DataComponentPatch changes = decode(mine);
        if (changes == null) return;
        apply(v, changes);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> got(DataComponentPatch changes, DataComponentType<? extends T> type) {
        Optional<? extends T> value = changes.get(type);
        return value == null ? Optional.empty() : (Optional<T>) value;
    }

    private static void apply(Value v, DataComponentPatch changes) {
        v.itemName = "";
        v.lore.clear();
        v.enchants.clear();
        v.unbreakable = false;
        v.itemDamage = 0;
        v.modelData = -1;
        v.glint = 0;
        v.hideTooltip = false;
        v.hidden.clear();
        if (changes.isEmpty()) return;

        got(changes, DataComponents.CUSTOM_NAME)
                .ifPresent(name -> v.itemName = McText.from(name, v.itemParsing));
        got(changes, DataComponents.LORE).ifPresent(lore -> {
            for (Component line : lore.lines()) v.lore.add(McText.from(line, v.itemParsing));
        });
        got(changes, DataComponents.ENCHANTMENTS).ifPresent(ench -> {
            for (var e : ench.entrySet()) {
                String id = e.getKey().unwrapKey().map(k -> k.identifier().toString()).orElse(null);
                if (id != null) v.enchants.add(new Value.Ench(id, e.getIntValue()));
            }
        });
        v.unbreakable = got(changes, DataComponents.UNBREAKABLE).isPresent();
        got(changes, DataComponents.DAMAGE).ifPresent(damage -> v.itemDamage = damage);
        got(changes, DataComponents.CUSTOM_MODEL_DATA).ifPresent(model -> {
            if (model.floats().size() == 1 && model.flags().isEmpty()
                    && model.strings().isEmpty() && model.colors().isEmpty())
                v.modelData = Math.max(0, (int) model.floats().get(0).floatValue());
        });
        got(changes, DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
                .ifPresent(glint -> v.glint = glint ? 1 : 2);
        got(changes, DataComponents.TOOLTIP_DISPLAY).ifPresent(tip -> {
            v.hideTooltip = tip.hideTooltip();
            for (DataComponentType<?> type : tip.hiddenComponents()) {
                Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                if (id != null) v.hidden.add(id.toString());
            }
        });
    }

    private static DataComponentType<?> componentType(String id) {
        Identifier ident = id == null ? null : Identifier.tryParse(id);
        return ident == null ? null
                : BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ident).orElse(null);
    }

    public static List<Component> tooltip(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        Minecraft client = Minecraft.getInstance();
        try {
            Item.TooltipContext ctx = client.level == null
                    ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(client.level);
            return stack.getTooltipLines(ctx, client.player, TooltipFlag.NORMAL);
        } catch (Exception e) {
            return List.of(stack.getHoverName());
        }
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

    private static RegistryOps<Tag> ops() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return null;
        return RegistryOps.create(NbtOps.INSTANCE, world.registryAccess());
    }

    public static DataComponentPatch components(String snbt) {
        if (snbt == null || snbt.isBlank()) return DataComponentPatch.EMPTY;
        CompoundTag nbt;
        try {
            nbt = TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getRawMessage().getString(), e);
        }
        RegistryOps<Tag> ops = ops();
        if (ops == null) throw new IllegalArgumentException("нет мира — компоненты не разобрать");
        DataResult<DataComponentPatch> parsed = DataComponentPatch.CODEC.parse(ops, nbt);
        if (parsed.error().isPresent())
            throw new IllegalArgumentException(parsed.error().get().message());
        return parsed.result().orElse(DataComponentPatch.EMPTY);
    }

    private static CompoundTag compound(String snbt) {
        if (snbt == null || snbt.isBlank()) return new CompoundTag();
        try {
            return TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static DataComponentPatch decode(CompoundTag nbt) {
        RegistryOps<Tag> ops = ops();
        if (ops == null) return null;
        return DataComponentPatch.CODEC.parse(ops, nbt).result().orElse(null);
    }

    public static DataComponentPatch extras(String snbt) {
        CompoundTag nbt = compound(snbt);
        if (nbt == null) return DataComponentPatch.EMPTY;
        for (String key : MODELLED) nbt.remove(key);
        DataComponentPatch changes = decode(nbt);
        return changes == null ? DataComponentPatch.EMPTY : changes;
    }

    private static CompoundTag encode(DataComponentPatch changes) {
        RegistryOps<Tag> ops = ops();
        if (ops == null) return null;
        Tag el = DataComponentPatch.CODEC.encodeStart(ops, changes).result().orElse(null);
        return el instanceof CompoundTag c ? c : null;
    }

    public static String print(Value v) {
        CompoundTag nbt = encode(build(v).getComponentsPatch());
        return nbt == null ? "" : nbt.toString();
    }

    private static String memoText = "\0";
    private static String memoError;
    private static int memoCount;

    private static void memo(String snbt) {
        if (java.util.Objects.equals(memoText, snbt)) return;
        memoText = snbt;
        memoError = null;
        memoCount = 0;
        if (snbt == null || snbt.isBlank()) return;
        try {
            memoCount = TagParser.parseCompoundFully(snbt).size();
            components(snbt);
        } catch (CommandSyntaxException e) {
            memoError = e.getRawMessage().getString();
        } catch (RuntimeException e) {
            memoError = e.getMessage() == null || e.getMessage().isBlank()
                    ? "не разобрано" : e.getMessage();
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
        CompoundTag nbt = compound(snbt);
        if (nbt == null) return 0;
        for (String key : MODELLED) nbt.remove(key);
        return nbt.size();
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

    private static final Map<Integer, ItemStack> PREVIEW =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, ItemStack> eldest) {
                    return size() > 64;
                }
            };

    public static String indent(String snbt) {
        if (snbt == null || snbt.isBlank()) return snbt;
        CompoundTag before;
        try {
            before = TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException e) {
            return snbt;
        }
        String flat = snbt.trim();
        if (!flat.startsWith("{") || !flat.endsWith("}")) return snbt;
        StringBuilder out = new StringBuilder("{\n");
        int depth = 0;
        boolean quoted = false, escape = false;
        char quote = 0;
        StringBuilder line = new StringBuilder();
        for (int i = 1; i < flat.length() - 1; i++) {
            char c = flat.charAt(i);
            if (quoted) {
                line.append(c);
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == quote) quoted = false;
                continue;
            }
            switch (c) {
                case '"', '\'' -> { quoted = true; quote = c; line.append(c); }
                case '{', '[' -> { depth++; line.append(c); }
                case '}', ']' -> { depth--; line.append(c); }
                case ',' -> {
                    if (depth == 0) { flush(out, line); out.append(",\n"); } else line.append(c);
                }
                case '\n', '\r', '\t' -> line.append(' ');
                default -> line.append(c);
            }
        }
        flush(out, line);
        out.append("\n}");
        String result = out.toString();
        try {
            return TagParser.parseCompoundFully(result).equals(before) ? result : snbt;
        } catch (CommandSyntaxException e) {
            return snbt;
        }
    }

    private static void flush(StringBuilder out, StringBuilder line) {
        String s = line.toString().trim();
        if (!s.isEmpty()) out.append("    ").append(s);
        line.setLength(0);
    }

    private Stacks() {}
}
