package org.teavm.classlib.java.util.concurrent;

public interface TCompletionStage<T> {
    TCompletableFuture<T> toCompletableFuture();
}
