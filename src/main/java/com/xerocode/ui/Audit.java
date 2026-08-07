package com.xerocode.ui;

import com.xerocode.XeroCode;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Audit {
    public static final int FILL = 0, TEXT = 1, GLYPH = 2, ITEM = 3;

    private static final int MAX = 20000;
    private static final int NEAR = 4, FAR = 8, SKEW = 2, ALMOST = 7;

    private static boolean on;
    private static int frames;
    private static String role = "";
    private static String where = "";
    private static String last = "";

    private static final List<Spot> SPOTS = new ArrayList<>();

    private record Spot(int kind, String role, int x, int y, int w, int h, String said) {
        int right() { return x + w; }
        int bottom() { return y + h; }

        boolean holds(Spot o) {
            return o.x >= x && o.y >= y && o.right() <= right() && o.bottom() <= bottom();
        }

        boolean meets(Spot o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        int area() { return Math.max(0, w) * Math.max(0, h); }

        String name() {
            String kindWord = switch (kind) {
                case TEXT -> "текст";
                case GLYPH -> "значок";
                case ITEM -> "предмет";
                default -> "плитка";
            };
            String tag = role.isEmpty() ? kindWord : kindWord + "/" + role;
            String tail = said.isEmpty() ? "" : " «" + said + "»";
            return tag + tail + " (" + x + "," + y + " " + w + "×" + h + ")";
        }
    }

    public static boolean on() { return on; }

    public static String last() { return last; }

    public static void role(String what) { role = what == null ? "" : what; }

    public static void clearRole() { role = ""; }

    public static void begin(String label) {
        SPOTS.clear();
        where = label == null || label.isBlank() ? screenName() : label;
        frames = 2;
        on = true;
    }

    public static void arm(String label) { begin(label); }

    public static void arm() { begin(null); }

    private static String screenName() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            java.lang.reflect.Field f = MinecraftClient.class.getDeclaredField("field_1755");
            f.setAccessible(true);
            Object screen = f.get(client);
            return screen == null ? "мир" : screen.getClass().getSimpleName();
        } catch (Throwable e) {
            return "экран";
        }
    }

    public static void note(int kind, int x, int y, int w, int h, String said) {
        if (!on || SPOTS.size() >= MAX || w <= 0 || h <= 0) return;
        SPOTS.add(new Spot(kind, role, x, y, w, h, said == null ? "" : said));
    }

    public static void tick() {
        if (!on) return;
        if (--frames > 0) return;
        on = false;
        MinecraftClient client = MinecraftClient.getInstance();
        int sw = client.getWindow() == null ? 0 : client.getWindow().getScaledWidth();
        int sh = client.getWindow() == null ? 0 : client.getWindow().getScaledHeight();
        last = look(sw, sh);
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("xerocode").resolve("audit.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, last, StandardCharsets.UTF_8);
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] отчёт о раскладке не записался", e);
        }
        SPOTS.clear();
    }

    private static List<Spot> unique() {
        List<Spot> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Spot s : SPOTS) {
            if (s.role.equals("block")) continue;
            String key = s.kind + "|" + s.role + "|" + s.x + "|" + s.y + "|" + s.w + "|" + s.h
                    + "|" + s.said;
            if (seen.add(key)) out.add(s);
        }
        return out;
    }

    private static Spot parentOf(List<Spot> fills, Spot kid) {
        Spot best = null;
        for (Spot box : fills) {
            if (box == kid || !box.holds(kid)) continue;
            if (box.area() <= kid.area()) continue;
            if (best == null || box.area() < best.area()) best = box;
        }
        return best;
    }

    public static String look(int sw, int sh) {
        List<Spot> all = unique();
        List<Spot> fills = new ArrayList<>();
        List<Spot> marks = new ArrayList<>();
        for (Spot s : all) {
            if (s.kind == FILL) fills.add(s);
            else marks.add(s);
        }

        List<String> found = new ArrayList<>();
        offScreen(marks, fills, sw, sh, found);
        spill(all, fills, found);
        edges(marks, fills, found);
        middles(marks, fills, found);
        clashes(marks, found);

        StringBuilder sb = new StringBuilder();
        sb.append("ОСМОТР РАСКЛАДКИ · ").append(where)
                .append(" · экран ").append(sw).append("×").append(sh)
                .append(" · плиток ").append(fills.size())
                .append(", подписей ").append(marks.size()).append('\n');
        sb.append("правила: за рамкой · прижат к краю · не по центру · внахлёст\n\n");
        if (found.isEmpty()) {
            sb.append("чисто — ни одной зацепки\n");
            return sb.toString();
        }
        sb.append("зацепок: ").append(found.size()).append('\n');
        for (String line : found) sb.append("  ").append(line).append('\n');
        return sb.toString();
    }

    private static void offScreen(List<Spot> marks, List<Spot> fills, int sw, int sh,
                                  List<String> found) {
        if (sw <= 0 || sh <= 0) return;
        List<Spot> all = new ArrayList<>(marks);
        all.addAll(fills);
        for (Spot s : all) {
            if (s.x >= 0 && s.y >= 0 && s.right() <= sw && s.bottom() <= sh) continue;
            if (s.w >= sw || s.h >= sh) continue;
            found.add("[за экраном] " + s.name());
        }
    }

    private static void spill(List<Spot> all, List<Spot> fills, List<String> found) {
        for (Spot kid : all) {
            if (kid.kind == FILL && kid.area() > 40000) continue;
            Spot box = parentOf(fills, kid);
            if (box != null) continue;
            Spot over = null;
            for (Spot f : fills) {
                if (f == kid || f.area() <= kid.area() || !f.meets(kid)) continue;
                if (f.holds(kid)) continue;
                boolean inside = kid.x >= f.x && kid.x < f.right()
                        && kid.y >= f.y && kid.y < f.bottom();
                if (!inside) continue;
                if (over == null || f.area() < over.area()) over = f;
            }
            if (over != null)
                found.add("[за рамкой] " + kid.name() + " вылезает из " + over.name());
        }
    }

    private static boolean control(Spot s) {
        return s.role.equals("button") || s.role.equals("chip") || s.role.equals("icon")
                || s.role.equals("input");
    }

    private static void edges(List<Spot> marks, List<Spot> fills, List<String> found) {
        List<Spot> kids = new ArrayList<>(marks);
        for (Spot f : fills) if (control(f)) kids.add(f);
        for (Spot kid : kids) {
            Spot box = parentOf(fills, kid);
            if (box == null || box.w < 24 || box.h < 12) continue;
            int left = kid.x - box.x, right = box.right() - kid.right();
            int top = kid.y - box.y, low = box.bottom() - kid.bottom();
            if (left <= 2 && right <= 2) continue;
            if (top <= 2 && low <= 2) continue;
            if (left < NEAR && right >= FAR)
                found.add("[прижат к краю] " + kid.name() + ": слева " + left
                        + ", справа " + right + " · внутри " + box.name());
            else if (right < NEAR && left >= FAR)
                found.add("[прижат к краю] " + kid.name() + ": справа " + right
                        + ", слева " + left + " · внутри " + box.name());
            if (top < 2 && low >= 6)
                found.add("[прижат к краю] " + kid.name() + ": сверху " + top
                        + ", снизу " + low + " · внутри " + box.name());
            else if (low < 2 && top >= 6)
                found.add("[прижат к краю] " + kid.name() + ": снизу " + low
                        + ", сверху " + top + " · внутри " + box.name());
        }
    }

    private static void middles(List<Spot> marks, List<Spot> fills, List<String> found) {
        for (Spot kid : marks) {
            Spot box = parentOf(fills, kid);
            if (box == null || !control(box)) continue;
            int top = kid.y - box.y, low = box.bottom() - kid.bottom();
            if (Math.abs(top - low) > 1)
                found.add("[не по центру] " + kid.name() + ": сверху " + top + ", снизу " + low
                        + " · внутри " + box.name());
            int left = kid.x - box.x, right = box.right() - kid.right();
            int skew = Math.abs(left - right);
            if (skew > SKEW && skew <= ALMOST && left > 2 && right > 2)
                found.add("[не по центру] " + kid.name() + ": слева " + left + ", справа " + right
                        + " · внутри " + box.name());
        }
    }

    private static void clashes(List<Spot> marks, List<String> found) {
        List<Spot> texts = new ArrayList<>();
        for (Spot s : marks) if (s.kind == TEXT && !s.said.isEmpty()) texts.add(s);
        texts.sort(Comparator.comparingInt(s -> s.x));
        for (int i = 0; i < texts.size(); i++)
            for (int j = i + 1; j < texts.size(); j++) {
                Spot a = texts.get(i), b = texts.get(j);
                if (b.x >= a.right()) break;
                if (!a.meets(b)) continue;
                found.add("[внахлёст] " + a.name() + " и " + b.name());
            }
    }

    public static String finish(int sw, int sh) {
        on = false;
        last = look(sw, sh);
        SPOTS.clear();
        return last;
    }

    private Audit() {}
}
