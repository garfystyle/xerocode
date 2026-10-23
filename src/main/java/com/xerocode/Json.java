package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Json {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH.mm.ss");

    public static JsonObject asset(Class<?> owner, String name) throws IOException {
        try (InputStream in = owner.getResourceAsStream("/assets/xerocode/" + name)) {
            if (in == null) {
                XeroCode.LOG.error("[xerocode] в jar нет {}", name);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    public static JsonObject read(Path path) throws IOException {
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    public static void write(Path path, JsonElement data) throws IOException {
        Path dir = path.getParent();
        if (dir != null && !Files.isDirectory(dir)) Files.createDirectories(dir);
        if (Env.browser()) {
            text(path, data);
            return;
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        text(temp, data);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void text(Path path, JsonElement data) throws IOException {
        try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(data.toString());
        }
    }

    public static String setAside(Path path) {
        try {
            String name = path.getFileName().toString();
            String stem = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
            Path to = path.resolveSibling(stem + ".broken-" + LocalDateTime.now().format(STAMP) + ".json");
            Files.move(path, to, StandardCopyOption.REPLACE_EXISTING);
            return to.getFileName().toString();
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] битый файл не удалось отложить", e);
            return "";
        }
    }

    public static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    public static boolean flag(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public static int num(JsonObject o, String key) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static long big(JsonObject o, String key) {
        try {
            return o.has(key) ? o.get(key).getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray()
                ? o.getAsJsonArray(key) : null;
    }

    private Json() {}
}
