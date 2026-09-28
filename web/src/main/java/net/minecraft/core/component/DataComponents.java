package net.minecraft.core.component;

import com.xerocode.web.Nbt;
import com.xerocode.web.NbtText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;

public final class DataComponents {
    public static final DataComponentType<Component> CUSTOM_NAME =
            new DataComponentType<>("minecraft:custom_name", NbtText::of);
    public static final DataComponentType<Component> ITEM_NAME =
            new DataComponentType<>("minecraft:item_name", NbtText::of);
    public static final DataComponentType<PotionContents> POTION_CONTENTS =
            new DataComponentType<>("minecraft:potion_contents", DataComponents::potion);

    private DataComponents() {}

    private static Nbt.Tag potion(PotionContents p) {
        Nbt.Compound c = new Nbt.Compound();
        p.potion().ifPresent(h -> c.put("potion", new Nbt.Str(h.id().toString())));
        p.customColor().ifPresent(col -> c.put("custom_color", Nbt.ofInt(col)));
        if (!p.customEffects().isEmpty()) {
            Nbt.ListTag list = new Nbt.ListTag();
            for (MobEffectInstance e : p.customEffects()) {
                Nbt.Compound ec = new Nbt.Compound();
                ec.put("id", new Nbt.Str(e.effect().id().toString()));
                if (e.amplifier() != 0) ec.put("amplifier", Nbt.ofByte(e.amplifier()));
                ec.put("duration", Nbt.ofInt(e.duration()));
                list.items.add(ec);
            }
            c.put("custom_effects", list);
        }
        return c;
    }
}
