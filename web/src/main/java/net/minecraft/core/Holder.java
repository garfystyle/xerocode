package net.minecraft.core;

import net.minecraft.resources.Identifier;

public interface Holder<T> {
    T value();

    Identifier id();

    final class Reference<T> implements Holder<T> {
        private final Identifier id;
        private final T value;

        public Reference(Identifier id, T value) {
            this.id = id;
            this.value = value;
        }

        @Override public T value() { return value; }
        @Override public Identifier id() { return id; }
    }
}
