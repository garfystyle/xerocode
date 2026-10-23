package com.xerocode.ui;

import java.util.LinkedHashSet;
import com.xerocode.Sync;
import com.xerocode.Exporter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.xerocode.Audio;
import com.xerocode.Backpack;
import com.xerocode.Catalog;
import com.xerocode.Clip;
import com.xerocode.Codespace;
import com.xerocode.Collab;
import com.xerocode.Env;
import com.xerocode.Finder;
import com.xerocode.Functions;
import com.xerocode.History;
import com.xerocode.Importer;
import com.xerocode.LineEdit;
import com.xerocode.Mapping;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Upgrade;
import com.xerocode.Value;
import com.xerocode.XeroCode;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class EditorScreen extends Screen implements TopBar.Host {
    private final Script script;
    private final Palette palette = new Palette();
    private TopBar top;
    private Tips tips;
    private EditBox search;

    private double panX = 60, panY = 50, zoom = 1.0;
    private boolean viewRestored;
    private boolean panning, draggingSearch, resizingPalette, paletteSwipe;
    private double paletteDrag;
    private double mouseCanvasX, mouseCanvasY;

    private List<Script.Node> drag;
    private double dragOffX, dragOffY;
    private boolean dragFromPalette, dragMoved, dragAwaitsClick, swallowRelease;
    private String dragSnapshot;
    private Snap snap;
    private Layout.Ghost ghost;
    private Layout ghostLayout;
    private int ghostStamp = Integer.MIN_VALUE;

    private final Selection picked = new Selection(new Selection.Host() {
        @Override public Layout layout() { return EditorScreen.this.layout(); }
        @Override public int stamp() { return layoutStamp; }
        @Override public int screenX(double cx) { return toScreenX(cx); }
        @Override public int screenY(double cy) { return toScreenY(cy); }
    });
    private boolean banding;
    private int bandMode;
    private double bandX0, bandY0, bandX1, bandY1;
    private boolean panHinted;
    private boolean moving, moveShifted;
    private double moveDX, moveDY;
    private String moveSnapshot;

    private BackpackPanel backpack;
    private FindPanel finder;
    private final MiniMap map = new MiniMap();
    private final List<Layout.Box> found = new ArrayList<>();
    private int foundStamp = Integer.MIN_VALUE;
    private Script.Node focusNode;
    private Script.Node lastPlaced;
    private long focusAt;
    private boolean mapDragging;
    private double panFromX, panFromY, panToX, panToY;
    private long panStart;
    private boolean panAnim;
    private final BlockView.Look look = new BlockView.Look();
    private final BlockView.Accepts accepts = (box, chip) -> Carry.accepts(this.carry, box, chip);

    private List<Value> carry;
    private boolean carryHeld;
    private String carrySnapshot;
    private Script.Root heldRoot;
    private Layout.Box pressBox;
    private Layout.Chip pressChip;
    private double pressX, pressY;

    private int soloKey = Settings.NONE;
    private Settings.Hot pendHot;
    private Layout.Box pressOne;
    private Layout.Box pendBox;
    private Layout.Chip pendChip;
    private int pendButton = -1;
    private boolean emptyPress;
    private double emptyX, emptyY;
    private final List<double[]> navBack = new ArrayList<>();
    private int callCycle;

    private StatusBar foot;
    private Menu menu;
    private BlockMenu blockMenu;
    private CatalogPicker condPicker;
    private ChestMenu chest;
    private boolean chestsShown = Settings.chests();
    private ValueEditor editor;
    private SettingsPanel settings;
    private String editorUndo;

    private Layout layout;
    private int layoutStamp = Integer.MIN_VALUE;
    private int revision;
    private Layout.Box hoverBox;
    private Layout.Chip hoverChip;
    private String status = "";
    private long statusAt;
    private String exitTo = "build";
    private boolean exitPrompt;

    private int savedStamp;
    private boolean stampTaken;

    private boolean dirty() { return !script.scratch && stampTaken && codeHash() != savedStamp; }

    public void markPublished() {
        savedStamp = script.codeHash();
        stampTaken = true;
    }

    private void saveScript() {
        script.save();
    }

    public EditorScreen(Script script) {
        super(Component.literal("XeroCode"));
        this.script = script;
        History.clear();
    }

    @Override
    public int left() { return canvasLeft(); }

    @Override
    public int width() { return width; }

    @Override
    public double zoom() { return zoom; }

    @Override
    public boolean empty() { return script.roots.isEmpty(); }

    @Override
    public boolean finding() { return finder != null; }

    @Override
    public StatusBar.Said sync() { return foot == null || script.scratch ? null : foot.syncSaid(script); }

    private int canvasLeft() { return Settings.chests() ? 0 : Theme.PALETTE_W; }
    private int canvasRight() { return finder == null ? width : Math.max(canvasLeft() + 40, finder.x()); }
    private int canvasBottom() { return height - (Settings.statusBar() ? Theme.STATUS_H : 0); }
    private double midX() { return (canvasLeft() + canvasRight()) / 2.0; }
    private double midY() { return (Theme.TOPBAR_H + canvasBottom()) / 2.0; }
    private double toCanvasX(double sx) { return (sx - canvasLeft() - panX) / zoom; }
    private double toCanvasY(double sy) { return (sy - Theme.TOPBAR_H - panY) / zoom; }
    private int toScreenX(double cx) { return (int) Math.round(canvasLeft() + panX + cx * zoom); }
    private int toScreenY(double cy) { return (int) Math.round(Theme.TOPBAR_H + panY + cy * zoom); }

    private ScreenRectangle canvasArea() {
        return new ScreenRectangle(canvasLeft(), Theme.TOPBAR_H,
                width - canvasLeft(), canvasBottom() - Theme.TOPBAR_H);
    }

    private boolean busy() { return drag != null || held != null || carry != null || moving; }

    private static final int PLUS_H = 24, PLUS_EDGE = 12, PLUS_DISC = 18;
    private static final String PLUS_LABEL = "Блоки";

    private int plusW() { return 3 + PLUS_DISC + 6 + font.width(PLUS_LABEL) + 6 + 16 + 5; }

    private int plusX() { return canvasLeft() + PLUS_EDGE; }

    private int plusY() { return canvasBottom() - PLUS_EDGE - PLUS_H; }

    private boolean overPlus(double mx, double my) {
        return Settings.chests() && Ui.hit(mx, my, plusX(), plusY(), plusW(), PLUS_H);
    }

    private void drawPlus(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!Settings.chests() || drag != null) return;
        int px = plusX(), py = plusY(), pw = plusW(), r = PLUS_H / 2;
        boolean lit = chest != null || overPlus(mouseX, mouseY);
        int top = Draw.mix(Theme.ACCENT, 0xFFFFFF, lit ? 0.28f : 0.16f);
        int bottom = Draw.mix(Theme.ACCENT, 0x000000, lit ? 0.12f : 0.22f);
        Draw.shadow(ctx, px, py, pw, PLUS_H, r, 0);
        Draw.round(ctx, px, py, pw, PLUS_H, r, Draw.opaque(Draw.mix(Theme.ACCENT, 0x000000, 0.45f)));
        Draw.pillGrad(ctx, px + 1, py + 1, pw - 2, PLUS_H - 2, Draw.opaque(top), Draw.opaque(bottom));
        Draw.rect(ctx, px + r - 4, py + 1, pw - 2 * r + 8, 1, Draw.argb(0x46, 0xFFFFFF));
        int dx = px + 3, dy = py + (PLUS_H - PLUS_DISC) / 2, c = PLUS_DISC / 2;
        Draw.round(ctx, dx, dy, PLUS_DISC, PLUS_DISC, c, Draw.argb(lit ? 0x5A : 0x3C, 0xFFFFFF));
        int ink = Draw.opaque(Theme.ON_ACCENT);
        Draw.rect(ctx, dx + c - 5, dy + c - 1, 10, 2, ink);
        Draw.rect(ctx, dx + c - 1, dy + c - 5, 2, 10, ink);
        Draw.text(ctx, font, PLUS_LABEL, dx + PLUS_DISC + 6, py + (PLUS_H - Ui.TEXT_H) / 2,
                Theme.ON_ACCENT, false);
        Draw.item(ctx, Catalog.stackOf("minecraft:chest"), px + pw - 5 - 16, py + (PLUS_H - 16) / 2, 16);
    }

    private void openChest(Consumer<Catalog.Action> onPick) {
        if (busy()) return;
        closeOverlays();
        chest = new ChestMenu(font, width, height, a -> {
            lastAdded = a;
            onPick.accept(a);
        });
    }

    private void openChestHome(Consumer<Catalog.Action> onPick) {
        ChestMenu.home();
        openChest(onPick);
    }

    private void openChestToCursor() {
        openChestHome(a -> holdOnCursor(new ArrayList<>(List.of(new Script.Node(a))), a.name));
    }

    private final List<Script.Root> lineOrder = new ArrayList<>();
    private final Map<Script.Root, Integer> lineOf = new IdentityHashMap<>();
    private final Map<Script.Root, String> lineTag = new IdentityHashMap<>();
    private int lineStamp = Integer.MIN_VALUE;
    private Script.Root navRoot;
    private long navAt;

    private static final int TAG_H = 10;
    private static final long NAV_KEEP = 2000;
    private static final int TIDY_GAP_X = 48, TIDY_GAP_Y = 26;

    private void syncLines() {
        layout();
        if (lineStamp == layoutStamp) return;
        lineStamp = layoutStamp;
        lineOrder.clear();
        lineOf.clear();
        lineTag.clear();
        List<Script.Root> ordered = Finder.ordered(script);
        for (int i = 0; i < ordered.size(); i++) {
            Script.Root r = ordered.get(i);
            if (r.chain.isEmpty()) continue;
            lineOrder.add(r);
            lineOf.put(r, i + 1);
            lineTag.put(r, String.valueOf(i + 1));
        }
    }

    private int lines() {
        syncLines();
        return lineOrder.size();
    }

    private List<Script.Root> lineOrder() {
        syncLines();
        return List.copyOf(lineOrder);
    }

    private Integer lineOf(Script.Root root) {
        syncLines();
        return lineOf.get(root);
    }

    private String tagText(Layout.Box box) {
        if (!Settings.lineNumbers()) return "";
        syncLines();
        String s = lineTag.get(box.root);
        return s == null ? "" : s;
    }

    private boolean tagCaret(Layout.Box box) { return box.root != null && box.root.foldable(); }

    private boolean tagShown(Layout.Box box) {
        return box.root != null && box.index == 0 && !box.nested
                && (tagCaret(box) || !tagText(box).isEmpty());
    }

    private int tagW(Layout.Box box) {
        String s = tagText(box);
        int w = 10 + (s.isEmpty() ? 0 : font.width(s));
        if (tagCaret(box)) w += Draw.glyphW(Draw.CARET_DOWN) + (s.isEmpty() ? 0 : 3);
        return w;
    }

    private boolean hitTag(Layout.Box box, double cx, double cy) {
        if (!tagShown(box) || !tagCaret(box)) return false;
        int x = box.x + 5, y = box.y - TAG_H;
        return cx >= x - 2 && cx < x + tagW(box) + 2 && cy >= y - 2 && cy < box.y + 1;
    }

    private void drawLineTag(GuiGraphicsExtractor ctx, Layout.Box box) {
        if (!tagShown(box)) return;
        String s = tagText(box);
        int base = BlockView.color(box.node);
        boolean grad = Settings.gradient();
        int top = Draw.shade(base, grad ? -0.20f : -0.24f);
        int bottom = Draw.shade(base, grad ? -0.30f : -0.24f);
        int w = tagW(box);
        int x = box.x + 5, y = box.y - TAG_H;
        int r = Settings.radius(TAG_H), ri = Math.max(0, r - 1);
        Draw.roundRect(ctx, x, y, w, TAG_H, r, r, 0, 0, box.border);
        Draw.roundRectGrad(ctx, x + 1, y + 1, w - 2, TAG_H, ri, ri, 0, 0,
                Draw.opaque(top), Draw.opaque(bottom));
        int ink = Layout.ink(Draw.isLight(top));
        int at = x + 5;
        if (tagCaret(box)) {
            String[] caret = box.root.hides() ? Draw.CARET_RIGHT : Draw.CARET_DOWN;
            Draw.glyph(ctx, caret, at, y + 1 + (TAG_H - 1 - Draw.glyphH(caret)) / 2, ink);
            at += Draw.glyphW(Draw.CARET_DOWN) + 3;
        }
        if (!s.isEmpty()) Draw.text(ctx, font, s, at, y + 2, ink, false);
    }

    private void toggleStack(Script.Root r) {
        if (r == null || !r.foldable()) return;
        r.folded = !r.folded;
        foldsChanged();
    }

    private void foldStackHovered() {
        if (hoverBox == null || drag != null || hoverBox.root == null) {
            toast("наведись на стопку — сверну её");
            return;
        }
        toggleStack(hoverBox.root);
    }

    private void foldStacks() {
        if (drag != null) return;
        List<Script.Root> all = new ArrayList<>();
        for (Script.Root r : script.roots) if (r.foldable()) all.add(r);
        if (all.isEmpty()) { toast("сворачивать нечего"); return; }
        boolean fold = all.stream().anyMatch(r -> !r.folded);
        for (Script.Root r : all) r.folded = fold;
        foldsChanged();
        toast(fold ? "свёрнуто стопок: " + all.size() : "все стопки развёрнуты");
    }

    private Map<Script.Root, Layout.Chunk> chunksByRoot() {
        Layout l = layout();
        Map<Script.Root, Layout.Chunk> out = new IdentityHashMap<>();
        for (Layout.Chunk c : l.chunks) {
            Layout.Box head = l.boxes.get(c.from);
            if (head.root != null) out.put(head.root, c);
        }
        return out;
    }

    private int nearestLine(List<Script.Root> order, int dir) {
        Map<Script.Root, Layout.Chunk> chunks = chunksByRoot();
        double cx = toCanvasX(midX()), cy = toCanvasY(midY());
        int near = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < order.size(); i++) {
            Layout.Chunk c = chunks.get(order.get(i));
            if (c == null) continue;
            double dx = Math.max(0, Math.max(c.x0 - cx, cx - c.x1));
            double dy = Math.max(0, Math.max(c.y0 - cy, cy - c.y1));
            double d = dx * dx + dy * dy;
            if (d < best) { best = d; near = i; }
        }
        return best > 0 ? near : Math.floorMod(near + dir, order.size());
    }

    private void stepLine(int dir) {
        if (drag != null || moving) return;
        List<Script.Root> order = lineOrder();
        if (order.isEmpty()) { toast("на полотне нет строк"); return; }
        long now = System.currentTimeMillis();
        int cur = now - navAt < NAV_KEEP ? order.indexOf(navRoot) : -1;
        int next = cur >= 0 ? Math.floorMod(cur + dir, order.size()) : nearestLine(order, dir);
        Script.Root r = order.get(next);
        navRoot = r;
        navAt = now;
        focusNode = r.chain.get(0);
        focusAt = now;
        Layout.Box box = boxOf(focusNode);
        if (box != null) centerOnBox(box);
        Integer n = lineOf(r);
        toast("строка " + (n == null ? next + 1 : n) + " из " + order.size());
    }

    private void tidy() {
        if (busy()) return;
        List<Script.Root> order = lineOrder();
        if (order.size() < 2) { toast("упорядочивать нечего"); return; }
        Map<Script.Root, Layout.Chunk> chunks = chunksByRoot();
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE;
        double area = 0;
        int tallest = 0;
        for (Script.Root r : order) {
            Layout.Chunk c = chunks.get(r);
            if (c == null) continue;
            x0 = Math.min(x0, r.x);
            y0 = Math.min(y0, r.y);
            area += (double) (c.x1 - c.x0 + TIDY_GAP_X) * (c.y1 - c.y0 + TIDY_GAP_Y);
            tallest = Math.max(tallest, c.y1 - c.y0);
        }
        double aspect = (canvasBottom() - Theme.TOPBAR_H) / (double) Math.max(1, canvasRight() - canvasLeft());
        double limit = Math.max(tallest, Math.sqrt(area * aspect));
        pushUndo();
        double colX = Math.round(x0), y = Math.round(y0);
        int colW = 0;
        for (Script.Root r : order) {
            Layout.Chunk c = chunks.get(r);
            if (c == null) continue;
            int w = c.x1 - c.x0, h = c.y1 - c.y0;
            if (y > y0 && y - y0 + h > limit) {
                colX += colW + TIDY_GAP_X;
                y = Math.round(y0);
                colW = 0;
            }
            r.x = colX;
            r.y = y;
            y += h + TIDY_GAP_Y;
            colW = Math.max(colW, w);
        }
        fitView();
        toast("строки выстроены по порядку · номера те же");
    }

    private static boolean foldable(Script.Node n) { return n.wraps() && !n.body.isEmpty(); }

    private void foldsChanged() {
        revision++;
        hoverBox = null;
        hoverChip = null;
    }

    private void toggleFold(Script.Node n) {
        if (!foldable(n)) return;
        n.folded = !n.folded;
        foldsChanged();
    }

    private Script.Node parentOf(Layout.Box box) {
        for (Layout.Box b : layout().boxes) if (b.node.body == box.owner) return b.node;
        return null;
    }

    private void foldHovered() {
        if (hoverBox == null || drag != null) return;
        Script.Node n = foldable(hoverBox.node) ? hoverBox.node : parentOf(hoverBox);
        if (n != null) toggleFold(n);
    }

    private static void collectFoldable(List<Script.Node> chain, List<Script.Node> out) {
        for (Script.Node n : chain) {
            if (!foldable(n)) continue;
            out.add(n);
            collectFoldable(n.body, out);
        }
    }

    private void foldAll() {
        if (drag != null) return;
        List<Script.Node> all = new ArrayList<>();
        for (Script.Root r : script.roots) collectFoldable(r.chain, all);
        if (all.isEmpty()) { toast("сворачивать нечего"); return; }
        boolean fold = all.stream().anyMatch(n -> !n.folded);
        for (Script.Node n : all) n.folded = fold;
        foldsChanged();
        toast(fold ? "свёрнуто: " + Ui.plural(all.size(), "тело", "тела", "тел") : "всё развёрнуто");
    }

    private Catalog.Action lastAdded;

    private void quickAddHere(boolean chests) {
        int sx = toScreenX(mouseCanvasX), sy = toScreenY(mouseCanvasY);
        boolean inside = sx >= canvasLeft() && sx < canvasRight()
                && sy >= Theme.TOPBAR_H && sy < canvasBottom();
        if (inside) quickAdd(hoverBox, mouseCanvasX, mouseCanvasY, chests);
        else quickAdd(null, toCanvasX(midX()), toCanvasY(midY()), chests);
    }

    private void quickAdd(Layout.Box under, double cx, double cy, boolean chests) {
        quickAdd(under, cx, cy, chests, false);
    }

    private void quickAdd(Layout.Box under, double cx, double cy, boolean chests, boolean home) {
        if (busy()) return;
        Script.Node host = under == null ? null : under.node;
        List<Script.Node> owner = under == null ? null : under.owner;
        Consumer<Catalog.Action> place = a -> placeQuick(a, host, owner, cx - 10, cy - 8);
        if (chests && home) { openChestHome(place); return; }
        if (chests) { openChest(place); return; }
        closeOverlays();
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Catalog.Category c : Catalog.CATEGORIES)
            for (int s = 0; s < c.subActions.size(); s++) {
                String sub = c.subNames.get(s);
                for (Catalog.Action a : c.subActions.get(s))
                    items.add(new CatalogPicker.Item(Catalog.keyOf(a), a.name, c.name, a.item,
                            a.description, sub == null ? "" : sub, ""));
            }
        if (items.isEmpty()) return;
        String title = host == null ? "Добавить блок"
                : "Добавить под «" + Draw.fit(font, Layout.blockTitle(host), 180) + "»";
        condPicker = new CatalogPicker(font, width, height, title, Theme.ACCENT, items, null,
                lastAdded == null ? null : Catalog.keyOf(lastAdded), null, id -> {
                    Catalog.Action a = Catalog.byKey(id);
                    if (a == null) return;
                    lastAdded = a;
                    place.accept(a);
                });
    }

    private void chooseAction(Layout.Box box) {
        if (busy()) return;
        Script.Node old = box.node;
        List<Script.Node> owner = box.owner;
        closeOverlays();
        ChestMenu.openAt(old.action.category);
        chest = new ChestMenu(font, width, height, a -> replaceAction(owner, old, a));
        chest.titled("Заменить «" + Draw.fit(font, old.action.name, 160) + "»");
    }

    private boolean rootChain(List<Script.Node> owner) {
        for (Script.Root r : script.roots) if (r.chain == owner) return true;
        return false;
    }

    private void replaceAction(List<Script.Node> owner, Script.Node old, Catalog.Action a) {
        int at = indexOf(owner, old);
        if (at < 0 || a == old.action) return;
        Script.Node fresh = new Script.Node(a);
        if (fresh.isHat() && (at != 0 || !rootChain(owner))) {
            toast("событие ставится только в начало строки");
            return;
        }
        pushUndo();
        int lost = 0;
        List<Catalog.Arg> was = old.args(), now = fresh.args();
        for (Map.Entry<Integer, List<Value>> e : old.values.entrySet()) {
            int i = e.getKey();
            if (e.getValue().isEmpty()) continue;
            int j = i < was.size() ? sameArg(now, was.get(i)) : -1;
            if (j < 0) { lost++; continue; }
            List<Value> into = fresh.valuesOf(j);
            for (Value v : e.getValue()) into.add(v.copy());
        }
        List<Catalog.Setting> oldSettings = old.settings();
        for (int i = 0; i < oldSettings.size(); i++) {
            int j = fresh.settingIndex(oldSettings.get(i).label);
            if (j < 0) continue;
            String option = old.marker(i);
            if (fresh.settings().get(j).options.contains(option)) fresh.markers.put(j, option);
            Value bound = old.markerVar(i);
            if (bound != null) fresh.bindMarker(j, bound);
        }
        if (Mapping.hasConditional(a) && old.cond != null) fresh.cond = old.cond;
        if (fresh.wraps()) {
            fresh.body.addAll(old.body);
            fresh.folded = old.folded;
        } else if (!old.body.isEmpty()) {
            owner.addAll(at + 1, old.body);
        }
        owner.set(at, fresh);
        lastAdded = a;
        focusNode = fresh;
        focusAt = System.currentTimeMillis();
        toast("«" + old.action.name + "» → «" + a.name + "»"
                + (lost > 0 ? " · не перенесено значений: " + lost : ""));
    }

    private static int sameArg(List<Catalog.Arg> args, Catalog.Arg want) {
        for (int j = 0; j < args.size(); j++)
            if (args.get(j).purpose.equals(want.purpose) && args.get(j).type.equals(want.type)) return j;
        for (int j = 0; j < args.size(); j++)
            if (args.get(j).purpose.equals(want.purpose)) return j;
        return -1;
    }

    private void placeQuick(Catalog.Action a, Script.Node host, List<Script.Node> owner,
                            double cx, double cy) {
        Script.Node node = new Script.Node(a);
        int at = owner == null ? -1 : indexOf(owner, host);
        pushUndo();
        if (at >= 0 && !node.isHat()) {
            owner.add(at + 1, node);
            toast("«" + a.name + "» — под блоком");
        } else {
            while (occupied(cx, cy)) { cx += 18; cy += 18; }
            Script.Root r = new Script.Root(cx, cy);
            r.chain.add(node);
            script.roots.add(r);
            toast("«" + a.name + "» на полотне");
        }
        focusNode = node;
        focusAt = System.currentTimeMillis();
        lastPlaced = node;
    }

    private Layout.Box placedBox() {
        Layout.Box b = lastPlaced == null ? null : boxOf(lastPlaced);
        if (b == null || b.owner == null) return null;
        int sx = toScreenX(b.x), sy = toScreenY(b.y);
        boolean seen = sx < canvasRight() && sx + b.w * zoom > canvasLeft()
                && sy < canvasBottom() && sy + b.totalH * zoom > Theme.TOPBAR_H;
        return seen ? b : null;
    }

    private static int indexOf(List<Script.Node> chain, Script.Node node) {
        for (int i = 0; i < chain.size(); i++) if (chain.get(i) == node) return i;
        return -1;
    }

    private int paletteLimit() {
        int share = width < 600 ? 40 : 55;
        return Math.max(Theme.PALETTE_MIN_W,
                Math.min(Math.min(Theme.PALETTE_MAX_W, width - 200), width * share / 100));
    }

    private void setPaletteWidth(int w) {
        Theme.PALETTE_W = Math.max(Theme.PALETTE_MIN_W, Math.min(paletteLimit(), w));
        script.paletteW = Theme.PALETTE_W;
        Ui.width(search, Palette.searchTextW());
        search.setX(Palette.searchTextX());
        if (finder != null) finder.resize(width, canvasBottom());
        palette.invalidate();
    }

    private boolean overSplitter(double mx) {
        return !Settings.chests() && Math.abs(mx - Theme.PALETTE_W) <= 3;
    }

    private void drawSplitter(GuiGraphicsExtractor ctx, int mouseX) {
        if (!resizingPalette && !overSplitter(mouseX)) return;
        Draw.rect(ctx, Theme.PALETTE_W - 2, 0, 2, canvasBottom(), Draw.opaque(Theme.ACCENT));
        for (int i = -1; i <= 1; i++)
            Draw.rect(ctx, Theme.PALETTE_W - 2, canvasBottom() / 2 + i * 6, 2, 3, Draw.opaque(0xFFFFFF));
    }

    @Override
    protected void init() {
        changeHint = true;
        if (top == null) top = new TopBar(font, this);
        if (tips == null) tips = new Tips(font, script);
        if (foot == null) foot = new StatusBar(font, new StatusBar.Host() {
            @Override public int lines() { return EditorScreen.this.lines(); }
            @Override public int blocks() { return countNodes(); }
            @Override public boolean dirty() { return EditorScreen.this.dirty(); }
            @Override public String picked() { return picked.isEmpty() ? "" : pickedSaid(); }
            @Override public double zoom() { return zoom; }
            @Override public int code() { return codeHash(); }
        });
        String text = search == null ? palette.query() : search.getValue();
        if (!viewRestored) {
            viewRestored = true;
            panX = script.viewX;
            panY = script.viewY;
            zoom = snapZoom(script.viewZoom);
        }
        if (script.paletteW > 0) Theme.PALETTE_W = script.paletteW;
        Theme.PALETTE_W = Math.max(Theme.PALETTE_MIN_W,
                Math.min(paletteLimit(), Theme.PALETTE_W));
        search = Ui.field(font, Palette.searchTextX(), Palette.SEARCH_Y + 6,
                Palette.searchTextW(), 10, "поиск блока…");
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(Draw.opaque(Theme.TEXT));
        search.setResponder(palette::setQuery);
        search.setValue(text);
        addWidget(search);
        palette.invalidate();
        if (script.fitOnOpen) {
            script.fitOnOpen = false;
            fitView();
            status = "";
        }
        List<String> said = new ArrayList<>();
        String updated = XeroCode.updateNote();
        if (!updated.isEmpty()) said.add(updated);
        String aside = Script.takeSetAside();
        if (!aside.isEmpty()) said.add("полотно не прочиталось — файл отложен: " + aside);
        List<String> lost = Script.lostOnLoad();
        if (!lost.isEmpty()) {
            Script.forgetLost();
            said.add("при загрузке полотна потеряно: " + String.join(", ", lost));
        }
        List<String> upgraded = Upgrade.done();
        if (!upgraded.isEmpty()) {
            Upgrade.forget();
            said.add("блоки, убранные сервером, заменены: " + String.join(", ", upgraded));
        }
        if (!said.isEmpty()) toast(String.join(" · ", said));
        if (editor != null) editor.resize(width, height);
        if (finder != null) finder.resize(width, canvasBottom());
        if (settings != null) settings.resize(width, height);
        if (condPicker != null) condPicker.resize(width, height);
        if (chest != null) chest.resize(width, height);
        if (backpack != null) backpack.resize(width, height);
        menu = null;
        blockMenu = null;
        top.invalidate();
        reopenPending();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (!overlayOpen() && !busy()) XeroCode.offerUpdate(this);
    }

    @Override
    public boolean shouldCloseOnEsc() { return !Env.browser(); }

    void toast(String message) {
        status = message;
        statusAt = System.currentTimeMillis();
    }

    private String snapshot() { return History.snapshot(script); }

    private void pushUndo() { pushUndo(snapshot()); }

    private void pushUndo(String state) {
        History.push(state);
        revision++;
    }

    private void undo() {
        closeOverlays();
        if (History.undo(script)) { Collab.afterHistory(script); revision++; toast("отменено"); }
    }

    private void redo() {
        closeOverlays();
        if (History.redo(script)) { Collab.afterHistory(script); revision++; toast("возвращено"); }
    }

    private void openFinder(String query) {
        closeOverlays();
        if (finder == null) {
            finder = new FindPanel(font, script, width, canvasBottom(), this::jumpTo);
            top.invalidate();
        }
        if (query != null) finder.setQuery(query);
        finder.refocus();
        search.setFocused(false);
    }

    private void closeFinder() {
        finder = null;
        found.clear();
        foundStamp = Integer.MIN_VALUE;
        focusNode = null;
        top.invalidate();
    }

    private void toggleFinder() {
        if (finder == null) openFinder(null); else closeFinder();
    }

    private void jumpTo(Finder.Hit hit) {
        reveal(hit.node);
    }

    private void reveal(Script.Node node) {
        focusNode = node;
        focusAt = System.currentTimeMillis();
        for (Script.Root r : script.roots)
            if (Script.unfoldTo(r.chain, node)) {
                if (r.folded) { r.folded = false; foldsChanged(); }
                break;
            }
        Layout.Box box = boxOf(node);
        if (box == null) return;
        if (zoom < 0.7) applyZoom(snapZoom(0.75), midX(), midY());
        centerOnBox(box);
    }

    private static final int NAV_MAX = 32;

    private void rememberSpot() {
        navBack.add(new double[]{panX, panY, zoom});
        if (navBack.size() > NAV_MAX) navBack.remove(0);
    }

    private void goBack() {
        if (navBack.isEmpty()) { toast("возвращаться некуда"); return; }
        double[] spot = navBack.remove(navBack.size() - 1);
        panAnim = false;
        zoom = spot[2];
        glideTo(spot[0], spot[1]);
    }

    private List<Script.Node> callsOf(Script.Node declaration) {
        List<Script.Node> out = new ArrayList<>();
        String name = Functions.nameOf(declaration);
        if (name.isBlank()) return out;
        for (Script.Root r : script.roots) collectCalls(r.chain, declaration.isProcess(), name, out);
        return out;
    }

    private static void collectCalls(List<Script.Node> chain, boolean process, String name,
                                     List<Script.Node> out) {
        for (Script.Node n : chain) {
            if (n.invokes() && n.isStart() == process && name.equals(Functions.targetOf(n))) out.add(n);
            collectCalls(n.body, process, name, out);
        }
    }

    private Script.Node jumpTarget(Script.Node n) {
        if (n.invokes()) {
            Functions.Known known = Functions.of(script);
            Functions.Signature s = (n.isStart() ? known.processes() : known.functions())
                    .get(Functions.targetOf(n));
            return s == null ? null : s.declaration();
        }
        if (!n.declares()) return null;
        List<Script.Node> calls = callsOf(n);
        return calls.isEmpty() ? null : calls.get(Math.floorMod(callCycle, calls.size()));
    }

    private void goTo(Script.Node from) {
        Script.Node to = jumpTarget(from);
        if (to == null) {
            toast(from.declares() ? "её нигде не вызывают" : "на полотне нет такой функции");
            return;
        }
        String back = Settings.get().label(Settings.Hot.GO_BACK) + " — назад";
        if (from.declares()) {
            int total = callsOf(from).size();
            int at = Math.floorMod(callCycle, total) + 1;
            callCycle++;
            toast("вызов " + at + " из " + total + " · " + back);
        } else {
            callCycle = 0;
            toast("объявление «" + Functions.targetOf(from) + "» · " + back);
        }
        rememberSpot();
        reveal(to);
    }

    private Layout.Box boxOf(Script.Node node) {
        for (Layout.Box b : layout().boxes) if (b.node == node) return b;
        return null;
    }

    private void centerOn(double cx, double cy, boolean glide) {
        double tx = midX() - canvasLeft() - cx * zoom;
        double ty = midY() - Theme.TOPBAR_H - cy * zoom;
        if (glide) glideTo(tx, ty);
        else { panX = tx; panY = ty; panAnim = false; }
    }

    private void centerOnBox(Layout.Box box) {
        centerOn(box.x + Math.min(box.w, 260) / 2.0, box.y + Math.min(box.totalH, 170) / 2.0, true);
    }

    private void glideTo(double tx, double ty) {
        if (Math.abs(tx - panX) < 1 && Math.abs(ty - panY) < 1) { panX = tx; panY = ty; return; }
        panFromX = panX;
        panFromY = panY;
        panToX = tx;
        panToY = ty;
        panStart = System.currentTimeMillis();
        panAnim = true;
    }

    private void advancePan() {
        if (!panAnim) return;
        double t = (System.currentTimeMillis() - panStart) / 220.0;
        if (t >= 1) { panX = panToX; panY = panToY; panAnim = false; return; }
        double k = t * t * (3 - 2 * t);
        panX = panFromX + (panToX - panFromX) * k;
        panY = panFromY + (panToY - panFromY) * k;
    }

    private void syncFound() {
        if (finder == null) return;
        int stamp = layoutStamp * 31 + System.identityHashCode(finder.hits())
                + (finder.listing() ? 1 : 0);
        if (stamp == foundStamp) return;
        foundStamp = stamp;
        found.clear();
        if (finder.listing()) return;
        Set<Script.Node> want = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Finder.Hit hit : finder.hits()) want.add(hit.node);
        for (Layout.Box b : layout.boxes) if (want.contains(b.node)) found.add(b);
    }

    private int focusInk() {
        if (focusNode == null) return 0;
        long age = System.currentTimeMillis() - focusAt;
        if (finder != null)
            return age < 900 ? 0x80 + (int) (0x7F * Math.abs(Math.cos(age / 130.0))) : 0xFF;
        if (age > 1800) { focusNode = null; return 0; }
        return (int) (255 * (1 - age / 1800.0));
    }

    private void drawFound(GuiGraphicsExtractor ctx, double vx0, double vy0, double vx1, double vy1) {
        int ink = focusInk();
        if (found.isEmpty() && (focusNode == null || ink == 0)) return;
        ScreenRectangle area = canvasArea();
        Draw.batch(Batch.open(ctx, area, area, 512));
        int soft = Draw.mix(Theme.ACCENT, Theme.CANVAS, 0.3f);
        for (Layout.Box b : found) {
            if (b.node == focusNode || !visible(b, vx0, vy0, vx1, vy1)) continue;
            Draw.roundOutline(ctx, b.x - 2, b.y - 2, b.w + 4, b.totalH + 4, 5,
                    Draw.argb(0xC0, soft));
        }
        Layout.Box focus = ink == 0 ? null : focusBox();
        if (focus != null && visible(focus, vx0, vy0, vx1, vy1)) {
            Draw.roundOutline(ctx, focus.x - 2, focus.y - 2, focus.w + 4, focus.totalH + 4, 5,
                    Draw.argb(ink, Theme.ACCENT));
            Draw.roundOutline(ctx, focus.x - 3, focus.y - 3, focus.w + 6, focus.totalH + 6, 6,
                    Draw.argb(ink / 3, Theme.ACCENT));
        }
        Draw.batch(null);
    }

    private boolean rides(Layout.Box box) {
        return moving && picked.lastCovered().contains(box.node);
    }

    private void drawMoving(GuiGraphicsExtractor ctx, ScreenRectangle area,
                            double vx0, double vy0, double vx1, double vy1) {
        if (!moving) return;
        int dx = (int) Math.round(moveDX), dy = (int) Math.round(moveDY);
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(dx, dy);
        double sx0 = vx0 - dx, sy0 = vy0 - dy, sx1 = vx1 - dx, sy1 = vy1 - dy;
        Draw.batch(Batch.open(ctx, area, area, 512));
        for (Selection.Piece p : picked.pieces())
            for (Layout.Box box : p.boxes())
                if (visible(box, sx0, sy0, sx1, sy1)) BlockView.shadow(ctx, box);
        Draw.batch(null);
        for (Selection.Piece p : picked.pieces()) {
            Draw.batch(Batch.open(ctx, area, area, 512));
            for (Layout.Box box : p.boxes())
                if (visible(box, sx0, sy0, sx1, sy1)) BlockView.block(ctx, font, box, look);
            Draw.batch(null);
        }
        m.popMatrix();
    }

    private void drawBand(GuiGraphicsExtractor ctx) {
        if (!banding) return;
        ScreenRectangle area = canvasArea();
        Draw.batch(Batch.open(ctx, area, area, 16));
        int bx = toScreenX(Math.min(bandX0, bandX1)), by = toScreenY(Math.min(bandY0, bandY1));
        int bw = toScreenX(Math.max(bandX0, bandX1)) - bx;
        int bh = toScreenY(Math.max(bandY0, bandY1)) - by;
        int band = bandMode == Selection.SUB ? Theme.DANGER : Theme.ACCENT;
        Draw.rect(ctx, bx, by, bw, bh, Draw.argb(0x26, band));
        Draw.roundOutline(ctx, bx, by, bw, bh, 2, Draw.argb(0xC0, band));
        Draw.batch(null);
    }

    private void drawMap(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!Settings.minimap() || drag != null || script.roots.isEmpty()) { map.hide(); return; }
        map.frame(layout, layoutStamp, canvasLeft(), Theme.TOPBAR_H, canvasRight(), canvasBottom());
        if (!map.shown()) return;
        map.draw(ctx, toCanvasX(canvasLeft()), toCanvasY(Theme.TOPBAR_H),
                toCanvasX(canvasRight()), toCanvasY(canvasBottom()),
                mapDragging || map.hit(mouseX, mouseY));
        if (!found.isEmpty()) map.marks(ctx, found, Theme.ACCENT);
        Layout.Box focus = focusBox();
        if (focus != null) map.marks(ctx, List.of(focus), Theme.OK);
    }

    private Layout focusLayout;
    private Script.Node focusOf;
    private Layout.Box focusCache;

    private Layout.Box focusBox() {
        if (focusNode == null) return null;
        Layout l = layout();
        if (l != focusLayout || focusNode != focusOf) {
            focusLayout = l;
            focusOf = focusNode;
            focusCache = boxOf(focusNode);
        }
        return focusCache;
    }

    private void closeOverlays() {
        if (settings != null) { settings.dispose(); settings = null; }
        finishEditor();
        menu = null;
        blockMenu = null;
        backpack = null;
        pressChip = null;
        pressBox = null;
    }

    private void finishEditor() {
        if (editor == null) return;
        if (!editor.cancelled()) editor.commit();
        editor.dispose();
        if (editor.changed() && editorUndo != null) pushUndo(editorUndo);
        editorUndo = null;
        boolean toWorld = editor.pickInWorld();
        Script.Node node = editor.node();
        int arg = editor.argIndex(), cell = editor.selected();
        editor = null;
        revision++;
        if (toWorld && node != null) LocationPick.start(node, arg, cell);
    }

    private static Script.Node pendingNode;
    private static int pendingArg, pendingCell;

    public static void openPanelAfter(Script.Node node, int argIndex, int cell) {
        pendingNode = node;
        pendingArg = argIndex;
        pendingCell = cell;
    }

    private void reopenPending() {
        if (pendingNode == null) return;
        Script.Node node = pendingNode;
        int arg = pendingArg, cell = pendingCell;
        pendingNode = null;
        if (arg < 0 || arg >= node.args().size()) return;
        List<Value> slot = node.values.get(arg);
        boolean list = node.args().get(arg).list;
        openValue(node, arg, canvasLeft() + 20, Theme.TOPBAR_H + 20,
                false, list && slot != null && cell >= 0 && cell < slot.size() ? cell : -1);
    }

    private static final int TAPE_SMALL = 48, QUIET_LIMIT = 20;
    private static final long RECORD_BUDGET = 3_000_000L;

    private record ShadowTape(long key, int[] quads) {}

    private final Map<Script.Root, Tape> tapes = new IdentityHashMap<>();
    private final Map<Script.Root, ShadowTape> shadowTapes = new IdentityHashMap<>();
    private int styleStamp, callsStamp, hintChanges = Integer.MIN_VALUE;
    private int[] shadowBuffer = new int[0];
    private int[] gridBuffer = new int[0];
    private boolean inFrame, framing, hintChecked;
    private boolean changeHint = true;
    private int quietFrames, hintRevision = Integer.MIN_VALUE;
    private int hashStamp = Integer.MIN_VALUE, hashCache;

    private int style() { return styleStamp * 31 + Tape.epoch(); }

    private void restyle() {
        styleStamp++;
        revision++;
    }

    private void pruneTapes() {
        Set<Script.Root> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(script.roots);
        tapes.keySet().retainAll(live);
        shadowTapes.keySet().retainAll(live);
    }

    private long tapeKey(Layout.Chunk chunk, int flags) {
        return ((long) chunk.key << 32) | (flags & 0xFF);
    }

    private int codeHash() {
        layout();
        if (hashStamp != layoutStamp) {
            hashCache = script.codeHash();
            hashStamp = layoutStamp;
        }
        return hashCache;
    }

    private Layout layout() {
        if (inFrame && layout != null) return layout;
        if (layout != null && (!changeHint || hintChecked) && revision == hintRevision
                && Collab.changes() == hintChanges && (!framing || quietFrames < QUIET_LIMIT)) {
            if (framing) quietFrames++;
            return layout;
        }
        quietFrames = 0;
        hintChecked = changeHint;
        hintRevision = revision;
        hintChanges = Collab.changes();
        int fingerprint = script.fingerprint();
        int stamp = fingerprint * 31 + revision;
        if (layout == null || stamp != layoutStamp) {
            nodeCount = -1;
            callsStamp = Functions.rebuild(script);
            layout = Layout.of(script, font, null, layout, style(), callsStamp);
            layoutStamp = stamp;
            pruneTapes();
            if (!stampTaken) { savedStamp = script.codeHash(); stampTaken = true; }
        }
        return layout;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft != null) Backstage.hide(minecraft);
        inFrame = false;
        Draw.batch(null);
        SmoothText.clip(null);
        if (settings != null && settings.consumeChanged()) {
            restyle();
            top.invalidate();
            search.setTextColor(Draw.opaque(Theme.TEXT));
            if (finder != null) finder.resize(width, canvasBottom());
        }
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        advancePan();
        mouseCanvasX = toCanvasX(mouseX);
        mouseCanvasY = toCanvasY(mouseY);
        Collab.frame(drag != null || editor != null ? heldRoot : null,
                mouseCanvasX, mouseCanvasY, zoom);

        hintChecked = false;
        framing = true;
        layout = layout();
        framing = false;
        inFrame = true;
        picked.prune();
        if (moving) picked.covered();
        if (finder != null) finder.sync(layoutStamp);
        syncFound();
        updateHover(mouseX, mouseY);
        snap = drag == null ? null : findSnap(layout);
        Layout view = syncGhost();

        look.hover = hoverBox;
        look.chip = hoverChip;
        look.dragging = drag != null;
        look.carrying = carry != null;
        look.accepts = accepts;
        look.mx = mouseCanvasX;
        look.my = mouseCanvasY;

        ctx.enableScissor(canvasLeft(), Theme.TOPBAR_H, width, canvasBottom());
        drawGrid(ctx);
        if (script.roots.isEmpty() && drag == null) drawEmptyHint(ctx);

        enterCanvas(ctx);
        double vx0 = toCanvasX(canvasLeft()) - 8, vx1 = toCanvasX(width) + 8;
        double vy0 = toCanvasY(Theme.TOPBAR_H) - 8, vy1 = toCanvasY(canvasBottom()) + 8;
        ScreenRectangle area = canvasArea();
        SmoothText.clip(area);

        boolean taping = carry == null && !moving;
        int flags = SmoothText.flags(ctx);
        drawShadows(ctx, view, area, taping, flags, vx0, vy0, vx1, vy1);
        drawChunks(ctx, view, area, taping, flags, vx0, vy0, vx1, vy1);

        drawFound(ctx, vx0, vy0, vx1, vy1);
        drawMoving(ctx, area, vx0, vy0, vx1, vy1);

        if (snap != null || drag != null || held != null) {
            Draw.batch(Batch.open(ctx, area, area, 512));
            if (snap != null) drawSnapMark(ctx);
            if (drag != null) drawDragged(ctx);
            if (held != null) drawHeld(ctx);
            Draw.batch(null);
        }

        ctx.pose().popMatrix();
        if (moving) picked.drawOutlines(ctx, picked.pieces(), Math.round(moveDX), Math.round(moveDY),
                area, vx0, vy0, vx1, vy1);
        drawBand(ctx);
        Peers.render(ctx, font, script, layout, area, canvasLeft(), Theme.TOPBAR_H,
                panX, panY, zoom, width, height);
        ctx.disableScissor();
        SmoothText.clip(null);
        ctx.nextStratum();

        drawMap(ctx, mouseX, mouseY);
        if (finder != null) finder.render(ctx, mouseX, mouseY, delta);
        if (!Settings.chests()) {
            palette.render(ctx, font, mouseX, mouseY, canvasBottom());
            search.extractRenderState(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, font, search);
        }
        top.draw(ctx, mouseX, mouseY);
        drawSplitter(ctx, mouseX);
        if (Settings.statusBar()) foot.draw(ctx, script, 0, canvasBottom(), width, mouseX, mouseY);

        ctx.nextStratum();
        drawPocket(ctx, mouseX, mouseY);
        drawPlus(ctx, mouseX, mouseY);
        drawBar(ctx, mouseX, mouseY);
        drawToast(ctx);
        if (editor != null) editor.render(ctx, mouseX, mouseY, delta);
        if (menu != null) menu.render(ctx, font, mouseX, mouseY);
        else if (blockMenu != null) blockMenu.render(ctx, mouseX, mouseY);
        else if (!modal() && carry == null) drawTooltips(ctx, mouseX, mouseY);
        Carry.draw(ctx, font, carry, carryHeld, mouseX, mouseY, width, height);
        if (exitPrompt) drawExitPrompt(ctx, mouseX, mouseY);
        if (condPicker != null) {
            ctx.nextStratum();
            condPicker.render(ctx, mouseX, mouseY, delta);
        }
        if (backpack != null) {
            ctx.nextStratum();
            backpack.render(ctx, mouseX, mouseY, delta);
        }
        if (chest != null) {
            ctx.nextStratum();
            chest.render(ctx, mouseX, mouseY, delta);
            Catalog.Action tipped = chest.hoverAction();
            List<Component> tip = chest.hoverLines();
            if (tip == null) tip = chest.headLines(mouseX, mouseY);
            if (tipped != null) tips.action(ctx, tipped, null, width, mouseX, mouseY);
            else if (tip != null) ctx.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
        if (settings != null) settings.render(ctx, mouseX, mouseY, delta);
        inFrame = false;
        changeHint = false;
    }

    private double snapped(double pan) {
        int gs = minecraft != null && minecraft.getWindow() != null
                ? Math.max(1, minecraft.getWindow().getGuiScale()) : 1;
        return Math.round(pan * gs) / (double) gs;
    }

    private void enterCanvas(GuiGraphicsExtractor ctx) {
        var m = ctx.pose();
        m.pushMatrix();
        m.translate((float) (canvasLeft() + snapped(panX)), (float) (Theme.TOPBAR_H + snapped(panY)));
        m.scale((float) zoom, (float) zoom);
    }

    private static boolean tapeable(Layout.Chunk c, double x0, double y0, double x1, double y1) {
        return c.to - c.from <= TAPE_SMALL || c.x0 >= x0 && c.x1 <= x1 && c.y0 >= y0 && c.y1 <= y1;
    }

    private void drawShadows(GuiGraphicsExtractor ctx, Layout view, ScreenRectangle area, boolean taping,
                             int flags, double vx0, double vy0, double vx1, double vy1) {
        Batch shadows = Batch.reuse(ctx, area, area, shadowBuffer);
        Draw.batch(shadows);
        for (Layout.Chunk chunk : view.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            long tapeKey = tapeKey(chunk, flags);
            ShadowTape taped = taping && chunk.root != null ? shadowTapes.get(chunk.root) : null;
            if (taped != null && taped.key() == tapeKey) { shadows.append(taped.quads()); continue; }
            boolean record = taping && chunk.root != null && tapeable(chunk, vx0, vy0, vx1, vy1);
            int from = shadows.size();
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = view.boxes.get(i);
                if (record || !rides(box) && visible(box, vx0, vy0, vx1, vy1)) BlockView.shadow(ctx, box);
            }
            if (record) shadowTapes.put(chunk.root, new ShadowTape(tapeKey, shadows.copyFrom(from)));
        }
        shadowBuffer = shadows.buffer();
        Draw.batch(null);
    }

    private void drawChunks(GuiGraphicsExtractor ctx, Layout view, ScreenRectangle area, boolean taping,
                            int flags, double vx0, double vy0, double vx1, double vy1) {
        Script.Root hot = hoverBox == null ? null : hoverBox.root;
        long budget = System.nanoTime() + RECORD_BUDGET;
        for (Layout.Chunk chunk : view.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            Script.Root owner = chunk.root;
            long tapeKey = tapeKey(chunk, flags);
            boolean usable = taping && owner != null && owner != hot;
            Tape tape = usable ? tapes.get(owner) : null;
            if (tape != null && tape.valid(tapeKey)) tape.replay(ctx, font, area);
            else drawChunk(ctx, view, chunk, area, tapeKey, usable && tapeable(chunk, vx0, vy0, vx1, vy1)
                    && System.nanoTime() < budget, vx0, vy0, vx1, vy1);
            chunkTag(ctx, view.boxes.get(chunk.from), area, vx0, vy0, vx1, vy1);
            if (moving || picked.isEmpty() || view != layout) continue;
            List<Selection.Piece> mine = picked.byRoot().get(owner);
            if (mine == null) continue;
            ctx.pose().popMatrix();
            picked.drawOutlines(ctx, mine, 0, 0, area, vx0, vy0, vx1, vy1);
            enterCanvas(ctx);
        }
    }

    private void drawChunk(GuiGraphicsExtractor ctx, Layout view, Layout.Chunk chunk, ScreenRectangle area,
                           long tapeKey, boolean record, double vx0, double vy0, double vx1, double vy1) {
        Draw.batch(Batch.open(ctx, area, area, 512));
        if (record) Tape.begin(tapeKey);
        try {
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = view.boxes.get(i);
                if (!record && (rides(box) || !visible(box, vx0, vy0, vx1, vy1))) continue;
                BlockView.block(ctx, font, box, look);
            }
            if (record) tapes.put(chunk.root, Tape.end(Draw.batch()));
        } finally {
            Tape.abort();
        }
        Draw.batch(null);
    }

    private void chunkTag(GuiGraphicsExtractor ctx, Layout.Box head, ScreenRectangle area,
                          double vx0, double vy0, double vx1, double vy1) {
        if (!tagShown(head) || rides(head)) return;
        if (head.x + 5 + tagW(head) < vx0 || head.x > vx1 || head.y < vy0 || head.y - TAG_H > vy1) return;
        Draw.batch(Batch.open(ctx, area, area, 16));
        drawLineTag(ctx, head);
        Draw.batch(null);
    }

    private static boolean visible(Layout.Box b, double x0, double y0, double x1, double y1) {
        return b.x + b.w >= x0 && b.x <= x1 && b.bottom() >= y0 && b.y <= y1;
    }

    private void updateHover(int mouseX, int mouseY) {
        hoverBox = null;
        hoverChip = null;
        if (drag != null || moving || banding || modal()
                || mouseX < canvasLeft() || mouseY < Theme.TOPBAR_H || mouseY >= canvasBottom()) return;
        if (editor != null && editor.contains(mouseX, mouseY)) return;
        if (finder != null && finder.contains(mouseX, mouseY)) return;
        if (map.hit(mouseX, mouseY)) return;
        if (barShown() && Ui.hit(mouseX, mouseY, barX(), barY(), barW(), BAR_H)) return;
        for (int ci = layout.chunks.size() - 1; ci >= 0; ci--) {
            Layout.Chunk chunk = layout.chunks.get(ci);
            if (!chunk.visible(mouseCanvasX, mouseCanvasY, mouseCanvasX, mouseCanvasY)) continue;
            for (int i = chunk.to - 1; i >= chunk.from; i--) {
                Layout.Box b = layout.boxes.get(i);
                if (!b.contains(mouseCanvasX, mouseCanvasY)) continue;
                Layout.Chip c = b.chipAt(mouseCanvasX, mouseCanvasY);
                if (c != null) { hoverBox = b; hoverChip = c; return; }
                if (b.hitGrab(mouseCanvasX, mouseCanvasY) || b.hitTail(mouseCanvasX, mouseCanvasY)) {
                    hoverBox = b;
                    return;
                }
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor ctx) {
        int style = Settings.gridStyle();
        if (style == Settings.GRID_NONE) return;
        double step = 26 * zoom;
        while (step < 13) step *= 2;
        double ox = canvasLeft() + panX, oy = Theme.TOPBAR_H + panY;
        int faint = Theme.GRID, strong = Theme.GRID_STRONG;
        if (style == Settings.GRID_DOTS) {
            drawGridDots(ctx, step, ox, oy, faint, strong);
            return;
        }

        int i0 = (int) Math.floor((canvasLeft() - ox) / step);
        for (int i = i0; ; i++) {
            int x = (int) Math.round(ox + i * step);
            if (x > width) break;
            if (x < canvasLeft()) continue;
            Draw.rect(ctx, x, Theme.TOPBAR_H, 1, canvasBottom() - Theme.TOPBAR_H,
                    Math.floorMod(i, 4) == 0 ? strong : faint);
        }
        int j0 = (int) Math.floor((Theme.TOPBAR_H - oy) / step);
        for (int j = j0; ; j++) {
            int y = (int) Math.round(oy + j * step);
            if (y > canvasBottom()) break;
            if (y < Theme.TOPBAR_H) continue;
            Draw.rect(ctx, canvasLeft(), y, width - canvasLeft(), 1,
                    Math.floorMod(j, 4) == 0 ? strong : faint);
        }
    }

    private void drawGridDots(GuiGraphicsExtractor ctx, double step, double ox, double oy,
                              int faint, int strong) {
        ScreenRectangle area = canvasArea();
        Batch dots = Batch.reuse(ctx, area, area, gridBuffer);
        Draw.batch(dots);
        int i0 = (int) Math.floor((canvasLeft() - ox) / step);
        int j0 = (int) Math.floor((Theme.TOPBAR_H - oy) / step);
        for (int i = i0; ; i++) {
            int x = (int) Math.round(ox + i * step);
            if (x > width) break;
            if (x < canvasLeft()) continue;
            boolean bigX = Math.floorMod(i, 4) == 0;
            for (int j = j0; ; j++) {
                int y = (int) Math.round(oy + j * step);
                if (y > canvasBottom()) break;
                if (y < Theme.TOPBAR_H) continue;
                boolean big = bigX && Math.floorMod(j, 4) == 0;
                int size = big ? 2 : 1;
                Draw.rect(ctx, x, y, size, size, big ? strong : faint);
            }
        }
        gridBuffer = dots.buffer();
        Draw.batch(null);
    }

    private void drawEmptyHint(GuiGraphicsExtractor ctx) {
        boolean keys = !Env.touch();
        int w = Math.min(230, width - canvasLeft() - 16), h = keys ? 64 : 52;
        int x = canvasLeft() + (width - canvasLeft() - w) / 2;
        int y = (Theme.TOPBAR_H + canvasBottom() - h) / 2;
        Draw.round(ctx, x, y, w, h, 8, Draw.argb(0x50, Ui.PANEL));
        Draw.roundOutline(ctx, x, y, w, h, 8, Draw.argb(0x66, Ui.BORDER));
        Draw.textFit(ctx, font, "Полотно пустое", x + 20, y + 16, w - 40, Theme.TEXT_DIM, false);
        Draw.textFit(ctx, font, Env.touch() ? "двойной тап — каталог блоков" : "двойной клик — каталог блоков",
                x + 20, y + 30, w - 40, Theme.TEXT_FAINT, false);
        if (keys) Draw.textFit(ctx, font, Settings.get().label(Settings.Hot.QUICK_ADD) + " — найти по имени",
                x + 20, y + 42, w - 40, Theme.TEXT_FAINT, false);
    }

    private static final class ChainView {
        private List<Script.Node> chain;
        private int x, y, key;
        private Layout layout;

        Layout at(List<Script.Node> chain, int x, int y, int key, Font font) {
            if (layout == null || chain != this.chain || x != this.x || y != this.y || key != this.key) {
                layout = Layout.ofChain(chain, x, y, font);
                this.chain = chain;
                this.x = x;
                this.y = y;
                this.key = key;
            }
            return layout;
        }
    }

    private final ChainView draggedView = new ChainView(), markView = new ChainView();
    private final List<ChainView> heldViews = new ArrayList<>();
    private static final int HELD_X = 26, HELD_Y = 10;
    private List<Script.Root> held;
    private String heldName = "";
    private List<Script.Node> sizedDrag;
    private int sizedKey, payloadW, payloadH;

    private int dragKey() { return style() * 31 + callsStamp; }

    private void sizeDrag() {
        int key = dragKey();
        if (drag == sizedDrag && key == sizedKey) return;
        payloadW = Layout.ofChain(drag, 0, 0, font).boxes.get(0).w;
        payloadH = Layout.chainHeight(drag, font);
        sizedDrag = drag;
        sizedKey = key;
    }

    private void drawDragged(GuiGraphicsExtractor ctx) {
        int px = (int) Math.round(mouseCanvasX - dragOffX);
        int py = (int) Math.round(mouseCanvasY - dragOffY);
        Layout l = draggedView.at(drag, px, py, dragKey(), font);
        for (Layout.Box b : l.boxes) BlockView.shadow(ctx, b);
        for (Layout.Box b : l.boxes) BlockView.block(ctx, font, b, look);
    }

    private void drawHeld(GuiGraphicsExtractor ctx) {
        int px = (int) Math.round(mouseCanvasX - HELD_X), py = (int) Math.round(mouseCanvasY - HELD_Y);
        while (heldViews.size() < held.size()) heldViews.add(new ChainView());
        for (int i = 0; i < held.size(); i++) {
            Script.Root r = held.get(i);
            Layout l = heldViews.get(i).at(r.chain, px + (int) r.x, py + (int) r.y, dragKey(), font);
            for (Layout.Box b : l.boxes) BlockView.shadow(ctx, b);
            for (Layout.Box b : l.boxes) BlockView.block(ctx, font, b, look);
        }
    }

    private void drawSnapMark(GuiGraphicsExtractor ctx) {
        int mx = ghost != null ? ghost.x : snap.x;
        int my = ghost != null ? ghost.y : snap.y;
        Layout mark = markView.at(drag, mx, my, dragKey(), font);
        int ink = Draw.opaque(Theme.GHOST);
        for (Layout.Box b : mark.boxes) BlockView.ghost(ctx, b, ink);
    }

    private boolean choosingFile;

    private void openLoadDialog() {
        closeOverlays();
        if (Env.browser()) {
            if (minecraft != null) minecraft.gui.setScreen(new LoadScreen(this, this::loadJson));
            return;
        }
        if (choosingFile) return;
        choosingFile = true;
        Path dir = Codespace.savedDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось создать папку {}", dir, e);
        }
        toast(FileDialog.hint());
        FileDialog.open("Загрузить json", dir.toAbsolutePath() + File.separator,
                new String[]{"*.json"}, "код JustMC (*.json)", file -> {
                    choosingFile = false;
                    if (file == null || (minecraft != null && minecraft.gui.screen() != this)) return;
                    loadJson(file);
                });
    }

    private void loadJson(Path file) {
        JsonArray handlers = null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("handlers") && root.get("handlers").isJsonArray())
                handlers = root.getAsJsonArray("handlers");
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] не удалось прочитать {}", file, e);
            toast("файл не читается");
            return;
        }
        if (handlers == null) {
            toast("в файле нет «handlers» — это не код JustMC");
            return;
        }
        closeOverlays();
        drag = null;
        snap = null;
        hoverBox = null;
        hoverChip = null;
        pushUndo();
        script.roots.clear();
        Importer.Result res;
        try {
            res = Importer.importInto(script, handlers, font);
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] импорт из {} упал", file, e);
            toast("код не разобрался, см. лог");
            return;
        }
        fitView();
        String note = Ui.plural(res.lines, "строка", "строки", "строк")
                + " · " + Ui.plural(res.blocks, "блок", "блока", "блоков");
        if (res.brokenLines > 0)
            note += " · не разобралось " + Ui.plural(res.brokenLines, "строка", "строки", "строк");
        toast("полотно заменено: " + note + " · Ctrl+Z вернёт");
    }

    private int nodeCount = -1;

    private int countNodes() {
        if (nodeCount < 0) nodeCount = Script.blocksIn(script.roots);
        return nodeCount;
    }

    private void onTopButton(int id) {
        switch (id) {
            case TopBar.UNDO -> undo();
            case TopBar.REDO -> redo();
            case TopBar.PLAY -> askExit("play");
            case TopBar.BUILD -> askExit("build");
            case TopBar.CLEAR -> clearCanvas();
            case TopBar.ZOOM_OUT -> zoomTo(zoom / 1.15, (canvasLeft() + width) / 2.0,
                    midY());
            case TopBar.ZOOM_IN -> zoomTo(zoom * 1.15, (canvasLeft() + width) / 2.0,
                    midY());
            case TopBar.FIT -> fitView();
            case TopBar.FIND -> toggleFinder();
            case TopBar.UPLOAD -> publish(null);
            case TopBar.LOAD -> openLoadDialog();
            case TopBar.BACKPACK -> openBackpack();
            case TopBar.MARKET -> openModules();
            case TopBar.SETTINGS -> openSettings();
            case TopBar.ORIGINAL -> toOriginal();
            default -> { }
        }
    }

    private void clearCanvas() {
        if (script.roots.isEmpty()) return;
        closeOverlays();
        pushUndo();
        script.roots.clear();
        toast("полотно очищено");
    }

    private static final double MIN_ZOOM = 0.10, MAX_ZOOM = 2.0;

    private double[] zoomSteps() {
        int gs = minecraft != null && minecraft.getWindow() != null
                ? Math.max(1, minecraft.getWindow().getGuiScale()) : 2;
        double base = 1.0 / gs;
        List<Double> out = new ArrayList<>();
        for (double k = 0.125; k <= gs * MAX_ZOOM + 1e-6; k += k < 0.25 ? 0.125 : k < 1 ? 0.25 : 1) {
            double z = k * base;
            if (z >= MIN_ZOOM - 1e-6 && z <= MAX_ZOOM + 1e-6) out.add(z);
        }
        if (out.isEmpty()) out.add(1.0);
        double[] steps = new double[out.size()];
        for (int i = 0; i < steps.length; i++) steps[i] = out.get(i);
        return steps;
    }

    private static int nearestStep(double[] steps, double target) {
        int best = 0;
        for (int i = 1; i < steps.length; i++)
            if (Math.abs(steps[i] - target) < Math.abs(steps[best] - target)) best = i;
        return best;
    }

    private double snapZoom(double target) {
        double[] steps = zoomSteps();
        double best = steps[nearestStep(steps, target)];
        return Math.abs(best - target) <= target * 0.03 ? best : target;
    }

    public boolean touchPan(double mx, double my, double dx, double dy) {
        if (overlayOpen() || (finder != null && mx >= finder.x())) return false;
        changeHint = true;
        if (my < Theme.TOPBAR_H) return true;
        if (mx < canvasLeft()) { palette.scrollBy(dy / 42.0, canvasBottom()); return true; }
        panAnim = false;
        panX += dx;
        panY += dy;
        return true;
    }

    public void touchZoom(double mx, double my, double factor) {
        if (overlayOpen() || mx < canvasLeft() || my < Theme.TOPBAR_H) return;
        changeHint = true;
        applyZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor)), mx, my);
    }

    public void touchHold(double mx, double my) {
        if (overlayOpen() || busy() || hoverBox == null || hoverChip != null) return;
        if (!hoverBox.hitGrab(toCanvasX(mx), toCanvasY(my))) return;
        openBlockMenu(hoverBox, (int) mx, (int) my);
    }

    public boolean touchScrolls(double mx, double my) {
        if (panel() != null) return true;
        if (menu != null && menu.contains(mx, my)) return true;
        if (blockMenu != null && blockMenu.contains(mx, my)) return true;
        if (editor != null && editor.contains(mx, my)) return true;
        return finder != null && finder.contains(mx, my);
    }

    private Overlay panel() {
        if (condPicker != null) return condPicker;
        if (chest != null) return chest;
        if (backpack != null) return backpack;
        return settings;
    }

    private boolean modal() {
        return panel() != null || exitPrompt || menu != null || blockMenu != null;
    }

    private boolean overlayOpen() { return modal() || editor != null; }

    private void settle(Overlay panel) {
        if (!panel.isClosed()) return;
        if (panel == condPicker) condPicker = null;
        else if (panel == chest) chest = null;
        else if (panel == backpack) backpack = null;
        else if (panel == settings) closeSettings();
    }

    private void zoomTo(double target, double aroundX, double aroundY) {
        applyZoom(snapZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target))), aroundX, aroundY);
    }

    private void applyZoom(double target, double aroundX, double aroundY) {
        panAnim = false;
        double before = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target));
        double cx = (aroundX - canvasLeft() - panX) / before, cy = (aroundY - Theme.TOPBAR_H - panY) / before;
        panX = aroundX - canvasLeft() - cx * zoom;
        panY = aroundY - Theme.TOPBAR_H - cy * zoom;
    }

    private void fitView() {
        panAnim = false;
        if (script.roots.isEmpty()) { zoom = 1; panX = 60; panY = 50; toast("масштаб 100%"); return; }
        Layout l = layout();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Layout.Box b : l.boxes) {
            minX = Math.min(minX, b.x);
            minY = Math.min(minY, b.y);
            maxX = Math.max(maxX, b.x + b.w);
            maxY = Math.max(maxY, b.bottom());
        }
        int vw = Math.max(40, canvasRight() - canvasLeft() - 40);
        int vh = Math.max(40, canvasBottom() - Theme.TOPBAR_H - 40);
        zoom = snapZoom(Math.max(MIN_ZOOM, Math.min(1.5,
                Math.min(vw / (double) (maxX - minX), vh / (double) (maxY - minY)))));
        panX = 20 + (vw - (maxX - minX) * zoom) / 2 - minX * zoom;
        panY = 20 + (vh - (maxY - minY) * zoom) / 2 - minY * zoom;
        toast("показано всё");
    }

    private void openSettings() {
        closeOverlays();
        settings = new SettingsPanel(font, width, height);
    }

    private void closeSettings() {
        settings = null;
        if (chestsShown != Settings.chests()) {
            chestsShown = Settings.chests();
            panX += chestsShown ? Theme.PALETTE_W : -Theme.PALETTE_W;
            search.setFocused(false);
            if (finder != null) finder.resize(width, canvasBottom());
        }
        restyle();
        top.invalidate();
        palette.invalidate();
    }

    private void toOriginal() {
        if (Env.browser()) return;
        Settings s = Settings.get();
        s.mode = Settings.Mode.ORIGINAL;
        s.save();
        closeOverlays();
        rememberView();
        saveScript();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        XeroCode.canvasClosed();
        mc.gui.setScreen(null);
        mc.gui.hud.setTimes(3, 50, 10);
        mc.gui.hud.setTitle(Component.literal("3D-кодинг"));
        mc.gui.hud.setSubtitle(Component.literal(
                s.label(Settings.Hot.OPEN) + " — назад в 2D"));
    }

    private static final int EXIT_WANT_W = 344, EXIT_H = 92, EXIT_BTN_H = 22;

    private int exitW() { return Ui.fitW(width, EXIT_WANT_W); }
    private int exitX() { return Ui.midX(width, exitW()); }
    private int exitY() { return Ui.midY(height, EXIT_H); }

    private void askExit(String command) {
        if (Env.browser()) return;
        closeOverlays();
        exitTo = command;
        if (!dirty()) { leaveTo(command); return; }
        exitPrompt = true;
    }

    private void drawExitPrompt(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Ui.dim(ctx, width, height);
        int x = exitX(), y = exitY();
        Ui.panel(ctx, x, y, exitW(), EXIT_H);
        Ui.headerStrip(ctx, x, y, exitW(), 26, Theme.ACCENT);
        Draw.textFit(ctx, font, "СОХРАНИТЬ?", x + 14, y + (26 - Ui.TEXT_H) / 2 + 1,
                exitW() - 28, Theme.TEXT, false);
        Ui.hairline(ctx, x + 1, y + 26, exitW() - 2);

        int cx = x + 14, cw = exitW() - 28, cy = y + 26 + 14;
        Draw.textFit(ctx, font, script.roots.isEmpty()
                        ? "Полотно пусто — очистить код в мире?"
                        : "Сохранить код в блоках JustMC?",
                cx, cy, cw, Theme.TEXT, false);

        int bx = cx + cw;
        for (int i = EXIT_BUTTONS.length - 1; i >= 0; i--) {
            int bw = exitBtnW(i);
            bx -= bw;
            Ui.button(ctx, font, mouseX, mouseY, bx, y + EXIT_H - 14 - EXIT_BTN_H, bw,
                    EXIT_BTN_H, EXIT_BUTTONS[i], i == EXIT_SAVE ? Ui.ACCENT : Ui.GHOST);
            bx -= 6;
        }
    }

    private static final String[] EXIT_BUTTONS = {"Отмена", "Не сохранять", "Сохранить"};
    private static final int EXIT_CANCEL = 0, EXIT_LEAVE = 1, EXIT_SAVE = 2;

    private int exitBtnW(int i) {
        return Math.max(i == EXIT_CANCEL ? 70 : 80, Ui.buttonW(font, EXIT_BUTTONS[i]));
    }

    private boolean exitPromptClicked(double mx, double my) {
        int x = exitX(), y = exitY();
        int cx = x + 14, cw = exitW() - 28, by = y + EXIT_H - 14 - EXIT_BTN_H;
        int bx = cx + cw;
        for (int i = EXIT_BUTTONS.length - 1; i >= 0; i--) {
            int bw = exitBtnW(i);
            bx -= bw;
            if (Ui.hit(mx, my, bx, by, bw, EXIT_BTN_H)) {
                exitPrompt = false;
                if (i == EXIT_SAVE) publish(exitTo);
                else if (i == EXIT_LEAVE) leaveTo(exitTo);
                return true;
            }
            bx -= 6;
        }
        if (!Ui.hit(mx, my, x, y, exitW(), EXIT_H)) exitPrompt = false;
        return true;
    }

    private void leaveTo(String command) {
        rememberView();
        saveScript();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        XeroCode.canvasClosed();
        if (XeroCode.RESTART.equals(command)) {
            XeroCode.restart();
            return;
        }
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
        XeroCode.cover("play".equals(command) ? "Запуск мира…" : "Режим строительства…", null);
    }

    private void publish(String exitCommand) {
        if (script.scratch) { lineAction(0); return; }
        closeOverlays();
        rememberView();
        saveScript();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(new ExportScreen(script, this, exitCommand));
    }

    private void drawToast(GuiGraphicsExtractor ctx) {
        if (status.isEmpty()) return;
        long age = System.currentTimeMillis() - statusAt;
        if (age > 2600) { status = ""; return; }
        int a = age > 2100 ? (int) (255 * (1 - (age - 2100) / 500.0)) : 255;
        a = Math.max(0, Math.min(255, a));
        int w = Math.min(font.width(status) + 24, width - canvasLeft() - 16);
        int x = canvasLeft() + (width - canvasLeft() - w) / 2;
        int y = canvasBottom() - 34 - (barShown() ? BAR_H + 6 : 0);
        if (Settings.chests() && x < plusX() + plusW() + 6 && y + 20 > plusY()) y = plusY() - 26;
        Draw.round(ctx, x, y, w, 20, 6, Draw.argb(a * 0xE0 / 255, Ui.HEAD));
        Draw.roundOutline(ctx, x, y, w, 20, 6, Draw.argb(a * 0x80 / 255, Ui.BORDER));
        ctx.text(font, Draw.fit(font, status, w - 24), x + 12, y + 6, Draw.argb(a, Theme.TEXT), false);
    }

    private void drawTooltips(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (editor != null && editor.contains(mouseX, mouseY)) return;
        if (Settings.statusBar() && foot.contains(mouseX, mouseY)) {
            String said = foot.tip(mouseX, mouseY);
            if (said != null)
                ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(said)), mouseX, mouseY);
            return;
        }

        if (mouseY < Theme.TOPBAR_H && mouseX >= canvasLeft()) {
            top.tooltip(ctx, mouseX, mouseY);
            return;
        }
        if (mouseX < canvasLeft()) {
            Palette.Entry e = palette.entryAt(mouseX, mouseY, canvasBottom());
            if (e == null) return;
            if (e.action != null) tips.action(ctx, e.action, null, width, mouseX, mouseY);
            else if (e.category != null) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(e.category.name));
                lines.add(Component.literal("§8" + e.category.count() + " действий"));
                ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
            return;
        }
        if (hoverChip != null && hoverBox != null) {
            tips.chip(ctx, hoverBox, hoverChip, width, mouseX, mouseY);
            return;
        }
        if (hoverBox != null)
            tips.action(ctx, hoverBox.node.action, hoverBox.node, width, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        changeHint = true;
        double mx = click.x(), my = click.y();
        mouseCanvasX = toCanvasX(mx);
        mouseCanvasY = toCanvasY(my);
        int button = click.button();

        Overlay panel = panel();
        if (panel != null) {
            panel.mouseClicked(click, doubled);
            settle(panel);
            return true;
        }
        if (exitPrompt) return exitPromptClicked(mx, my);
        if (menu != null) {
            Menu open = menu;
            open.mouseClicked(mx, my);
            if (open.isClosed() && menu == open) menu = null;
            return true;
        }
        if (blockMenu != null) {
            BlockMenu open = blockMenu;
            open.mouseClicked(mx, my);
            if (open.isClosed() || blockMenu != open) blockMenu = null;
            return true;
        }
        if (editor != null) {
            boolean inside = editor.mouseClicked(click, doubled);
            if (editor.isClosed()) finishEditor();
            if (inside) return true;
        }

        if (finder != null && finder.mouseClicked(click, doubled)) {
            if (finder.isClosed()) closeFinder();
            return true;
        }
        if (drag == null && map.hit(mx, my)) {
            if (button == 1) {
                Settings s = Settings.get();
                s.minimap = false;
                s.save();
                map.hide();
                toast("мини-карта убрана · вернуть — в настройках");
                return true;
            }
            if (button == 0) {
                mapDragging = true;
                centerOn(map.canvasX(mx), map.canvasY(my), true);
            }
            return true;
        }
        if (held != null) {
            if (button == 0 && mx >= canvasLeft() && my >= Theme.TOPBAR_H && my < canvasBottom()) placeHeld();
            else cancelHeld();
            return true;
        }
        if (drag != null && dragAwaitsClick) {
            if (button == 0 && mx >= canvasLeft() && my >= Theme.TOPBAR_H && my < canvasBottom()) {
                finishDrag(mx, my);
            } else {
                cancelDrag();
            }
            return true;
        }
        if (drag != null) return true;
        soloKey = Settings.NONE;
        if (drag == null && carry == null && !moving && mouseHotkey(click)) return true;
        if (drag == null && carry == null && barClicked(mx, my)) return true;
        if (button == 0 && drag == null && carry == null && overPlus(mx, my)) {
            if (Env.touch()) quickAdd(placedBox(), toCanvasX(midX()), toCanvasY(midY()), true, true);
            else openChestToCursor();
            return true;
        }
        if (my >= canvasBottom()) {
            if (button == 0 && drag == null && carry == null) statusClicked(foot.hit(mx, my));
            return true;
        }
        if (button == 0 && overSplitter(mx)) {
            resizingPalette = true;
            paletteDrag = Theme.PALETTE_W;
            search.setFocused(false);
            return true;
        }
        if (my < Theme.TOPBAR_H && mx >= canvasLeft()) {
            int id = top.hit(mx, my);
            if (id == TopBar.MORE) menu = top.menu(width, height, this::onTopButton);
            else if (id != TopBar.NONE) onTopButton(id);
            return true;
        }
        if (mx < canvasLeft()) {
            if (carry != null) { cancelCarry(); return true; }
            return paletteClicked(click, doubled);
        }
        return canvasClicked(mx, my, button, click.modifiers(), doubled);
    }

    private static boolean aimed(Settings.Hot hot) {
        return switch (hot) {
            case DELETE, DEL_STACK, COPY, COPY_ONE, CUT, CUT_ONE, DUPLICATE, DUP_ONE, STASH, FOLD,
                 FOLD_STACK, GO_DEF -> true;
            default -> false;
        };
    }

    private boolean onCanvas(double mx, double my) {
        return mx >= canvasLeft() && mx < canvasRight() && my >= Theme.TOPBAR_H && my < canvasBottom()
                && !map.hit(mx, my) && !overPlus(mx, my);
    }

    private boolean mouseHotkey(MouseButtonEvent click) {
        Settings.Hot hot = Settings.get().match(Settings.mouse(click.button()), click.modifiers());
        if (hot == null) return false;
        if (!aimed(hot)) return runHotkey(hot);
        if (!onCanvas(click.x(), click.y()) || hoverBox == null) return false;
        if (hot == Settings.Hot.GO_DEF && (hoverChip != null || jumpTarget(hoverBox.node) == null))
            return false;
        pendHot = hot;
        pendBox = hoverBox;
        pendChip = hoverChip;
        pendButton = click.button();
        pressX = click.x();
        pressY = click.y();
        if (click.button() != 0) panning = true;
        return true;
    }

    private void runPending() {
        Settings.Hot hot = pendHot;
        Layout.Box box = pendBox;
        pendHot = null;
        pendBox = null;
        if (hot == null || box == null) return;
        hoverBox = box;
        hoverChip = pendChip;
        pendChip = null;
        switch (hot) {
            case DELETE -> { if (picked.covered().contains(box.node)) deletePicked(); else deleteHovered(); }
            case DEL_STACK -> { if (picked.covered().contains(box.node)) deletePicked(); else deleteStackHovered(); }
            case GO_DEF -> goTo(box.node);
            default -> runHotkey(hot);
        }
    }

    private void statusClicked(int item) {
        if (item == StatusBar.SYNC) publish(null);
        else if (item == StatusBar.ZOOM && Math.abs(zoom - 1.0) < 1e-3) fitView();
        else if (item == StatusBar.ZOOM) zoomTo(1.0, midX(), midY());
    }

    private boolean paletteClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        search.setFocused(false);
        if (finder != null) finder.blur();
        if (my < Palette.HEADER_H) {
            if (Palette.hitSearchClear(mx, my) && !search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            if (my >= Palette.SEARCH_Y && my < Palette.SEARCH_Y + Theme.SEARCH_H) {
                search.setFocused(true);
                setFocused(search);
                if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
                draggingSearch = true;
            }
            return true;
        }
        if (palette.barPress(mx, my)) return true;
        if (palette.hitCrumb(mx, my)) {
            if (!search.getValue().isEmpty()) search.setValue("");
            else palette.openCategory(-1);
            return true;
        }
        Palette.Entry e = palette.entryAt(mx, my, canvasBottom());
        if (e == null) return true;
        if (e.isSection()) {
            palette.toggle(e);
            return true;
        }
        if (e.category != null) {
            palette.openCategory(Catalog.CATEGORIES.indexOf(e.category));
            return true;
        }
        if (e.action != null) {
            closeOverlays();
            dragSnapshot = snapshot();
            drag = new ArrayList<>();
            drag.add(new Script.Node(e.action));
            dragOffX = 26;
            dragOffY = 10;
            dragFromPalette = true;
            dragMoved = false;
        }
        return true;
    }

    private boolean canvasClicked(double mx, double my, int button, int mods, boolean doubled) {
        search.setFocused(false);
        if (carry != null) {
            if (button == 0) dropCarry(); else cancelCarry();
            return true;
        }
        boolean add = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean sub = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
        if (button == 0 && (add || sub) && !overChip()) {
            Layout.Box box = grabAt();
            if (box != null) {
                if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && !add) {
                    pressOne = box;
                    pressX = mx;
                    pressY = my;
                    return true;
                }
                if (sub) picked.unpick(box);
                else picked.toggle(box);
                return true;
            }
            startBand(sub ? Selection.SUB : Selection.ADD);
            return true;
        }
        Layout l = layout();
        if (button == 0)
            for (int ci = l.chunks.size() - 1; ci >= 0; ci--) {
                Layout.Box head = l.boxes.get(l.chunks.get(ci).from);
                if (!hitTag(head, mouseCanvasX, mouseCanvasY)) continue;
                toggleStack(head.root);
                return true;
            }
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box box = l.boxes.get(i);
            if (!box.contains(mouseCanvasX, mouseCanvasY)) continue;
            if (box.hitTail(mouseCanvasX, mouseCanvasY)) {
                if (button == 0) toggleStack(box.root);
                else if (button == 1) openBlockMenu(box, (int) mx, (int) my);
                return true;
            }
            Layout.Chip chip = button <= 1 ? box.chipAt(mouseCanvasX, mouseCanvasY) : null;
            if (chip != null) {
                if (button == 1 && add && paramVariable(box, chip)) return true;
                if (button == 0 && !Carry.values(box, chip).isEmpty()) {
                    pressBox = box;
                    pressChip = chip;
                    pressX = mx;
                    pressY = my;
                    return true;
                }
                chipClicked(box, chip, button);
                return true;
            }
            if (!box.hitGrab(mouseCanvasX, mouseCanvasY)) continue;
            if (button == 1) { openBlockMenu(box, (int) mx, (int) my); return true; }
            if (button == 0 && box.hitFold(mouseCanvasX, mouseCanvasY)) {
                toggleFold(box.node);
                return true;
            }
            if (button == 0 && box.hitTarget(mouseCanvasX, mouseCanvasY)) {
                chooseTarget(box.node, box.targetSetting, (int) mx, (int) my);
                return true;
            }
            if (button == 0 && box.card != null && cardClicked(box, (int) mx, (int) my)) return true;
            if (button == 0 && doubled) {
                chooseAction(box);
                return true;
            }
            if (button != 0) break;
            if (picked.covered().contains(box.node)) startMove();
            else startDrag(box);
            return true;
        }
        if (button == 0 && doubled && !spaceHeld()) {
            banding = false;
            quickAdd(null, mouseCanvasX, mouseCanvasY, true);
            return true;
        }
        if (button == 0 && !spaceHeld() && !Env.touch()) { startBand(Selection.NEW); return true; }
        if (button == 1) {
            emptyPress = true;
            emptyX = mouseCanvasX;
            emptyY = mouseCanvasY;
            pressX = mx;
            pressY = my;
        }
        panning = true;
        return true;
    }

    private boolean spaceHeld() { return held(GLFW.GLFW_KEY_SPACE); }

    private boolean inDev() {
        return !Env.browser() && minecraft != null && Codespace.inDev(minecraft.level);
    }

    private boolean ctrlHeld() {
        return held(GLFW.GLFW_KEY_LEFT_CONTROL) || held(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean held(int key) {
        return minecraft != null && minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow(), key);
    }

    private void startBand(int mode) {
        closeOverlays();
        bandMode = mode;
        if (mode == Selection.NEW) clearPicked();
        banding = true;
        bandX0 = bandX1 = mouseCanvasX;
        bandY0 = bandY1 = mouseCanvasY;
        if (panHinted) return;
        panHinted = true;
        toast("полотно двигается ПКМ, средней кнопкой или пробелом");
    }

    private void startMove() {
        closeOverlays();
        moving = true;
        moveShifted = false;
        moveDX = 0;
        moveDY = 0;
        moveSnapshot = snapshot();
    }

    private void finishMove() {
        moving = false;
        int dx = (int) Math.round(moveDX), dy = (int) Math.round(moveDY);
        moveDX = 0;
        moveDY = 0;
        if (!moveShifted || (dx == 0 && dy == 0)) { moveSnapshot = null; return; }
        pushUndo(moveSnapshot);
        moveSnapshot = null;
        picked.moveBy(script, dx, dy);
    }

    private void cancelMove() {
        moving = false;
        moveShifted = false;
        moveDX = 0;
        moveDY = 0;
        moveSnapshot = null;
    }

    private Layout.Box grabAt() {
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box b = l.boxes.get(i);
            if (b.contains(mouseCanvasX, mouseCanvasY) && b.hitGrab(mouseCanvasX, mouseCanvasY))
                return b;
        }
        return null;
    }

    private boolean overChip() {
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box b = l.boxes.get(i);
            if (!b.contains(mouseCanvasX, mouseCanvasY)) continue;
            if (b.chipAt(mouseCanvasX, mouseCanvasY) != null) return true;
            if (b.hitGrab(mouseCanvasX, mouseCanvasY) || b.hitTail(mouseCanvasX, mouseCanvasY)) return false;
        }
        return false;
    }

    private boolean cardClicked(Layout.Box box, int mx, int my) {
        Layout.Card c = box.card;
        Script.Node n = box.node;
        boolean name = c.hitName(mouseCanvasX, mouseCanvasY);
        boolean id = c.hitId(mouseCanvasX, mouseCanvasY);
        boolean icon = c.hitIcon(mouseCanvasX, mouseCanvasY);
        if (!name && !id && !icon && !c.hitDesc(mouseCanvasX, mouseCanvasY)) return false;
        if (n.invokes()) { chooseFunction(n, mx, my); return true; }
        int arg = id ? Catalog.FN_NAME
                : name ? (c.id != null ? Catalog.FN_DISPLAY : Catalog.FN_NAME)
                : icon ? Catalog.FN_ICON : Catalog.FN_DESC;
        openValue(n, arg, mx, my, false, -1);
        return true;
    }

    private void chipClicked(Layout.Box box, Layout.Chip chip, int button) {
        heldRoot = box.root;
        int sx = toScreenX(chip.x), sy = toScreenY(chip.y + Layout.CHIP_H) + 3;
        if (chip.isCondition()) {
            if (button == 1) {
                if (box.node.cond != null) {
                    pushUndo();
                    box.node.cond.setSetting(Catalog.INVERT,
                            box.node.cond.inverted() ? Catalog.INVERT_OFF : Catalog.INVERT_ON);
                }
                return;
            }
            chooseCondition(box.node, sx, sy);
            return;
        }
        Script.Node target = Layout.chipNode(box.node);
        if (chip.isPlus()) {
            if (button == 0) openValue(target, chip.argIndex, sx, sy, true, -1);
            return;
        }
        if (chip.isCell()) {
            if (button == 1) {
                pushUndo();
                List<Value> params = target.valuesOf(chip.argIndex);
                if (chip.cell < params.size()) params.remove(chip.cell);
                toast("параметр удалён");
                return;
            }
            openValue(target, chip.argIndex, sx, sy, false, chip.cell);
            return;
        }
        if (chip.isArg()) {
            if (button == 1) {
                if (Layout.argFilled(target, chip.argIndex)) {
                    pushUndo();
                    target.valuesOf(chip.argIndex).clear();
                    toast("значение очищено");
                }
                return;
            }
            openValue(target, chip.argIndex, sx, sy, false, -1);
            return;
        }
        Catalog.Setting s = target.settings().get(chip.settingIndex);
        if (button == 1) {
            pushUndo();
            target.cycleMarker(chip.settingIndex, false);
            return;
        }
        Script.Node node = target;
        int settingIndex = chip.settingIndex;
        boolean bound = Layout.markerBound(node, settingIndex);
        List<Menu.Item> items = new ArrayList<>();
        for (String option : s.options) items.add(new Menu.Item(option));
        int bindAt = items.size();
        Value was = node.markerVar(settingIndex);
        items.add(new Menu.Item(bound ? "Переменная: " + was.name : "Привязать переменную…",
                false, Draw.PIN));
        int unbindAt = bound ? items.size() : -1;
        if (bound) items.add(new Menu.Item("Отвязать переменную", true, Draw.CROSS));
        int current = Math.max(0, s.options.indexOf(node.marker(settingIndex)));
        menu = Menu.picker(width, height, toScreenX(chip.x), toScreenY(chip.y + Layout.CHIP_H) + 2,
                font, s.label, items, current, i -> {
                    if (i == bindAt) { bindMarker(node, settingIndex, chip); return; }
                    if (i == unbindAt) {
                        pushUndo();
                        node.bindMarker(settingIndex, null);
                        toast("переменная отвязана");
                        return;
                    }
                    if (i >= 0 && i < s.options.size()) {
                        pushUndo();
                        node.markers.put(settingIndex, s.options.get(i));
                    }
                });
    }

    private void bindMarker(Script.Node node, int settingIndex, Layout.Chip chip) {
        Value was = node.markerVar(settingIndex);
        Value start = was != null ? was.copy() : Value.of(Value.VARIABLE);
        String label = node.settings().get(settingIndex).label;
        editorUndo = snapshot();
        editor = ValueEditor.forCell(start, "Настройка «" + label + "»", Value.VARIABLE,
                font, toScreenX(chip.x), toScreenY(chip.y + Layout.CHIP_H) + 2,
                width, height, ScriptNames.known(script, node), ScriptNames.used(script, true),
                edited -> node.bindMarker(settingIndex, edited));
    }

    private void chooseCondition(Script.Node wrapper, int sx, int sy) {
        List<String> allowed = Mapping.conditionCategories(wrapper.action);
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Catalog.Action a : Catalog.ACTIONS) {
            if (a.category == null || !allowed.contains(a.category.name)) continue;
            items.add(new CatalogPicker.Item(Catalog.keyOf(a), a.name, a.category.name, a.item,
                    a.description, a.args.isEmpty() ? "" : a.args.size() + " арг.", ""));
        }
        if (items.isEmpty()) { toast("для этого блока условий нет"); return; }
        String current = wrapper.cond == null ? "" : Catalog.keyOf(wrapper.cond.action);
        condPicker = new CatalogPicker(font, width, height, "Условие блока",
                wrapper.action.category == null ? Theme.ACCENT : wrapper.action.category.color,
                items, null, current, null, id -> {
                    Catalog.Action chosen = Catalog.byKey(id);
                    if (chosen == null) return;
                    pushUndo();
                    if (wrapper.cond == null || wrapper.cond.action != chosen) {
                        boolean was = wrapper.cond != null && wrapper.cond.inverted();
                        wrapper.cond = new Script.Node(chosen);
                        if (was) wrapper.cond.setSetting(Catalog.INVERT, Catalog.INVERT_ON);
                    }
                    revision++;
                });
    }

    private void openBlockMenu(Layout.Box box, int mx, int my) {
        Script.Node node = box.node;
        Catalog.Action a = node.action;
        boolean tail = box.index < box.owner.size() - 1 || !node.body.isEmpty();

        String title = a.name;
        String subtitle = a.category == null ? "" : a.category.name;
        ItemStack icon = a.icon();
        if (node.declares() || node.invokes()) {
            String named = node.declares() ? Functions.nameOf(node) : Functions.targetOf(node);
            if (!named.isBlank()) { title = named; subtitle = a.name; }
            Value own = node.declares() ? Functions.iconOf(node) : null;
            if (own != null) icon = Stacks.preview(own);
        }

        BlockMenu m = new BlockMenu(width, height, mx, my, font, icon, title, subtitle,
                a.category == null ? Theme.ACCENT : a.category.color);

        int target = node.settingIndex(Catalog.TARGET);
        int invert = node.settingIndex(Catalog.INVERT);
        if (target >= 0)
            m.row(Draw.TARGET, "Цель", null, () -> chooseTarget(node, target, mx, my))
                    .note(node.marker(target)).caret();
        if (invert >= 0)
            m.row(Draw.NOT, Catalog.INVERT_ON.equals(node.marker(invert))
                            ? "Снять «НЕ»" : "Условие «НЕ»", null,
                    () -> { pushUndo(); node.cycleMarker(invert, true); });
        if (foldable(node))
            m.row(node.folded ? Draw.CARET_DOWN : Draw.CARET_RIGHT,
                    node.folded ? "Развернуть тело" : "Свернуть тело", Settings.Hot.FOLD,
                    () -> toggleFold(node));
        Script.Root stack = box.root;
        if (stack != null && stack.foldable())
            m.row(stack.hides() ? Draw.CARET_DOWN : Draw.CARET_RIGHT,
                    stack.hides() ? "Развернуть стопку" : "Свернуть стопку", Settings.Hot.FOLD_STACK,
                    () -> toggleStack(stack));
        if (node.invokes() && jumpTarget(node) != null)
            m.row(Draw.ARROW_RIGHT, node.isStart() ? "К объявлению процесса" : "К объявлению функции",
                    Settings.Hot.GO_DEF, () -> goTo(node));
        else if (node.declares() && jumpTarget(node) != null)
            m.row(Draw.ARROW_RIGHT, "К вызову", Settings.Hot.GO_DEF, () -> goTo(node));
        if (node.declares()) {
            String what = node.isProcess() ? "процесса" : "функции";
            m.row(Draw.NAME, "Имя " + what + "…", null,
                    () -> openValue(node, Catalog.FN_NAME, mx, my, false, -1));
            m.row(Draw.PLUS, "Добавить параметр", null,
                    () -> openValue(node, Catalog.FN_PARAMS, mx, my, true, -1));
            m.row(Draw.NAME, "Отображаемое имя…", null,
                    () -> openValue(node, Catalog.FN_DISPLAY, mx, my, false, -1));
            m.row(Draw.LINES, "Описание…", null,
                    () -> openValue(node, Catalog.FN_DESC, mx, my, false, -1));
            m.row(Draw.IMAGE, "Значок…", null,
                    () -> openValue(node, Catalog.FN_ICON, mx, my, false, -1));
        } else if (node.invokes()) {
            m.row(Draw.SEARCH, node.isStart() ? "Выбрать процесс…" : "Выбрать функцию…", null,
                    () -> chooseFunction(node, mx, my)).caret();
        }

        m.gap();
        String named = node.declares() ? Functions.nameOf(node)
                : node.invokes() ? Functions.targetOf(node) : "";
        if (!named.isBlank())
            m.row(Draw.SEARCH, "Найти по имени «" + named + "»", null, () -> openFinder(named));
        else
            m.row(Draw.SEARCH, "Найти такие же блоки", Settings.Hot.FIND,
                    () -> openFinder(a.name));
        String variable = variableIn(node);
        if (variable != null)
            m.row(Draw.SEARCH, "Найти переменную «" + variable + "»", null,
                    () -> openFinder(variable));
        Value item = heldItem(box);
        if (item != null)
            m.row(Draw.COPY, "Копировать предмет для Xero", null, () -> copyForXero(item));
        if (!Env.browser() && minecraft.level != null && Codespace.inDev(minecraft.level))
            m.row(Draw.UPLOAD, "Выложить модулем JustMC…", null, () -> publishModule(box));

        m.gap();
        m.row(Draw.COPY, tail ? "Копировать стопку" : "Копировать",
                Settings.Hot.COPY, () -> copyBox(box, true));
        if (tail) m.row(Draw.COPY, "Копировать блок",
                Settings.Hot.COPY_ONE, () -> copyBox(box, false));
        m.row(Draw.CUT, tail ? "Вырезать стопку" : "Вырезать",
                Settings.Hot.CUT, () -> cutBox(box));
        if (tail) m.row(Draw.CUT, "Вырезать блок",
                Settings.Hot.CUT_ONE, () -> cutOne(box));
        m.row(Draw.PASTE, "Вставить под блок", Settings.Hot.PASTE,
                () -> pasteAt(box)).on(Clip.has());
        m.row(Draw.DUPLICATE, tail ? "Дублировать стопку" : "Дублировать",
                Settings.Hot.DUPLICATE, () -> duplicate(box, true));
        if (tail) m.row(Draw.DUPLICATE, "Дублировать блок",
                Settings.Hot.DUP_ONE, () -> duplicate(box, false));

        if (box.index == 0 && !box.nested && (node.isHat() || node.declares()) && inDev())
            m.row(Draw.LOAD, "Шаблон строки в руку", null, () -> handToWorld(chainOf(box, true)));

        m.gap();
        m.row(Draw.PACK, tail ? "Стопку в рюкзак" : "В рюкзак",
                Settings.Hot.STASH, () -> stashBlock(box, true));
        if (tail) m.row(Draw.PACK, "Блок в рюкзак", null, () -> stashBlock(box, false));
        m.row(Draw.SELECT, picked.covered().contains(box.node) ? "Убрать из выделения" : "Выделить",
                null, () -> picked.toggle(box));
        if (!picked.isEmpty()) {
            String said = "  ·  " + Ui.plural(picked.pieces().size(), "кусок", "куска", "кусков");
            m.row(Draw.COPY, "Копировать выделенное" + said, Settings.Hot.COPY, this::copyPicked);
            m.row(Draw.PACK, "Выделенное в рюкзак" + said, null, this::stashPicked);
            m.row(Draw.CROSS, "Снять выделение", null, this::clearPicked);
        }

        m.gap();
        m.row(Draw.TRASH, tail ? "Удалить блок" : "Удалить",
                Settings.Hot.DELETE, () -> deleteBlock(box)).danger();
        if (tail) m.row(Draw.TRASH, "Удалить стопку",
                Settings.Hot.DEL_STACK, () -> deleteStack(box)).danger();

        menu = null;
        blockMenu = m.open();
    }

    private static String variableIn(Script.Node node) {
        for (List<Value> list : node.values.values())
            for (Value v : list)
                if ((Value.VARIABLE.equals(v.type) || Value.PARAMETER.equals(v.type))
                        && !v.name.isBlank()) return v.name;
        for (Value v : node.markerVars.values())
            if (!v.name.isBlank()) return v.name;
        return null;
    }

    private void openValue(Script.Node node, int argIndex, int sx, int sy,
                           boolean add, int select) {
        editorUndo = snapshot();
        ValueEditor open = new ValueEditor(node, argIndex, font, sx, sy, width, height,
                ScriptNames.known(script, node), ScriptNames.used(script, true));
        if (add) open.addCell();
        else if (select >= 0) open.select(select);
        editor = open;
    }

    private void chooseFunction(Script.Node call, int mx, int my) {
        Functions.Known known = Functions.of(script);
        Map<String, Functions.Signature> map = call.isStart() ? known.processes() : known.functions();
        String chosen = Functions.targetOf(call);
        List<String> names = new ArrayList<>(map.size());
        int skipped = 0;
        for (Functions.Signature s : map.values()) {
            if (Functions.hidden(s.declaration()) && !s.name().equals(chosen)) { skipped++; continue; }
            names.add(s.name());
        }
        List<Menu.Item> items = new ArrayList<>(names.size() + 2);
        for (String name : names) {
            Functions.Signature s = map.get(name);
            ItemStack icon = s.icon() != null ? Stacks.preview(s.icon())
                    : s.declaration().action.icon();
            String sig = Functions.signatureText(s);
            if (sig.isEmpty()) sig = "без параметров";
            String shown = Layout.titleOf(s);
            items.add(Menu.Item.rich(shown, icon,
                    shown.equals(name) ? sig : name + "  ·  " + sig,
                    Layout.descAll(s.declaration())));
        }
        items.add(new Menu.Item(call.isStart() ? "Ввести имя процесса…" : "Ввести имя функции…",
                false, Draw.STRIKE_TEXT));
        int current = names.indexOf(chosen);
        String title = (call.isStart() ? "ПРОЦЕССЫ ПОЛОТНА" : "ФУНКЦИИ ПОЛОТНА")
                + (skipped == 0 ? "" : "  ·  СКРЫТО " + skipped);
        menu = Menu.picker(width, height, mx, my, font, title, items, current, i -> {
                    if (i >= names.size()) {
                        openValue(call, Catalog.CALL_NAME, mx, my, false, -1);
                        return;
                    }
                    pushUndo();
                    List<Value> slot = call.valuesOf(Catalog.CALL_NAME);
                    slot.clear();
                    Value name = Value.text(names.get(i));
                    name.parsing = "plain";
                    slot.add(name);
                });
    }

    private void chooseTarget(Script.Node node, int settingIndex, int mx, int my) {
        Catalog.Setting s = node.settings().get(settingIndex);
        int current = Math.max(0, s.options.indexOf(node.marker(settingIndex)));
        menu = Menu.options(width, height, mx, my, font, s.label, s.options, current,
                i -> {
                    pushUndo();
                    node.markers.put(settingIndex, s.options.get(i));
                });
    }

    private boolean paramVariable(Layout.Box box, Layout.Chip chip) {
        Script.Node n = Layout.chipNode(box.node);
        if (!Carry.paramCell(box, chip)) return false;
        List<Value> params = n.values.get(Catalog.FN_PARAMS);
        if (params == null || chip.cell >= params.size()) return false;
        Value param = params.get(chip.cell);
        if (param.name.isBlank()) { toast("у параметра нет имени"); return true; }
        Value line = Value.of(Value.VARIABLE);
        line.name = param.name;
        line.scope = "line";
        take(box, chip, List.of(line), false);
        toast("строчная переменная «" + param.name + "» — " + Ui.click() + " по слоту");
        return true;
    }

    private void endDrag() {
        drag = null;
        snap = null;
        dragSnapshot = null;
    }

    private void dropEmpty(Script.Root root) {
        if (root != null && root.chain.isEmpty()) script.roots.remove(root);
    }

    private void cancelDrag() {
        boolean lifted = drag != null && !dragFromPalette;
        endDrag();
        dragAwaitsClick = false;
        if (lifted && History.revert(script)) {
            Collab.afterHistory(script);
            revision++;
        }
        toast("отменено");
    }

    private void startDragOne(Layout.Box box) {
        closeOverlays();
        heldRoot = box.root;
        pushUndo();
        drag = new ArrayList<>();
        drag.add(box.owner.remove(box.index));
        dropEmpty(box.root);
        dragOffX = mouseCanvasX - box.x;
        dragOffY = mouseCanvasY - box.y;
        dragFromPalette = false;
        dragMoved = false;
        dragSnapshot = null;
    }

    private void startDrag(Layout.Box box) {
        closeOverlays();
        heldRoot = box.root;
        pushUndo();
        drag = new ArrayList<>(box.owner.subList(box.index, box.owner.size()));
        box.owner.subList(box.index, box.owner.size()).clear();
        dropEmpty(box.root);
        dragOffX = mouseCanvasX - box.x;
        dragOffY = mouseCanvasY - box.y;
        dragFromPalette = false;
        dragMoved = false;
        dragSnapshot = null;
    }

    private static List<Script.Node> chainOf(Layout.Box box, boolean wholeStack) {
        List<Script.Node> chain = new ArrayList<>();
        if (wholeStack)
            for (int i = box.index; i < box.owner.size(); i++) chain.add(box.owner.get(i).copy());
        else
            chain.add(box.node.copy());
        return chain;
    }

    private static String blocksText(List<Script.Node> chain) {
        return Ui.plural(Script.blocks(chain), "блок", "блока", "блоков");
    }

    private void duplicate(Layout.Box box, boolean wholeStack) {
        List<Script.Node> copy = chainOf(box, wholeStack);
        if (copy.isEmpty()) return;
        boolean head = box.index == 0 && !box.nested && box.root != null && box.owner == box.root.chain;
        pushUndo();
        focusNode = copy.get(0);
        focusAt = System.currentTimeMillis();
        if (!copy.get(0).isHat() && !(wholeStack && head)) {
            box.owner.addAll(wholeStack ? box.owner.size() : box.index + 1, copy);
            toast(wholeStack ? "стопка продублирована ниже" : "блок продублирован ниже");
            return;
        }
        Layout.Chunk own = box.root == null ? null : chunksByRoot().get(box.root);
        int top = own == null ? box.bottom() : own.y1;
        int w = own == null ? box.w : own.x1 - own.x0;
        int h = Layout.chainHeight(copy, font);
        Script.Root r = new Script.Root(box.x, freeBelow(box.x, top + TIDY_GAP_Y, w, h));
        r.chain.addAll(copy);
        script.roots.add(r);
        toast(wholeStack ? "стопка продублирована ниже" : "блок продублирован ниже");
    }

    private int freeBelow(int x, int y, int w, int h) {
        Layout l = layout();
        for (int tries = 0; tries < 64; tries++) {
            Layout.Chunk hit = null;
            for (Layout.Chunk c : l.chunks)
                if (c.x0 < x + w && c.x1 > x && c.y0 < y + h + TAG_H && c.y1 + TAG_H > y) { hit = c; break; }
            if (hit == null) return y;
            y = hit.y1 + TIDY_GAP_Y;
        }
        return y;
    }

    private void copyBox(Layout.Box box, boolean wholeStack) {
        List<Script.Node> chain = chainOf(box, wholeStack);
        Clip.copy(chain);
        toast("скопировано · " + blocksText(chain));
    }

    private void cutBox(Layout.Box box) {
        List<Script.Node> chain = chainOf(box, true);
        Clip.copy(chain);
        pushUndo();
        box.owner.subList(box.index, box.owner.size()).clear();
        dropEmpty(box.root);
        toast("вырезано · " + blocksText(chain));
    }

    private void copyHovered(boolean wholeStack) {
        if (drag != null || carry != null) return;
        if (hoverBox == null) { toast("наведись на блок — скопирую его"); return; }
        copyBox(hoverBox, wholeStack);
    }

    private void cutOne(Layout.Box box) {
        List<Script.Node> chain = chainOf(box, false);
        Clip.copy(chain);
        pushUndo();
        box.owner.remove(box.index);
        dropEmpty(box.root);
        toast("блок вырезан · " + blocksText(chain));
    }

    private void cutOneHovered() {
        if (drag != null || carry != null) return;
        if (hoverBox == null) { toast("наведись на блок — вырежу только его"); return; }
        cutOne(hoverBox);
        hoverBox = null;
    }

    private void cutHovered() {
        if (drag != null || carry != null) return;
        if (hoverBox == null) { toast("наведись на блок — вырежу его стопку"); return; }
        cutBox(hoverBox);
        hoverBox = null;
    }

    private void pasteClip() { pasteAt(hoverBox); }

    private void pasteAt(Layout.Box box) {
        if (drag != null || carry != null) return;
        List<Script.Root> roots = Clip.pasteRoots();
        if (!roots.isEmpty()) { pasteRoots(roots); return; }
        List<Script.Node> chain = Clip.paste();
        if (chain.isEmpty()) { toast("в буфере нет кода"); return; }
        if (box != null && !chain.get(0).isHat()) {
            pushUndo();
            box.owner.addAll(box.index + 1, chain);
            toast("вставлено под блок · " + blocksText(chain));
            return;
        }
        holdChain(chain, "вставлено · " + blocksText(chain) + " — " + Ui.click() + " по полотну поставит");
    }

    public void openModules() {
        closeOverlays();
        rememberView();
        saveScript();
        XeroCode.canvasClosed();
        minecraft.gui.setScreen(new ModulesScreen(script, this));
    }

    private void openBackpack() {
        closeOverlays();
        backpack = new BackpackPanel(font, width, height, this::takeFromBackpack);
    }

    private void takeFromBackpack(Backpack.Item item) {
        if (item.pieces() > 1) {
            List<Script.Root> roots = item.roots();
            if (roots.isEmpty()) return;
            dropRoots(roots, "«" + item.name + "» на полотне · " + item.blocksText(), false);
            return;
        }
        List<Script.Node> chain = item.copy();
        if (chain.isEmpty()) return;
        holdOnCursor(chain, item.name);
    }

    public void holdModule(List<Script.Root> roots, String name) {
        if (roots.isEmpty()) return;
        if (roots.size() == 1) {
            holdOnCursor(roots.get(0).chain, name);
            return;
        }
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE;
        for (Script.Root r : roots) {
            x0 = Math.min(x0, r.x);
            y0 = Math.min(y0, r.y);
        }
        for (Script.Root r : roots) {
            r.x = Math.round(r.x - x0);
            r.y = Math.round(r.y - y0);
        }
        closeOverlays();
        menu = null;
        blockMenu = null;
        held = new ArrayList<>(roots);
        heldName = name;
        swallowRelease = true;
        toast("«" + name + "» на курсоре · " + Ui.plural(roots.size(), "стопка", "стопки", "стопок")
                + " — " + Ui.click() + " по полотну поставит, Esc — отмена");
    }

    private void placeHeld() {
        List<Script.Root> roots = held;
        held = null;
        heldViews.clear();
        pushUndo();
        double dx = Math.round(mouseCanvasX - HELD_X), dy = Math.round(mouseCanvasY - HELD_Y);
        for (Script.Root r : roots) {
            r.x += dx;
            r.y += dy;
            script.roots.add(r);
        }
        picked.set(roots);
        toast("«" + heldName + "» на полотне · Ctrl+Z вернёт");
    }

    private void cancelHeld() {
        held = null;
        heldViews.clear();
        toast("отменено");
    }

    private void holdOnCursor(List<Script.Node> chain, String name) {
        holdChain(chain, "«" + name + "» на курсоре — " + Ui.click() + " по полотну поставит");
    }

    private void holdChain(List<Script.Node> chain, String note) {
        menu = null;
        blockMenu = null;
        drag = chain;
        dragSnapshot = snapshot();
        dragOffX = 26;
        dragOffY = 10;
        dragFromPalette = true;
        dragMoved = false;
        dragAwaitsClick = true;
        swallowRelease = true;
        toast(note);
    }

    private void stash(List<Script.Node> chain, String note) {
        if (chain == null || chain.isEmpty()) return;
        Backpack.Item item = Backpack.put(Backpack.suggest(chain), chain);
        if (item == null) return;
        top.invalidate();
        toast(note + ": «" + item.name + "» · " + item.blocksText());
    }

    private void stashBlock(Layout.Box box, boolean wholeStack) {
        stash(chainOf(box, wholeStack), "в рюкзаке");
    }

    private void stashHovered() {
        if (drag != null || carry != null) return;
        if (!picked.isEmpty()) { stashPicked(); return; }
        if (hoverBox == null) { toast("наведись на блок — уберу его стопку в рюкзак"); return; }
        stashBlock(hoverBox, true);
    }

    private void clearPicked() { picked.clear(); }

    private String pickedSaid() { return picked.said(); }

    private List<Script.Root> pickedAsRoots() { return picked.asRoots(); }

    private void selectAll() {
        if (script.roots.isEmpty()) { toast("полотно пустое"); return; }
        closeOverlays();
        picked.set(script.roots);
    }

    private void stashPicked() {
        List<Script.Root> roots = pickedAsRoots();
        if (roots.isEmpty()) { toast("ничего не выделено"); return; }
        Backpack.Item item = Backpack.putAll(Backpack.suggestAll(roots), roots);
        top.invalidate();
        clearPicked();
        if (item == null) { toast("убирать нечего"); return; }
        toast("в рюкзаке «" + item.name + "» · " + item.blocksText());
    }

    private void deletePicked() {
        if (picked.isEmpty()) return;
        String said = pickedSaid();
        pushUndo();
        picked.cutOut(script);
        clearPicked();
        hoverBox = null;
        toast("удалено " + said);
    }

    private void copyPicked() {
        List<Script.Root> roots = pickedAsRoots();
        if (roots.isEmpty()) return;
        Clip.copyRoots(roots);
        toast("скопировано " + pickedSaid());
    }

    private void cutPicked() {
        List<Script.Root> roots = pickedAsRoots();
        if (roots.isEmpty()) return;
        Clip.copyRoots(roots);
        String said = pickedSaid();
        pushUndo();
        picked.cutOut(script);
        clearPicked();
        hoverBox = null;
        toast("вырезано " + said);
    }

    private void duplicatePicked() {
        List<Script.Root> copies = pickedAsRoots();
        if (copies.isEmpty()) return;
        pushUndo();
        for (Script.Root r : copies) {
            r.x += 24;
            r.y += 24;
            script.roots.add(r);
        }
        picked.set(copies);
        toast("продублировано " + pickedSaid());
    }

    private void pasteRoots(List<Script.Root> roots) {
        dropRoots(roots, null, true);
    }

    private void dropRoots(List<Script.Root> roots, String note, boolean atCursor) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (Script.Root r : roots) {
            x0 = Math.min(x0, r.x);
            y0 = Math.min(y0, r.y);
            x1 = Math.max(x1, r.x);
            y1 = Math.max(y1, r.y);
        }
        boolean over = atCursor
                && mouseCanvasX >= toCanvasX(canvasLeft()) && mouseCanvasX <= toCanvasX(canvasRight())
                && mouseCanvasY >= toCanvasY(Theme.TOPBAR_H) && mouseCanvasY <= toCanvasY(canvasBottom());
        double dx, dy;
        if (over) {
            dx = Math.round(mouseCanvasX - x0);
            dy = Math.round(mouseCanvasY - y0);
        } else {
            dx = Math.round(toCanvasX(midX()) - (x0 + x1) / 2 - 70);
            dy = Math.round(toCanvasY(midY()) - (y0 + y1) / 2 - 40);
        }
        pushUndo();
        for (Script.Root r : roots) {
            r.x += dx;
            r.y += dy;
            script.roots.add(r);
        }
        picked.set(roots);
        toast(note == null ? "вставлено " + pickedSaid() : note);
    }

    private void applyBand() {
        banding = false;
        picked.band(bandMode, picked.bandHits(Math.min(bandX0, bandX1), Math.min(bandY0, bandY1),
                Math.max(bandX0, bandX1), Math.max(bandY0, bandY1)));
    }

    private void lineAction(int i) {
        if (script.roots.isEmpty()) return;
        Script.Root first = Finder.ordered(script).get(0);
        switch (LineEdit.hasPlace(script) ? i : i + 1) {
            case 0 -> putBack(first.chain);
            case 1 -> handToWorld(first.chain);
            case 2 -> {
                Clip.copyRoots(copies(script.roots));
                toast("строка скопирована · " + Ui.plural(Script.blocksIn(script.roots), "блок", "блока", "блоков"));
            }
            default -> toCanvas();
        }
    }

    private static List<Script.Root> copies(List<Script.Root> roots) {
        List<Script.Root> out = new ArrayList<>();
        for (Script.Root r : roots) {
            Script.Root c = new Script.Root(r.x, r.y);
            for (Script.Node n : r.chain) c.chain.add(n.copy());
            out.add(c);
        }
        return out;
    }

    private void handToWorld(List<Script.Node> chain) {
        String why = LineEdit.toHand(chain);
        if (why != null) { toast(why); return; }
        onClose();
    }

    private void putBack(List<Script.Node> chain) {
        String why = LineEdit.putBack(script, chain);
        if (why != null) { toast(why); return; }
        onClose();
    }

    private void toCanvas() {
        Script main = XeroCode.script();
        double x = main.roots.isEmpty() ? 40 : main.lastRootX() + 320, y = 40;
        for (Script.Root r : copies(script.roots)) {
            double dx = x - script.roots.get(0).x, dy = y - script.roots.get(0).y;
            r.x += dx;
            r.y += dy;
            main.roots.add(r);
        }
        main.viewX = 80 - x * main.viewZoom;
        main.viewY = 60 - y * main.viewZoom;
        main.save();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        XeroCode.canvasClosed();
        mc.gui.setScreen(new EditorScreen(main));
    }

    private int barStamp = Integer.MIN_VALUE, barWidth;
    private int compactStamp = Integer.MIN_VALUE;
    private boolean compact;

    private static final int BAR_H = 26;
    private static final String[] LINE_ACTS = {"В руку", "Копировать", "В полотно"};
    private static final String[][] LINE_ICONS = {Draw.LOAD, Draw.COPY, Draw.CANVAS};
    private static final String[] PLACE_ACTS = {"На место", "В руку", "Копировать", "В полотно"};
    private static final String[][] PLACE_ICONS = {Draw.UPLOAD, Draw.LOAD, Draw.COPY, Draw.CANVAS};

    private String[] barActs() { return LineEdit.hasPlace(script) ? PLACE_ACTS : LINE_ACTS; }

    private String[][] barIcons() { return LineEdit.hasPlace(script) ? PLACE_ICONS : LINE_ICONS; }

    private boolean barShown() { return drag == null && script.scratch; }

    private boolean barOn(int i) { return !script.roots.isEmpty(); }

    private String barText() { return "строка · " + script.scratchLabel; }

    private int barRoom() { return canvasRight() - canvasLeft() - 20; }

    private boolean barCompact() {
        String said = barText();
        int stamp = said.hashCode() * 31 + barRoom() * 7;
        if (stamp == compactStamp) return compact;
        int w = 26 + font.width(said);
        for (String s : barActs()) w += Ui.buttonW(font, s) + 6;
        compactStamp = stamp;
        compact = w > barRoom();
        return compact;
    }

    private int barBtnW(int i) {
        return barCompact() ? 16 : Ui.buttonW(font, barActs()[i]);
    }

    private int barW() {
        String said = barText();
        int stamp = said.hashCode() * 31 + barRoom() * 7;
        if (stamp == barStamp) return barWidth;
        int w = 26 + font.width(said);
        for (int i = 0; i < barActs().length; i++) w += barBtnW(i) + 6;
        barStamp = stamp;
        barWidth = Math.min(w, Math.max(120, barRoom()));
        return barWidth;
    }

    private int barX() { return canvasLeft() + (canvasRight() - canvasLeft() - barW()) / 2; }

    private int barY() { return Math.max(Theme.TOPBAR_H + 8, canvasBottom() - 12 - BAR_H); }

    private int barBtnX(int i) {
        int right = barX() + barW() - 8;
        for (int k = barActs().length - 1; k > i; k--) right -= barBtnW(k) + 6;
        return right - barBtnW(i);
    }

    private void drawBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!barShown()) return;
        int bw = barW(), bx = barX(), by = barY(), by2 = by + (BAR_H - 16) / 2;
        boolean small = barCompact();
        Draw.shadow(ctx, bx, by, bw, BAR_H, Ui.R);
        Draw.card(ctx, bx, by, bw, BAR_H, Ui.R, Draw.argb(0xF2, Ui.PANEL),
                Draw.opaque(Theme.ACCENT));
        for (int i = 0; i < barActs().length; i++) {
            if (small) Ui.iconButton(ctx, mouseX, mouseY, barBtnX(i), by2, 16, barIcons()[i],
                    barKind(i), barOn(i));
            else Ui.button(ctx, font, mouseX, mouseY, barBtnX(i), by2, barBtnW(i), 16,
                    barActs()[i], barKind(i), barOn(i));
        }
        Draw.textFit(ctx, font, barText(), bx + 10, by + (BAR_H - Ui.TEXT_H) / 2,
                barBtnX(0) - bx - 16, Theme.TEXT_DIM, false);
    }

    private int barKind(int i) { return i == 0 ? Ui.ACCENT : Ui.GHOST; }

    private boolean barClicked(double mx, double my) {
        if (!barShown()) return false;
        if (!Ui.hit(mx, my, barX(), barY(), barW(), BAR_H)) return false;
        int by2 = barY() + (BAR_H - 16) / 2;
        for (int i = 0; i < barActs().length; i++) {
            if (!Ui.hit(mx, my, barBtnX(i), by2, barBtnW(i), 16)) continue;
            if (barOn(i)) lineAction(i);
            return true;
        }
        return true;
    }

    private static final int POCKET_W = 152, POCKET_H = 34, POCKET_EDGE = 10;

    private int pocketW() {
        int room = canvasRight() - canvasLeft() - (trashShown() ? 3 * POCKET_EDGE : 40);
        return Math.min(POCKET_W, Math.max(36, trashShown() ? room / 2 : room));
    }

    private boolean trashShown() { return drag != null && Settings.chests(); }

    private int trashX() { return canvasLeft() + POCKET_EDGE; }

    private boolean overTrash(double mx, double my) {
        return trashShown() && Ui.hit(mx, my, trashX(), pocketY(), pocketW(), POCKET_H);
    }

    private void drawTrash(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!trashShown()) return;
        boolean hot = overTrash(mouseX, mouseY);
        dropTarget(ctx, trashX(), hot, Theme.DANGER, Draw.TRASH, "УДАЛИТЬ",
                hot ? "отпусти — удалю" : "перетащи сюда");
    }

    private void dropTarget(GuiGraphicsExtractor ctx, int px, boolean hot, int accent, String[] glyph,
                            String title, String hint) {
        int py = pocketY(), pw = pocketW();
        Draw.shadow(ctx, px, py, pw, POCKET_H, Ui.R);
        Draw.card(ctx, px, py, pw, POCKET_H, Ui.R,
                Draw.argb(hot ? 0xFF : 0xE0,
                        hot ? Draw.mix(Ui.PANEL, accent, 0.22f) : Ui.PANEL),
                Draw.opaque(hot ? accent : Ui.BORDER));
        int gx = px + 11, gy = py + (POCKET_H - Draw.glyphH(glyph)) / 2;
        Draw.glyph(ctx, glyph, gx, gy, hot ? accent : Theme.TEXT_DIM);
        int tx = gx + Draw.glyphW(glyph) + 8;
        int room = px + pw - 9 - tx;
        if (room < 34) return;
        Draw.textFit(ctx, font, title, tx, py + 8, room,
                hot ? Theme.TEXT : Theme.TEXT_DIM, false);
        Draw.textFit(ctx, font, hint, tx, py + 19, room, Theme.TEXT_FAINT, false);
    }

    private int pocketX() { return canvasRight() - pocketW() - POCKET_EDGE; }

    private int pocketY() {
        return Math.max(Theme.TOPBAR_H + 8, canvasBottom() - POCKET_H - POCKET_EDGE);
    }

    private boolean overPocket(double mx, double my) {
        return drag != null && Ui.hit(mx, my, pocketX(), pocketY(), pocketW(), POCKET_H);
    }

    private void drawPocket(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (drag == null) return;
        drawTrash(ctx, mouseX, mouseY);
        boolean hot = overPocket(mouseX, mouseY);
        dropTarget(ctx, pocketX(), hot, Theme.ACCENT, Draw.PACK, "В РЮКЗАК",
                hot ? "отпусти — уберу" : "перетащи сюда");
    }

    private void finishDrag(double mx, double my) {
        if (drag == null) return;
        dragAwaitsClick = false;
        if (overPocket(mx, my)) {
            List<Script.Node> chain = drag;
            endDrag();
            stash(chain, "убрано в рюкзак");
            return;
        }
        if (overTrash(mx, my)) {
            toast(dragFromPalette ? "отменено" : "удалено · " + blocksText(drag));
            endDrag();
            return;
        }
        if (mx < canvasLeft()) {
            if (dragFromPalette && !dragMoved) placeAtCenter();
            else toast(dragFromPalette ? "отменено" : "блок удалён");
            endDrag();
            return;
        }
        Snap s = findSnap(layout());
        if (dragFromPalette && dragSnapshot != null) pushUndo(dragSnapshot);
        if (s != null) {
            s.target.addAll(s.index, drag);
            if (s.above && s.root != null && s.target == s.root.chain) { s.root.x = s.x; s.root.y = s.y; }
        } else {
            Script.Root r = new Script.Root(mouseCanvasX - dragOffX, mouseCanvasY - dragOffY);
            r.chain.addAll(drag);
            script.roots.add(r);
        }
        endDrag();
    }

    private void deleteBlock(Layout.Box box) {
        pushUndo();
        List<Script.Node> body = new ArrayList<>(box.node.body);
        box.owner.remove(box.index);
        if (!body.isEmpty()) box.owner.addAll(box.index, body);
        dropEmpty(box.root);
        toast("блок удалён");
    }

    private void deleteStack(Layout.Box box) {
        pushUndo();
        box.owner.subList(box.index, box.owner.size()).clear();
        dropEmpty(box.root);
        toast("стопка удалена");
    }

    private void deleteHovered() {
        if (hoverBox == null || drag != null) return;
        deleteBlock(hoverBox);
        hoverBox = null;
    }

    private void deleteStackHovered() {
        if (hoverBox == null || drag != null) return;
        deleteStack(hoverBox);
        hoverBox = null;
    }

    private void duplicateHovered() {
        if (drag != null || (carry != null && carryHeld)) return;
        if (hoverBox != null && hoverChip != null && grabFromChip(hoverBox, hoverChip, false)) return;
        if (carry == null && hoverBox != null) duplicate(hoverBox, true);
    }

    private void duplicateBlockHovered() {
        if (drag != null || carry != null) return;
        if (hoverBox != null) duplicate(hoverBox, false);
    }

    private static final int GRAB_SLOP = 3;

    private boolean grabFromChip(Layout.Box box, Layout.Chip chip, boolean move) {
        List<Value> have = Carry.values(box, chip);
        if (have.isEmpty()) return false;
        if (move) { take(box, chip, have, true); return true; }
        boolean nested = false;
        for (Value v : have) nested |= !Carry.inner(v).isEmpty();
        if (have.size() == 1 && !nested) { take(box, chip, have, false); return true; }
        chooseValues(box, chip, have);
        return true;
    }

    private void chooseValues(Layout.Box box, Layout.Chip chip, List<Value> have) {
        record Pick(Value value, boolean top, Menu.Item item) {}
        List<Pick> picks = new ArrayList<>();
        for (Value v : have) {
            picks.add(new Pick(v, true, Menu.Item.rich(v.label(), Carry.icon(v), v.note(), List.of())));
            for (Value in : Carry.inner(v))
                picks.add(new Pick(in, false,
                        Menu.Item.rich("· " + in.label(), Carry.icon(in), in.note(), List.of())));
        }
        List<Menu.Item> items = new ArrayList<>(picks.size());
        boolean[] on = new boolean[picks.size()];
        for (int i = 0; i < picks.size(); i++) {
            items.add(picks.get(i).item());
            on[i] = picks.get(i).top();
        }
        int sx = toScreenX(chip.x), sy = toScreenY(chip.y + Layout.CHIP_H) + 3;
        menu = Menu.multi(width, height, sx, sy, font, "ЧТО СКОПИРОВАТЬ", items, on,
                "Копировать", picked -> {
                    if (picked.isEmpty()) return;
                    List<Value> out = new ArrayList<>(picked.size());
                    for (int i : picked) out.add(picks.get(i).value());
                    take(box, chip, out, false);
                });
    }

    private void take(Layout.Box box, Layout.Chip chip, List<Value> picked, boolean move) {
        if (!move && carry != null && !carryHeld) {
            for (Value v : picked) carry.add(v.copy());
            toast("на курсоре значений: " + carry.size());
            return;
        }
        carrySnapshot = snapshot();
        carry = new ArrayList<>(picked.size());
        for (Value v : picked) carry.add(v.copy());
        carryHeld = move;
        if (move) {
            List<Value> src = Layout.chipNode(box.node).values.get(chip.argIndex);
            if (src != null) {
                if (chip.isCell()) { if (chip.cell < src.size()) src.remove(chip.cell); }
                else src.clear();
            }
            revision++;
            return;
        }
        toast(carry.size() == 1
                ? "значение скопировано — " + Ui.click() + " по слоту"
                : "скопировано значений: " + carry.size() + " — " + Ui.click() + " по слоту");
    }

    private void cancelCarry() {
        if (carry == null) return;
        boolean moved = carryHeld;
        carry = null;
        carryHeld = false;
        if (moved && carrySnapshot != null) {
            History.restore(script, carrySnapshot);
            revision++;
        }
        carrySnapshot = null;
        toast("отменено");
    }

    private void dropCarry() {
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box b = l.boxes.get(i);
            if (!b.contains(mouseCanvasX, mouseCanvasY)) continue;
            Layout.Chip c = b.chipAt(mouseCanvasX, mouseCanvasY);
            if (c != null && Carry.accepts(carry, b, c)) { placeCarry(b, c); return; }
            break;
        }
        cancelCarry();
    }

    private void placeCarry(Layout.Box box, Layout.Chip chip) {
        pushUndo(carrySnapshot);
        int put = Carry.put(box, chip, carry);
        int left = carry.size() - put;
        carry = null;
        carryHeld = false;
        carrySnapshot = null;
        toast(left > 0 ? "вставлено " + put + ", не влезло " + left : "вставлено");
    }

    private void publishModule(Layout.Box box) {
        Set<Script.Root> roots = new LinkedHashSet<>();
        Set<Script.Node> covered = picked.covered();
        if (!covered.isEmpty() && covered.contains(box.node)) {
            for (Layout.Box b : layout().boxes) if (b.root != null && covered.contains(b.node)) roots.add(b.root);
        } else if (box.root != null) roots.add(box.root);
        if (Sync.state(script, minecraft.level) != Sync.State.IN_SYNC) {
            toast("сначала отправь код в мир («На сервер») — модуль собирается из строк /dev");
            return;
        }
        Map<Script.Root, Integer> at = Exporter.positions(script);
        List<Integer> positions = new ArrayList<>();
        for (Script.Root r : roots) {
            Integer p = at.get(r);
            if (p != null) positions.add(p);
        }
        if (positions.isEmpty()) {
            toast("здесь нет строк, которые уходят на сервер");
            return;
        }
        Collections.sort(positions);
        closeOverlays();
        rememberView();
        saveScript();
        minecraft.gui.setScreen(new PublishModuleScreen(this, positions));
    }

    private Value heldItem(Layout.Box box) {
        if (hoverChip == null || hoverBox != box) return null;
        List<Value> have = Carry.values(box, hoverChip);
        if (have.size() != 1) return null;
        Value v = have.get(0);
        return Value.ITEM.equals(v.type) && !v.isBlank() ? v : null;
    }

    private void copyForXero(Value item) {
        String raw = Stacks.toServer(item);
        if (raw == null) { toast("предмет не собрался"); return; }
        minecraft.keyboardHandler.setClipboard("item(\"" + raw + "\")");
        toast("скопировано для Xero: item(\"…\")");
    }

    private boolean copyChip(boolean cut) {
        if (hoverBox == null || hoverChip == null) return false;
        List<Value> have = Carry.values(hoverBox, hoverChip);
        if (have.isEmpty()) {
            toast(cut ? "в этом слоте нечего вырезать" : "в этом слоте пусто");
            return true;
        }
        Clip.copyValues(have);
        String said = have.size() == 1 ? "«" + have.get(0).label() + "»"
                : "значений: " + have.size();
        if (!cut) { toast("скопировано " + said); return true; }
        pushUndo();
        List<Value> src = Layout.chipNode(hoverBox.node).values.get(hoverChip.argIndex);
        if (src != null) {
            if (hoverChip.isCell()) {
                if (hoverChip.cell < src.size()) src.set(hoverChip.cell, Value.blank());
            } else src.clear();
        }
        revision++;
        toast("вырезано " + said);
        return true;
    }

    private boolean pasteChip() {
        if (hoverBox == null || hoverChip == null) return false;
        if (!hoverChip.isArg() && !hoverChip.isCell()) return false;
        Script.Node n = Layout.chipNode(hoverBox.node);
        if (hoverChip.argIndex < 0 || hoverChip.argIndex >= n.args().size()) return false;
        List<Value> values = Clip.pasteValues();
        if (values.isEmpty()) return false;
        List<Value> ok = new ArrayList<>(values.size());
        for (Value v : values) if (Carry.fits(n, hoverChip.argIndex, v)) ok.add(v);
        if (ok.isEmpty()) {
            toast("такое значение в этот слот не кладётся");
            return true;
        }
        pushUndo();
        int put = Carry.put(hoverBox, hoverChip, ok);
        int left = ok.size() - put;
        toast(left > 0 ? "вставлено " + put + ", не влезло " + left
                : put == 1 ? "вставлено «" + ok.get(0).label() + "»" : "вставлено " + put);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        changeHint = true;
        boolean far = Math.abs(click.x() - pressX) >= GRAB_SLOP || Math.abs(click.y() - pressY) >= GRAB_SLOP;
        if (far) {
            if (pendHot == Settings.Hot.GO_DEF && pendButton == 0 && pendBox != null) pressOne = pendBox;
            pendHot = null;
            emptyPress = false;
            if (pressOne != null) {
                Layout.Box one = pressOne;
                pressOne = null;
                pendBox = null;
                startDragOne(one);
            }
        }
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (blockMenu != null && blockMenu.mouseDragged(click.y())) return true;
        if (palette.barDragging()) { palette.barDrag(click.y(), canvasBottom()); return true; }
        Overlay panel = panel();
        if (panel != null && panel != chest) return panel.mouseDragged(click, dx, dy);
        if (resizingPalette) {
            paletteDrag += dx;
            setPaletteWidth((int) Math.round(paletteDrag));
            return true;
        }
        if (mapDragging) {
            centerOn(map.canvasX(click.x()), map.canvasY(click.y()), false);
            return true;
        }
        if (banding) {
            bandX1 = toCanvasX(click.x());
            bandY1 = toCanvasY(click.y());
            return true;
        }
        if (moving) {
            moveDX += dx / zoom;
            moveDY += dy / zoom;
            if (Math.abs(moveDX) > 0.5 || Math.abs(moveDY) > 0.5) moveShifted = true;
            return true;
        }
        if (editor != null) return editor.mouseDragged(click, dx, dy);
        if (finder != null && finder.mouseDragged(click)) return true;
        if (draggingSearch) return search.mouseDragged(click, dx, dy);
        if (pressChip != null) {
            if (Math.abs(click.x() - pressX) < GRAB_SLOP
                    && Math.abs(click.y() - pressY) < GRAB_SLOP) return true;
            grabFromChip(pressBox, pressChip, true);
            pressChip = null;
            pressBox = null;
            return true;
        }
        if (paletteSwipe) { palette.scrollBy(dy / 42.0, canvasBottom()); return true; }
        if (drag != null && dragFromPalette && !dragMoved && Env.touch() && click.x() < canvasLeft()
                && Math.abs(dy) > Math.abs(dx)) {
            endDrag();
            dragFromPalette = false;
            paletteSwipe = true;
            palette.scrollBy(dy / 42.0, canvasBottom());
            return true;
        }
        if (drag != null) { dragMoved = true; return true; }
        if (panning) {
            panAnim = false;
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        changeHint = true;
        palette.barRelease();
        if (menu != null) menu.mouseReleased();
        if (blockMenu != null) blockMenu.mouseReleased();
        Overlay panel = panel();
        if (panel != null && panel != chest) { panel.mouseReleased(); return true; }
        if (banding) { applyBand(); return true; }
        if (moving) { finishMove(); return true; }
        if (pressOne != null) {
            Layout.Box one = pressOne;
            pressOne = null;
            picked.unpick(one);
            return true;
        }
        if (pendHot != null && click.button() == pendButton) {
            panning = false;
            runPending();
            return true;
        }
        if (emptyPress && click.button() == 1) {
            emptyPress = false;
            panning = false;
            quickAdd(null, emptyX, emptyY, true, true);
            return true;
        }
        panning = false;
        paletteSwipe = false;
        draggingSearch = false;
        resizingPalette = false;
        mapDragging = false;
        if (finder != null) finder.mouseReleased();
        if (editor != null) editor.mouseReleased();
        if (pressChip != null) {
            Layout.Box box = pressBox;
            Layout.Chip chip = pressChip;
            pressChip = null;
            pressBox = null;
            chipClicked(box, chip, 0);
            return true;
        }
        if (carry != null && carryHeld) {
            dropCarry();
            return true;
        }
        if (swallowRelease) { swallowRelease = false; return true; }
        if (drag == null) return super.mouseReleased(click);
        if (dragAwaitsClick) { dragAwaitsClick = false; return true; }
        finishDrag(click.x(), click.y());
        return true;
    }

    private void placeAtCenter() {
        if (dragSnapshot != null) pushUndo(dragSnapshot);
        double cx = toCanvasX((canvasLeft() + width) / 2.0) - 70;
        double cy = toCanvasY(midY()) - 20;
        while (occupied(cx, cy)) { cx += 18; cy += 18; }
        Script.Root r = new Script.Root(cx, cy);
        r.chain.addAll(drag);
        script.roots.add(r);
        toast("блок добавлен");
    }

    private boolean occupied(double x, double y) {
        for (Script.Root r : script.roots)
            if (Math.abs(r.x - x) < 12 && Math.abs(r.y - y) < 12) return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        changeHint = true;
        Overlay panel = panel();
        if (panel != null) return panel.mouseScrolled(mx, my, vAmount);
        if (menu != null && menu.mouseScrolled(mx, my, vAmount)) return true;
        if (blockMenu != null) { blockMenu.mouseScrolled(mx, my, vAmount); return true; }
        if (editor != null && editor.mouseScrolled(mx, my, vAmount)) return true;
        if (finder != null && finder.mouseScrolled(mx, my, vAmount)) return true;
        if (map.hit(mx, my)) return true;
        if (mx < canvasLeft()) { palette.scrollBy(vAmount, canvasBottom()); return true; }
        if (my < Theme.TOPBAR_H) return true;
        if (hoverChip != null && hoverBox != null && hoverChip.isMarker() && ctrlHeld()) {
            pushUndo();
            Layout.chipNode(hoverBox.node).cycleMarker(hoverChip.settingIndex, vAmount < 0);
            return true;
        }
        zoomTo(zoom * (vAmount > 0 ? 1.08 : 1 / 1.08), mx, my);
        return true;
    }

    private static final class Snap {
        final List<Script.Node> target;
        final int index, x, y, markX, markY, width, height;
        final boolean above;
        final Script.Root root;
        final boolean reflow;
        Snap(List<Script.Node> target, int index, int x, int y, int markX, int markY,
             int width, int height, boolean above, Script.Root root) {
            this.target = target; this.index = index; this.x = x; this.y = y;
            this.markX = markX; this.markY = markY; this.width = width; this.height = height;
            this.above = above; this.root = root;
            this.reflow = !(above && root != null && target == root.chain);
        }
    }

    private Layout syncGhost() {
        if (snap == null || !snap.reflow) { dropGhost(); return layout; }
        if (ghostLayout != null && ghostStamp == layoutStamp
                && ghost.same(snap.target, snap.index, snap.width, snap.height))
            return ghostLayout;
        Layout.Ghost g = new Layout.Ghost(snap.target, snap.index, snap.width, snap.height);
        Layout built = Layout.of(script, font, g, layout, style(), callsStamp);
        if (!g.placed) { dropGhost(); return layout; }
        ghost = g;
        ghostLayout = built;
        ghostStamp = layoutStamp;
        return ghostLayout;
    }

    private void dropGhost() {
        ghost = null;
        ghostLayout = null;
        ghostStamp = Integer.MIN_VALUE;
    }

    private static double dist(double dx, double dy) { return Math.hypot(dx * 0.6, dy); }

    private Snap findSnap(Layout layout) {
        if (drag == null || drag.isEmpty()) return null;
        boolean hat = drag.get(0).isHat();
        double px = mouseCanvasX - dragOffX, py = mouseCanvasY - dragOffY;
        sizeDrag();

        Snap best = null;
        double bestDist = 44;
        for (Layout.Box box : layout.boxes) {
            if (!hat && box.tailH == 0) {
                int by = box.bottom();
                double d = dist(px - box.x, py - by);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Snap(box.owner, box.index + 1, box.x, by,
                            box.x, by, payloadW, payloadH, false, box.root);
                }
                if (box.node.wraps() && !box.node.folded) {
                    int ix = box.x + Layout.INDENT, iy = box.bodyTop();
                    double di = dist(px - ix, py - iy);
                    if (di < bestDist) {
                        bestDist = di;
                        best = new Snap(box.node.body, 0, ix, iy, ix, iy,
                                payloadW, payloadH, false, box.root);
                    }
                }
            }
            boolean rootChain = box.root != null && box.owner == box.root.chain;
            if (box.index == 0 && !box.node.isHat() && (!hat || rootChain)) {
                double d = dist(px - box.x, (py + payloadH) - box.y);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Snap(box.owner, 0, box.x, box.y - payloadH,
                            box.x, box.y, payloadW, payloadH, true, box.root);
                }
            }
        }
        return best;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        changeHint = true;
        int key = input.key();
        Overlay panel = panel();
        if (panel != null) {
            panel.keyPressed(input);
            settle(panel);
            return true;
        }
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { menu = null; return true; }
            return true;
        }
        if (blockMenu != null) {
            BlockMenu open = blockMenu;
            boolean ate = open.keyPressed(input);
            if (open.isClosed() || blockMenu != open) blockMenu = null;
            if (ate) return true;
        }
        if (editor != null) {
            editor.keyPressed(input);
            if (editor.isClosed()) finishEditor();
            return true;
        }
        Settings st = Settings.get();
        boolean modifier = Settings.isModifier(key);
        soloKey = modifier ? key : Settings.NONE;
        Settings.Hot hot = modifier ? null : st.match(key, input.modifiers());
        if (hot != null && typing(hot)) hot = null;
        if (hot != null && (st.mods(hot) != 0 || !typingText()) && runHotkey(hot)) return true;
        if (hot == null && key == GLFW.GLFW_KEY_DELETE && !typingText() && drag == null) {
            boolean stack = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (!picked.isEmpty()) deletePicked();
            else if (stack) deleteStackHovered();
            else deleteHovered();
            return true;
        }
        if (finder != null) {
            boolean ate = finder.keyPressed(input);
            if (finder.isClosed()) closeFinder();
            if (ate) return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.getValue().isEmpty()) search.setValue("");
                else search.setFocused(false);
                return true;
            }
            if (search.keyPressed(input)) return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (exitPrompt) { exitPrompt = false; return true; }
            if (carry != null) { cancelCarry(); return true; }
            if (banding) { banding = false; return true; }
            if (moving) { cancelMove(); return true; }
            if (!picked.isEmpty()) { clearPicked(); toast("выделение снято"); return true; }
            if (drag != null) {
                cancelDrag();
                return true;
            }
            if (held != null) { cancelHeld(); return true; }
            if (!Settings.chests() && palette.hasCrumb()) {
                if (!search.getValue().isEmpty()) search.setValue("");
                else palette.openCategory(-1);
                return true;
            }
            if (script.scratch) { onClose(); return true; }
            askExit("build");
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        changeHint = true;
        if (settings != null) { settings.keyReleased(input); return true; }
        int key = input.key();
        if (soloKey == Settings.NONE || Settings.canonical(key) != Settings.canonical(soloKey))
            return super.keyReleased(input);
        soloKey = Settings.NONE;
        if (overlayOpen() || typingText()) return true;
        Settings.Hot hot = Settings.get().match(key, input.modifiers());
        if (hot != null) runHotkey(hot);
        return true;
    }

    private boolean typingText() {
        return (search.isFocused() && !Settings.chests()) || (finder != null && finder.focused());
    }

    private boolean typing(Settings.Hot hot) {
        if (!typingText()) return false;
        return hot == Settings.Hot.COPY || hot == Settings.Hot.CUT || hot == Settings.Hot.PASTE
                || hot == Settings.Hot.SELECT;
    }

    private boolean runHotkey(Settings.Hot hot) {
        switch (hot) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE -> { saveScript(); toast("сохранено"); }
            case UPLOAD -> publish(null);
            case SEARCH -> {
                if (Settings.chests()) { quickAddHere(false); break; }
                if (finder != null) finder.blur();
                search.setFocused(true);
                setFocused(search);
            }
            case FIND -> toggleFinder();
            case FIT -> fitView();
            case DUPLICATE -> { if (picked.isEmpty()) duplicateHovered(); else duplicatePicked(); }
            case DUP_ONE -> duplicateBlockHovered();
            case COPY -> {
                if (!copyChip(false)) { if (picked.isEmpty()) copyHovered(true); else copyPicked(); }
            }
            case COPY_ONE -> { if (!copyChip(false)) copyHovered(false); }
            case CUT -> {
                if (!copyChip(true)) { if (picked.isEmpty()) cutHovered(); else cutPicked(); }
            }
            case CUT_ONE -> { if (picked.isEmpty()) cutOneHovered(); else cutPicked(); }
            case PASTE -> { if (!pasteChip()) pasteClip(); }
            case DELETE -> { if (picked.isEmpty()) deleteHovered(); else deletePicked(); }
            case DEL_STACK -> { if (picked.isEmpty()) deleteStackHovered(); else deletePicked(); }
            case SELECT -> selectAll();
            case BACKPACK -> openBackpack();
            case MARKET -> openModules();
            case STASH -> stashHovered();
            case PLAY -> askExit("play");
            case BUILD -> askExit("build");
            case RESTART -> askExit(XeroCode.RESTART);
            case SETTINGS -> openSettings();
            case MODE -> toOriginal();
            case FOLD -> foldHovered();
            case FOLD_ALL -> foldAll();
            case FOLD_STACK -> foldStackHovered();
            case FOLD_STACKS -> foldStacks();
            case GO_DEF -> {
                if (hoverBox == null || jumpTarget(hoverBox.node) == null)
                    toast("наведись на вызов или объявление функции");
                else goTo(hoverBox.node);
            }
            case GO_BACK -> goBack();
            case QUICK_ADD -> quickAddHere(false);
            case PREV_LINE -> stepLine(-1);
            case NEXT_LINE -> stepLine(1);
            case TIDY -> tidy();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        changeHint = true;
        Overlay panel = panel();
        if (panel != null) { panel.charTyped(input); return true; }
        if (menu != null || blockMenu != null) return true;
        if (editor != null) return editor.charTyped(input);
        if (finder != null && finder.focused()) return finder.charTyped(input);
        if (Settings.chests()) return false;
        if (!search.isFocused()) {
            String s = input.codepointAsString();
            if (s != null && !s.isBlank()) {
                search.setFocused(true);
                setFocused(search);
            }
        }
        return search.isFocused() && search.charTyped(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) Backstage.restore(minecraft);
        closeOverlays();
        rememberView();
        saveScript();
        XeroCode.canvasClosed();
        super.onClose();
    }

    public void rememberView() {
        script.viewX = panX;
        script.viewY = panY;
        script.viewZoom = zoom;
    }

    public void finishEditing() { finishEditor(); }

    @Override
    public void removed() {
        if (minecraft != null) Backstage.restore(minecraft);
        finishEditor();
        Audio.release();
        if (settings != null) { settings.dispose(); settings = null; }
        rememberView();
        super.removed();
    }
}
