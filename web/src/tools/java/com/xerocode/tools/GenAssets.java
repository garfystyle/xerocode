package com.xerocode.tools;

import java.nio.file.Path;
import java.util.zip.ZipFile;

public final class GenAssets {
    private GenAssets() {}

    private static void unicode(Path symbols, Path res) throws java.io.IOException {
        java.util.TreeSet<Integer> cps = new java.util.TreeSet<>();
        if (java.nio.file.Files.exists(symbols))
            java.nio.file.Files.readString(symbols).codePoints().filter(c -> c > 127).forEach(cps::add);
        StringBuilder sb = new StringBuilder();
        for (int cp : cps) {
            String name = Character.getName(cp);
            if (name != null) sb.append(Integer.toHexString(cp)).append(' ').append(name).append((char) 10);
        }
        java.nio.file.Files.writeString(res.resolve("web/unicode.txt"), sb);
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        Path res = Path.of(args[1]);
        Path site = Path.of(args[2]);
        Path assets = args.length > 3 && !args[3].isEmpty() ? Path.of(args[3]) : null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            FontGen.run(zip, res, site);
            ItemGen.run(zip, assets, Path.of(args[4]), res, site);
            unicode(Path.of("../src/main/resources/assets/xerocode/symbols.json"), res);
        }
    }
}
