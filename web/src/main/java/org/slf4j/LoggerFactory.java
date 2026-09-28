package org.slf4j;

import com.xerocode.web.Console;

public final class LoggerFactory {
    private LoggerFactory() {}

    public static Logger getLogger(String name) {
        return new Logger() {
            @Override public void info(String msg, Object... args) { Console.log("info", format(msg, args)); }
            @Override public void warn(String msg, Object... args) { Console.log("warn", format(msg, args)); }
            @Override public void error(String msg, Object... args) { Console.log("error", format(msg, args)); }
            @Override public void debug(String msg, Object... args) { }
        };
    }

    private static String format(String msg, Object... args) {
        StringBuilder sb = new StringBuilder();
        int a = 0, i = 0;
        while (i < msg.length()) {
            int at = msg.indexOf("{}", i);
            if (at < 0 || a >= args.length) {
                sb.append(msg, i, msg.length());
                break;
            }
            sb.append(msg, i, at).append(args[a++]);
            i = at + 2;
        }
        for (; a < args.length; a++) {
            Object o = args[a];
            if (o instanceof Throwable t) {
                sb.append('\n').append(t);
                for (StackTraceElement e : t.getStackTrace()) sb.append("\n  at ").append(e);
            } else {
                sb.append(' ').append(o);
            }
        }
        return sb.toString();
    }
}
