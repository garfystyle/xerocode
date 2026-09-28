package net.minecraft.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public final class Registry<T> implements Iterable<T> {
    private final Map<Identifier, Holder.Reference<T>> byId = new HashMap<>();
    private final Map<T, Identifier> keys = new HashMap<>();
    private final List<T> ordered = new ArrayList<>();

    public void register(Identifier id, T value) {
        if (byId.containsKey(id)) return;
        byId.put(id, new Holder.Reference<>(id, value));
        keys.put(value, id);
        ordered.add(value);
    }

    public Optional<Holder.Reference<T>> get(Identifier id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public Optional<T> getOptional(Identifier id) {
        Holder.Reference<T> h = id == null ? null : byId.get(id);
        return Optional.ofNullable(h == null ? null : h.value());
    }

    public Identifier getKey(T value) { return keys.get(value); }

    public List<T> values() { return ordered; }

    @Override
    public Iterator<T> iterator() { return ordered.iterator(); }
}
