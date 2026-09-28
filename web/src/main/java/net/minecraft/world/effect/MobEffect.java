package net.minecraft.world.effect;

import net.minecraft.network.chat.Component;

public final class MobEffect {
    private final String name;
    private final int color;

    public MobEffect(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public int getColor() { return color; }

    public Component getDisplayName() { return Component.literal(name); }
}
