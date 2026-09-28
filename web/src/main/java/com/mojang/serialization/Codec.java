package com.mojang.serialization;

public interface Codec<A> {
    <T> DataResult<A> parse(DynamicOps<T> ops, T input);

    <T> DataResult<T> encodeStart(DynamicOps<T> ops, A input);
}
