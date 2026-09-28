package com.xerocode.web;

import net.minecraft.world.item.ItemStack;

public final class ItemIcons {
    private ItemIcons() {}

    public static int icon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return stack.getItem().icon();
    }

    public static int overlay(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return ItemData.overlay(stack.getItem().id().toString());
    }

    public static int tint(ItemStack stack) {
        String id = stack.getItem().id().toString();
        Nbt.Tag potion = stack.getRaw("minecraft:potion_contents");
        if (potion != null) return 0xFF000000 | potionColor(potion);
        if (stack.getRaw("minecraft:dyed_color") instanceof Nbt.Num n) return 0xFF000000 | n.asInt();
        if (stack.getRaw("minecraft:dyed_color") instanceof Nbt.Compound c) return 0xFF000000 | c.getInt("rgb", 0xA06540);
        return 0xFF000000 | ItemData.overlayTint(id);
    }

    private static int potionColor(Nbt.Tag tag) {
        if (tag instanceof Nbt.Str s) {
            Integer c = ItemData.potionColor(s.value);
            return c == null ? 0x385DC6 : c;
        }
        if (!(tag instanceof Nbt.Compound c)) return 0x385DC6;
        if (c.get("custom_color") instanceof Nbt.Num n) return n.asInt();
        if (c.get("custom_effects") instanceof Nbt.ListTag list && !list.items.isEmpty()) {
            int r = 0, g = 0, b = 0, n = 0;
            for (Nbt.Tag t : list.items) {
                if (!(t instanceof Nbt.Compound e)) continue;
                Integer col = ItemData.effectColor(e.getString("id"));
                if (col == null) continue;
                int w = e.getInt("amplifier", 0) + 1;
                r += ((col >> 16) & 0xFF) * w;
                g += ((col >> 8) & 0xFF) * w;
                b += (col & 0xFF) * w;
                n += w;
            }
            if (n > 0) return ((r / n) << 16) | ((g / n) << 8) | (b / n);
        }
        String potion = c.getString("potion");
        Integer pc = potion.isEmpty() ? null : ItemData.potionColor(potion);
        return pc == null ? 0x385DC6 : pc;
    }
}
