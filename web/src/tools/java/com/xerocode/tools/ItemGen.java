package com.xerocode.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

final class ItemGen {
    static final int FLAT = 16, CUBE = 48;

    private final ZipFile jar;
    private final Map<String, JsonObject> models = new HashMap<>();
    private final Map<String, BufferedImage> textures = new HashMap<>();
    private BufferedImage grassMap, foliageMap;

    private ItemGen(ZipFile jar) {
        this.jar = jar;
    }

    static void run(ZipFile jar, Path assets, Path tsv, Path res, Path site) throws IOException {
        new ItemGen(jar).build(assets, tsv, res, site);
    }

    private void build(Path assets, Path tsv, Path res, Path site) throws IOException {
        Map<String, String> lang = Lang.load(jar, assets);
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) rows.add(line.split("\t", -1));
        }
        JsonObject version = FontGen.json(jar, "version.json");

        List<BufferedImage> flat = new ArrayList<>(), cube = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        int rendered = 0, failed = 0;
        for (String[] r : rows) {
            if (!r[0].equals("item")) continue;
            String id = r[1];
            String path = id.substring(id.indexOf(':') + 1);
            Icon icon;
            try {
                icon = icon(path);
            } catch (Exception e) {
                System.out.println("иконка " + id + ": " + e);
                icon = null;
            }
            if (icon == null || icon.base == null) {
                failed++;
                continue;
            }
            rendered++;
            List<BufferedImage> into = icon.flat ? flat : cube;
            head.append("icon\t").append(id).append('\t').append(into.size()).append('\t')
                    .append(icon.flat ? "f" : "c").append('\n');
            into.add(icon.base);
            if (icon.overlay != null) {
                head.append("overlay\t").append(id).append('\t').append(flat.size()).append('\t')
                        .append(Integer.toHexString(icon.overlayTint & 0xFFFFFF)).append('\n');
                flat.add(icon.overlay);
            }
        }
        Files.createDirectories(site);
        BufferedImage flatAtlas = pack(flat, FLAT, 64);
        BufferedImage cubeAtlas = pack(cube, CUBE, 32);
        ImageIO.write(flatAtlas, "png", site.resolve("items16.png").toFile());
        ImageIO.write(cubeAtlas, "png", site.resolve("items48.png").toFile());

        StringBuilder out = new StringBuilder();
        out.append("dataversion\t").append(version.get("world_version").getAsInt()).append('\n');
        out.append("atlas\tf\t").append(flatAtlas.getWidth()).append('\t').append(flatAtlas.getHeight()).append('\t').append(FLAT).append('\n');
        out.append("atlas\tc\t").append(cubeAtlas.getWidth()).append('\t').append(cubeAtlas.getHeight()).append('\t').append(CUBE).append('\n');
        out.append(head);
        for (Map.Entry<String, String> e : lang.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("enchantment.level.") || k.startsWith("item.minecraft.") && k.contains(".effect."))
                out.append("lang\t").append(k).append('\t').append(clean(e.getValue())).append('\n');
        }
        for (String[] r : rows) {
            switch (r[0]) {
                case "component" -> out.append("component\t").append(r[1]).append('\n');
                case "block" -> out.append("block\t").append(r[1]).append('\t').append(clean(name(lang, r[2], r[1])))
                        .append('\t').append(r[3].equals("minecraft:air") ? "" : r[3]).append('\n');
                default -> { }
            }
        }
        for (String[] r : rows) {
            switch (r[0]) {
                case "item" -> out.append("item\t").append(r[1]).append('\t').append(clean(name(lang, r[2], r[1])))
                        .append('\t').append(r[3]).append('\t').append(r[4]).append('\n');
                case "tab" -> out.append("tab\t").append(clean(resolve(lang, r[1]))).append('\t').append(r[2]).append('\n');
                case "search" -> out.append("search\t\t\n");
                case "in" -> out.append("in\t").append(r[1]).append('\t').append(r[2]).append('\t')
                        .append(clean(resolve(lang, r[3]))).append('\n');
                case "effect" -> out.append("effect\t").append(r[1]).append('\t').append(clean(name(lang, r[2], r[1])))
                        .append('\t').append(r[3]).append('\n');
                case "potion" -> out.append("potion\t").append(r[1]).append('\t').append(r[2]).append('\n');
                case "ench" -> out.append("ench\t").append(r[1]).append('\t').append(clean(resolve(lang, r[2])))
                        .append('\t').append(r[3]).append('\n');
                default -> { }
            }
        }
        Files.createDirectories(res.resolve("web"));
        Files.writeString(res.resolve("web/items.txt"), out, StandardCharsets.UTF_8);
        System.out.println("items: " + rendered + " icons (" + flat.size() + " flat, " + cube.size()
                + " cube), " + failed + " without icon");
        particles(res, site);
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    private static String name(Map<String, String> lang, String key, String id) {
        String v = lang.get(key);
        if (v != null) return v;
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String resolve(Map<String, String> lang, String encoded) {
        if (encoded.startsWith("=")) return encoded.substring(1);
        String[] parts = encoded.split("\\|");
        String pattern = lang.getOrDefault(parts[0], parts[0]);
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++)
            args.add(parts[i].startsWith("=") ? parts[i].substring(1) : lang.getOrDefault(parts[i], parts[i]));
        StringBuilder sb = new StringBuilder();
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '%' && i + 1 < pattern.length()) {
                char d = pattern.charAt(i + 1);
                if (d == '%') {
                    sb.append('%');
                    i++;
                    continue;
                }
                if (d == 's') {
                    sb.append(next < args.size() ? args.get(next) : "");
                    next++;
                    i++;
                    continue;
                }
                if (Character.isDigit(d) && i + 3 < pattern.length() && pattern.charAt(i + 2) == '$') {
                    int n = d - '1';
                    sb.append(n < args.size() ? args.get(n) : "");
                    i += 3;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private BufferedImage textureOrNull(String id) {
        try {
            return texture(id);
        } catch (IOException e) {
            return null;
        }
    }

    private int[] tintsOf(Choice c) throws IOException {
        int[] tints = new int[16];
        java.util.Arrays.fill(tints, 0xFFFFFFFF);
        if (c.tints != null)
            for (int i = 0; i < c.tints.size() && i < 16; i++) tints[i] = tintColor(c.tints.get(i).getAsJsonObject());
        return tints;
    }

    private record Icon(BufferedImage base, BufferedImage overlay, int overlayTint, boolean flat) {}

    private static BufferedImage pack(List<BufferedImage> cells, int cell, int cols) {
        int rows = Math.max(1, (cells.size() + cols - 1) / cols);
        BufferedImage atlas = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < cells.size(); i++) {
            BufferedImage c = cells.get(i);
            int ox = (i % cols) * cell, oy = (i / cols) * cell;
            for (int y = 0; y < cell; y++)
                for (int x = 0; x < cell; x++) atlas.setRGB(ox + x, oy + y, c.getRGB(x, y));
        }
        return atlas;
    }

    private record Choice(String model, JsonArray tints, JsonObject special, JsonObject transform, List<Choice> parts) {}

    private Icon icon(String item) throws IOException {
        JsonObject def = FontGen.json(jar, "assets/minecraft/items/" + item + ".json");
        Choice c = choose(def.getAsJsonObject("model"));
        if (c == null) return null;
        return render(c);
    }

    private Choice choose(JsonObject node) {
        if (node == null) return null;
        String type = node.get("type").getAsString().replace("minecraft:", "");
        switch (type) {
            case "model":
                return new Choice(node.get("model").getAsString(), node.has("tints") ? node.getAsJsonArray("tints") : null,
                        null, node.has("transformation") ? node.getAsJsonObject("transformation") : null, null);
            case "select":
                if (node.has("fallback")) return choose(node.getAsJsonObject("fallback"));
                return choose(node.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model"));
            case "condition":
                return choose(node.getAsJsonObject("on_false"));
            case "range_dispatch":
                if (node.has("fallback")) return choose(node.getAsJsonObject("fallback"));
                return choose(node.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("model"));
            case "composite": {
                List<Choice> parts = new ArrayList<>();
                for (JsonElement e : node.getAsJsonArray("models")) {
                    Choice c = choose(e.getAsJsonObject());
                    if (c != null) parts.add(c);
                }
                if (parts.isEmpty()) return null;
                if (parts.size() == 1) return parts.get(0);
                return new Choice(parts.get(0).model(), parts.get(0).tints(), null, null, parts);
            }
            case "special":
                return new Choice(node.get("base").getAsString(), null, node.getAsJsonObject("model"),
                        node.has("transformation") ? node.getAsJsonObject("transformation") : null, null);
            default:
                return null;
        }
    }

    private JsonObject model(String id) throws IOException {
        String key = id.replace("minecraft:", "");
        JsonObject have = models.get(key);
        if (have != null) return have;
        JsonObject m = FontGen.json(jar, "assets/minecraft/models/" + key + ".json");
        models.put(key, m);
        return m;
    }

    private static final class Resolved {
        final Map<String, String> textures = new LinkedHashMap<>();
        JsonArray elements;
        JsonObject gui;
        boolean generated;
        boolean entity;
    }

    private Resolved resolve(String id) throws IOException {
        Resolved r = new Resolved();
        List<JsonObject> chain = new ArrayList<>();
        String cur = id;
        for (int depth = 0; cur != null && depth < 32; depth++) {
            String k = cur.replace("minecraft:", "");
            if (k.equals("builtin/generated")) {
                r.generated = true;
                break;
            }
            if (k.equals("builtin/entity")) {
                r.entity = true;
                break;
            }
            JsonObject m = model(cur);
            chain.add(m);
            cur = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            JsonObject m = chain.get(i);
            if (m.has("textures"))
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet())
                    r.textures.put(e.getKey(), e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("sprite").getAsString() : e.getValue().getAsString());
            if (m.has("elements")) r.elements = m.getAsJsonArray("elements");
            if (m.has("display") && m.getAsJsonObject("display").has("gui"))
                r.gui = m.getAsJsonObject("display").getAsJsonObject("gui");
        }
        return r;
    }

    private String texRef(Resolved r, String ref) {
        String t = ref;
        for (int i = 0; i < 16 && t != null && t.startsWith("#"); i++) t = r.textures.get(t.substring(1));
        return t;
    }

    private BufferedImage texture(String id) throws IOException {
        if (id == null) return null;
        String key = id.replace("minecraft:", "");
        if (textures.containsKey(key)) return textures.get(key);
        BufferedImage img = null;
        ZipEntry e = jar.getEntry("assets/minecraft/textures/" + key + ".png");
        if (e != null) {
            try (var in = jar.getInputStream(e)) {
                img = ImageIO.read(in);
            }
            if (img != null && img.getHeight() > img.getWidth()) img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
            if (img != null) {
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                argb.getGraphics().drawImage(img, 0, 0, null);
                img = argb;
            }
        }
        textures.put(key, img);
        return img;
    }

    private int tintColor(JsonObject tint) throws IOException {
        String type = tint.get("type").getAsString().replace("minecraft:", "");
        switch (type) {
            case "constant":
                return tint.get("value").getAsInt();
            case "grass": {
                if (grassMap == null) grassMap = texture("colormap/grass");
                return colormap(grassMap, tint.get("temperature").getAsDouble(), tint.get("downfall").getAsDouble());
            }
            default:
                return tint.has("default") ? tint.get("default").getAsInt() : 0xFFFFFFFF;
        }
    }

    private static boolean dynamic(JsonObject tint) {
        String type = tint.get("type").getAsString().replace("minecraft:", "");
        return type.equals("potion") || type.equals("dye") || type.equals("firework") || type.equals("map_color");
    }

    private static int colormap(BufferedImage map, double temperature, double downfall) {
        if (map == null) return 0xFF7CBD6B;
        double t = Math.max(0, Math.min(1, temperature));
        double d = Math.max(0, Math.min(1, downfall)) * t;
        int x = (int) ((1 - t) * 255), y = (int) ((1 - d) * 255);
        return map.getRGB(Math.min(map.getWidth() - 1, x), Math.min(map.getHeight() - 1, y));
    }

    private Icon render(Choice c) throws IOException {
        Resolved r = resolve(c.model);
        if (c.special != null) {
            List<Cube.Quad> quads = Entity.quads(c.special, c.transform, this::textureOrNull);
            if (quads != null && !quads.isEmpty()) return new Icon(Cube.render(quads, r.gui), null, 0, false);
        }
        if (c.parts != null) {
            List<Cube.Quad> quads = new ArrayList<>();
            for (Choice part : c.parts) {
                Resolved pr = resolve(part.model);
                if (pr.elements == null) continue;
                quads.addAll(Cube.elements(pr.elements, ref -> textureOrNull(texRef(pr, ref)), tintsOf(part),
                        part.transform == null ? null : Entity.matrix(part.transform)));
            }
            if (!quads.isEmpty()) return new Icon(Cube.render(quads, r.gui), null, 0, false);
        }
        int[] tints = new int[16];
        boolean[] dyn = new boolean[16];
        java.util.Arrays.fill(tints, 0xFFFFFFFF);
        if (c.tints != null) {
            for (int i = 0; i < c.tints.size() && i < 16; i++) {
                JsonObject t = c.tints.get(i).getAsJsonObject();
                tints[i] = tintColor(t);
                dyn[i] = dynamic(t);
            }
        }
        if (r.generated || (r.elements == null && r.textures.containsKey("layer0"))) {
            BufferedImage base = new BufferedImage(FLAT, FLAT, BufferedImage.TYPE_INT_ARGB);
            BufferedImage over = null;
            int overTint = 0xFFFFFFFF;
            for (int i = 0; i < 16; i++) {
                String t = r.textures.get("layer" + i);
                if (t == null) continue;
                BufferedImage img = texture(texRef(r, t));
                if (img == null) continue;
                if (dyn[i]) {
                    if (over == null) over = new BufferedImage(FLAT, FLAT, BufferedImage.TYPE_INT_ARGB);
                    blitScaled(img, over, 0xFFFFFFFF);
                    overTint = tints[i];
                } else {
                    blitScaled(img, base, tints[i]);
                }
            }
            return new Icon(base, over, overTint, true);
        }
        if (r.elements != null && !r.entity) {
            return new Icon(Cube.render(Cube.elements(r.elements, ref -> textureOrNull(texRef(r, ref)), tints,
                    c.transform == null ? null : Entity.matrix(c.transform)), r.gui), null, 0, false);
        }
        String particle = texRef(r, "#particle");
        BufferedImage img = texture(particle);
        if (img == null) return null;
        BufferedImage base = new BufferedImage(FLAT, FLAT, BufferedImage.TYPE_INT_ARGB);
        blitScaled(img, base, 0xFFFFFFFF);
        return new Icon(base, null, 0, true);
    }

    static int mul(int argb, int tint) {
        int a = (argb >>> 24) * (tint >>> 24) / 255;
        int rr = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }

    static int over(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 0) return dst;
        if (sa == 255) return src;
        int da = dst >>> 24;
        int oa = sa + da * (255 - sa) / 255;
        if (oa == 0) return 0;
        int r = (((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * da * (255 - sa) / 255) / oa;
        int g = (((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * da * (255 - sa) / 255) / oa;
        int b = ((src & 0xFF) * sa + (dst & 0xFF) * da * (255 - sa) / 255) / oa;
        return (oa << 24) | (r << 16) | (g << 8) | b;
    }

    private static void blitScaled(BufferedImage src, BufferedImage dst, int tint) {
        int w = src.getWidth(), h = src.getHeight();
        int cw = dst.getWidth(), chh = dst.getHeight();
        for (int y = 0; y < chh; y++) {
            for (int x = 0; x < cw; x++) {
                int sx = x * w / cw, sy = y * h / chh;
                int c = mul(src.getRGB(sx, sy), tint);
                dst.setRGB(x, y, over(dst.getRGB(x, y), c));
            }
        }
    }

    private void particles(Path res, Path site) throws IOException {
        Map<String, BufferedImage> used = new LinkedHashMap<>();
        Path defs = res.resolve("web/mc/minecraft/particles");
        Files.createDirectories(defs);
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (!name.startsWith("assets/minecraft/particles/") || !name.endsWith(".json")) continue;
            byte[] raw = FontGen.bytes(jar, name);
            Files.write(defs.resolve(name.substring(name.lastIndexOf('/') + 1)), raw);
            JsonObject o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("textures")) continue;
            for (JsonElement t : o.getAsJsonArray("textures")) {
                String id = t.getAsString();
                if (id.indexOf(':') < 0) id = "minecraft:" + id;
                if (used.containsKey(id)) continue;
                BufferedImage img = texture("particle/" + id.substring(id.indexOf(':') + 1));
                if (img != null) used.put(id, img);
            }
        }
        int w = 1024, x = 0, y = 0, shelf = 0;
        Map<String, int[]> at = new LinkedHashMap<>();
        for (Map.Entry<String, BufferedImage> e : used.entrySet()) {
            BufferedImage img = e.getValue();
            if (x + img.getWidth() > w) {
                x = 0;
                y += shelf + 1;
                shelf = 0;
            }
            at.put(e.getKey(), new int[]{x, y, img.getWidth(), img.getHeight()});
            x += img.getWidth() + 1;
            shelf = Math.max(shelf, img.getHeight());
        }
        int h = Math.max(1, y + shelf);
        BufferedImage atlas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        StringBuilder txt = new StringBuilder("atlas " + w + " " + h + "\n");
        for (Map.Entry<String, int[]> e : at.entrySet()) {
            int[] p = e.getValue();
            BufferedImage img = used.get(e.getKey());
            for (int yy = 0; yy < p[3]; yy++)
                for (int xx = 0; xx < p[2]; xx++) atlas.setRGB(p[0] + xx, p[1] + yy, img.getRGB(xx, yy));
            txt.append(e.getKey()).append(' ').append(p[0]).append(' ').append(p[1]).append(' ')
                    .append(p[2]).append(' ').append(p[3]).append('\n');
        }
        ImageIO.write(atlas, "png", site.resolve("particles.png").toFile());
        Files.writeString(res.resolve("web/particles.txt"), txt, StandardCharsets.UTF_8);
        System.out.println("particles: " + at.size() + " textures");
    }

    static final class Lang {
        static Map<String, String> load(ZipFile jar, Path assets) throws IOException {
            Map<String, String> out = new HashMap<>();
            try {
                JsonObject en = FontGen.json(jar, "assets/minecraft/lang/en_us.json");
                for (Map.Entry<String, JsonElement> e : en.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            } catch (IOException ignored) {
            }
            if (assets == null) return out;
            String version = FontGen.json(jar, "version.json").get("id").getAsString();
            Path meta = assets.getParent().resolve("meta/net.minecraft/" + version + ".json");
            String index = null;
            if (Files.exists(meta)) {
                JsonObject m = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
                if (m.has("assetIndex")) index = m.getAsJsonObject("assetIndex").get("id").getAsString();
            }
            if (index == null) throw new IOException("не найден индекс ассетов для " + version);
            JsonObject objects = JsonParser.parseString(Files.readString(assets.resolve("indexes/" + index + ".json")))
                    .getAsJsonObject().getAsJsonObject("objects");
            String hash = objects.getAsJsonObject("minecraft/lang/ru_ru.json").get("hash").getAsString();
            Path ru = assets.resolve("objects/" + hash.substring(0, 2) + "/" + hash);
            JsonObject ruJson = JsonParser.parseString(Files.readString(ru, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : ruJson.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            return out;
        }
    }

}
