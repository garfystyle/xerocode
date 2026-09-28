package org.slf4j;

public interface Logger {
    void info(String msg, Object... args);

    void warn(String msg, Object... args);

    void error(String msg, Object... args);

    void debug(String msg, Object... args);
}
