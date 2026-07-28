package com.xerocode;

import com.xerocode.ui.McText;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

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
            return new Entry(stack, idOf(stack), stack.getName().getString());
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
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        Object token = world == null ? null : world.getRegistryManager();
        if (!TABS.isEmpty() && token == built) return;

        TABS.clear();
        ALL.clear();
        ENCHANTS.clear();
        NAMES.clear();
        PREVIEW.clear();
        built = token;
        if (world == null) { fallback(); return; }

        try {
            ItemGroups.updateDisplayContext(world.getEnabledFeatures(), true,
                    world.getRegistryManager());
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] could not update the creative display context", e);
        }

        for (ItemGroup group : ItemGroups.getGroups()) {
            if (group.getType() != ItemGroup.Type.CATEGORY) continue;
            List<Entry> entries = entries(stacksOf(group));
            if (!entries.isEmpty())
                TABS.add(new Tab(group.getDisplayName().getString(), group.getIcon(), entries));
        }
        ALL.addAll(entries(stacksOf(ItemGroups.getSearchGroup())));
        if (ALL.isEmpty()) for (Tab t : TABS) ALL.addAll(t.entries());
        if (ALL.isEmpty()) fallback();
    }

    private static void fallback() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            ItemStack st = item.getDefaultStack();
            if (!st.isEmpty()) stacks.add(st);
        }
        ALL.addAll(entries(stacks));
        if (!ALL.isEmpty())
            TABS.add(new Tab("Все предметы", ALL.get(0).stack(), new ArrayList<>(ALL)));
    }

    private static Collection<ItemStack> stacksOf(ItemGroup group) {
        try {
            return group.getDisplayStacks();
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
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        List<Entry> out = new ArrayList<>();
        if (player == null) return out;
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().getMainStacks());
        stacks.add(player.getOffHandStack());
        for (ItemStack st : stacks) if (!st.isEmpty()) out.add(Entry.of(st.copy()));
        return out;
    }

    public static List<Entry> search(List<Entry> pool, String query, int limit) {
        if (query.isBlank()) return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
        return Search.rank(pool, query, limit, e -> Search.Fields.of(e.name(), e.id()));
    }

    public static List<Ench> enchantments() {
        if (!ENCHANTS.isEmpty()) return ENCHANTS;
        Registry<Enchantment> reg = registry(RegistryKeys.ENCHANTMENT);
        if (reg == null) return ENCHANTS;
        for (Map.Entry<RegistryKey<Enchantment>, Enchantment> e : reg.getEntrySet()) {
            Enchantment ench = e.getValue();
            ENCHANTS.add(new Ench(e.getKey().getValue().toString(),
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
        RegistryEntry<Enchantment> entry = enchEntry(id);
        if (entry == null) {
            Ench e = ench(id);
            return (e == null ? id : e.name()) + " " + level;
        }
        return Enchantment.getName(entry, level).getString();
    }

    private static RegistryEntry<Enchantment> enchEntry(String id) {
        Registry<Enchantment> reg = registry(RegistryKeys.ENCHANTMENT);
        Identifier ident = id == null ? null : Identifier.tryParse(id);
        if (reg == null || ident == null) return null;
        return reg.getEntry(ident).orElse(null);
    }

    private static <T> Registry<T> registry(RegistryKey<? extends Registry<? extends T>> key) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        return world.getRegistryManager().getOptional(key).orElse(null);
    }

    public static ItemStack fromServer(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world == null) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            boolean zeros = true;
            for (byte b : raw) if (b != 0) { zeros = false; break; }
            if (zeros) return null;
            NbtCompound nbt = NbtIo.readCompressed(
                    new ByteArrayInputStream(raw), NbtSizeTracker.ofUnlimitedBytes());
            RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, world.getRegistryManager());
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
            RegistryOps<NbtElement> ops = ops();
            if (ops == null) return null;
            NbtElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (!(encoded instanceof NbtCompound nbt)) return null;
            nbt.putInt(SharedConstants.DATA_VERSION_KEY,
                    SharedConstants.getGameVersion().dataVersion().id());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] предмет {} не сериализовался", v.itemId, e);
            return null;
        }
    }

    public static String idOf(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    public static ItemStack stack(String id) {
        Identifier ident = id == null || id.isEmpty() ? null : Identifier.tryParse(id);
        if (ident == null) return ItemStack.EMPTY;
        return Registries.ITEM.getOptionalValue(ident).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static String itemName(String id) {
        String cached = NAMES.get(id);
        if (cached != null) return cached;
        ItemStack st = stack(id);
        String name = st.isEmpty() ? id : st.getName().getString();
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
            ComponentChanges extra = extras(v.components);
            if (!extra.isEmpty()) st.applyChanges(extra);
        } catch (RuntimeException ignored) {
        }

        if (!v.itemName.isEmpty())
            st.set(DataComponentTypes.CUSTOM_NAME, styled(v.itemName, v.itemParsing, false));
        if (!v.lore.isEmpty()) {
            List<Text> lines = new ArrayList<>();
            for (String line : v.lore) lines.add(styled(line, v.itemParsing, true));
            st.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
        if (!v.enchants.isEmpty()) {
            ItemEnchantmentsComponent.Builder b =
                    new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            for (Value.Ench e : v.enchants) {
                RegistryEntry<Enchantment> entry = enchEntry(e.id);
                if (entry != null) b.set(entry, Math.max(1, e.level));
            }
            ItemEnchantmentsComponent comp = b.build();
            if (!comp.isEmpty()) st.set(DataComponentTypes.ENCHANTMENTS, comp);
        }
        if (v.unbreakable) st.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        if (v.itemDamage > 0) st.set(DataComponentTypes.DAMAGE, v.itemDamage);
        if (v.modelData >= 0)
            st.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                    List.of((float) v.modelData), List.of(), List.of(), List.of()));
        if (v.glint != 0) st.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, v.glint == 1);
        if (v.hideTooltip || !v.hidden.isEmpty()) {
            LinkedHashSet<ComponentType<?>> hide = new LinkedHashSet<>();
            for (String id : v.hidden) {
                ComponentType<?> type = componentType(id);
                if (type != null) hide.add(type);
            }
            st.set(DataComponentTypes.TOOLTIP_DISPLAY,
                    new TooltipDisplayComponent(v.hideTooltip, hide));
        }
        return st;
    }

    private static Text styled(String raw, String parsing, boolean lore) {
        Style style = Style.EMPTY.withItalic(false);
        if (lore) style = style.withColor(Formatting.GRAY);
        MutableText out = Text.empty().setStyle(style);
        for (McText.Run run : McText.runs(raw, parsing))
            out.append(Text.literal(run.text()).setStyle(run.style()));
        return out;
    }

    public static void read(Value v, ItemStack stack) {
        v.itemId = idOf(stack);
        v.itemCount = Math.max(1, stack.getCount());
        apply(v, stack.getComponentChanges());
        NbtCompound nbt = encode(stack.getComponentChanges());
        v.components = nbt == null || nbt.isEmpty() ? "" : nbt.toString();
    }

    public static void readText(Value v) {
        NbtCompound nbt = compound(v.components);
        if (nbt == null) return;
        NbtCompound mine = new NbtCompound();
        for (String key : MODELLED) {
            NbtElement el = nbt.get(key);
            if (el != null) mine.put(key, el);
        }
        ComponentChanges changes = decode(mine);
        if (changes == null) return;
        apply(v, changes);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> got(ComponentChanges changes, ComponentType<? extends T> type) {
        Optional<? extends T> value = changes.get(type);
        return value == null ? Optional.empty() : (Optional<T>) value;
    }

    private static void apply(Value v, ComponentChanges changes) {
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

        got(changes, DataComponentTypes.CUSTOM_NAME)
                .ifPresent(name -> v.itemName = McText.from(name, v.itemParsing));
        got(changes, DataComponentTypes.LORE).ifPresent(lore -> {
            for (Text line : lore.lines()) v.lore.add(McText.from(line, v.itemParsing));
        });
        got(changes, DataComponentTypes.ENCHANTMENTS).ifPresent(ench -> {
            for (var e : ench.getEnchantmentEntries()) {
                String id = e.getKey().getKey().map(k -> k.getValue().toString()).orElse(null);
                if (id != null) v.enchants.add(new Value.Ench(id, e.getIntValue()));
            }
        });
        v.unbreakable = got(changes, DataComponentTypes.UNBREAKABLE).isPresent();
        got(changes, DataComponentTypes.DAMAGE).ifPresent(damage -> v.itemDamage = damage);
        got(changes, DataComponentTypes.CUSTOM_MODEL_DATA).ifPresent(model -> {
            if (model.floats().size() == 1 && model.flags().isEmpty()
                    && model.strings().isEmpty() && model.colors().isEmpty())
                v.modelData = Math.max(0, (int) model.floats().get(0).floatValue());
        });
        got(changes, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)
                .ifPresent(glint -> v.glint = glint ? 1 : 2);
        got(changes, DataComponentTypes.TOOLTIP_DISPLAY).ifPresent(tip -> {
            v.hideTooltip = tip.hideTooltip();
            for (ComponentType<?> type : tip.hiddenComponents()) {
                Identifier id = Registries.DATA_COMPONENT_TYPE.getId(type);
                if (id != null) v.hidden.add(id.toString());
            }
        });
    }

    private static ComponentType<?> componentType(String id) {
        Identifier ident = id == null ? null : Identifier.tryParse(id);
        return ident == null ? null
                : Registries.DATA_COMPONENT_TYPE.getOptionalValue(ident).orElse(null);
    }

    public static List<Text> tooltip(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            Item.TooltipContext ctx = client.world == null
                    ? Item.TooltipContext.DEFAULT : Item.TooltipContext.create(client.world);
            return stack.getTooltip(ctx, client.player, TooltipType.BASIC);
        } catch (Exception e) {
            return List.of(stack.getName());
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

    private static RegistryOps<NbtElement> ops() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        return RegistryOps.of(NbtOps.INSTANCE, world.getRegistryManager());
    }

    public static ComponentChanges components(String snbt) {
        if (snbt == null || snbt.isBlank()) return ComponentChanges.EMPTY;
        NbtCompound nbt;
        try {
            nbt = StringNbtReader.readCompound(snbt);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getRawMessage().getString(), e);
        }
        RegistryOps<NbtElement> ops = ops();
        if (ops == null) throw new IllegalArgumentException("нет мира — компоненты не разобрать");
        DataResult<ComponentChanges> parsed = ComponentChanges.CODEC.parse(ops, nbt);
        if (parsed.error().isPresent())
            throw new IllegalArgumentException(parsed.error().get().message());
        return parsed.result().orElse(ComponentChanges.EMPTY);
    }

    private static NbtCompound compound(String snbt) {
        if (snbt == null || snbt.isBlank()) return new NbtCompound();
        try {
            return StringNbtReader.readCompound(snbt);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static ComponentChanges decode(NbtCompound nbt) {
        RegistryOps<NbtElement> ops = ops();
        if (ops == null) return null;
        return ComponentChanges.CODEC.parse(ops, nbt).result().orElse(null);
    }

    public static ComponentChanges extras(String snbt) {
        NbtCompound nbt = compound(snbt);
        if (nbt == null) return ComponentChanges.EMPTY;
        for (String key : MODELLED) nbt.remove(key);
        ComponentChanges changes = decode(nbt);
        return changes == null ? ComponentChanges.EMPTY : changes;
    }

    private static NbtCompound encode(ComponentChanges changes) {
        RegistryOps<NbtElement> ops = ops();
        if (ops == null) return null;
        NbtElement el = ComponentChanges.CODEC.encodeStart(ops, changes).result().orElse(null);
        return el instanceof NbtCompound c ? c : null;
    }

    public static String print(Value v) {
        NbtCompound nbt = encode(build(v).getComponentChanges());
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
            memoCount = StringNbtReader.readCompound(snbt).getSize();
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
        NbtCompound nbt = compound(snbt);
        if (nbt == null) return 0;
        for (String key : MODELLED) nbt.remove(key);
        return nbt.getSize();
    }

    public static ItemStack preview(Value v) {
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
        NbtCompound before;
        try {
            before = StringNbtReader.readCompound(snbt);
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
            return StringNbtReader.readCompound(result).equals(before) ? result : snbt;
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
