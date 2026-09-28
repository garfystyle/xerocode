package net.minecraft.core.registries;

import com.xerocode.web.ItemData;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class BuiltInRegistries {
    public static final DefaultedRegistry<Item> ITEM;
    public static final DefaultedRegistry<Block> BLOCK;
    public static final Registry<MobEffect> MOB_EFFECT;

    static {
        ItemData.load();
        ITEM = ItemData.ITEMS;
        BLOCK = ItemData.BLOCKS;
        MOB_EFFECT = ItemData.EFFECTS;
    }

    private BuiltInRegistries() {}
}
