package net.minecraft.core.component;

import com.google.gson.JsonElement;
import com.xerocode.web.Nbt;
import com.xerocode.web.NbtText;
import net.minecraft.network.chat.Component;

public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch(new Nbt.Compound());

    private final Nbt.Compound nbt;

    public DataComponentPatch(Nbt.Compound nbt) {
        this.nbt = nbt;
    }

    public Nbt.Compound nbt() { return nbt; }

    public boolean isEmpty() { return nbt.isEmpty(); }

    public int size() { return nbt.size(); }

    public DataComponentPatch copy() { return this == EMPTY ? EMPTY : new DataComponentPatch(nbt.copy()); }

    public Component customName() { return NbtText.component(nbt.get("minecraft:custom_name")); }

    public Component itemName() { return NbtText.component(nbt.get("minecraft:item_name")); }

    public DataComponentPatch with(String key, Nbt.Tag value) {
        Nbt.Compound c = nbt.copy();
        if (value == null) c.remove(key);
        else c.put(key, value);
        return new DataComponentPatch(c);
    }

    public static JsonElement json(Nbt.Tag tag) { return NbtText.json(tag); }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof DataComponentPatch p && nbt.equals(p.nbt));
    }

    @Override
    public int hashCode() { return nbt.hashCode(); }
}
