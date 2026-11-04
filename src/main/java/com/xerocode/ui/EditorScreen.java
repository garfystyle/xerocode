package com.xerocode.ui;

import java.util.LinkedHashSet;
import com.xerocode.Sync;
import com.xerocode.Exporter;
import com.xerocode.Tabs;
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
import com.xerocode.Rename;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import com.xerocode.XeroCode;
import org.lwjgl.glfw.GLFW;

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
    private TabStrip strip;
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
    private History.State dragSnapshot;
    private List<Script.Node> liftFrom;
    private int liftAt, liftRootAt = -1, liftX, liftY;
    private Script.Root liftRoot;
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
    private boolean bandTails;
    private double bandX0, bandY0, bandX1, bandY1;
    private boolean panHinted;
    private boolean selecting, selectPress;
    private Layout.Box selectTap;
    private double selectX, selectY;
    private Layout.Box pressCard;
    private double cardX, cardY;
    private boolean moving, moveShifted;
    private double moveDX, moveDY;
    private double moveFromX, moveFromY;
    private int dragPan = -1;
    private boolean dragPanMoved;
    private double dragPanFromX, dragPanFromY;
    private History.State moveSnapshot;

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
    private History.State carrySnapshot;
    private Script.Root heldRoot;
    private Layout.Box pressBox;
    private Layout.Chip pressChip;
    private double pressX, pressY;
    private boolean tapArmed;
    private int tapButton;
    private double tapX, tapY;
    private static final int TAP_SLOP = 6, TAP_SLOP_TOUCH = 24;

    private int soloKey = Settings.NONE;
    private boolean ateChar;
    private Settings.Hot pendHot;
    private Layout.Box pressOne;
    private Layout.Box pendBox;
    private Layout.Chip pendChip;
    private int pendButton = -1;
    private boolean emptyPress;
    private double emptyX, emptyY;
    private Runnable rightPress;
    private final List<double[]> navBack = new ArrayList<>();

    private StatusBar foot;
    private Menu menu;
    private BlockMenu blockMenu;
    private CatalogPicker condPicker;
    private ChestMenu chest;
    private boolean chestsShown = Settings.chests();
    private ValueEditor editor;
    private SettingsPanel settings;
    private History.State editorUndo;

    private Layout layout;
    private int layoutStamp = Integer.MIN_VALUE;
    private int revision;
    private Layout.Box hoverBox;
    private Script.Node renaming;
    private String renameFrom = "";
    private Layout.Chip hoverChip;
    private boolean overCanvas, strayed;
    private int hoverMouseX = Integer.MIN_VALUE, hoverMouseY = Integer.MIN_VALUE;
    private String status = "";
    private long statusAt;
    private String exitTo = "build";
    private boolean exitPrompt;

    private int savedStamp;
    private boolean stampTaken;

    private boolean dirty() {
        return !script.scratch && stampTaken && (codeHash() != savedStamp || Tabs.touched());
    }

    public void markPublished() {
        savedStamp = script.codeHash();
        stampTaken = true;
    }

    private void saveScript() {
        script.save();
    }

    private boolean tabsShown() { return strip != null && drag == null; }

    private void placeStrip() {
        if (strip != null) strip.place(canvasLeft() + 8, Theme.topH() + 6, canvasRight() - canvasLeft() - 16);
    }

    private void switchTab(int index) {
        if (index == Tabs.indexOf(script) || index < 0 || index >= Tabs.all().size()) return;
        openTab(Tabs.script(index));
    }

    private void openTab(Script next) {
        finishEditor();
        settleDrag();
        if (dirty()) Tabs.touch();
        rememberView();
        saveScript();
        Tabs.shown(next);
        Tabs.saveAll();
        if (minecraft != null) minecraft.gui.setScreen(new EditorScreen(next, true));
    }

    private void newTab() {
        if (Tabs.full()) { toast("вкладок не больше " + Tabs.MAX); return; }
        finishEditor();
        if (dirty()) Tabs.touch();
        rememberView();
        saveScript();
        Script fresh = Tabs.add(null);
        if (fresh != null && minecraft != null) minecraft.gui.setScreen(new EditorScreen(fresh, true));
    }

    private void askCloseTab(int index) {
        if (index <= 0 || index >= Tabs.all().size()) return;
        Script doomed = Tabs.script(index);
        String name = Tabs.all().get(index).name;
        if (doomed.roots.isEmpty()) { closeTab(index); return; }
        List<Menu.Item> items = List.of(
                new Menu.Item("Закрыть и удалить полотно", true, Draw.TRASH),
                new Menu.Item("Оставить"));
        menu = Menu.picker(width, height, (int) Math.min(width - 160, canvasLeft() + 8),
                strip.bottom() + 2, font, "Вкладка «" + name + "» не пустая", items, 1, i -> {
                    if (i == 0) closeTab(index);
                });
    }

    private void closeTab(int index) {
        boolean here = Tabs.indexOf(script) == index;
        String name = Tabs.all().get(index).name;
        if (!here) {
            Tabs.close(index);
            strip.invalidate();
            toast("вкладка «" + name + "» закрыта");
            return;
        }
        finishEditor();
        Tabs.close(index);
        if (minecraft != null) minecraft.gui.setScreen(new EditorScreen(Tabs.activeScript(), true));
    }

    private void tabMenu(int index, int x, int y) {
        List<Menu.Item> items = new ArrayList<>();
        items.add(new Menu.Item("Переименовать", false, Draw.NAME));
        items.add(new Menu.Item("Сохранить в файл  " + Settings.get().label(Settings.Hot.SAVE_TAB),
                false, Draw.LOAD));
        List<Script> filled = Tabs.filled(script);
        boolean several = filled.size() > 1;
        if (several) items.add(new Menu.Item("Все полотна в файл", false, Draw.LOAD));
        if (index > 0) items.add(new Menu.Item("Закрыть", true, Draw.CROSS));
        menu = Menu.picker(width, height, x, y, font, Tabs.all().get(index).name, items, -1, i -> {
            if (i == 0) {
                if (index != Tabs.indexOf(script)) switchTab(index);
                else strip.rename(index);
            } else if (i == 1) {
                if (index == Tabs.indexOf(script)) saveTabFile();
                else saveTabFile(Tabs.script(index), Tabs.all().get(index).name);
            } else if (i == 2 && several) saveFile(Exporter.export(filled), "все полотна");
            else if (i >= 2) askCloseTab(index);
        });
    }

    private void saveTabFile() {
        String name = script.scratch ? script.scratchLabel : Tabs.name(script);
        saveTabFile(script, name);
    }

    private void saveTabFile(Script which, String name) {
        if (which.roots.isEmpty()) { toast("вкладка пустая — сохранять нечего"); return; }
        saveFile(Exporter.export(which), name);
    }

    private void saveFile(Exporter.Result out, String name) {
        String stem = (name == null || name.isBlank() ? "полотно" : name)
                .replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        byte[] bytes = out.json().toString().getBytes(StandardCharsets.UTF_8);
        int skipped = out.report().unmapped;
        FileDialog.save("Сохранить вкладку", Codespace.savedDir().resolve(stem + ".json"),
                new String[]{"*.json"}, "код JustMC (*.json)", bytes, path -> {
                    if (path == null) { toast("не сохранено"); return; }
                    toast("сохранено: " + path.getFileName()
                            + (skipped > 0 ? " · не попало блоков: " + skipped : ""));
                });
    }

    public EditorScreen(Script script) { this(script, false); }

    public EditorScreen(Script script, boolean keepHistory) {
        super(Component.literal("XeroCode"));
        this.script = script;
        History.use(script);
        if (!keepHistory) History.clear();
        if (!script.scratch) Tabs.shown(script);
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
    public boolean nothingToSend() { return script.scratch ? script.roots.isEmpty() : Tabs.blank(script); }

    @Override
    public boolean finding() { return finder != null; }

    @Override
    public boolean selecting() { return selecting; }

    @Override
    public int pickedCount() { return picked.blocks(); }

    @Override
    public StatusBar.Said sync() { return foot == null || script.scratch ? null : foot.syncSaid(script); }

    private int canvasLeft() { return Settings.chests() ? 0 : Theme.PALETTE_W; }
    private int canvasRight() { return finder == null ? width : Math.max(canvasLeft() + 40, finder.x()); }
    private int canvasBottom() { return height - (Settings.statusBar() ? Theme.STATUS_H : 0); }
    private double midX() { return (canvasLeft() + canvasRight()) / 2.0; }
    private double midY() { return (Theme.topH() + canvasBottom()) / 2.0; }
    private double toCanvasX(double sx) { return (sx - canvasLeft() - panX) / zoom; }
    private double toCanvasY(double sy) { return (sy - Theme.topH() - panY) / zoom; }
    private int toScreenX(double cx) { return (int) Math.round(canvasLeft() + panX + cx * zoom); }
    private int toScreenY(double cy) { return (int) Math.round(Theme.topH() + panY + cy * zoom); }

    private ScreenRectangle canvasArea() {
        return new ScreenRectangle(canvasLeft(), Theme.topH(),
                width - canvasLeft(), canvasBottom() - Theme.topH());
    }

    private boolean busy() { return drag != null || held != null || carry != null || moving; }

    private static final int PLUS_H = 24, PLUS_EDGE = 12, PLUS_DISC = 18;
    private static final String PLUS_LABEL = "Блоки";

    private int plusW() { return 3 + PLUS_DISC + 6 + font.width(PLUS_LABEL) + 6 + 16 + 5; }

    private int plusX() { return canvasLeft() + PLUS_EDGE; }

    private int plusY() { return canvasBottom() - PLUS_EDGE - PLUS_H; }

    private boolean overPlus(double mx, double my) {
        return Settings.chests() && !trashTap() && Ui.hit(mx, my, plusX(), plusY(), plusW(), PLUS_H);
    }

    private void drawPlus(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!Settings.chests() || carrying() || trashTap()) return;
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
        int ink = Draw.opaque(Theme.ON_FILL);
        Draw.rect(ctx, dx + c - 5, dy + c - 1, 10, 2, ink);
        Draw.rect(ctx, dx + c - 1, dy + c - 5, 2, 10, ink);
        Draw.text(ctx, font, PLUS_LABEL, dx + PLUS_DISC + 6, py + (PLUS_H - Ui.TEXT_H) / 2,
                Theme.ON_FILL, false);
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
        if (!r.folded) unfoldBodies(r.chain);
        foldsChanged();
    }

    private static void unfoldBodies(List<Script.Node> chain) {
        for (Script.Node n : chain) {
            n.folded = false;
            unfoldBodies(n.body);
        }
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
        for (Script.Root r : all) {
            r.folded = fold;
            if (!fold) unfoldBodies(r.chain);
        }
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
        double aspect = (canvasBottom() - Theme.topH()) / (double) Math.max(1, canvasRight() - canvasLeft());
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

    private Catalog.Action lastAdded, lastWrap;

    private record Wrap(List<Script.Node> owner, List<Script.Node> nodes) {}

    private void wrapHere() {
        if (busy()) return;
        List<Wrap> targets = new ArrayList<>();
        if (!picked.isEmpty()) {
            for (Selection.Piece p : picked.pieces()) {
                Layout.Box head = p.head();
                int to = Math.min(head.owner.size(), head.index + p.count());
                addWrap(targets, head.owner, head.index, to);
            }
        } else if (hoverBox != null) {
            wrapBox(hoverBox);
            return;
        }
        if (targets.isEmpty()) {
            toast(picked.isEmpty() ? "наведись на блок или выдели блоки" : "оборачивать нечего");
            return;
        }
        chooseWrap(targets);
    }

    private void wrapBox(Layout.Box box) {
        if (busy()) return;
        List<Wrap> targets = new ArrayList<>();
        boolean head = box.node.isHat() || box.node.declares();
        addWrap(targets, box.owner, box.index, head ? box.owner.size() : box.index + 1);
        if (targets.isEmpty()) { toast("под шапкой нет блоков"); return; }
        chooseWrap(targets);
    }

    private static void addWrap(List<Wrap> out, List<Script.Node> owner, int from, int to) {
        while (from < to && (owner.get(from).isHat() || owner.get(from).declares())) from++;
        if (from < to) out.add(new Wrap(owner, new ArrayList<>(owner.subList(from, to))));
    }

    private static List<Catalog.Category> wrapCategories() {
        List<Catalog.Category> out = new ArrayList<>();
        for (Catalog.Category c : Catalog.CATEGORIES)
            if (c.isCondition() && !Catalog.ELSE_CATEGORY.equals(c.name)) out.add(c);
        for (boolean loops : new boolean[]{true, false})
            for (Catalog.Category c : Catalog.CATEGORIES)
                if (c.wraps() && !c.isCondition() && c.name.equals(LOOPS) == loops) out.add(c);
        return out;
    }

    private static final String LOOPS = "Повторение";

    private void chooseWrap(List<Wrap> targets) {
        closeOverlays();
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Catalog.Category c : wrapCategories())
            for (int i = 0; i < c.subActions.size(); i++) {
                String sub = c.subNames.get(i);
                for (Catalog.Action a : c.subActions.get(i))
                    items.add(new CatalogPicker.Item(Catalog.keyOf(a), a.name, c.name, a.item,
                            a.description, sub == null ? "" : sub, "", a.icon(), a.sign));
            }
        if (items.isEmpty()) return;
        int blocks = 0;
        for (Wrap w : targets) blocks += w.nodes().size();
        String title = blocks == 1
                ? "Обернуть «" + Draw.fit(font, Layout.blockTitle(targets.get(0).nodes().get(0)), 180) + "»"
                : "Обернуть " + Ui.plural(blocks, "блок", "блока", "блоков");
        condPicker = new CatalogPicker(font, width, height, title, Theme.ACCENT, items, null,
                lastWrap == null ? null : Catalog.keyOf(lastWrap), null, id -> {
                    Catalog.Action a = Catalog.byKey(id);
                    if (a != null) wrapInto(targets, a);
                }).forBlocks();
    }

    private void wrapInto(List<Wrap> targets, Catalog.Action a) {
        List<Integer> starts = new ArrayList<>();
        for (Wrap w : targets) starts.add(runAt(w));
        if (!starts.stream().allMatch(i -> i >= 0)) {
            toast("код изменился — оберни ещё раз");
            return;
        }
        pushUndo();
        Script.Node last = null;
        int blocks = 0;
        for (int t = 0; t < targets.size(); t++) {
            Wrap w = targets.get(t);
            int at = runAt(w);
            List<Script.Node> run = w.owner().subList(at, at + w.nodes().size());
            Script.Node shell = new Script.Node(a);
            shell.body.addAll(run);
            run.clear();
            w.owner().add(at, shell);
            blocks += w.nodes().size();
            last = shell;
        }
        clearPicked();
        lastWrap = a;
        lastAdded = a;
        focusNode = last;
        focusAt = System.currentTimeMillis();
        toast("«" + a.name + "» вокруг " + Ui.plural(blocks, "блока", "блоков", "блоков")
                + (targets.size() > 1 ? " · " + Ui.plural(targets.size(), "кусок", "куска", "кусков") : ""));
    }

    private static int runAt(Wrap w) {
        int at = indexOf(w.owner(), w.nodes().get(0));
        if (at < 0 || at + w.nodes().size() > w.owner().size()) return -1;
        for (int i = 1; i < w.nodes().size(); i++)
            if (w.owner().get(at + i) != w.nodes().get(i)) return -1;
        return at;
    }

    private void quickAddHere(boolean chests) {
        int sx = toScreenX(mouseCanvasX), sy = toScreenY(mouseCanvasY);
        boolean inside = sx >= canvasLeft() && sx < canvasRight()
                && sy >= Theme.topH() && sy < canvasBottom();
        if (inside) quickAdd(hoverBox, bodyAt(mouseCanvasX, mouseCanvasY), mouseCanvasX, mouseCanvasY,
                chests, false);
        else quickAdd(null, null, toCanvasX(midX()), toCanvasY(midY()), chests, false);
    }

    private Layout.Box bodyAt(double cx, double cy) {
        Layout l = layout();
        for (int ci = l.chunks.size() - 1; ci >= 0; ci--) {
            Layout.Chunk c = l.chunks.get(ci);
            if (!c.visible(cx, cy, cx, cy)) continue;
            for (int i = c.to - 1; i >= c.from; i--) {
                Layout.Box b = l.boxes.get(i);
                if (b.inBody(cx, cy)) return b;
                if (b.contains(cx, cy) && (b.hitGrab(cx, cy) || b.hitTail(cx, cy))) return null;
            }
        }
        return null;
    }


    private void quickAdd(Layout.Box under, Layout.Box shell, double cx, double cy, boolean chests,
                          boolean home) {
        if (busy()) return;
        Script.Node host = under == null ? null : under.node;
        List<Script.Node> owner = under == null ? null : under.owner;
        Script.Node into = under == null && shell != null ? shell.node : null;
        Consumer<Catalog.Action> place = a -> placeQuick(a, host, owner, into, cx - 10, cy - 8);
        String aim = into == null ? null
                : "Добавить в «" + Draw.fit(font, Layout.blockTitle(into), 160) + "»";
        if (chests) {
            if (home) openChestHome(place);
            else openChest(place);
            if (chest != null && aim != null) chest.titled(aim);
            return;
        }
        closeOverlays();
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Catalog.Category c : Catalog.CATEGORIES)
            for (int s = 0; s < c.subActions.size(); s++) {
                String sub = c.subNames.get(s);
                for (Catalog.Action a : c.subActions.get(s))
                    items.add(new CatalogPicker.Item(Catalog.keyOf(a), a.name, c.name, a.item,
                            a.description, sub == null ? "" : sub, "", a.icon(), a.sign));
            }
        if (items.isEmpty()) return;
        String title = aim != null ? aim : host == null ? "Добавить блок"
                : "Добавить под «" + Draw.fit(font, Layout.blockTitle(host), 180) + "»";
        condPicker = new CatalogPicker(font, width, height, title, Theme.ACCENT, items, null,
                lastAdded == null ? null : Catalog.keyOf(lastAdded), null, id -> {
                    Catalog.Action a = Catalog.byKey(id);
                    if (a == null) return;
                    lastAdded = a;
                    place.accept(a);
                }).forBlocks();
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
        boolean condLost = old.cond != null && Mapping.hasConditional(a)
                && !Mapping.allowsCondition(a, old.cond.action);
        if (Mapping.hasConditional(a) && old.cond != null && !condLost) fresh.cond = old.cond;
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
                + (lost > 0 ? " · не перенесено значений: " + lost : "")
                + (condLost ? " · условие «" + old.cond.action.name + "» сюда не ставится" : ""));
    }

    private static int sameArg(List<Catalog.Arg> args, Catalog.Arg want) {
        for (int j = 0; j < args.size(); j++)
            if (args.get(j).purpose.equals(want.purpose) && args.get(j).type.equals(want.type)) return j;
        for (int j = 0; j < args.size(); j++)
            if (args.get(j).purpose.equals(want.purpose)) return j;
        return -1;
    }

    private void placeQuick(Catalog.Action a, Script.Node host, List<Script.Node> owner,
                            Script.Node into, double cx, double cy) {
        Script.Node node = new Script.Node(a);
        int at = owner == null ? -1 : indexOf(owner, host);
        boolean inside = into != null && !node.isHat() && boxOf(into) != null;
        pushUndo();
        if (inside) {
            into.body.add(0, node);
            toast("«" + a.name + "» — в «" + Draw.fit(font, Layout.blockTitle(into), 140) + "»");
        } else if (at >= 0 && !node.isHat()) {
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
                && sy < canvasBottom() && sy + b.totalH * zoom > Theme.topH();
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
        inputArrived();
        if (top == null) top = new TopBar(font, this);
        if (strip == null && !script.scratch) strip = new TabStrip(font, new TabStrip.Host() {
            @Override public void select(int index) { switchTab(index); }
            @Override public void add() { newTab(); }
            @Override public void close(int index) { askCloseTab(index); }
            @Override public void menu(int index, int x, int y) { tabMenu(index, x, y); }
        });
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

    private Collab.Stage collabSeen = Collab.stage();
    private String collabSaid = "";
    private long collabSaidAt;
    private static final long COLLAB_SAID_FRESH = 60_000;

    private void watchCollab() {
        Collab.Stage now = Collab.stage();
        if (now != collabSeen) {
            if (now == Collab.Stage.LIVE) {
                int people = Collab.peerCount() + 1;
                collabSaid = "совместная работа: " + Collab.note()
                        + (people > 1 ? " · " + Ui.plural(people, "участник", "участника", "участников") : "");
                collabSaidAt = System.currentTimeMillis();
            } else if (now == Collab.Stage.BROKEN) {
                collabSaid = "совместная работа прервана: " + Collab.note();
                collabSaidAt = System.currentTimeMillis();
            }
            collabSeen = now;
        }
        if (collabSaid.isEmpty() || settings != null) return;
        if (System.currentTimeMillis() - collabSaidAt < COLLAB_SAID_FRESH) toast(collabSaid);
        collabSaid = "";
    }

    private History.State snapshot() { return History.snapshot(script); }

    private void pushUndo() { pushUndo(snapshot()); }

    private void pushUndo(History.State state) {
        History.push(state);
        revision++;
    }

    private void undo() {
        closeOverlays();
        if (lifting()) { cancelDrag(); return; }
        if (History.undo(script)) { Collab.afterHistory(script); revision++; toast("отменено"); }
    }

    private void redo() {
        closeOverlays();
        settleDrag();
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
        strayed = false;
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

    private void renameHovered() {
        if (renameVariable()) return;
        Script.Node n = hoverBox == null ? null : hoverBox.node;
        if (n == null || !n.declares() && !n.invokes()) {
            toast("наведись на функцию, процесс, вызов или переменную");
            return;
        }
        Script.Node target = n.declares() ? n : jumpTarget(n);
        if (target == null) target = n;
        closeOverlays();
        renaming = target;
        renameFrom = target.declares() ? Functions.nameOf(target) : Functions.targetOf(target);
        openValue(target, target.declares() ? Catalog.FN_NAME : Catalog.CALL_NAME,
                toScreenX(mouseCanvasX), toScreenY(mouseCanvasY), false, -1);
    }

    private boolean renameVariable() {
        if (hoverBox == null || hoverChip == null) return false;
        Script.Node node = Layout.chipNode(hoverBox.node);
        Rename.Target t = Rename.target(chipValues(hoverBox, hoverChip));
        if (t == null) return false;
        Script.Root root = hoverBox.root != null ? hoverBox.root : rootOf(hoverBox.node);
        Rename.Plan plan = Rename.plan(script, root, t.name(), t.scope());
        int sx = toScreenX(hoverChip.x), sy = toScreenY(hoverChip.y + Layout.CHIP_H) + 2;
        closeOverlays();
        Value start = Value.of(Value.VARIABLE);
        start.name = t.name();
        start.scope = t.scope();
        editorUndo = snapshot();
        editor = ValueEditor.forCell(start, "Переименовать переменную", Value.VARIABLE, font, sx, sy,
                width, height, knownNames(node), usedNames(),
                edited -> renamedVariable(plan, edited));
        editor.note(RENAME_PLACES + plan.places + " · " + RENAME_LINES + plan.lines);
        return true;
    }

    private static final String RENAME_PLACES = "мест: ", RENAME_LINES = "строк: ";

    private static List<Value> chipValues(Layout.Box box, Layout.Chip chip) {
        if (chip.settingIndex >= 0) {
            Value bound = Layout.chipNode(box.node).markerVar(chip.settingIndex);
            return bound == null ? List.of() : List.of(bound);
        }
        return Carry.values(box, chip);
    }

    private Script.Root rootOf(Script.Node node) {
        for (Script.Root r : script.roots) if (holds(r.chain, node)) return r;
        return null;
    }

    private static boolean holds(List<Script.Node> chain, Script.Node node) {
        for (Script.Node n : chain)
            if (n == node || n.cond == node || holds(n.body, node)) return true;
        return false;
    }

    private int renameElsewhere(Rename.Plan plan, Rename.Result r) {
        if (!Rename.GAME.equals(plan.scope) && !Rename.SAVE.equals(plan.scope)) return 0;
        int places = 0;
        for (Script other : Tabs.others(script)) {
            Rename.Result there = Rename.apply(Rename.plan(other, null, r.from(), plan.scope), r.to(), r.scope());
            if (there == null || there.places() == 0) continue;
            places += there.places();
            other.save();
        }
        return places;
    }

    private void renamedVariable(Rename.Plan plan, Value edited) {
        Rename.Result r = Rename.apply(plan, edited.name, edited.scope);
        if (r == null) { toast("имя переменной пустое — ничего не переименовано"); return; }
        int elsewhere = renameElsewhere(plan, r);
        if (r.places() + elsewhere == 0 && r.from().equals(r.to()) && r.scope().equals(plan.scope)) return;
        String scope = r.scope().equals(plan.scope) ? ""
                : " · " + Values.scope(r.scope()).name().toLowerCase(java.util.Locale.ROOT);
        toast("«" + r.from() + "» → «" + r.to() + "»" + scope + " · " + RENAME_PLACES + (r.places() + elsewhere)
                + " · " + RENAME_LINES + r.lines()
                + (r.clash() ? " · такая переменная уже есть" : "")
                + (r.keptLine() ? " · параметр остаётся строчным" : ""));
    }

    private String spreadRename() {
        Script.Node node = renaming;
        int moved = Functions.rename(script, node, renameFrom);
        if (moved < 0) return null;
        boolean process = node.declares() ? node.isProcess() : node.isStart();
        String to = node.declares() ? Functions.nameOf(node) : Functions.targetOf(node);
        boolean clash = Functions.taken(script, node, process, to);
        return "«" + renameFrom + "» → «" + to + "» · вызовов: " + moved
                + (clash ? " · такое имя уже есть" : "");
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
        return calls.isEmpty() ? null : calls.get(0);
    }

    private List<Finder.Hit> usagesOf(Script.Node n) {
        return Finder.usages(script, Finder.usageName(n), Finder.usageProcess(n));
    }

    private boolean navigable(Script.Node n) {
        if (n.invokes() && elsewhere(n)) return true;
        return (n.declares() || n.invokes()) && usagesOf(n).size() > 1;
    }

    private boolean elsewhere(Script.Node call) {
        Script.Node declaration = jumpTarget(call);
        return declaration != null && boxOf(declaration) == null;
    }

    private boolean openDeclaration(Script.Node call) {
        Script.Node declaration = jumpTarget(call);
        for (Script other : Tabs.others(script)) {
            for (Script.Root r : other.roots) {
                if (r.chain.isEmpty() || r.chain.get(0) != declaration) continue;
                other.viewX = 80 - r.x * other.viewZoom;
                other.viewY = 60 - r.y * other.viewZoom;
                openTab(other);
                return true;
            }
        }
        return false;
    }

    private void goTo(Script.Node from) {
        if (from.invokes() && elsewhere(from) && openDeclaration(from)) return;
        List<Finder.Hit> uses = usagesOf(from);
        if (uses.size() < 2) {
            toast(from.declares() ? "её нигде не вызывают" : "на полотне нет такой функции");
            return;
        }
        int here = 0, declared = 0;
        for (int i = 0; i < uses.size(); i++) {
            if (uses.get(i).node == from) here = i;
            if (uses.get(i).node.declares()) declared++;
        }
        int to = from.invokes() && declared > 0 ? 0 : Math.floorMod(here + 1, uses.size());
        String name = Finder.usageName(from);
        boolean process = Finder.usageProcess(from);
        rememberSpot();
        openFinder(null);
        finder.showUsages(name, () -> Finder.usages(script, name, process), uses.get(to).node);
        finder.blur();
        String back = Settings.get().label(Settings.Hot.GO_BACK) + " — назад";
        toast(to < declared ? "объявление «" + name + "» · " + back
                : "вызов " + (to - declared + 1) + " из " + (uses.size() - declared) + " · " + back);
    }

    private void stepUsage(int delta) {
        if (finder == null || finder.hits().isEmpty()) { toast("листать нечего"); return; }
        finder.step(delta);
    }

    private Layout.Box boxOf(Script.Node node) {
        for (Layout.Box b : layout().boxes) if (b.node == node) return b;
        return null;
    }

    private void centerOn(double cx, double cy, boolean glide) {
        double tx = midX() - canvasLeft() - cx * zoom;
        double ty = midY() - Theme.topH() - cy * zoom;
        if (glide) glideTo(tx, ty);
        else { panX = tx; panY = ty; panAnim = false; }
    }

    private void centerOnBox(Layout.Box box) {
        centerOn(box.x + Math.min(box.w, 260) / 2.0, box.y + Math.min(box.totalH, 170) / 2.0, true);
    }

    private void glideTo(double tx, double ty) {
        boolean near = Math.abs(tx - panX) < 1 && Math.abs(ty - panY) < 1;
        if (near || !Settings.get().glide) { panX = tx; panY = ty; panAnim = false; return; }
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

    private static final int STICK_H = 18, STICK_ICON = 12;

    private record Stuck(Layout.Box head, int x, int y, int w, int h, int cx, boolean icon,
                         String text, String kind, int textW, boolean cut) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int line() { return y + h - STICK_H; }
    }

    private final List<Stuck> stuck = new ArrayList<>();
    private Stuck stuckHover;

    private int stickBottom() {
        if (!tabsShown()) return Theme.topH() + STICK_H;
        placeStrip();
        return strip.bottom();
    }

    private static boolean scope(Layout.Box head) {
        return !head.nested && head.root != null && (head.node.isHat() || head.node.declares());
    }

    private void syncStuck() {
        stuck.clear();
        if (drag != null || moving) return;
        int top = Theme.topH(), h = stickBottom() - top, left = canvasLeft(), right = canvasRight();
        for (Layout.Chunk c : layout.chunks) {
            Layout.Box head = layout.boxes.get(c.from);
            if (!scope(head) || toScreenY(head.y + head.hatH) >= top) continue;
            int bottom = toScreenY(c.y1);
            if (bottom <= top) continue;
            int x = toScreenX(head.x), w = Math.max(STICK_H, (int) Math.round(head.w * zoom));
            int end = x + w;
            if (end <= left || x >= right) continue;
            int cx = Math.max(x, left) + 6;
            boolean icon = end - 6 - cx >= STICK_ICON;
            String text = head.card != null ? head.card.nameRaw : Layout.blockTitle(head.node);
            String kind = head.card != null && head.card.kindRaw != null ? head.card.kindRaw : "";
            int room = end - 6 - (cx + STICK_ICON + 4);
            int full = font.width(text);
            if (!kind.isEmpty() && full + 8 + font.width(kind) > room) kind = "";
            if (!kind.isEmpty()) room -= font.width(kind) + 8;
            if (!icon || room < 8) room = 0;
            stuck.add(new Stuck(head, x, Math.min(top, bottom - h), w, h, cx, icon, text, kind,
                    room, full > room));
        }
    }

    private Stuck stuckAt(double mx, double my) {
        for (int i = stuck.size() - 1; i >= 0; i--) if (stuck.get(i).hit(mx, my)) return stuck.get(i);
        return null;
    }

    private void drawStuck(GuiGraphicsExtractor ctx, ScreenRectangle area) {
        if (stuck.isEmpty()) return;
        boolean grad = Settings.gradient();
        Draw.batch(Batch.open(ctx, area, area, 64));
        for (Stuck s : stuck) Draw.shadow(ctx, s.x(), s.y(), s.w(), s.h(), 1);
        for (Stuck s : stuck) {
            Layout.Box b = s.head();
            boolean over = s == stuckHover;
            int base = BlockView.color(b.node);
            int top = over ? Draw.opaque(Draw.shade(base, grad ? 0.28f : 0.16f)) : b.top;
            int bottom = over ? (grad ? Draw.opaque(Draw.shade(base, 0.02f)) : top) : b.bottom;
            int border = over ? Draw.opaque(Draw.shade(base, -0.28f)) : b.border;
            Draw.blockShape(ctx, s.x(), s.y(), s.w(), s.h(), 0, 0, top, bottom, border);
            Draw.rect(ctx, s.x() + 1, s.y() + 1, s.w() - 2, 1, Draw.argb(0x2E, 0xFFFFFF));
            boolean light = Draw.isLight(top);
            int ink = Layout.ink(light);
            ItemStack icon = b.card != null ? b.card.icon : b.node.action.icon();
            if (s.icon()) Draw.item(ctx, icon, s.cx(), s.line() + (STICK_H - STICK_ICON) / 2, STICK_ICON);
            int ty = s.line() + (STICK_H - Ui.TEXT_H) / 2;
            if (s.textW() > 0)
                Draw.textFit(ctx, font, s.text(), s.cx() + STICK_ICON + 4, ty, s.textW(), ink, !light);
            if (!s.kind().isEmpty())
                Draw.textRight(ctx, font, s.kind(), s.x() + s.w() - 6, ty,
                        Draw.mix(ink, top, 0.42f), !light);
        }
        Draw.batch(null);
    }

    private void revealHead(Layout.Box head) {
        int clear = tabsShown() ? stickBottom() : Theme.topH();
        glideTo(panX, clear - Theme.topH() - (head.y - TAG_H - 8) * zoom);
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
        map.frame(layout, layoutStamp, canvasLeft(), Theme.topH(), canvasRight(), canvasBottom());
        if (!map.shown()) return;
        map.draw(ctx, toCanvasX(canvasLeft()), toCanvasY(Theme.topH()),
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
        String renamed = renaming != null && editor.node() == renaming && !editor.cancelled()
                ? spreadRename() : null;
        renaming = null;
        if (editor.changed() && editorUndo != null) pushUndo(editorUndo);
        if (renamed != null) toast(renamed);
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
        openValue(node, arg, canvasLeft() + 20, Theme.topH() + 20,
                false, list && slot != null && cell >= 0 && cell < slot.size() ? cell : -1);
    }

    private static final int TAPE_LIMIT = 400, SWEEP_FRAMES = 60;
    private static final long RECORD_BUDGET = 3_000_000L;

    private record ShadowTape(long key, int[] quads, int[] cuts, int[] spans) {
        void play(Batch into, double vx0, double vy0, double vx1, double vy1) {
            int from = 0, open = -1;
            for (int k = 0; k < cuts.length; k++) {
                int at = k * 4, to = cuts[k];
                boolean seen = spans[at + 2] >= vx0 && spans[at] <= vx1
                        && spans[at + 3] >= vy0 && spans[at + 1] <= vy1;
                if (seen && open < 0) open = from;
                if (!seen && open >= 0) { into.append(quads, open, from); open = -1; }
                from = to;
            }
            if (open >= 0) into.append(quads, open, from);
        }
    }

    private final Map<Script.Root, Tape> tapes = new IdentityHashMap<>();
    private final Map<Script.Root, ShadowTape> shadowTapes = new IdentityHashMap<>();
    private int styleStamp, callsStamp, hintChanges = Integer.MIN_VALUE;
    private int[] shadowBuffer = new int[0];
    private int[] gridBuffer = new int[0];
    private boolean inFrame, framing, hintChecked;
    private boolean changeHint = true;
    private int sweepAt, sweepHash, hintRevision = Integer.MIN_VALUE;
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
            hashCache = script.codeHashByView();
            hashStamp = layoutStamp;
        }
        return hashCache;
    }

    private void inputArrived() {
        changeHint = true;
        hintChecked = false;
    }

    private boolean sweepDiffers() {
        int size = script.roots.size();
        if (sweepAt == 0) sweepHash = size;
        int end = Math.min(size, sweepAt + size / SWEEP_FRAMES + 1);
        if (end > sweepAt) sweepHash = script.fingerprint(sweepHash, sweepAt, end);
        sweepAt = end;
        if (sweepAt < size) return false;
        sweepAt = 0;
        return sweepHash * 31 + revision != layoutStamp;
    }

    private Layout layout() {
        if (inFrame && layout != null) return layout;
        if (layout != null && (!changeHint || hintChecked) && revision == hintRevision
                && Collab.changes() == hintChanges && (!framing || !sweepDiffers())) {
            return layout;
        }
        sweepAt = 0;
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
        if (moving) followMove(mouseCanvasX, mouseCanvasY);
        if (banding) {
            bandX1 = mouseCanvasX;
            bandY1 = mouseCanvasY;
        }
        if (script == XeroCode.script())
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
        syncStuck();
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

        ctx.enableScissor(canvasLeft(), Theme.topH(), width, canvasBottom());
        drawGrid(ctx);
        if (script.roots.isEmpty() && drag == null) drawEmptyHint(ctx);

        enterCanvas(ctx);
        double vx0 = toCanvasX(canvasLeft()) - 8, vx1 = toCanvasX(width) + 8;
        double vy0 = toCanvasY(Theme.topH()) - 8, vy1 = toCanvasY(canvasBottom()) + 8;
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
        drawStuck(ctx, area);
        if (script == XeroCode.script())
            Peers.render(ctx, font, script, layout, area, canvasLeft(), Theme.topH(),
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
        drawTrashTap(ctx, mouseX, mouseY);
        drawPlus(ctx, mouseX, mouseY);
        drawBar(ctx, mouseX, mouseY);
        if (tabsShown()) {
            placeStrip();
            strip.render(ctx, mouseX, mouseY, delta);
        }
        watchCollab();
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
        m.translate((float) (canvasLeft() + snapped(panX)), (float) (Theme.topH() + snapped(panY)));
        m.scale((float) zoom, (float) zoom);
    }

    private static boolean tapeable(Layout.Chunk c, double x0, double y0, double x1, double y1) {
        return c.to - c.from <= TAPE_LIMIT || c.x0 >= x0 && c.x1 <= x1 && c.y0 >= y0 && c.y1 <= y1;
    }

    private void drawShadows(GuiGraphicsExtractor ctx, Layout view, ScreenRectangle area, boolean taping,
                             int flags, double vx0, double vy0, double vx1, double vy1) {
        Batch shadows = Batch.reuse(ctx, area, area, shadowBuffer);
        Draw.batch(shadows);
        for (Layout.Chunk chunk : view.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            long tapeKey = tapeKey(chunk, flags);
            ShadowTape taped = taping && chunk.root != null ? shadowTapes.get(chunk.root) : null;
            if (taped != null && taped.key() == tapeKey) { taped.play(shadows, vx0, vy0, vx1, vy1); continue; }
            boolean record = taping && chunk.root != null && tapeable(chunk, vx0, vy0, vx1, vy1);
            int from = shadows.size();
            int[] cuts = record ? new int[chunk.to - chunk.from] : null;
            int[] spans = record ? new int[cuts.length * 4] : null;
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = view.boxes.get(i);
                if (record || !rides(box) && visible(box, vx0, vy0, vx1, vy1)) BlockView.shadow(ctx, box);
                if (!record) continue;
                int k = i - chunk.from;
                cuts[k] = shadows.size() - from;
                spans[k * 4] = box.x;
                spans[k * 4 + 1] = box.y;
                spans[k * 4 + 2] = box.x + box.w;
                spans[k * 4 + 3] = box.bottom();
            }
            if (record)
                shadowTapes.put(chunk.root, new ShadowTape(tapeKey, shadows.copy(from, shadows.size()), cuts, spans));
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
            if (tape != null && tape.valid(tapeKey)) tape.replay(ctx, font, area, vx0, vy0, vx1, vy1);
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
                if (record) Tape.mark(box.x, box.y, box.x + box.w, box.bottom());
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
        stuckHover = null;
        overCanvas = false;
        boolean moved = mouseX != hoverMouseX || mouseY != hoverMouseY;
        hoverMouseX = mouseX;
        hoverMouseY = mouseY;
        if (drag != null || moving || banding || modal()
                || mouseX < canvasLeft() || mouseY < Theme.topH() || mouseY >= canvasBottom()) return;
        if (editor != null && editor.contains(mouseX, mouseY)) return;
        if (finder != null && finder.contains(mouseX, mouseY)) return;
        if (map.hit(mouseX, mouseY)) return;
        if (barShown() && Ui.hit(mouseX, mouseY, barX(), barY(), barW(), BAR_H)) return;
        if (tabsShown() && strip.contains(mouseX, mouseY)) return;
        overCanvas = true;
        if (moved) strayed = true;
        stuckHover = stuckAt(mouseX, mouseY);
        if (stuckHover != null) return;
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
        double step = Settings.gridStep() * zoom;
        while (step < 13) step *= 2;
        double ox = canvasLeft() + panX, oy = Theme.topH() + panY;
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
            Draw.rect(ctx, x, Theme.topH(), 1, canvasBottom() - Theme.topH(),
                    Math.floorMod(i, 4) == 0 ? strong : faint);
        }
        int j0 = (int) Math.floor((Theme.topH() - oy) / step);
        for (int j = j0; ; j++) {
            int y = (int) Math.round(oy + j * step);
            if (y > canvasBottom()) break;
            if (y < Theme.topH()) continue;
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
        int j0 = (int) Math.floor((Theme.topH() - oy) / step);
        for (int i = i0; ; i++) {
            int x = (int) Math.round(ox + i * step);
            if (x > width) break;
            if (x < canvasLeft()) continue;
            boolean bigX = Math.floorMod(i, 4) == 0;
            for (int j = j0; ; j++) {
                int y = (int) Math.round(oy + j * step);
                if (y > canvasBottom()) break;
                if (y < Theme.topH()) continue;
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
        int y = (Theme.topH() + canvasBottom() - h) / 2;
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

    private void openLoadDialog() {
        closeOverlays();
        if (minecraft != null) minecraft.gui.setScreen(new LoadScreen(this, this::loadJson));
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
        int tab = Tabs.indexOf(script);
        if (tab >= 0 && Tabs.autoName(tab)) {
            String stem = file.getFileName().toString().replaceFirst("(?i)\\.json$", "");
            Tabs.rename(tab, stem);
            if (strip != null) strip.invalidate();
        }
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
            case TopBar.SELECT -> toggleSelecting();
            case TopBar.COPY -> copyPicked();
            case TopBar.CUT -> cutPicked();
            case TopBar.PASTE -> Env.clipboard(text -> {
                if (minecraft != null && minecraft.gui.screen() == this) pasteAt(null);
            });
            case TopBar.SETTINGS -> openSettings();
            case TopBar.ORIGINAL -> toOriginal();
            case TopBar.CODE -> toCode();
            default -> { }
        }
    }

    private void toggleSelecting() {
        closeOverlays();
        selecting = !selecting;
        if (!selecting) clearPicked();
    }

    private Layout.Box blockAt() {
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box b = l.boxes.get(i);
            if (!b.contains(mouseCanvasX, mouseCanvasY)) continue;
            if (b.chipAt(mouseCanvasX, mouseCanvasY) != null || b.hitGrab(mouseCanvasX, mouseCanvasY)
                    || b.hitTail(mouseCanvasX, mouseCanvasY)) return b;
        }
        return null;
    }

    private void pressSelecting(double mx, double my) {
        Layout.Box box = blockAt();
        selectPress = true;
        selectTap = box;
        selectX = mx;
        selectY = my;
        if (box != null && picked.covered().contains(box.node)) {
            startMove();
            return;
        }
        closeOverlays();
        bandMode = Selection.ADD;
        bandTails = false;
        banding = true;
        bandX0 = bandX1 = mouseCanvasX;
        bandY0 = bandY1 = mouseCanvasY;
    }

    private boolean releaseSelecting(MouseButtonEvent click) {
        if (!selectPress || click.button() != 0) return false;
        selectPress = false;
        Layout.Box tapped = selectTap;
        selectTap = null;
        int slop = Env.touch() ? TAP_SLOP_TOUCH : TAP_SLOP;
        if (Math.abs(click.x() - selectX) > slop || Math.abs(click.y() - selectY) > slop) return false;
        banding = false;
        if (moving) cancelMove();
        if (tapped != null) picked.toggle(tapped);
        return true;
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
        if (my < Theme.topH()) return true;
        if (mx < canvasLeft()) { palette.scrollBy(dy / 42.0, canvasBottom()); return true; }
        panAnim = false;
        panX += dx;
        panY += dy;
        return true;
    }

    public void touchZoom(double mx, double my, double factor) {
        if (overlayOpen() || mx < canvasLeft() || my < Theme.topH()) return;
        applyZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor)), mx, my);
    }

    public void touchHold(double mx, double my) {
        Overlay panel = panel();
        if (panel != null && panel == chest) { chest.hold(mx, my); return; }
        if (panel != null && panel == condPicker) { condPicker.hold(mx, my); return; }
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
        double cx = (aroundX - canvasLeft() - panX) / before, cy = (aroundY - Theme.topH() - panY) / before;
        panX = aroundX - canvasLeft() - cx * zoom;
        panY = aroundY - Theme.topH() - cy * zoom;
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
        int vh = Math.max(40, canvasBottom() - Theme.topH() - 40);
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

    private void toCode() {
        Settings s = Settings.get();
        s.mode = Settings.Mode.CODE;
        s.save();
        closeOverlays();
        rememberView();
        saveScript();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(new CodeScreen());
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
        if (!dirty() || !Settings.get().askExit) { leaveTo(command); return; }
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
        if (XeroCode.CLOSE.equals(command)) {
            XeroCode.canvasShut();
            onClose();
            return;
        }
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
        if (Env.browser() && nothingToSend()) { toast("полотна пустые — отправлять нечего"); return; }
        closeOverlays();
        rememberView();
        saveScript();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(new ExportScreen(script, this, exitCommand));
    }

    private void drawToast(GuiGraphicsExtractor ctx) {
        if (status.isEmpty()) return;
        long age = System.currentTimeMillis() - statusAt;
        long life = Settings.toastMs();
        if (age > life) { status = ""; return; }
        int a = age > life - 500 ? (int) (255 * (1 - (age - life + 500) / 500.0)) : 255;
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
        boolean hints = Settings.get().tooltips;
        if (hints && finder != null && finder.tooltip(ctx, mouseX, mouseY)) return;
        if (Settings.statusBar() && foot.contains(mouseX, mouseY)) {
            String said = foot.tip(mouseX, mouseY);
            if (hints && said != null)
                ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(said)), mouseX, mouseY);
            return;
        }

        if (mouseY < Theme.topH() && mouseX >= canvasLeft()) {
            if (hints) top.tooltip(ctx, mouseX, mouseY);
            return;
        }
        if (stuckHover != null) {
            if (stuckHover.cut())
                ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(stuckHover.text())),
                        mouseX, mouseY);
            return;
        }
        if (!hints) return;
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
        if (hoverBox != null && hoverBox.hitFoldButton(mouseCanvasX, mouseCanvasY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal((hoverBox.shut() ? "Развернуть " : "Свернуть ")
                    + (hoverBox.foldStack ? "стопку" : "тело")));
            String key = Settings.get().label(hoverBox.foldStack ? Settings.Hot.FOLD_STACK : Settings.Hot.FOLD);
            if (!key.isEmpty()) lines.add(Component.literal("§7" + key));
            ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (hoverBox != null)
            tips.action(ctx, hoverBox.node.action, hoverBox.node, width, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        inputArrived();
        double mx = click.x(), my = click.y();
        mouseCanvasX = toCanvasX(mx);
        mouseCanvasY = toCanvasY(my);
        int button = click.button();
        boolean twice = doubled && followsTap(click);
        tapArmed = false;
        rightPress = null;

        Overlay panel = panel();
        if (panel != null) {
            panel.mouseClicked(click, doubled);
            settle(panel);
            return true;
        }
        if (exitPrompt) return exitPromptClicked(mx, my);
        if (menu == null && blockMenu == null && editor == null && held == null && tabsShown()) {
            placeStrip();
            if (strip.mouseClicked(click, doubled)) return true;
        }
        if ((button == 1 || button == 2) && pannable()) {
            dragPan = button;
            dragPanMoved = false;
            dragPanFromX = mx;
            dragPanFromY = my;
            panAnim = false;
            return true;
        }
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
        boolean dismissed = false;
        if (editor != null) {
            boolean inside = editor.mouseClicked(click, doubled);
            if (editor.isClosed()) { finishEditor(); dismissed = true; }
            if (inside) return true;
        }

        if (finder != null && finder.mouseClicked(click, doubled)) {
            strayed = false;
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
            if (button == 0 && mx >= canvasLeft() && my >= Theme.topH() && my < canvasBottom()) placeHeld();
            else cancelHeld();
            return true;
        }
        if (drag != null && dragAwaitsClick) {
            if (button == 0 && mx >= canvasLeft() && my >= Theme.topH() && my < canvasBottom()) {
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
        if (button == 0 && drag == null && carry == null && overTrashTap(mx, my)) {
            deletePicked();
            return true;
        }
        if (button == 0 && drag == null && carry == null && overPlus(mx, my)) {
            if (Env.touch()) quickAdd(placedBox(), null, toCanvasX(midX()), toCanvasY(midY()), true, true);
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
        if (my < Theme.topH() && mx >= canvasLeft()) {
            int id = top.hit(mx, my);
            if (id == TopBar.MORE) menu = top.menu(width, height, this::onTopButton);
            else if (id != TopBar.NONE) onTopButton(id);
            return true;
        }
        if (mx < canvasLeft()) {
            if (carry != null) { cancelCarry(); return true; }
            return paletteClicked(click, doubled);
        }
        Stuck pinned = carry == null && !dismissed ? stuckAt(mx, my) : null;
        if (pinned != null) {
            if (button == 0) revealHead(pinned.head());
            else panning = true;
            return true;
        }
        boolean used = canvasClicked(mx, my, button, click.modifiers(), twice && !dismissed);
        if (dismissed) tapArmed = false;
        return used;
    }

    private boolean followsTap(MouseButtonEvent click) {
        int slop = Env.touch() ? TAP_SLOP_TOUCH : TAP_SLOP;
        return tapArmed && click.button() == tapButton
                && Math.abs(click.x() - tapX) <= slop && Math.abs(click.y() - tapY) <= slop;
    }

    private void pressRight(Runnable action, double mx, double my) {
        rightPress = action;
        pressX = mx;
        pressY = my;
        panning = true;
    }

    private void armTap(double mx, double my, int button) {
        tapArmed = true;
        tapButton = button;
        tapX = mx;
        tapY = my;
    }

    private static boolean aimed(Settings.Hot hot) {
        return switch (hot) {
            case DELETE, DEL_STACK, COPY, COPY_ONE, CUT, CUT_ONE, DUPLICATE, DUP_ONE, WRAP, STASH,
                 FOLD, FOLD_STACK, GO_DEF -> true;
            default -> false;
        };
    }

    private boolean onCanvas(double mx, double my) {
        return mx >= canvasLeft() && mx < canvasRight() && my >= Theme.topH() && my < canvasBottom()
                && !map.hit(mx, my) && !overPlus(mx, my);
    }

    private boolean mouseHotkey(MouseButtonEvent click) {
        Settings.Hot hot = Settings.get().match(Settings.mouse(click.button()), click.modifiers());
        if (hot == null) return false;
        if (!aimed(hot)) return runHotkey(hot);
        if (!onCanvas(click.x(), click.y()) || hoverBox == null) return false;
        if (hot == Settings.Hot.GO_DEF && (hoverChip != null || !navigable(hoverBox.node)))
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
        if (finder != null) finder.blur();
        if (carry != null) {
            if (button == 0) dropCarry(); else cancelCarry();
            return true;
        }
        if (button == 0 && selecting) {
            pressSelecting(mx, my);
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
                if (sub && add) picked.unpickTail(box);
                else if (sub) picked.unpick(box);
                else picked.toggleTail(box);
                return true;
            }
            startBand(sub ? Selection.SUB : Selection.ADD, add);
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
                else if (button == 1) pressRight(() -> openBlockMenu(box, (int) mx, (int) my), mx, my);
                return true;
            }
            Layout.Chip chip = button <= 1 ? box.chipAt(mouseCanvasX, mouseCanvasY) : null;
            if (chip != null) {
                if (button == 1) {
                    pressRight(() -> {
                        if (!add || !paramVariable(box, chip)) chipClicked(box, chip, 1);
                    }, mx, my);
                    return true;
                }
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
            if (button == 1) {
                pressRight(() -> openBlockMenu(box, (int) mx, (int) my), mx, my);
                return true;
            }
            if (button == 0 && box.hitFoldButton(mouseCanvasX, mouseCanvasY)) {
                if (box.foldStack) toggleStack(box.root);
                else toggleFold(box.node);
                return true;
            }
            if (button == 0 && box.hitFold(mouseCanvasX, mouseCanvasY)) {
                toggleFold(box.node);
                return true;
            }
            if (button == 0 && box.hitTarget(mouseCanvasX, mouseCanvasY)) {
                chooseTarget(box.node, box.targetSetting, (int) mx, (int) my);
                return true;
            }
            if (button == 0 && box.card != null && cardHot(box, mouseCanvasX, mouseCanvasY)) {
                pressCard = box;
                cardX = mouseCanvasX;
                cardY = mouseCanvasY;
                pressX = mx;
                pressY = my;
                return true;
            }
            if (button == 0 && doubled) {
                chooseAction(box);
                return true;
            }
            if (button != 0) break;
            armTap(mx, my, button);
            if (picked.covered().contains(box.node)) startMove();
            else startDrag(box);
            return true;
        }
        if (button == 0 && doubled && !spaceHeld()) {
            banding = false;
            quickAdd(null, bodyAt(mouseCanvasX, mouseCanvasY), mouseCanvasX, mouseCanvasY, true, false);
            return true;
        }
        armTap(mx, my, button);
        if (button == 0 && !spaceHeld() && !Env.touch()) { startBand(Selection.NEW, false); return true; }
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

    private boolean shiftHeld() {
        return held(GLFW.GLFW_KEY_LEFT_SHIFT) || held(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean ctrlHeld() {
        return held(GLFW.GLFW_KEY_LEFT_CONTROL) || held(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean held(int key) {
        return minecraft != null && minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow(), key);
    }

    private void startBand(int mode, boolean tails) {
        closeOverlays();
        bandMode = mode;
        bandTails = tails;
        if (mode == Selection.NEW) clearPicked();
        banding = true;
        bandX0 = bandX1 = mouseCanvasX;
        bandY0 = bandY1 = mouseCanvasY;
        if (panHinted) return;
        panHinted = true;
        toast("полотно двигается ПКМ, средней кнопкой или пробелом");
    }

    private boolean pannable() { return drag != null || held != null || moving || banding; }

    private boolean sliding() {
        Overlay panel = panel();
        return pendHot == null && pressOne == null && pressCard == null && pressChip == null
                && editor == null && (panel == null || panel == chest || panel == condPicker)
                && !(drag != null && dragFromPalette && !dragMoved);
    }

    private void followMove(double cx, double cy) {
        moveDX = cx - moveFromX;
        moveDY = cy - moveFromY;
        if (Math.abs(moveDX) > 0.5 || Math.abs(moveDY) > 0.5) moveShifted = true;
    }

    private void startMove() {
        closeOverlays();
        moving = true;
        moveShifted = false;
        moveDX = 0;
        moveDY = 0;
        moveFromX = mouseCanvasX;
        moveFromY = mouseCanvasY;
        moveSnapshot = snapshot();
    }

    private void finishMove(double mx, double my) {
        if (moveShifted && (overTrash(mx, my) || overPocket(mx, my) || mx < canvasLeft())) {
            boolean trash = !overPocket(mx, my);
            cancelMove();
            if (trash) deletePicked();
            else movePickedToBackpack();
            return;
        }
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

    private static boolean cardHot(Layout.Box box, double cx, double cy) {
        Layout.Card c = box.card;
        return c.hitName(cx, cy) || c.hitId(cx, cy) || c.hitIcon(cx, cy) || c.hitDesc(cx, cy);
    }

    private boolean cardClicked(Layout.Box box, double cx, double cy, int mx, int my) {
        Layout.Card c = box.card;
        Script.Node n = box.node;
        boolean name = c.hitName(cx, cy);
        boolean id = c.hitId(cx, cy);
        boolean icon = c.hitIcon(cx, cy);
        if (!name && !id && !icon && !c.hitDesc(cx, cy)) return false;
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
                width, height, knownNames(node), usedNames(),
                edited -> node.bindMarker(settingIndex, edited));
    }

    private void chooseCondition(Script.Node wrapper, int sx, int sy) {
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Catalog.Action a : Mapping.conditions(wrapper.action)) {
            items.add(new CatalogPicker.Item(Catalog.keyOf(a), a.name, a.category.name, a.item,
                    a.description, a.args.isEmpty() ? "" : a.args.size() + " арг.", "", a.icon(),
                    a.sign));
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
                }).forBlocks();
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
        if (a.category != null && Catalog.ELSE_CATEGORY.equals(a.category.name))
            m.row(Draw.CROSS, "Убрать иначе", null, () -> dropElse(box));
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
        else if (node.invokes() && navigable(node))
            m.row(Draw.ARROW_RIGHT, "Другие вызовы", Settings.Hot.GO_DEF, () -> goTo(node));
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
        Value variable = variableIn(node);
        if (variable != null)
            m.row(Draw.SEARCH, "Найти переменную «" + variable.name + "»", null,
                    () -> findVariable(variable));
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
        if (!(node.isHat() || node.declares()) || box.index < box.owner.size() - 1)
            m.row(Draw.WRAP, "Обернуть в условие или цикл…", Settings.Hot.WRAP, () -> wrapBox(box));

        if (box.index == 0 && !box.nested && (node.isHat() || node.declares()) && inDev())
            m.row(Draw.LOAD, "Шаблон строки в руку", null, () -> handToWorld(chainOf(box, true)));

        m.gap();
        m.row(Draw.PACK, tail ? "Стопку в рюкзак" : "В рюкзак",
                Settings.Hot.STASH, () -> stashBlock(box, true));
        if (tail) m.row(Draw.PACK, "Блок в рюкзак", null, () -> stashBlock(box, false));
        boolean chosen = picked.covered().contains(box.node);
        m.row(Draw.SELECT, chosen ? "Убрать из выделения" : "Выделить", null, () -> picked.toggle(box));
        if (!chosen && box.index < box.owner.size() - 1)
            m.row(Draw.SELECT, "Выделить с блоками ниже", null, () -> picked.toggleTail(box));
        if (!picked.isEmpty()) {
            String said = "  ·  " + Ui.plural(picked.pieces().size(), "кусок", "куска", "кусков");
            m.row(Draw.COPY, "Копировать выделенное" + said, Settings.Hot.COPY, this::copyPicked);
            m.row(Draw.CUT, "Вырезать выделенное" + said, Settings.Hot.CUT, this::cutPicked);
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

    private static Value variableIn(Script.Node node) {
        for (List<Value> list : node.values.values())
            for (Value v : list)
                if ((Value.VARIABLE.equals(v.type) || Value.PARAMETER.equals(v.type))
                        && !v.name.isBlank()) return v;
        for (Value v : node.markerVars.values())
            if (!v.name.isBlank()) return v;
        return null;
    }

    private void findVariable(Value v) {
        openFinder(null);
        finder.showVariable(v.name, Value.PARAMETER.equals(v.type) ? "line" : v.scope);
    }

    private void openValue(Script.Node node, int argIndex, int sx, int sy,
                           boolean add, int select) {
        editorUndo = snapshot();
        ValueEditor open = new ValueEditor(node, argIndex, font, sx, sy, width, height,
                knownNames(node), usedNames());
        if (add) open.addCell();
        else if (select >= 0) open.select(select);
        editor = open;
    }

    private List<NamePick.Known> knownNames(Script.Node near) {
        layout();
        return ScriptNames.known(script, near, layoutStamp);
    }

    private List<String> usedNames() {
        layout();
        return ScriptNames.used(script, layoutStamp);
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
        menu = Menu.searching(width, height, mx, my, font, title, items, current, 1,
                call.isStart() ? "найти процесс…" : "найти функцию…", i -> {
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
        liftFrom = null;
        liftRoot = null;
        liftRootAt = -1;
    }

    private void dropEmpty(Script.Root root) {
        if (root != null && root.chain.isEmpty()) script.roots.remove(root);
    }

    private void cancelDrag() {
        if (lifting()) restoreLift();
        endDrag();
        dragAwaitsClick = false;
        toast("отменено");
    }

    private void settleDrag() {
        if (!lifting()) return;
        restoreLift();
        endDrag();
    }

    private boolean lifting() { return drag != null && !dragFromPalette && liftFrom != null; }

    private boolean rootHome() {
        if (liftRootAt < 0 || script.roots.contains(liftRoot)) return false;
        script.roots.add(Math.min(liftRootAt, script.roots.size()), liftRoot);
        return true;
    }

    private int chainHome() {
        int at = Math.min(liftAt, liftFrom.size());
        liftFrom.addAll(at, drag);
        return at;
    }

    private void restoreLift() {
        rootHome();
        chainHome();
        revision++;
    }

    private History.State liftedState() {
        boolean rootBack = rootHome();
        int at = chainHome();
        History.State state = snapshot();
        liftFrom.subList(at, at + drag.size()).clear();
        if (rootBack) script.roots.remove(liftRoot);
        return state;
    }

    private void commitLift() {
        if (lifting()) pushUndo(liftedState());
    }

    private boolean returnsHome(Snap s) {
        if (!lifting()) return false;
        if (Math.abs(mouseCanvasX - dragOffX - liftX) < 0.5
                && Math.abs(mouseCanvasY - dragOffY - liftY) < 0.5) return true;
        return s != null && liftRootAt < 0 && s.target == liftFrom && s.index == liftAt;
    }

    private void startDragOne(Layout.Box box) {
        closeOverlays();
        heldRoot = box.root;
        drag = new ArrayList<>();
        drag.add(box.owner.remove(box.index));
        lifted(box);
    }

    private void startDrag(Layout.Box box) {
        closeOverlays();
        heldRoot = box.root;
        drag = new ArrayList<>(box.owner.subList(box.index, box.owner.size()));
        box.owner.subList(box.index, box.owner.size()).clear();
        lifted(box);
    }

    private void lifted(Layout.Box box) {
        liftFrom = box.owner;
        liftAt = box.index;
        liftRoot = box.root;
        liftRootAt = box.root != null && box.root.chain.isEmpty() ? script.roots.indexOf(box.root) : -1;
        liftX = box.x;
        liftY = box.y;
        dropEmpty(box.root);
        revision++;
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

    private void movePickedToBackpack() {
        List<Script.Root> roots = pickedAsRoots();
        if (roots.isEmpty()) return;
        Backpack.Item item = Backpack.putAll(Backpack.suggestAll(roots), roots);
        if (item == null) { toast("убирать нечего"); return; }
        pushUndo();
        picked.cutOut(script);
        clearPicked();
        hoverBox = null;
        top.invalidate();
        toast("убрано в рюкзак: «" + item.name + "» · " + item.blocksText());
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
                && mouseCanvasY >= toCanvasY(Theme.topH()) && mouseCanvasY <= toCanvasY(canvasBottom());
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
        picked.band(bandMode, bandTails, picked.bandHits(Math.min(bandX0, bandX1), Math.min(bandY0, bandY1),
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

    private int barY() { return Math.max(Theme.topH() + 8, canvasBottom() - 12 - BAR_H); }

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

    private boolean carrying() { return drag != null || (moving && moveShifted); }

    private boolean trashShown() { return carrying() && Settings.chests(); }

    private int trashX() { return canvasLeft() + POCKET_EDGE; }

    private boolean overTrash(double mx, double my) {
        return trashShown() && Ui.hit(mx, my, trashX(), pocketY(), pocketW(), POCKET_H);
    }

    private boolean trashTap() { return selecting && !picked.isEmpty() && !carrying(); }

    private boolean overTrashTap(double mx, double my) {
        return trashTap() && Ui.hit(mx, my, trashX(), pocketY(), pocketW(), POCKET_H);
    }

    private void drawTrashTap(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!trashTap()) return;
        dropTarget(ctx, trashX(), overTrashTap(mouseX, mouseY), Theme.DANGER, Draw.TRASH, "УДАЛИТЬ",
                pickedSaid());
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
        return Math.max(Theme.topH() + 8, canvasBottom() - POCKET_H - POCKET_EDGE);
    }

    private boolean overPocket(double mx, double my) {
        return carrying() && Ui.hit(mx, my, pocketX(), pocketY(), pocketW(), POCKET_H);
    }

    private void drawPocket(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!carrying()) return;
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
            commitLift();
            endDrag();
            stash(chain, "убрано в рюкзак");
            return;
        }
        if (overTrash(mx, my)) {
            toast(dragFromPalette ? "отменено" : "удалено · " + blocksText(drag));
            commitLift();
            endDrag();
            return;
        }
        if (mx < canvasLeft()) {
            if (dragFromPalette && !dragMoved) placeAtCenter();
            else toast(dragFromPalette ? "отменено" : "блок удалён");
            commitLift();
            endDrag();
            return;
        }
        Snap s = findSnap(layout());
        if (returnsHome(s)) {
            restoreLift();
            endDrag();
            return;
        }
        if (dragFromPalette && dragSnapshot != null) pushUndo(dragSnapshot);
        commitLift();
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

    private void dropElse(Layout.Box box) {
        Script.Node node = box.node;
        String was = Layout.blockTitle(node);
        if (node.action == Catalog.ELSE) {
            deleteBlock(box);
            toast("«Иначе» убрано, тело осталось на месте");
            return;
        }
        Catalog.Action cond = Mapping.catalogAction(Mapping.action(Mapping.elseCondId(node.action)));
        if (cond == null) { toast("у «" + was + "» нет условия в каталоге"); return; }
        replaceAction(box.owner, node, cond);
        toast("«" + was + "» → «" + cond.name + "»");
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
        int base = Tabs.lineBase(script);
        List<Integer> positions = new ArrayList<>();
        for (Script.Root r : roots) {
            Integer p = at.get(r);
            if (p != null) positions.add(base + p);
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
        if (Carry.slotted(hoverBox, hoverChip))
            Clip.copySlots(Layout.chipNode(hoverBox.node).values.get(hoverChip.argIndex));
        else Clip.copyValues(have);
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
        List<Clip.Placed> layout = Carry.slotted(hoverBox, hoverChip)
                ? Clip.pasteSlots() : List.of();
        if (!layout.isEmpty()) return pasteLayout(n, layout);
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

    private boolean pasteLayout(Script.Node n, List<Clip.Placed> layout) {
        List<Clip.Placed> ok = new ArrayList<>(layout.size());
        for (Clip.Placed p : layout) if (Carry.fits(n, hoverChip.argIndex, p.value())) ok.add(p);
        if (ok.isEmpty()) {
            toast("такое значение в этот слот не кладётся");
            return true;
        }
        pushUndo();
        int put = Carry.place(hoverBox, hoverChip, ok), left = layout.size() - put;
        toast("вставлено " + put + " · слоты те же" + (left > 0 ? " · не влезло " + left : ""));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (dragPan >= 0) {
            if (click.button() != dragPan) return true;
            if (Math.abs(click.x() - dragPanFromX) >= GRAB_SLOP
                    || Math.abs(click.y() - dragPanFromY) >= GRAB_SLOP) dragPanMoved = true;
            panAnim = false;
            panX += dx;
            panY += dy;
            return true;
        }
        if (!sliding()) inputArrived();
        boolean far = Math.abs(click.x() - pressX) >= GRAB_SLOP || Math.abs(click.y() - pressY) >= GRAB_SLOP;
        if (far) {
            if (pendHot == Settings.Hot.GO_DEF && pendButton == 0 && pendBox != null) pressOne = pendBox;
            pendHot = null;
            emptyPress = false;
            rightPress = null;
            if (pressOne != null) {
                Layout.Box one = pressOne;
                pressOne = null;
                pendBox = null;
                startDragOne(one);
            }
            if (pressCard != null) {
                Layout.Box card = pressCard;
                pressCard = null;
                if (picked.covered().contains(card.node)) startMove();
                else startDrag(card);
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
            followMove(toCanvasX(click.x()), toCanvasY(click.y()));
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
        inputArrived();
        mouseCanvasX = toCanvasX(click.x());
        mouseCanvasY = toCanvasY(click.y());
        palette.barRelease();
        if (menu != null) menu.mouseReleased();
        if (blockMenu != null) blockMenu.mouseReleased();
        Overlay panel = panel();
        if (panel != null && panel == chest) {
            chest.released(click.x(), click.y());
            settle(panel);
            return true;
        }
        if (panel != null) {
            panel.mouseReleased();
            if (panel == condPicker) condPicker.released(click.x(), click.y());
            settle(panel);
            return true;
        }
        if (dragPan >= 0 && click.button() == dragPan) {
            boolean cancel = !dragPanMoved && (held != null || drag != null && dragAwaitsClick);
            dragPan = -1;
            if (cancel && held != null) cancelHeld();
            else if (cancel) cancelDrag();
            return true;
        }
        if (click.button() != 0 && (moving || banding || drag != null && !dragAwaitsClick)) return true;
        if (releaseSelecting(click)) return true;
        if (banding) { applyBand(); return true; }
        if (moving) { finishMove(click.x(), click.y()); return true; }
        if (pressOne != null) {
            Layout.Box one = pressOne;
            pressOne = null;
            picked.toggle(one);
            return true;
        }
        if (pressCard != null) {
            Layout.Box card = pressCard;
            pressCard = null;
            if (click.button() == 0) cardClicked(card, cardX, cardY, (int) pressX, (int) pressY);
            return true;
        }
        if (pendHot != null && click.button() == pendButton) {
            panning = false;
            runPending();
            return true;
        }
        if (rightPress != null && click.button() == 1) {
            Runnable action = rightPress;
            rightPress = null;
            panning = false;
            action.run();
            return true;
        }
        if (emptyPress && click.button() == 1) {
            emptyPress = false;
            panning = false;
            quickAdd(null, bodyAt(emptyX, emptyY), emptyX, emptyY, true, true);
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
        Overlay panel = panel();
        if (panel != null || menu != null || blockMenu != null || editor != null) inputArrived();
        if (panel != null) return panel.mouseScrolled(mx, my, vAmount);
        if (menu != null && menu.mouseScrolled(mx, my, vAmount)) return true;
        if (blockMenu != null) { blockMenu.mouseScrolled(mx, my, vAmount); return true; }
        if (editor != null && editor.mouseScrolled(mx, my, vAmount)) return true;
        if (finder != null && finder.mouseScrolled(mx, my, vAmount)) return true;
        if (map.hit(mx, my)) return true;
        if (mx < canvasLeft()) { palette.scrollBy(vAmount, canvasBottom()); return true; }
        if (my < Theme.topH()) return true;
        if (hoverChip != null && hoverBox != null && hoverChip.isMarker() && ctrlHeld()) {
            pushUndo();
            Layout.chipNode(hoverBox.node).cycleMarker(hoverChip.settingIndex, vAmount < 0);
            return true;
        }
        wheel(mx, my, hAmount, vAmount);
        return true;
    }

    private void wheel(double mx, double my, double across, double down) {
        Settings s = Settings.get();
        double dx = s.wheelInvert ? -across : across, dy = s.wheelInvert ? -down : down;
        if (s.wheelZooms != ctrlHeld()) {
            double k = Settings.WHEEL_ZOOM[s.wheelSpeed];
            if (dy != 0) zoomTo(zoom * (dy > 0 ? k : 1 / k), mx, my);
            return;
        }
        panAnim = false;
        double step = Settings.WHEEL_STEP[s.wheelSpeed];
        boolean sideways = shiftHeld();
        panX += (dx + (sideways ? dy : 0)) * step;
        if (!sideways) panY += dy * step;
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
        inputArrived();
        ateChar = false;
        int key = input.key();
        Overlay panel = panel();
        if (panel != null) {
            panel.keyPressed(input);
            settle(panel);
            return true;
        }
        if (menu != null) {
            Menu open = menu;
            if (open.keyPressed(input)) {
                if (open.isClosed() && menu == open) menu = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { menu = null; return true; }
            return true;
        }
        if (strip != null && strip.renaming()) return strip.keyPressed(input);
        if (blockMenu != null) {
            BlockMenu open = blockMenu;
            boolean ate = open.keyPressed(input);
            if (open.isClosed() || blockMenu != open) blockMenu = null;
            if (ate) {
                ateChar = typesChar(input);
                return true;
            }
        }
        if (editor != null) {
            editor.keyPressed(input);
            if (editor.isClosed()) finishEditor();
            return true;
        }
        if (finder != null && finder.toggleKey(input)) return true;
        Settings st = Settings.get();
        boolean modifier = Settings.isModifier(key);
        soloKey = modifier ? key : Settings.NONE;
        Settings.Hot hot = modifier ? null : st.match(key, input.modifiers());
        if (hot != null && typing(hot)) hot = null;
        if (hot != null && ((st.mods(hot) & ~Settings.SHIFT) != 0 || !typingText()) && runHotkey(hot)) {
            ateChar = typesChar(input);
            return true;
        }
        if (hot == null && key == GLFW.GLFW_KEY_DELETE && !typingText() && drag == null) {
            boolean stack = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (!picked.isEmpty()) deletePicked();
            else if (stack) deleteStackHovered();
            else deleteHovered();
            return true;
        }
        if (!modifier && typingText()) strayed = false;
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
            askExit(XeroCode.CLOSE);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        inputArrived();
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

    private static boolean typesChar(KeyEvent input) {
        int key = input.key();
        boolean printable = (key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT)
                || (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL);
        return printable && (input.modifiers()
                & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }

    private boolean typingText() {
        return (search.isFocused() && !Settings.chests()) || (finder != null && finder.focused());
    }

    private boolean typing(Settings.Hot hot) {
        if (!typingText()) return false;
        return switch (hot) {
            case COPY, CUT -> !aimsCanvas();
            case PASTE -> !Clip.ours();
            case SELECT -> true;
            default -> false;
        };
    }

    private boolean aimsCanvas() {
        if (!overCanvas || (hoverBox == null && picked.isEmpty())) return false;
        return strayed || typedSelection().isEmpty();
    }

    private String typedSelection() {
        if (finder != null && finder.focused()) return finder.highlighted();
        return search.isFocused() ? search.getHighlighted() : "";
    }

    private boolean runHotkey(Settings.Hot hot) {
        switch (hot) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE -> {
                saveScript();
                if (!script.scratch) { rememberView(); Tabs.saveAll(); }
                toast("сохранено");
            }
            case SAVE_TAB -> saveTabFile();
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
            case WRAP -> wrapHere();
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
                if (hoverBox == null || !navigable(hoverBox.node))
                    toast("наведись на вызов или объявление функции");
                else goTo(hoverBox.node);
            }
            case GO_BACK -> goBack();
            case USAGE_NEXT -> stepUsage(1);
            case USAGE_PREV -> stepUsage(-1);
            case RENAME -> renameHovered();
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
        inputArrived();
        if (ateChar) {
            ateChar = false;
            return true;
        }
        Overlay panel = panel();
        if (panel != null) { panel.charTyped(input); return true; }
        if (menu != null) { menu.charTyped(input); return true; }
        if (blockMenu != null) return true;
        if (strip != null && strip.renaming()) return strip.charTyped(input);
        if (editor != null) return editor.charTyped(input);
        if (finder != null && finder.focused()) {
            strayed = false;
            return finder.charTyped(input);
        }
        if (Settings.chests()) return false;
        if (!search.isFocused()) {
            String s = input.codepointAsString();
            if (s != null && !s.isBlank()) {
                search.setFocused(true);
                setFocused(search);
            }
        }
        if (!search.isFocused()) return false;
        strayed = false;
        return search.charTyped(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) Backstage.restore(minecraft);
        closeOverlays();
        rememberView();
        saveScript();
        if (!script.scratch) Tabs.saveAll();
        XeroCode.canvasClosed();
        super.onClose();
    }

    public void rememberView() {
        script.viewX = panX;
        script.viewY = panY;
        script.viewZoom = zoom;
    }

    public void finishEditing() {
        finishEditor();
        settleDrag();
    }

    @Override
    public void removed() {
        if (minecraft != null) Backstage.restore(minecraft);
        finishEditor();
        settleDrag();
        Audio.release();
        if (settings != null) { settings.dispose(); settings = null; }
        rememberView();
        super.removed();
    }
}
