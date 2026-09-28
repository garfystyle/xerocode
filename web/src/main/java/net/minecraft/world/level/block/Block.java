package net.minecraft.world.level.block;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class Block implements ItemLike {
    private final Identifier id;
    private final String name;
    private Item item;

    public Block(Identifier id, String name) {
        this.id = id;
        this.name = name;
    }

    public Identifier id() { return id; }

    public void item(Item item) { this.item = item; }

    @Override
    public Item asItem() { return item == null ? Items.AIR : item; }

    public Component getName() { return Component.literal(name); }
}
