package net.minecraft.world.effect;

import net.minecraft.core.Holder;

public record MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }
}
