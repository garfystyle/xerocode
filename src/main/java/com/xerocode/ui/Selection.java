package com.xerocode.ui;

import com.xerocode.Script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

final class Selection {
    static final int NEW = 0, ADD = 1, SUB = 2;

    interface Host {
        Layout layout();
        int stamp();
        int screenX(double cx);
        int screenY(double cy);
    }

    record Piece(Layout.Box head, List<Layout.Box> boxes,
                 int x, int y, int w, int h, List<int[]> edge) {}

    private final Host host;
    private final Set<Script.Node> heads = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<Script.Node> coveredCache = Set.of();
    private int pickedStamp = Integer.MIN_VALUE;
    private List<Piece> pieceCache = List.of();
    private Map<Script.Root, List<Piece>> byRootCache = Map.of();
    private Map<Script.Node, Piece> pieceByNode = Map.of();
    private int pieceStamp = Integer.MIN_VALUE;
    private String saidCache = "";

    Selection(Host host) { this.host = host; }

    boolean isEmpty() { return heads.isEmpty(); }

    Map<Script.Root, List<Piece>> byRoot() {
        pieces();
        return byRootCache;
    }

    void changed() {
        pieceStamp = Integer.MIN_VALUE;
        pickedStamp = host.stamp();
    }

    void clear() {
        if (heads.isEmpty()) return;
        heads.clear();
        changed();
    }

    private static void tailNodes(List<Script.Node> owner, int from, Set<Script.Node> out) {
        for (int i = from; i < owner.size(); i++) subtree(owner.get(i), out);
    }

    private static void subtree(Script.Node node, Set<Script.Node> out) {
        out.add(node);
        if (node.cond != null) out.add(node.cond);
        for (Script.Node kid : node.body) subtree(kid, out);
    }

    private void pick(Layout.Box box) {
        if (box == null) return;
        Set<Script.Node> tail = Collections.newSetFromMap(new IdentityHashMap<>());
        tailNodes(box.owner, box.index, tail);
        if (covered().contains(box.node)) return;
        heads.removeIf(tail::contains);
        heads.add(box.node);
        changed();
    }

    Piece pieceOf(Script.Node node) {
        pieces();
        return pieceByNode.get(node);
    }

    void unpick(Layout.Box box) {
        Piece p = box == null ? null : pieceOf(box.node);
        if (p == null) return;
        heads.remove(p.head().node);
        changed();
    }

    void toggle(Layout.Box box) {
        if (box == null) return;
        if (covered().contains(box.node)) unpick(box); else pick(box);
    }

    void prune() {
        if (pickedStamp == host.stamp()) return;
        pickedStamp = host.stamp();
        pieceStamp = Integer.MIN_VALUE;
        if (heads.isEmpty()) return;
        Set<Script.Node> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Layout.Box b : host.layout().boxes) live.add(b.node);
        heads.removeIf(n -> !live.contains(n));
    }

    Set<Script.Node> covered() {
        pieces();
        return coveredCache;
    }

    Set<Script.Node> lastCovered() { return coveredCache; }

    List<Piece> pieces() {
        Layout l = host.layout();
        if (pieceStamp == host.stamp()) return pieceCache;
        pieceStamp = host.stamp();
        pieceCache = List.of();
        byRootCache = Map.of();
        pieceByNode = Map.of();
        coveredCache = Set.of();
        saidCache = "";
        if (heads.isEmpty()) return pieceCache;
        List<Layout.Box> found = new ArrayList<>(heads.size());
        for (Layout.Box b : l.boxes) if (heads.contains(b.node)) found.add(b);
        Map<Script.Node, Integer> headOf = new IdentityHashMap<>();
        Set<Script.Node> tail = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < found.size(); i++) {
            Layout.Box head = found.get(i);
            tail.clear();
            tailNodes(head.owner, head.index, tail);
            for (Script.Node n : tail) headOf.put(n, i);
        }
        int[][] bounds = new int[found.size()][];
        List<List<int[]>> rects = new ArrayList<>(found.size());
        List<List<Layout.Box>> mine = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            rects.add(new ArrayList<>());
            mine.add(new ArrayList<>());
            bounds[i] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE};
        }
        for (Layout.Box b : l.boxes) {
            Integer at = headOf.get(b.node);
            if (at == null) continue;
            shapeOf(b, rects.get(at));
            mine.get(at).add(b);
            int[] r = bounds[at];
            r[0] = Math.min(r[0], b.x);
            r[1] = Math.min(r[1], b.y);
            r[2] = Math.max(r[2], b.x + b.w);
            r[3] = Math.max(r[3], b.bottom());
        }
        Piece[] made = new Piece[found.size()];
        List<Piece> out = new ArrayList<>(found.size());
        int blocks = 0;
        for (int i = 0; i < found.size(); i++) {
            if (rects.get(i).isEmpty()) continue;
            int[] r = bounds[i];
            made[i] = new Piece(found.get(i), mine.get(i),
                    r[0], r[1], r[2] - r[0], r[3] - r[1], Outline.of(rects.get(i), 3));
            out.add(made[i]);
            blocks += Script.blocks(chainOf(made[i]));
        }
        out.sort((a, b) -> a.y() != b.y() ? a.y() - b.y() : a.x() - b.x());
        Map<Script.Root, List<Piece>> byRoot = new IdentityHashMap<>();
        for (Piece p : out)
            byRoot.computeIfAbsent(p.head().root, k -> new ArrayList<>()).add(p);
        Map<Script.Node, Piece> byNode = new IdentityHashMap<>(headOf.size() * 2);
        for (Map.Entry<Script.Node, Integer> e : headOf.entrySet())
            if (made[e.getValue()] != null) byNode.put(e.getKey(), made[e.getValue()]);
        pieceCache = out;
        byRootCache = byRoot;
        pieceByNode = byNode;
        coveredCache = byNode.keySet();
        saidCache = (out.size() > 1 ? Ui.plural(out.size(), "кусок", "куска", "кусков") + " · " : "")
                + Ui.plural(blocks, "блок", "блока", "блоков");
        return out;
    }

    private static void shapeOf(Layout.Box b, List<int[]> out) {
        out.add(new int[]{b.x, b.y, b.x + b.w, b.y + b.headerH});
        if (!b.node.wraps()) return;
        int arm = b.armY();
        out.add(new int[]{b.x, b.y + b.headerH - 1, b.x + Layout.INDENT + 1, arm + 1});
        out.add(new int[]{b.x, arm, b.x + b.w, arm + Layout.ARM_H});
    }

    private static List<Script.Node> copyOf(Piece p) {
        Layout.Box head = p.head();
        List<Script.Node> out = new ArrayList<>();
        for (int i = head.index; i < head.owner.size(); i++) out.add(head.owner.get(i).copy());
        return out;
    }

    private static List<Script.Node> chainOf(Piece p) {
        Layout.Box head = p.head();
        return head.owner.subList(head.index, head.owner.size());
    }

    String said() {
        pieces();
        return saidCache;
    }

    void set(List<Script.Root> roots) {
        heads.clear();
        for (Script.Root r : roots) if (!r.chain.isEmpty()) heads.add(r.chain.get(0));
        changed();
    }

    List<Script.Root> asRoots() {
        List<Script.Root> out = new ArrayList<>();
        for (Piece p : pieces()) {
            List<Script.Node> chain = copyOf(p);
            if (chain.isEmpty()) continue;
            Script.Root r = new Script.Root(p.head().x, p.head().y);
            r.chain.addAll(chain);
            out.add(r);
        }
        return out;
    }

    void cutOut(Script script) {
        for (Piece p : pieces()) {
            Layout.Box head = p.head();
            head.owner.subList(head.index, head.owner.size()).clear();
        }
        script.roots.removeIf(r -> r.chain.isEmpty());
    }

    void moveBy(Script script, int dx, int dy) {
        List<Script.Root> born = new ArrayList<>();
        for (Piece p : pieces()) {
            Layout.Box head = p.head();
            if (head.index == 0 && !head.nested && head.root != null) {
                head.root.x += dx;
                head.root.y += dy;
                continue;
            }
            List<Script.Node> tail = head.owner.subList(head.index, head.owner.size());
            Script.Root r = new Script.Root(head.x + dx, head.y + dy);
            r.chain.addAll(tail);
            tail.clear();
            born.add(r);
        }
        script.roots.addAll(born);
        script.roots.removeIf(r -> r.chain.isEmpty());
        changed();
    }

    List<Layout.Box> bandHits(double x0, double y0, double x1, double y1) {
        if (x1 - x0 < 3 && y1 - y0 < 3) return List.of();
        List<Layout.Box> out = new ArrayList<>();
        Set<Script.Node> taken = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Layout.Box b : host.layout().boxes) {
            if (taken.contains(b.node)) continue;
            if (!b.touches(x0, y0, x1, y1)) continue;
            out.add(b);
            tailNodes(b.owner, b.index, taken);
        }
        return out;
    }

    void band(int mode, List<Layout.Box> hit) {
        if (hit.isEmpty()) return;
        Set<Script.Node> was = covered();
        if (mode == SUB) {
            for (Layout.Box b : hit) {
                Piece p = pieceOf(b.node);
                if (p != null) heads.remove(p.head().node);
            }
        } else {
            Set<Script.Node> tails = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Layout.Box b : hit) tailNodes(b.owner, b.index, tails);
            heads.removeIf(tails::contains);
            for (Layout.Box b : hit) if (!was.contains(b.node)) heads.add(b.node);
        }
        changed();
    }

    private void edges(GuiGraphicsExtractor ctx, List<int[]> segments, double dx, double dy, int argb,
                       double vx0, double vy0, double vx1, double vy1) {
        for (int[] s : segments) {
            double a = s[0] + dx, b = s[1] + dy, c = s[2] + dx, d = s[3] + dy;
            if (c < vx0 || a > vx1 || d < vy0 || b > vy1) continue;
            int x0 = host.screenX(a), y0 = host.screenY(b);
            if (s[1] == s[3]) Draw.rect(ctx, x0, y0, Math.max(1, host.screenX(c) - x0) + 2, 2, argb);
            else Draw.rect(ctx, x0, y0, 2, Math.max(1, host.screenY(d) - y0) + 2, argb);
        }
    }

    void drawOutlines(GuiGraphicsExtractor ctx, List<Piece> list, double dx, double dy,
                      ScreenRectangle area, double vx0, double vy0, double vx1, double vy1) {
        List<Piece> shown = null;
        int cap = 0;
        for (Piece p : list) {
            if (p.x() + dx + p.w() < vx0 || p.x() + dx > vx1
                    || p.y() + dy + p.h() < vy0 || p.y() + dy > vy1) continue;
            if (shown == null) shown = new ArrayList<>(list.size());
            shown.add(p);
            cap += p.edge().size();
        }
        if (shown == null) return;
        Draw.batch(Batch.open(ctx, area, area, Math.min(cap, 2048)));
        int ink = Draw.argb(0xF0, Theme.ACCENT);
        for (Piece p : shown) edges(ctx, p.edge(), dx, dy, ink, vx0, vy0, vx1, vy1);
        Draw.batch(null);
    }
}
