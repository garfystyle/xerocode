package com.xerocode.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class Files2 {
    private Files2() {}

    public static void pick(String[] masks, Consumer<Path> done) {
        StringBuilder accept = new StringBuilder();
        for (String m : masks) {
            String ext = m.startsWith("*") ? m.substring(1) : m;
            if (!accept.isEmpty()) accept.append(',');
            accept.append(ext);
        }
        Js.pickFile(accept.toString(), (name, base64) -> {
            Path path = null;
            if (name != null && !name.isEmpty()) {
                try {
                    String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    path = Path.of("/upload", safe);
                    if (!Files.isDirectory(path.getParent())) Files.createDirectories(path.getParent());
                    Files.write(path, Base64.getDecoder().decode(base64));
                } catch (Exception e) {
                    Console.log("warn", "файл не принят: " + e);
                    path = null;
                }
            }
            Path result = path;
            Minecraft.getInstance().execute(() -> done.accept(result));
        });
    }

    public static void save(String name, byte[] bytes, String mime) {
        Js.saveFile(name, Base64.getEncoder().encodeToString(bytes), mime);
    }
}
