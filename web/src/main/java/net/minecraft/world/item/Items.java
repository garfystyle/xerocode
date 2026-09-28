package net.minecraft.world.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class Items {
    public static final Item AIR = get("air");
    public static final Item STONE = get("stone");
    public static final Item BARRIER = get("barrier");
    public static final Item PAPER = get("paper");
    public static final Item BOOK = get("book");
    public static final Item POTION = get("potion");
    public static final Item PLAYER_HEAD = get("player_head");
    public static final Item COMPASS = get("compass");
    public static final Item ENDER_PEARL = get("ender_pearl");
    public static final Item NOTE_BLOCK = get("note_block");
    public static final Item BLAZE_POWDER = get("blaze_powder");
    public static final Item WHITE_WOOL = get("white_wool");
    public static final Item CHEST = get("chest");
    public static final Item NAME_TAG = get("name_tag");
    public static final Item SLIME_BALL = get("slime_ball");
    public static final Item MAGMA_CREAM = get("magma_cream");
    public static final Item PRISMARINE_SHARD = get("prismarine_shard");
    public static final Item IRON_INGOT = get("iron_ingot");
    public static final Item GRASS_BLOCK = get("grass_block");

    private Items() {}

    private static Item get(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }
}
