package net.minecraft.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

public class Item implements ItemLike {
    private final Identifier id;
    private final String name;
    private final int icon;
    private final int maxStack;
    private final boolean block;

    public Item(Identifier id, String name, int icon, int maxStack, boolean block) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.maxStack = maxStack;
        this.block = block;
    }

    @Override
    public Item asItem() { return this; }

    public Identifier id() { return id; }
    public int icon() { return icon; }
    public boolean isBlock() { return block; }
    public int getDefaultMaxStackSize() { return maxStack; }
    public String getDescriptionId() { return (block ? "block." : "item.") + id.getNamespace() + "." + id.getPath().replace('/', '.'); }
    public Component getName() { return Component.literal(name); }
    public String plainName() { return name; }

    @Override
    public String toString() { return id.toString(); }
}
