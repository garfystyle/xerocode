package com.xerocode.ui;

import com.xerocode.web.Js;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class SpriteText {
    public static final List<String> GROUPS = List.of("Предметы", "Блоки");
    private static final String[] DIRS = {"item/", "block/"};
    private static final int BASE = 0x100000, ADVANCE = 8;
    private static final String MARK = "xerocode:sprite ";
    private static final List<String> ORDER = new ArrayList<>();
    private static final List<List<String>> LISTS = new ArrayList<>(List.of(List.of(), List.of()));
    private static final Map<String, Integer> AT = new HashMap<>();
    private static float atlasW, atlasH;
    private static int cols, stride, cell;
    private static boolean loaded;

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = SpriteText.class.getResourceAsStream("/web/sprites.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String[] head = r.readLine().split(" ");
            atlasW = Float.parseFloat(head[1]);
            atlasH = Float.parseFloat(head[2]);
            cols = Integer.parseInt(head[3]);
            stride = Integer.parseInt(head[4]);
            cell = Integer.parseInt(head[5]);
            List<List<String>> lists = List.of(new ArrayList<>(), new ArrayList<>());
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                AT.put(line, AT.size());
                ORDER.add(line);
                for (int g = 0; g < DIRS.length; g++) if (line.startsWith(DIRS[g])) lists.get(g).add(line);
            }
            for (int g = 0; g < DIRS.length; g++) LISTS.set(g, lists.get(g));
        } catch (Exception ignored) {
        }
    }

    public static boolean available() {
        load();
        return !AT.isEmpty();
    }

    public static List<String> ids(int group) {
        load();
        return group < 0 || group >= LISTS.size() ? List.of() : LISTS.get(group);
    }

    public static String tag(int group, String id) {
        return group == 0 ? "<sprite:items:" + id + ">" : "<sprite:" + id + ">";
    }

    public static McText.Run run(String atlas, String sprite, String raw, Style style) {
        load();
        String a = bare(atlas), id = bare(sprite);
        boolean items = "items".equals(a);
        if (!items && !a.isEmpty() && !"blocks".equals(a)) return new McText.Run(raw, style);
        Integer i = AT.get(id);
        if (i == null) return new McText.Run(raw, style);
        String tag = items ? "<sprite:items:" + id + ">" : "<sprite:" + id + ">";
        return new McText.Run(new String(Character.toChars(BASE + i)), style.withInsertion(MARK + tag));
    }

    public static String tagOf(Style style) {
        String s = style.getInsertion();
        return s != null && s.startsWith(MARK) ? s.substring(MARK.length()) : null;
    }

    public static int index(int cp) {
        if (cp < BASE) return -1;
        load();
        return cp - BASE < ORDER.size() ? cp - BASE : -1;
    }

    public static int advance() { return ADVANCE; }

    public static int texture() { return Js.texture("sprites"); }

    public static float[] uv(int index) {
        float u = (index % cols) * stride + 1, v = (index / cols) * stride + 1;
        return new float[]{u / atlasW, (u + cell) / atlasW, v / atlasH, (v + cell) / atlasH};
    }

    private static String bare(String id) {
        String s = id == null ? "" : id.trim();
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    public static Component component(Style style) { return null; }

    public static void draw(GuiGraphicsExtractor ctx, Font tr, int group, String id, int x, int y, int size) {
        load();
        Integer i = AT.get(id);
        int texture = texture();
        if (i == null || texture <= 0) return;
        float[] uv = uv(i);
        ctx.blitTexture(texture, false, x, y, x + size, y + size, uv[0], uv[1], uv[2], uv[3], -1);
    }

    private SpriteText() {}
}
