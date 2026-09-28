package com.xerocode.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class Storage {
    private static final Path ROOT = Path.of("/game");
    private static final Map<String, Integer> SAVED = new HashMap<>();
    private static double lastSync;

    private Storage() {}

    public static void restore() {
        String list = Js.storedPaths();
        if (list == null || list.isEmpty()) return;
        for (String path : list.split("\n")) {
            if (path.isEmpty()) continue;
            try {
                byte[] bytes = Base64.getDecoder().decode(Js.stored(path));
                Path p = Path.of(path);
                if (p.getParent() != null && !Files.isDirectory(p.getParent())) Files.createDirectories(p.getParent());
                Files.write(p, bytes);
                SAVED.put(path, Arrays.hashCode(bytes) ^ bytes.length);
            } catch (Exception e) {
                Console.log("warn", "не восстановлен " + path + ": " + e);
            }
        }
    }

    public static void tick() {
        double now = Js.now();
        if (now - lastSync < 1500) return;
        lastSync = now;
        sync();
    }

    public static void sync() {
        Set<String> seen = new HashSet<>();
        List<Path> files = new ArrayList<>();
        try {
            if (Files.exists(ROOT)) walk(ROOT, files);
        } catch (IOException e) {
            return;
        }
        for (Path p : files) {
            String key = p.toString().replace('\\', '/');
            seen.add(key);
            try {
                byte[] bytes = Files.readAllBytes(p);
                int hash = Arrays.hashCode(bytes) ^ bytes.length;
                Integer old = SAVED.get(key);
                if (old != null && old == hash) continue;
                SAVED.put(key, hash);
                Js.store(key, Base64.getEncoder().encodeToString(bytes));
            } catch (IOException ignored) {
            }
        }
        for (String key : new ArrayList<>(SAVED.keySet())) {
            if (seen.contains(key)) continue;
            SAVED.remove(key);
            Js.unstore(key);
        }
    }

    private static void walk(Path dir, List<Path> out) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) walk(p, out);
                else out.add(p);
            }
        }
    }
}
