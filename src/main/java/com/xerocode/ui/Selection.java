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

    record Piece(Layout.Box head, int count, List<Layout.Box> boxes,
                 int x, int y, int w, int h, List<int[]> edge) {}

    private final Host host;
    private final Set<Script.Node> units = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<Script.Node> coveredCache = Set.of();
    private Map<Script.Node, Script.Node> unitOf = Map.of();
    private int pickedStamp = Integer.MIN_VALUE;
    private List<Piece> pieceCache = List.of();
    private Map<Script.Root, List<Piece>> byRootCache = Map.of();
    private Map<Script.Node, Piece> pieceByNode = Map.of();
    private int pieceStamp = Integer.MIN_VALUE;
    private String saidCache = "";
    private int blocksCache;

    Selection(Host host) { this.host = host; }

    boolean isEmpty() { return units.isEmpty(); }

    Map<Script.Root, List<Piece>> byRoot() {
        pieces();
        return byRootCache;
    }

    void changed() {
        pieceStamp = Integer.MIN_VALUE;
        pickedStamp = host.stamp();
    }

    void clear() {
        if (units.isEmpty()) return;
        units.clear();
        changed();
    }

    private static void subtree(Script.Node node, Set<Script.Node> out) {
        out.add(node);
        if (node.cond != null) out.add(node.cond);
        for (Script.Node kid : node.body) subtree(kid, out);
    }

    private static Set<Script.Node> nodes() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void addAll(List<Script.Node> list) {
        if (list.isEmpty()) return;
        Set<Script.Node> taken = nodes();
        taken.addAll(covered());
        Set<Script.Node> inside = nodes();
        boolean any = false;
        for (Script.Node n : list) {
            if (taken.contains(n)) continue;
            inside.clear();
            subtree(n, inside);
            for (Script.Node k : inside) units.remove(k);
            units.add(n);
            taken.addAll(inside);
            any = true;
        }
        if (any) changed();
    }

    private void dropAll(List<Script.Node> list) {
        if (list.isEmpty()) return;
        Map<Script.Node, Script.Node> owners = unitOf();
        Set<Script.Node> inside = nodes();
        boolean any = false;
        for (Script.Node n : list) {
            Script.Node owner = owners.get(n);
            if (owner != null) any |= units.remove(owner);
            inside.clear();
            subtree(n, inside);
            for (Script.Node k : inside) any |= units.remove(k);
        }
        if (any) changed();
    }

    private Map<Script.Node, Script.Node> unitOf() {
        pieces();
        return unitOf;
    }

    Piece pieceOf(Script.Node node) {
        pieces();
        return pieceByNode.get(node);
    }

    void toggle(Layout.Box box) {
        if (box == null) return;
        if (covered().contains(box.node)) dropAll(List.of(box.node)); else addAll(List.of(box.node));
    }

    void unpick(Layout.Box box) {
        if (box != null) dropAll(List.of(box.node));
    }

    void toggleTail(Layout.Box box) {
        if (box == null) return;
        List<Script.Node> tail = List.copyOf(box.owner.subList(box.index, box.owner.size()));
        if (covered().contains(box.node)) dropAll(tail); else addAll(tail);
    }

    void unpickTail(Layout.Box box) {
        if (box != null) dropAll(List.copyOf(box.owner.subList(box.index, box.owner.size())));
    }

    void prune() {
        if (pickedStamp == host.stamp()) return;
        pickedStamp = host.stamp();
        pieceStamp = Integer.MIN_VALUE;
        if (units.isEmpty()) return;
        Set<Script.Node> live = nodes();
        for (Layout.Box b : host.layout().boxes) live.add(b.node);
        units.removeIf(n -> !live.contains(n));
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
        unitOf = Map.of();
        saidCache = "";
        blocksCache = 0;
        if (units.isEmpty()) return pieceCache;

        Map<Script.Node, Layout.Box> boxOf = new IdentityHashMap<>(units.size() * 2);
        for (Layout.Box b : l.boxes) if (units.contains(b.node)) boxOf.put(b.node, b);
        Map<List<Script.Node>, List<Layout.Box>> byOwner = new IdentityHashMap<>();
        for (Layout.Box b : l.boxes)
            if (boxOf.get(b.node) == b) byOwner.computeIfAbsent(b.owner, k -> new ArrayList<>()).add(b);

        List<Layout.Box> heads = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        Map<Script.Node, Integer> runOfUnit = new IdentityHashMap<>();
        for (List<Layout.Box> group : byOwner.values()) {
            group.sort((a, b) -> a.index - b.index);
            for (int i = 0; i < group.size(); i++) {
                Layout.Box b = group.get(i);
                boolean joins = i > 0 && group.get(i - 1).index == b.index - 1;
                if (!joins) {
                    heads.add(b);
                    counts.add(0);
                }
                int run = heads.size() - 1;
                counts.set(run, counts.get(run) + 1);
                runOfUnit.put(b.node, run);
            }
        }

        Map<Script.Node, Script.Node> owners = new IdentityHashMap<>();
        Set<Script.Node> inside = nodes();
        for (Script.Node u : boxOf.keySet()) {
            inside.clear();
            subtree(u, inside);
            for (Script.Node n : inside) owners.put(n, u);
        }

        int runs = heads.size();
        int[][] bounds = new int[runs][];
        List<List<int[]>> rects = new ArrayList<>(runs);
        List<List<Layout.Box>> mine = new ArrayList<>(runs);
        for (int i = 0; i < runs; i++) {
            rects.add(new ArrayList<>());
            mine.add(new ArrayList<>());
            bounds[i] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE};
        }
        for (Layout.Box b : l.boxes) {
            Script.Node u = owners.get(b.node);
            if (u == null) continue;
            int at = runOfUnit.get(u);
            shapeOf(b, rects.get(at));
            mine.get(at).add(b);
            int[] r = bounds[at];
            r[0] = Math.min(r[0], b.x);
            r[1] = Math.min(r[1], b.y);
            r[2] = Math.max(r[2], b.x + b.w);
            r[3] = Math.max(r[3], b.bottom());
        }

        Piece[] made = new Piece[runs];
        List<Piece> out = new ArrayList<>(runs);
        int blocks = 0;
        for (int i = 0; i < runs; i++) {
            if (rects.get(i).isEmpty()) continue;
            int[] r = bounds[i];
            made[i] = new Piece(heads.get(i), counts.get(i), mine.get(i),
                    r[0], r[1], r[2] - r[0], r[3] - r[1], Outline.of(rects.get(i), 3));
            out.add(made[i]);
            blocks += Script.blocks(chainOf(made[i]));
        }
        out.sort((a, b) -> a.y() != b.y() ? a.y() - b.y() : a.x() - b.x());
        Map<Script.Root, List<Piece>> byRoot = new IdentityHashMap<>();
        for (Piece p : out)
            byRoot.computeIfAbsent(p.head().root, k -> new ArrayList<>()).add(p);
        Map<Script.Node, Piece> byNode = new IdentityHashMap<>(owners.size() * 2);
        for (Map.Entry<Script.Node, Script.Node> e : owners.entrySet()) {
            Piece p = made[runOfUnit.get(e.getValue())];
            if (p != null) byNode.put(e.getKey(), p);
        }
        pieceCache = out;
        byRootCache = byRoot;
        pieceByNode = byNode;
        unitOf = owners;
        coveredCache = owners.keySet();
        blocksCache = blocks;
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

    private static List<Script.Node> chainOf(Piece p) {
        Layout.Box head = p.head();
        int to = Math.min(head.owner.size(), head.index + p.count());
        return head.owner.subList(Math.min(head.index, to), to);
    }

    private static List<Script.Node> copyOf(Piece p) {
        List<Script.Node> out = new ArrayList<>();
        for (Script.Node n : chainOf(p)) out.add(n.copy());
        return out;
    }

    private List<Piece> lastFirst() {
        List<Piece> order = new ArrayList<>(pieces());
        order.sort((a, b) -> b.head().index - a.head().index);
        return order;
    }

    String said() {
        pieces();
        return saidCache;
    }

    int blocks() {
        if (units.isEmpty()) return 0;
        pieces();
        return blocksCache;
    }

    void set(List<Script.Root> roots) {
        units.clear();
        for (Script.Root r : roots) units.addAll(r.chain);
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
        for (Piece p : lastFirst()) chainOf(p).clear();
        script.roots.removeIf(r -> r.chain.isEmpty());
    }

    void moveBy(Script script, int dx, int dy) {
        List<Script.Root> born = new ArrayList<>();
        for (Piece p : lastFirst()) {
            Layout.Box head = p.head();
            List<Script.Node> run = chainOf(p);
            if (head.index == 0 && !head.nested && head.root != null
                    && run.size() == head.owner.size()) {
                head.root.x += dx;
                head.root.y += dy;
                continue;
            }
            Script.Root r = new Script.Root(head.x + dx, head.y + dy);
            r.chain.addAll(run);
            run.clear();
            born.add(r);
        }
        script.roots.addAll(born);
        script.roots.removeIf(r -> r.chain.isEmpty());
        changed();
    }

    List<Layout.Box> bandHits(double x0, double y0, double x1, double y1) {
        if (x1 - x0 < 3 && y1 - y0 < 3) return List.of();
        List<Layout.Box> out = new ArrayList<>();
        for (Layout.Box b : host.layout().boxes) if (b.touches(x0, y0, x1, y1)) out.add(b);
        return out;
    }

    void band(int mode, boolean tails, List<Layout.Box> hit) {
        if (hit.isEmpty()) return;
        Set<Script.Node> deeper = nodes();
        for (Layout.Box b : hit) {
            for (Script.Node kid : b.node.body) subtree(kid, deeper);
            if (b.node.cond != null) deeper.add(b.node.cond);
        }
        Map<List<Script.Node>, int[]> spans = new IdentityHashMap<>();
        for (Layout.Box b : hit) {
            if (deeper.contains(b.node)) continue;
            int[] s = spans.computeIfAbsent(b.owner, k -> new int[]{b.index, b.index});
            s[0] = Math.min(s[0], b.index);
            s[1] = Math.max(s[1], b.index);
        }
        List<Script.Node> chosen = new ArrayList<>();
        for (Map.Entry<List<Script.Node>, int[]> e : spans.entrySet()) {
            List<Script.Node> owner = e.getKey();
            int to = tails ? owner.size() - 1 : Math.min(e.getValue()[1], owner.size() - 1);
            for (int i = e.getValue()[0]; i <= to; i++) chosen.add(owner.get(i));
        }
        if (mode == SUB) dropAll(chosen); else addAll(chosen);
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
