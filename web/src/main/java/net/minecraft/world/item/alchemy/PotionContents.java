package net.minecraft.world.item.alchemy;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;

public record PotionContents(Optional<Holder<Object>> potion, Optional<Integer> customColor,
                             List<MobEffectInstance> customEffects, Optional<String> customName) {
}
