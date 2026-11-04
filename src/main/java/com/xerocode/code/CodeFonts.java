package com.xerocode.code;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CodeFonts {
    public record Entry(String id, String title, String file) {}

    public static final String PIXEL = "pixel", CUSTOM = "file:", DEFAULT = "jetbrains";
    public static final List<Entry> LIST = List.of(
            new Entry(DEFAULT, "JetBrains Mono", "JetBrainsMono-Regular.ttf"),
            new Entry("fira", "Fira Code", "FiraCode-Regular.ttf"),
            new Entry("source", "Source Code Pro", "SourceCodePro-Regular.ttf"),
            new Entry(PIXEL, "Пиксельный, как в игре", null));

    private static final String ROOT = "/assets/xerocode/fonts/";
    private static final Map<String, byte[]> LOADED = new HashMap<>();

    private CodeFonts() {}

    public static Entry find(String id) {
        for (Entry e : LIST) if (e.id.equals(id)) return e;
        return null;
    }

    public static String title(String id) {
        if (id != null && id.startsWith(CUSTOM)) {
            String name = id.substring(CUSTOM.length()).replace('\\', '/');
            return name.substring(name.lastIndexOf('/') + 1);
        }
        Entry e = find(id);
        return e == null ? LIST.get(0).title : e.title;
    }

    public static synchronized void preload() {
        for (Entry e : LIST) if (e.file != null) bytes(e.id);
    }

    public static synchronized byte[] bytes(String id) {
        byte[] known = LOADED.get(id);
        if (known != null) return known;
        try {
            byte[] read = null;
            if (id.startsWith(CUSTOM)) read = Files.readAllBytes(Path.of(id.substring(CUSTOM.length())));
            else {
                Entry e = find(id);
                if (e == null || e.file == null) return null;
                try (InputStream in = CodeFonts.class.getResourceAsStream(ROOT + e.file)) {
                    if (in != null) read = in.readAllBytes();
                }
            }
            if (read != null) LOADED.put(id, read);
            return read;
        } catch (IOException e) {
            return null;
        }
    }
}
