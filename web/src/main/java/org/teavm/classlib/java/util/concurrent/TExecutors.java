package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.ThreadFactory;

public final class TExecutors {
    private TExecutors() {}

    public static TExecutorService newFixedThreadPool(int n, ThreadFactory factory) { return new Pool(factory); }

    public static TExecutorService newFixedThreadPool(int n) { return new Pool(null); }

    public static TExecutorService newCachedThreadPool(ThreadFactory factory) { return new Pool(factory); }

    public static TExecutorService newCachedThreadPool() { return new Pool(null); }

    public static TExecutorService newSingleThreadExecutor(ThreadFactory factory) { return new Pool(factory); }

    public static TExecutorService newSingleThreadExecutor() { return new Pool(null); }

    private static final class Pool implements TExecutorService {
        private final ThreadFactory factory;
        private boolean shut;

        Pool(ThreadFactory factory) {
            this.factory = factory;
        }

        @Override
        public void execute(Runnable command) {
            if (shut) return;
            Thread t = factory != null ? factory.newThread(command) : new Thread(command);
            t.start();
        }

        @Override
        public void shutdown() { shut = true; }

        @Override
        public boolean isShutdown() { return shut; }
    }
}
