package net.minecraft.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

public final class DefaultedRegistry<T> implements Iterable<T> {
    private final Identifier defaultKey;
    private final Function<T, Identifier> keyOf;
    private final Map<Identifier, T> byId = new HashMap<>();
    private final List<T> ordered = new ArrayList<>();

    public DefaultedRegistry(Identifier defaultKey, Function<T, Identifier> keyOf) {
        this.defaultKey = defaultKey;
        this.keyOf = keyOf;
    }

    public void register(T value) {
        Identifier id = keyOf.apply(value);
        if (byId.putIfAbsent(id, value) == null) ordered.add(value);
    }

    public Identifier getKey(T value) {
        return value == null ? defaultKey : keyOf.apply(value);
    }

    public Optional<T> getOptional(Identifier id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public T getValue(Identifier id) {
        T v = id == null ? null : byId.get(id);
        return v != null ? v : byId.get(defaultKey);
    }

    public boolean containsKey(Identifier id) { return byId.containsKey(id); }

    public int getId(T value) { return ordered.indexOf(value); }

    public T byId(int id) { return id >= 0 && id < ordered.size() ? ordered.get(id) : byId.get(defaultKey); }

    public int size() { return ordered.size(); }

    public List<T> values() { return Collections.unmodifiableList(ordered); }

    public Stream<T> stream() { return ordered.stream(); }

    @Override
    public Iterator<T> iterator() { return values().iterator(); }
}
