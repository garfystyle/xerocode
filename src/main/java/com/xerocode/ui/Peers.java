package com.xerocode.ui;

import com.xerocode.Collab;
import com.xerocode.Script;
import com.xerocode.Settings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;

import java.util.ArrayList;
import java.util.List;

public final class Peers {
    private static final double TAU = 0.055;
    private static final double SNAP = 260;
    private static final int PILL_H = 11;
    private static final int LABEL_DX = 10, LABEL_DY = 12;

    private static final String[] MASK = {
            "X",
            "XX",
            "XXX",
            "XXXX",
            "XXXXX",
            "XXXXXX",
            "XXXXXXX",
            "XXXX",
            "XX XXX",
            "X   XXX",
            "     XX"
    };

    private static final int ARROW_W = width(MASK);
    private static final int ARROW_H = MASK.length;
    private static final int[] FILL = spans(grid(), false);
    private static final int[] EDGE = spans(grid(), true);

    private static int width(String[] rows) {
        int w = 0;
        for (String row : rows) w = Math.max(w, row.length());
        return w;
    }

    private static boolean[][] grid() {
        boolean[][] on = new boolean[ARROW_H][ARROW_W];
        for (int y = 0; y < ARROW_H; y++)
            for (int x = 0; x < MASK[y].length(); x++)
                on[y][x] = MASK[y].charAt(x) != ' ';
        return on;
    }

    private static int[] spans(boolean[][] on, boolean outline) {
        int h = ARROW_H + 2, w = ARROW_W + 2;
        boolean[][] want = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean inside = y > 0 && y <= ARROW_H && x > 0 && x <= ARROW_W
                        && on[y - 1][x - 1];
                if (!outline) { want[y][x] = inside; continue; }
                if (inside) continue;
                for (int dy = -1; dy <= 1 && !want[y][x]; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y - 1 + dy, nx = x - 1 + dx;
                        if (ny >= 0 && ny < ARROW_H && nx >= 0 && nx < ARROW_W && on[ny][nx]) {
                            want[y][x] = true;
                            break;
                        }
                    }
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            int at = 0;
            while (at < w) {
                if (!want[y][at]) { at++; continue; }
                int end = at;
                while (end < w && want[y][end]) end++;
                out.add(at - 1);
                out.add(y - 1);
                out.add(end - at);
                at = end;
            }
        }
        int[] flat = new int[out.size()];
        for (int i = 0; i < flat.length; i++) flat[i] = out.get(i);
        return flat;
    }

    private static final class Spot {
        Collab.Peer peer;
        int x, y, nameX, nameY, nameW;
        String name;
    }

    private static final List<Spot> POOL = new ArrayList<>();
    private static int shown;
    private static long lastNanos;

    private static Spot spot() {
        while (POOL.size() <= shown) POOL.add(new Spot());
        return POOL.get(shown++);
    }

    public static void render(DrawContext ctx, TextRenderer tr, Script script, Layout layout,
                              ScreenRect area, int left, int top, double panX, double panY,
                              double zoom, int width, int height) {
        if (Collab.peerCount() == 0 || !Collab.live() || !Settings.get().collabCursors) return;

        long now = System.nanoTime();
        double step = lastNanos == 0 ? 1 / 60.0 : (now - lastNanos) / 1e9;
        lastNanos = now;
        double ease = step <= 0 ? 1 : 1 - Math.exp(-Math.min(step, 0.5) / TAU);

        shown = 0;
        for (Collab.Peer peer : Collab.peers()) {
            double dx = peer.x - peer.drawX, dy = peer.y - peer.drawY;
            if (!peer.drawn || dx * dx + dy * dy > SNAP * SNAP) {
                peer.drawX = peer.x;
                peer.drawY = peer.y;
                peer.drawn = true;
            } else {
                peer.drawX += dx * ease;
                peer.drawY += dy * ease;
            }
            int sx = (int) Math.round(left + panX + peer.drawX * zoom);
            int sy = (int) Math.round(top + panY + peer.drawY * zoom);
            if (sx < left - 60 || sx > width + 60 || sy < top - 60 || sy > height + 60) continue;
            Spot spot = spot();
            spot.peer = peer;
            spot.x = sx;
            spot.y = sy;
            spot.name = peer.name.isEmpty() ? "…" : peer.name;
            spot.nameW = tr.getWidth(spot.name) + 9;
            spot.nameX = Math.max(left + 2, Math.min(sx + LABEL_DX, width - spot.nameW - 2));
            spot.nameY = Math.max(top + 2, Math.min(sy + LABEL_DY, height - PILL_H - 2));
        }
        if (shown == 0) return;

        int back = Theme.LIGHT ? 0xF4F6FA : 0x141821;
        Draw.batch(Batch.open(ctx, area, area, 96 * shown + 64));
        for (int i = 0; i < shown; i++) {
            Spot spot = POOL.get(i);
            held(ctx, script, layout, spot.peer, left, top, panX, panY, zoom);
            arrow(ctx, spot.x, spot.y, spot.peer.ink);
            Draw.pill(ctx, spot.nameX - 1, spot.nameY - 1, spot.nameW + 2, PILL_H + 2,
                    Draw.argb(0x59, 0x000000));
            Draw.pill(ctx, spot.nameX, spot.nameY, spot.nameW, PILL_H, Draw.argb(0xF2, back));
            Draw.roundOutline(ctx, spot.nameX, spot.nameY, spot.nameW, PILL_H, PILL_H / 2,
                    Draw.opaque(spot.peer.ink));
        }
        Draw.batch(null);

        for (int i = 0; i < shown; i++) {
            Spot spot = POOL.get(i);
            Draw.text(ctx, tr, spot.name, spot.nameX + 5, spot.nameY + (PILL_H - Ui.TEXT_H) / 2,
                    Draw.opaque(Draw.readable(spot.peer.ink)), false);
        }
    }

    private static void held(DrawContext ctx, Script script, Layout layout, Collab.Peer peer,
                             int left, int top, double panX, double panY, double zoom) {
        if (peer.holding.isEmpty() || script == null || layout == null) return;
        Script.Root root = script.rootById(peer.holding);
        if (root == null) return;
        int at = script.roots.indexOf(root);
        if (at < 0 || at >= layout.chunks.size()) return;
        Layout.Chunk chunk = layout.chunks.get(at);
        int x0 = (int) Math.round(left + panX + chunk.x0 * zoom) - 2;
        int y0 = (int) Math.round(top + panY + chunk.y0 * zoom) - 2;
        int x1 = (int) Math.round(left + panX + chunk.x1 * zoom) + 2;
        int y1 = (int) Math.round(top + panY + chunk.y1 * zoom) + 2;
        if (x1 <= x0 || y1 <= y0) return;
        Draw.roundOutline(ctx, x0, y0, x1 - x0, y1 - y0, 4, Draw.argb(0xCC, peer.ink));
    }

    private static void arrow(DrawContext ctx, int x, int y, int ink) {
        int edge = Draw.argb(0xC8, 0x000000);
        for (int i = 0; i < EDGE.length; i += 3)
            Draw.rect(ctx, x + EDGE[i], y + EDGE[i + 1], EDGE[i + 2], 1, edge);
        int fill = Draw.opaque(ink);
        for (int i = 0; i < FILL.length; i += 3)
            Draw.rect(ctx, x + FILL[i], y + FILL[i + 1], FILL[i + 2], 1, fill);
    }

    private Peers() {}
}
