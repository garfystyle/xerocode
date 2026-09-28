package com.mojang.serialization;

import java.util.Optional;
import java.util.function.Function;

public final class DataResult<R> {
    private final R value;
    private final String error;

    private DataResult(R value, String error) {
        this.value = value;
        this.error = error;
    }

    public static <R> DataResult<R> success(R value) { return new DataResult<>(value, null); }

    public static <R> DataResult<R> error(String message) { return new DataResult<>(null, message); }

    public Optional<R> result() { return Optional.ofNullable(value); }

    public Optional<String> errorMessage() { return Optional.ofNullable(error); }

    public boolean isSuccess() { return error == null; }

    public boolean isError() { return error != null; }

    public R getOrThrow() {
        if (error != null) throw new IllegalStateException(error);
        return value;
    }

    public <T> DataResult<T> map(Function<? super R, ? extends T> f) {
        return error != null ? error(error) : success(f.apply(value));
    }
}
