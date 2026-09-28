package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Executor;

public interface TExecutorService extends Executor {
    void shutdown();

    boolean isShutdown();
}
