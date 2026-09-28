package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TCompletableFuture<T> implements TCompletionStage<T> {
    private boolean done;
    private T value;
    private Throwable error;
    private final List<Runnable> waiters = new ArrayList<>();

    public TCompletableFuture() {}

    public static <U> TCompletableFuture<U> completedFuture(U value) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.complete(value);
        return f;
    }

    public static <U> TCompletableFuture<U> failedFuture(Throwable ex) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        new Thread(() -> run(f, supplier)).start();
        return f;
    }

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        executor.execute(() -> run(f, supplier));
        return f;
    }

    private static <U> void run(TCompletableFuture<U> f, Supplier<U> supplier) {
        try {
            f.complete(supplier.get());
        } catch (Throwable e) {
            f.completeExceptionally(e);
        }
    }

    public static TCompletableFuture<Void> runAsync(Runnable r) {
        return supplyAsync(() -> {
            r.run();
            return null;
        });
    }

    public boolean complete(T v) {
        if (done) return false;
        done = true;
        value = v;
        fire();
        return true;
    }

    public boolean completeExceptionally(Throwable e) {
        if (done) return false;
        done = true;
        error = e;
        fire();
        return true;
    }

    private void fire() {
        List<Runnable> run = new ArrayList<>(waiters);
        waiters.clear();
        for (Runnable r : run) r.run();
    }

    private void onDone(Runnable r) {
        if (done) r.run();
        else waiters.add(r);
    }

    public boolean isDone() { return done; }

    public boolean isCompletedExceptionally() { return done && error != null; }

    public T getNow(T fallback) {
        if (!done) return fallback;
        if (error != null) throw new RuntimeException(error);
        return value;
    }

    public T join() {
        waitDone();
        if (error instanceof RuntimeException re) throw re;
        if (error != null) throw new RuntimeException(error);
        return value;
    }

    public T get() throws ExecutionException {
        waitDone();
        if (error != null) throw new ExecutionException(error);
        return value;
    }

    private void waitDone() {
        Object lock = new Object();
        synchronized (lock) {
            if (done) return;
            onDone(() -> {
                synchronized (lock) {
                    lock.notifyAll();
                }
            });
            while (!done) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    public <U> TCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        TCompletableFuture<U> out = new TCompletableFuture<>();
        onDone(() -> {
            if (error != null) {
                out.completeExceptionally(error);
                return;
            }
            try {
                out.complete(fn.apply(value));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        return out;
    }

    public TCompletableFuture<Void> thenAccept(Consumer<? super T> fn) {
        return thenApply(v -> {
            fn.accept(v);
            return null;
        });
    }

    public TCompletableFuture<Void> thenRun(Runnable fn) {
        return thenApply(v -> {
            fn.run();
            return null;
        });
    }

    public <U> TCompletableFuture<U> thenCompose(Function<? super T, ? extends TCompletionStage<U>> fn) {
        TCompletableFuture<U> out = new TCompletableFuture<>();
        onDone(() -> {
            if (error != null) {
                out.completeExceptionally(error);
                return;
            }
            try {
                TCompletionStage<U> next = fn.apply(value);
                if (next == null) {
                    out.complete(null);
                    return;
                }
                TCompletableFuture<U> f = next.toCompletableFuture();
                f.onDone(() -> {
                    if (f.error != null) out.completeExceptionally(f.error);
                    else out.complete(f.value);
                });
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        return out;
    }

    public TCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        TCompletableFuture<T> out = new TCompletableFuture<>();
        onDone(() -> {
            if (error == null) {
                out.complete(value);
                return;
            }
            try {
                out.complete(fn.apply(error));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        return out;
    }

    public TCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> fn) {
        TCompletableFuture<T> out = new TCompletableFuture<>();
        onDone(() -> {
            try {
                fn.accept(value, error);
            } catch (Throwable ignored) {
            }
            if (error != null) out.completeExceptionally(error);
            else out.complete(value);
        });
        return out;
    }

    public <U> TCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        TCompletableFuture<U> out = new TCompletableFuture<>();
        onDone(() -> {
            try {
                out.complete(fn.apply(value, error));
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });
        return out;
    }

    public TCompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        long ms = unit.toMillis(timeout);
        new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
            }
            completeExceptionally(new TTimeoutException());
        }).start();
        return this;
    }

    public boolean cancel(boolean interrupt) {
        return completeExceptionally(new java.util.concurrent.CancellationException());
    }

    @Override
    public TCompletableFuture<T> toCompletableFuture() { return this; }
}
