package net.minecraft.core.component;

import com.xerocode.web.Nbt;
import java.util.function.Function;

public final class DataComponentType<T> {
    private final String id;
    private final Function<T, Nbt.Tag> encoder;

    public DataComponentType(String id, Function<T, Nbt.Tag> encoder) {
        this.id = id;
        this.encoder = encoder;
    }

    public String id() { return id; }

    public Nbt.Tag encode(T value) { return encoder.apply(value); }

    @Override
    public String toString() { return id; }
}
