package com.xerocode;

import net.minecraft.client.MinecraftClient;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PickLog {
    public static final boolean ENABLED = Boolean.getBoolean("xerocode.debug");

    public static volatile boolean on;

    private static Writer out;
    private static long started;
    private static int lines, frames;
    private static String lastFrame = "";
    private static int repeats;

    public static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode/pick-log.txt");
    }

    public static void start(String why) {
        if (!ENABLED) return;
        stop("перезапуск записи");
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            out = new BufferedWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE));
            started = System.nanoTime();
            lines = frames = repeats = 0;
            lastFrame = "";
            on = true;
            MinecraftClient client = MinecraftClient.getInstance();
            write("=== выбор местоположения: " + why + " ===");
            write("MC " + client.getGameVersion() + "  окно "
                    + (client.getWindow() == null ? "?" : client.getWindow().getWidth() + "x"
                    + client.getWindow().getHeight() + " scale "
                    + client.getWindow().getScaleFactor())
                    + "  fps " + client.getCurrentFps());
            write("столбцы кадра: t | frame | screen | camera | camPos | camRot | player | точка | hold");
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] не удалось открыть pick-log.txt", e);
            on = false;
        }
    }

    public static void stop(String why) {
        if (out == null) { on = false; return; }
        try {
            flushRepeats();
            write("=== конец: " + why + " · кадров " + frames + " ===");
            out.close();
        } catch (IOException ignored) {
        } finally {
            out = null;
            on = false;
        }
    }

    public static void log(String tag, String msg) {
        if (!on) return;
        flushRepeats();
        write(stamp() + " " + tag + " " + msg);
    }

    public static void frame(String state) {
        if (!on) return;
        frames++;
        if (state.equals(lastFrame)) { repeats++; return; }
        flushRepeats();
        lastFrame = state;
        write(stamp() + " кадр " + frames + " | " + state);
    }

    public static void stack(String tag) {
        if (!on) return;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            if (e.getClassName().startsWith("com.xerocode.PickLog")) continue;
            sb.append("\n        ").append(e.getClassName()).append('.')
                    .append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (++n >= 14) break;
        }
        log(tag, sb.toString());
    }

    private static void flushRepeats() {
        if (repeats <= 0) return;
        int n = repeats;
        repeats = 0;
        write("           … то же ещё " + n + " кадров");
    }

    private static String stamp() {
        return String.format("%7.3fs", (System.nanoTime() - started) / 1.0E9);
    }

    private static void write(String s) {
        if (out == null) return;
        try {
            out.write(s);
            out.write(System.lineSeparator());
            if (++lines % 40 == 0) out.flush();
        } catch (IOException e) {
            on = false;
        }
    }

    private PickLog() {}
}
