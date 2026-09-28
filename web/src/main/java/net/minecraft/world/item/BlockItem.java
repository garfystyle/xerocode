package net.minecraft.world.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class BlockItem extends Item {
    private final Block block;

    public BlockItem(Identifier id, String name, int icon, int maxStack, Block block) {
        super(id, name, icon, maxStack, true);
        this.block = block;
    }

    public Block getBlock() { return block; }
}
