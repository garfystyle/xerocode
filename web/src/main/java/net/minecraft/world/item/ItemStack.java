package net.minecraft.world.item;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(Items.AIR, 0);

    private final Item item;
    private int count;
    private DataComponentPatch patch = DataComponentPatch.EMPTY;
    private String displayName;

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(ItemLike item, int count) {
        this.item = item == null ? Items.AIR : item.asItem();
        this.count = count;
    }

    public Item getItem() { return isEmpty() ? Items.AIR : item; }

    public boolean isEmpty() { return this == EMPTY || item == Items.AIR || item == null || count <= 0; }

    public int getCount() { return isEmpty() ? 0 : count; }

    public void setCount(int count) { this.count = count; }

    public int getMaxStackSize() { return item.getDefaultMaxStackSize(); }

    public DataComponentPatch getComponentsPatch() { return patch; }

    public <T> void set(net.minecraft.core.component.DataComponentType<T> type, T value) {
        patch = patch.with(type.id(), value == null ? null : type.encode(value));
    }

    public com.xerocode.web.Nbt.Tag getRaw(String key) { return patch.nbt().get(key); }

    public void setComponentsPatch(DataComponentPatch patch) { this.patch = patch == null ? DataComponentPatch.EMPTY : patch; }

    public ItemStack copy() {
        if (isEmpty()) return EMPTY;
        ItemStack s = new ItemStack(item, count);
        s.patch = patch.copy();
        s.displayName = displayName;
        return s;
    }

    public ItemStack copyWithCount(int n) {
        ItemStack s = copy();
        if (s != EMPTY) s.count = n;
        return s;
    }

    public Component getHoverName() {
        Component custom = patch.customName();
        if (custom != null) return custom;
        Component named = patch.itemName();
        if (named != null) return named;
        return displayName != null ? Component.literal(displayName) : item.getName();
    }

    public void setDisplayName(String name) { this.displayName = name; }

    public boolean is(Item other) { return getItem() == other; }

    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.patch.equals(b.patch);
    }

    public static boolean matches(ItemStack a, ItemStack b) {
        return a == b || (a.getCount() == b.getCount() && isSameItemSameComponents(a, b));
    }

    @Override
    public String toString() { return getCount() + " " + getItem(); }
}
