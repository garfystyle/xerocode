package com.xerocode.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.Audio;
import com.xerocode.Backpack;
import com.xerocode.Catalog;
import com.xerocode.Clip;
import com.xerocode.Codespace;
import com.xerocode.Collab;
import com.xerocode.Finder;
import com.xerocode.Functions;
import com.xerocode.History;
import com.xerocode.Importer;
import com.xerocode.Mapping;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import com.xerocode.XeroCode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditorScreen extends Screen implements TopBar.Host {
    private final Script script;
    private final Palette palette = new Palette();
    private TopBar top;
    private TextFieldWidget search;

    private double panX = 60, panY = 50, zoom = 1.0;
    private boolean viewRestored;
    private boolean panning, draggingSearch, resizingPalette;
    private double paletteDrag;
    private double mouseCanvasX, mouseCanvasY;

    private List<Script.Node> drag;
    private double dragOffX, dragOffY;
    private boolean dragFromPalette, dragMoved, dragAwaitsClick;
    private String dragSnapshot;
    private Snap snap;

    private final Set<Script.Node> picked = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<Script.Node> coveredCache = Set.of();
    private int pickedStamp = Integer.MIN_VALUE;
    private boolean banding;
    private int bandMode, bandCount;
    private double bandX0, bandY0, bandX1, bandY1;
    private boolean panHinted;
    private boolean moving, moveShifted;
    private double moveDX, moveDY;
    private String moveSnapshot;
    private ModulePick moduleDone;
    private Screen moduleBack;

    private BackpackPanel backpack;
    private FindPanel finder;
    private final MiniMap map = new MiniMap();
    private final List<Layout.Box> found = new ArrayList<>();
    private int foundStamp = Integer.MIN_VALUE;
    private Script.Node focusNode;
    private long focusAt;
    private boolean mapDragging;
    private double panFromX, panFromY, panToX, panToY;
    private long panStart;
    private boolean panAnim;
    private final BlockView.Look look = new BlockView.Look();
    private final BlockView.Accepts accepts = this::acceptsCarry;

    private List<Value> carry;
    private boolean carryHeld;
    private String carrySnapshot;
    private Script.Root heldRoot;
    private Layout.Box pressBox;
    private Layout.Chip pressChip;
    private double pressX, pressY;

    private Menu menu;
    private BlockMenu blockMenu;
    private CatalogPicker condPicker;
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

    private boolean dirty() { return stampTaken && script.codeHash() != savedStamp; }

    public void markPublished() {
        savedStamp = script.codeHash();
        stampTaken = true;
    }

    private void saveScript() {
        script.save();
    }

    public EditorScreen(Script script) {
        super(Text.literal("XeroCode"));
        this.script = script;
        History.clear();
    }

    @Override
    public int left() { return canvasLeft(); }

    @Override
    public int width() { return width; }

    @Override
    public int blocks() { return countNodes(); }

    @Override
    public double zoom() { return zoom; }

    @Override
    public boolean empty() { return script.roots.isEmpty(); }

    @Override
    public boolean finding() { return finder != null; }

    private int canvasLeft() { return Theme.PALETTE_W; }
    private int canvasRight() { return finder == null ? width : Math.max(canvasLeft() + 40, finder.x()); }
    private double toCanvasX(double sx) { return (sx - canvasLeft() - panX) / zoom; }
    private double toCanvasY(double sy) { return (sy - Theme.TOPBAR_H - panY) / zoom; }
    private int toScreenX(double cx) { return (int) Math.round(canvasLeft() + panX + cx * zoom); }
    private int toScreenY(double cy) { return (int) Math.round(Theme.TOPBAR_H + panY + cy * zoom); }

    private int paletteLimit() {
        return Math.max(Theme.PALETTE_MIN_W,
                Math.min(Math.min(Theme.PALETTE_MAX_W, width - 200), width * 55 / 100));
    }

    private void setPaletteWidth(int w) {
        Theme.PALETTE_W = Math.max(Theme.PALETTE_MIN_W, Math.min(paletteLimit(), w));
        script.paletteW = Theme.PALETTE_W;
        Ui.width(search, Palette.searchTextW());
        search.setX(Palette.searchTextX());
        if (finder != null) finder.resize(width, height);
        palette.invalidate();
    }

    private boolean overSplitter(double mx) {
        return Math.abs(mx - Theme.PALETTE_W) <= 3;
    }

    private void drawSplitter(DrawContext ctx, int mouseX) {
        if (!resizingPalette && !overSplitter(mouseX)) return;
        Draw.rect(ctx, Theme.PALETTE_W - 2, 0, 2, height, Draw.opaque(Theme.ACCENT));
        for (int i = -1; i <= 1; i++)
            Draw.rect(ctx, Theme.PALETTE_W - 2, height / 2 + i * 6, 2, 3, Draw.opaque(0xFFFFFF));
    }

    @Override
    protected void init() {
        if (top == null) top = new TopBar(textRenderer, this);
        String text = search == null ? palette.query() : search.getText();
        if (!viewRestored) {
            viewRestored = true;
            panX = script.viewX;
            panY = script.viewY;
            zoom = snapZoom(script.viewZoom);
        }
        if (script.paletteW > 0) Theme.PALETTE_W = script.paletteW;
        Theme.PALETTE_W = Math.max(Theme.PALETTE_MIN_W,
                Math.min(paletteLimit(), Theme.PALETTE_W));
        search = Ui.field(textRenderer, Palette.searchTextX(), Palette.SEARCH_Y + 6,
                Palette.searchTextW(), 10, "поиск блока…");
        search.setDrawsBackground(false);
        search.setMaxLength(48);
        search.setEditableColor(Draw.opaque(Theme.TEXT));
        search.setChangedListener(palette::setQuery);
        search.setText(text);
        addSelectableChild(search);
        palette.invalidate();
        if (script.fitOnOpen) {
            script.fitOnOpen = false;
            fitView();
            status = "";
        }
        if (editor != null) editor.resize(width, height);
        if (finder != null) finder.resize(width, height);
        if (settings != null) settings.resize(width, height);
        if (condPicker != null) condPicker.resize(width, height);
        if (backpack != null) backpack.resize(width, height);
        menu = null;
        blockMenu = null;
        top.invalidate();
        reopenPending();
    }

    @Override
    public boolean shouldPause() { return false; }

    private void toast(String message) {
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
            finder = new FindPanel(textRenderer, script, width, height, this::jumpTo);
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
        focusNode = hit.node;
        focusAt = System.currentTimeMillis();
        Layout.Box box = boxOf(hit.node);
        if (box == null) return;
        if (zoom < 0.7)
            applyZoom(snapZoom(0.75), (canvasLeft() + canvasRight()) / 2.0,
                    (Theme.TOPBAR_H + height) / 2.0);
        centerOn(box.x + Math.min(box.w, 260) / 2.0,
                box.y + Math.min(box.totalH, 170) / 2.0, true);
    }

    private Layout.Box boxOf(Script.Node node) {
        for (Layout.Box b : layout().boxes) if (b.node == node) return b;
        return null;
    }

    private void centerOn(double cx, double cy, boolean glide) {
        double tx = (canvasLeft() + canvasRight()) / 2.0 - canvasLeft() - cx * zoom;
        double ty = (Theme.TOPBAR_H + height) / 2.0 - Theme.TOPBAR_H - cy * zoom;
        if (glide) glideTo(tx, ty);
        else { panX = tx; panY = ty; panAnim = false; }
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

    private void drawFound(DrawContext ctx, double vx0, double vy0, double vx1, double vy1) {
        int ink = focusInk();
        if (found.isEmpty() && (focusNode == null || ink == 0)) return;
        ScreenRect area = new ScreenRect(canvasLeft(), Theme.TOPBAR_H,
                width - canvasLeft(), height - Theme.TOPBAR_H);
        Draw.batch(Batch.open(ctx, area, area, 512));
        int soft = Draw.mix(Theme.ACCENT, Theme.CANVAS, 0.3f);
        for (Layout.Box b : found) {
            if (b.node == focusNode || !visible(b, vx0, vy0, vx1, vy1)) continue;
            Draw.roundOutline(ctx, b.x - 2, b.y - 2, b.w + 4, b.totalH + 4, 5,
                    Draw.argb(0xC0, soft));
        }
        Layout.Box focus = focusNode == null || ink == 0 ? null : boxOf(focusNode);
        if (focus != null && visible(focus, vx0, vy0, vx1, vy1)) {
            Draw.roundOutline(ctx, focus.x - 2, focus.y - 2, focus.w + 4, focus.totalH + 4, 5,
                    Draw.argb(ink, Theme.ACCENT));
            Draw.roundOutline(ctx, focus.x - 3, focus.y - 3, focus.w + 6, focus.totalH + 6, 6,
                    Draw.argb(ink / 3, Theme.ACCENT));
        }
        Draw.batch(null);
    }

    private boolean rides(Layout.Box box) {
        return moving && coveredCache.contains(box.node);
    }

    private void drawMoving(DrawContext ctx, ScreenRect area,
                            double vx0, double vy0, double vx1, double vy1) {
        if (!moving) return;
        int dx = (int) Math.round(moveDX), dy = (int) Math.round(moveDY);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(dx, dy);
        double sx0 = vx0 - dx, sy0 = vy0 - dy, sx1 = vx1 - dx, sy1 = vy1 - dy;
        Draw.batch(Batch.open(ctx, area, area, 512));
        for (Piece p : pieces())
            for (Layout.Box box : p.boxes())
                if (visible(box, sx0, sy0, sx1, sy1)) BlockView.shadow(ctx, box);
        Draw.batch(null);
        for (Piece p : pieces()) {
            Draw.batch(Batch.open(ctx, area, area, 512));
            for (Layout.Box box : p.boxes())
                if (visible(box, sx0, sy0, sx1, sy1)) BlockView.block(ctx, textRenderer, box, look);
            Draw.batch(null);
        }
        m.popMatrix();
    }

    private void edges(DrawContext ctx, List<int[]> segments, double dx, double dy, int argb,
                       double vx0, double vy0, double vx1, double vy1) {
        for (int[] s : segments) {
            double a = s[0] + dx, b = s[1] + dy, c = s[2] + dx, d = s[3] + dy;
            if (c < vx0 || a > vx1 || d < vy0 || b > vy1) continue;
            int x0 = toScreenX(a), y0 = toScreenY(b);
            if (s[1] == s[3]) Draw.rect(ctx, x0, y0, Math.max(1, toScreenX(c) - x0) + 1, 1, argb);
            else Draw.rect(ctx, x0, y0, 1, Math.max(1, toScreenY(d) - y0) + 1, argb);
        }
    }

    private void drawOutlines(DrawContext ctx, List<Piece> list, double dx, double dy,
                              ScreenRect area, double vx0, double vy0, double vx1, double vy1) {
        List<Piece> shown = null;
        int cap = 0;
        for (Piece p : list) {
            if (p.x() + dx + p.w() < vx0 || p.x() + dx > vx1
                    || p.y() + dy + p.h() < vy0 || p.y() + dy > vy1) continue;
            if (shown == null) shown = new ArrayList<>(list.size());
            shown.add(p);
            cap += p.edge().size() + p.halo().size();
        }
        if (shown == null) return;
        Draw.batch(Batch.open(ctx, area, area, Math.min(cap, 2048)));
        int ink = modulePick() ? Theme.ACCENT : Theme.OK;
        for (Piece p : shown) {
            edges(ctx, p.halo(), dx, dy, Draw.argb(0x40, ink), vx0, vy0, vx1, vy1);
            edges(ctx, p.edge(), dx, dy, Draw.argb(0xF0, ink), vx0, vy0, vx1, vy1);
        }
        Draw.batch(null);
    }

    private void drawBand(DrawContext ctx) {
        if (!banding) return;
        ScreenRect area = new ScreenRect(canvasLeft(), Theme.TOPBAR_H,
                width - canvasLeft(), height - Theme.TOPBAR_H);
        Draw.batch(Batch.open(ctx, area, area, 16));
        int bx = toScreenX(Math.min(bandX0, bandX1)), by = toScreenY(Math.min(bandY0, bandY1));
        int bw = toScreenX(Math.max(bandX0, bandX1)) - bx;
        int bh = toScreenY(Math.max(bandY0, bandY1)) - by;
        int band = bandMode == BAND_SUB ? Theme.DANGER : Theme.ACCENT;
        Draw.rect(ctx, bx, by, bw, bh, Draw.argb(0x26, band));
        Draw.roundOutline(ctx, bx, by, bw, bh, 2, Draw.argb(0xC0, band));
        Draw.batch(null);
    }

    private void drawMap(DrawContext ctx, int mouseX, int mouseY) {
        if (!Settings.minimap() || drag != null || script.roots.isEmpty()) { map.hide(); return; }
        map.frame(layout, layoutStamp, canvasLeft(), Theme.TOPBAR_H, canvasRight(), height);
        if (!map.shown()) return;
        map.draw(ctx, toCanvasX(canvasLeft()), toCanvasY(Theme.TOPBAR_H),
                toCanvasX(canvasRight()), toCanvasY(height),
                mapDragging || map.hit(mouseX, mouseY));
        if (!found.isEmpty()) map.marks(ctx, found, Theme.ACCENT);
        Layout.Box focus = focusNode == null ? null : boxOf(focusNode);
        if (focus != null) map.marks(ctx, List.of(focus), Theme.OK);
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

    private List<String> namesUsed(boolean parameters) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Script.Root r : script.roots) collectNames(r.chain, parameters, counts);
        List<String> out = new ArrayList<>(counts.keySet());
        out.sort((a, b) -> counts.get(b) - counts.get(a));
        return out;
    }

    private static void collectNames(List<Script.Node> chain, boolean parameters,
                                     Map<String, Integer> counts) {
        String want = parameters ? Value.PARAMETER : Value.VARIABLE;
        for (Script.Node n : chain) {
            for (List<Value> list : n.values.values())
                for (Value v : list)
                    if (want.equals(v.type) && !v.name.isBlank())
                        counts.merge(v.name, 1, Integer::sum);
            for (Value v : n.markerVars.values())
                if (want.equals(v.type) && !v.name.isBlank())
                    counts.merge(v.name, 1, Integer::sum);
            if (n.cond != null) collectNames(List.of(n.cond), parameters, counts);
            collectNames(n.body, parameters, counts);
        }
    }

    private Layout layout() {
        int fingerprint = script.fingerprint();
        int stamp = fingerprint * 31 + revision;
        if (layout == null || stamp != layoutStamp) {
            nodeCount = -1;
            Functions.rebuild(script);
            layout = Layout.of(script, textRenderer);
            layoutStamp = stamp;
            if (!stampTaken) { savedStamp = script.codeHash(); stampTaken = true; }
        }
        return layout;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.batch(null);
        SmoothText.clip(null);
        if (settings != null && settings.consumeChanged()) {
            revision++;
            top.invalidate();
            search.setEditableColor(Draw.opaque(Theme.TEXT));
        }
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        advancePan();
        mouseCanvasX = toCanvasX(mouseX);
        mouseCanvasY = toCanvasY(mouseY);
        Collab.frame(drag != null || editor != null ? heldRoot : null,
                mouseCanvasX, mouseCanvasY, zoom);

        layout = layout();
        prunePicked();
        if (moving) covered();
        syncBand();
        if (finder != null) finder.sync(layoutStamp);
        syncFound();
        updateHover(mouseX, mouseY);
        snap = drag == null ? null : findSnap(layout);

        look.hover = hoverBox;
        look.chip = hoverChip;
        look.dragging = drag != null;
        look.carrying = carry != null;
        look.accepts = accepts;
        look.mx = mouseCanvasX;
        look.my = mouseCanvasY;

        ctx.enableScissor(canvasLeft(), Theme.TOPBAR_H, width, height);
        drawGrid(ctx);
        if (script.roots.isEmpty() && drag == null) drawEmptyHint(ctx);

        var m = ctx.getMatrices();
        m.pushMatrix();
        int gs = client != null && client.getWindow() != null
                ? Math.max(1, client.getWindow().getScaleFactor()) : 1;
        double snapX = Math.round(panX * gs) / (double) gs;
        double snapY = Math.round(panY * gs) / (double) gs;
        m.translate((float) (canvasLeft() + snapX), (float) (Theme.TOPBAR_H + snapY));
        m.scale((float) zoom, (float) zoom);

        double vx0 = toCanvasX(canvasLeft()) - 8, vx1 = toCanvasX(width) + 8;
        double vy0 = toCanvasY(Theme.TOPBAR_H) - 8, vy1 = toCanvasY(height) + 8;

        ScreenRect canvasArea = new ScreenRect(canvasLeft(), Theme.TOPBAR_H,
                width - canvasLeft(), height - Theme.TOPBAR_H);

        SmoothText.clip(canvasArea);

        Draw.batch(Batch.open(ctx, canvasArea, canvasArea, 1024));
        for (Layout.Chunk chunk : layout.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = layout.boxes.get(i);
                if (!rides(box) && visible(box, vx0, vy0, vx1, vy1)) BlockView.shadow(ctx, box);
            }
        }
        Draw.batch(null);

        for (Layout.Chunk chunk : layout.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            Draw.batch(Batch.open(ctx, canvasArea, canvasArea, 512));
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = layout.boxes.get(i);
                if (!rides(box) && visible(box, vx0, vy0, vx1, vy1))
                    BlockView.block(ctx, textRenderer, box, look);
            }
            Draw.batch(null);
            if (moving || picked.isEmpty()) continue;
            List<Piece> mine = byRoot().get(layout.boxes.get(chunk.from).root);
            if (mine == null) continue;
            m.popMatrix();
            drawOutlines(ctx, mine, 0, 0, canvasArea, vx0, vy0, vx1, vy1);
            m.pushMatrix();
            m.translate((float) (canvasLeft() + snapX), (float) (Theme.TOPBAR_H + snapY));
            m.scale((float) zoom, (float) zoom);
        }

        drawFound(ctx, vx0, vy0, vx1, vy1);
        drawMoving(ctx, canvasArea, vx0, vy0, vx1, vy1);

        if (snap != null || drag != null) {
            Draw.batch(Batch.open(ctx, canvasArea, canvasArea, 512));
            if (snap != null) drawSnapMark(ctx);
            if (drag != null) drawDragged(ctx);
            Draw.batch(null);
        }

        m.popMatrix();
        if (moving) drawOutlines(ctx, pieces(), Math.round(moveDX), Math.round(moveDY),
                canvasArea, vx0, vy0, vx1, vy1);
        drawBand(ctx);
        Peers.render(ctx, textRenderer, script, layout, canvasArea, canvasLeft(), Theme.TOPBAR_H,
                panX, panY, zoom, width, height);
        ctx.disableScissor();
        SmoothText.clip(null);

        drawMap(ctx, mouseX, mouseY);
        if (finder != null) finder.render(ctx, mouseX, mouseY, delta);
        palette.render(ctx, textRenderer, mouseX, mouseY, height);
        search.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, textRenderer, search);
        top.draw(ctx, mouseX, mouseY);
        drawSplitter(ctx, mouseX);

        ctx.createNewRootLayer();
        drawPocket(ctx, mouseX, mouseY);
        drawBar(ctx, mouseX, mouseY);
        drawToast(ctx);
        if (editor != null) editor.render(ctx, mouseX, mouseY, delta);
        if (menu != null) menu.render(ctx, textRenderer, mouseX, mouseY);
        else if (blockMenu != null) blockMenu.render(ctx, mouseX, mouseY);
        else if (!exitPrompt && settings == null && backpack == null && carry == null)
            drawTooltips(ctx, mouseX, mouseY);
        drawCarry(ctx, mouseX, mouseY);
        if (exitPrompt) drawExitPrompt(ctx, mouseX, mouseY);
        if (condPicker != null) {
            ctx.createNewRootLayer();
            condPicker.render(ctx, mouseX, mouseY, delta);
        }
        if (backpack != null) {
            ctx.createNewRootLayer();
            backpack.render(ctx, mouseX, mouseY, delta);
        }
        if (settings != null) settings.render(ctx, mouseX, mouseY, delta);
    }

    private static boolean visible(Layout.Box b, double x0, double y0, double x1, double y1) {
        return b.x + b.w >= x0 && b.x <= x1 && b.bottom() >= y0 && b.y <= y1;
    }

    private void updateHover(int mouseX, int mouseY) {
        hoverBox = null;
        hoverChip = null;
        if (drag != null || moving || banding || menu != null || blockMenu != null
                || mouseX < canvasLeft() || mouseY < Theme.TOPBAR_H) return;
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
                if (b.hitGrab(mouseCanvasX, mouseCanvasY)) { hoverBox = b; return; }
            }
        }
    }

    private void drawGrid(DrawContext ctx) {
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
            Draw.rect(ctx, x, Theme.TOPBAR_H, 1, height - Theme.TOPBAR_H,
                    Math.floorMod(i, 4) == 0 ? strong : faint);
        }
        int j0 = (int) Math.floor((Theme.TOPBAR_H - oy) / step);
        for (int j = j0; ; j++) {
            int y = (int) Math.round(oy + j * step);
            if (y > height) break;
            if (y < Theme.TOPBAR_H) continue;
            Draw.rect(ctx, canvasLeft(), y, width - canvasLeft(), 1,
                    Math.floorMod(j, 4) == 0 ? strong : faint);
        }
    }

    private void drawGridDots(DrawContext ctx, double step, double ox, double oy,
                              int faint, int strong) {
        ScreenRect area = new ScreenRect(canvasLeft(), Theme.TOPBAR_H,
                width - canvasLeft(), height - Theme.TOPBAR_H);
        Draw.batch(Batch.open(ctx, area, area, 4096));
        int i0 = (int) Math.floor((canvasLeft() - ox) / step);
        int j0 = (int) Math.floor((Theme.TOPBAR_H - oy) / step);
        for (int i = i0; ; i++) {
            int x = (int) Math.round(ox + i * step);
            if (x > width) break;
            if (x < canvasLeft()) continue;
            boolean bigX = Math.floorMod(i, 4) == 0;
            for (int j = j0; ; j++) {
                int y = (int) Math.round(oy + j * step);
                if (y > height) break;
                if (y < Theme.TOPBAR_H) continue;
                boolean big = bigX && Math.floorMod(j, 4) == 0;
                int size = big ? 2 : 1;
                Draw.rect(ctx, x, y, size, size, big ? strong : faint);
            }
        }
        Draw.batch(null);
    }

    private void drawEmptyHint(DrawContext ctx) {
        int w = 250, h = 62;
        int x = canvasLeft() + (width - canvasLeft() - w) / 2, y = (height - h) / 2;
        Draw.round(ctx, x, y, w, h, 8, Draw.argb(0x50, Ui.PANEL));
        Draw.roundOutline(ctx, x, y, w, h, 8, Draw.argb(0x66, Ui.BORDER));
        Draw.textFit(ctx, textRenderer, "Полотно пустое", x + 20, y + 16, w - 40, Theme.TEXT_DIM, false);
        Draw.textFit(ctx, textRenderer, "перетащи сюда событие из палитры", x + 20, y + 30, w - 40,
                Theme.TEXT_FAINT, false);
        Draw.textFit(ctx, textRenderer, "или просто кликни по блоку в списке", x + 20, y + 42, w - 40,
                Theme.TEXT_FAINT, false);
    }

    private void drawDragged(DrawContext ctx) {
        int px = (int) Math.round(mouseCanvasX - dragOffX);
        int py = (int) Math.round(mouseCanvasY - dragOffY);
        Layout l = Layout.ofChain(drag, px, py, textRenderer);
        for (Layout.Box b : l.boxes) BlockView.shadow(ctx, b);
        for (Layout.Box b : l.boxes) BlockView.block(ctx, textRenderer, b, look);
    }

    private void drawSnapMark(DrawContext ctx) {
        Layout ghost = Layout.ofChain(drag, snap.x, snap.y, textRenderer);
        if (ghost.boxes.isEmpty()) return;
        Layout.Box b = ghost.boxes.get(0);
        Draw.blockSilhouette(ctx, b.x, b.y, b.w, b.headerH, Draw.argb(0x66, 0xC3DEFF));
    }

    private boolean choosingFile;

    private void openLoadDialog() {
        closeOverlays();
        if (choosingFile) return;
        choosingFile = true;
        Path dir = Codespace.savedDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        toast(FileDialog.hint());
        FileDialog.open("Загрузить json", dir.toAbsolutePath() + File.separator,
                new String[]{"*.json"}, "код JustMC (*.json)", file -> {
                    choosingFile = false;
                    if (file == null || (client != null && client.currentScreen != this)) return;
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
            res = Importer.importInto(script, handlers, textRenderer);
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
                    (Theme.TOPBAR_H + height) / 2.0);
            case TopBar.ZOOM_IN -> zoomTo(zoom * 1.15, (canvasLeft() + width) / 2.0,
                    (Theme.TOPBAR_H + height) / 2.0);
            case TopBar.FIT -> fitView();
            case TopBar.FIND -> toggleFinder();
            case TopBar.UPLOAD -> publish(null);
            case TopBar.LOAD -> openLoadDialog();
            case TopBar.BACKPACK -> openBackpack();
            case TopBar.MARKET -> openMarket();
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

    private static final double MIN_ZOOM = 0.25, MAX_ZOOM = 2.0;

    private double[] zoomSteps() {
        int gs = client != null && client.getWindow() != null
                ? Math.max(1, client.getWindow().getScaleFactor()) : 2;
        double base = 1.0 / gs;
        List<Double> out = new ArrayList<>();
        for (double k = 0.25; k <= gs * MAX_ZOOM + 1e-6; k += k < 1 ? 0.25 : 1) {
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
        Layout l = Layout.of(script, textRenderer);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Layout.Box b : l.boxes) {
            minX = Math.min(minX, b.x);
            minY = Math.min(minY, b.y);
            maxX = Math.max(maxX, b.x + b.w);
            maxY = Math.max(maxY, b.bottom());
        }
        int vw = Math.max(40, canvasRight() - canvasLeft() - 40);
        int vh = Math.max(40, height - Theme.TOPBAR_H - 40);
        zoom = snapZoom(Math.max(MIN_ZOOM, Math.min(1.5,
                Math.min(vw / (double) (maxX - minX), vh / (double) (maxY - minY)))));
        panX = 20 + (vw - (maxX - minX) * zoom) / 2 - minX * zoom;
        panY = 20 + (vh - (maxY - minY) * zoom) / 2 - minY * zoom;
        toast("показано всё");
    }

    private void openSettings() {
        closeOverlays();
        settings = new SettingsPanel(textRenderer, width, height);
    }

    private void closeSettings() {
        settings = null;
        revision++;
        top.invalidate();
        palette.invalidate();
    }

    private void toOriginal() {
        Settings s = Settings.get();
        s.mode = Settings.Mode.ORIGINAL;
        s.save();
        closeOverlays();
        rememberView();
        saveScript();
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        XeroCode.canvasClosed();
        mc.setScreen(null);
        mc.inGameHud.setTitleTicks(3, 50, 10);
        mc.inGameHud.setTitle(Text.literal("3D-кодинг"));
        mc.inGameHud.setSubtitle(Text.literal(
                "код — блоками в мире · " + s.label(Settings.Hot.OPEN)
                        + " — вернуться в 2D"));
    }

    private static final int EXIT_WANT_W = 344, EXIT_H = 92, EXIT_BTN_H = 22;

    private int exitW() { return Ui.fitW(width, EXIT_WANT_W); }
    private int exitX() { return Ui.midX(width, exitW()); }
    private int exitY() { return Ui.midY(height, EXIT_H); }

    private void askExit(String command) {
        closeOverlays();
        exitTo = command;
        if (!dirty()) { leaveTo(command); return; }
        exitPrompt = true;
    }

    private void drawExitPrompt(DrawContext ctx, int mouseX, int mouseY) {
        Ui.dim(ctx, width, height);
        int x = exitX(), y = exitY();
        Ui.panel(ctx, x, y, exitW(), EXIT_H);
        Ui.headerStrip(ctx, x, y, exitW(), 26, Theme.ACCENT);
        Draw.textFit(ctx, textRenderer, "СОХРАНИТЬ?", x + 14, y + (26 - Ui.TEXT_H) / 2 + 1,
                exitW() - 28, Theme.TEXT, false);
        Ui.hairline(ctx, x + 1, y + 26, exitW() - 2);

        int cx = x + 14, cw = exitW() - 28, cy = y + 26 + 14;
        Draw.textFit(ctx, textRenderer, script.roots.isEmpty()
                        ? "Полотно пусто — очистить код в мире?"
                        : "Сохранить код в блоках JustMC?",
                cx, cy, cw, Theme.TEXT, false);

        int bx = cx + cw;
        for (int i = EXIT_BUTTONS.length - 1; i >= 0; i--) {
            int bw = exitBtnW(i);
            bx -= bw;
            Ui.button(ctx, textRenderer, mouseX, mouseY, bx, y + EXIT_H - 14 - EXIT_BTN_H, bw,
                    EXIT_BTN_H, EXIT_BUTTONS[i], i == EXIT_SAVE ? Ui.ACCENT : Ui.GHOST);
            bx -= 6;
        }
    }

    private static final String[] EXIT_BUTTONS = {"Отмена", "Не сохранять", "Сохранить"};
    private static final int EXIT_CANCEL = 0, EXIT_LEAVE = 1, EXIT_SAVE = 2;

    private int exitBtnW(int i) {
        return Math.max(i == EXIT_CANCEL ? 70 : 80, Ui.buttonW(textRenderer, EXIT_BUTTONS[i]));
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
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        XeroCode.canvasClosed();
        if (XeroCode.RESTART.equals(command)) {
            XeroCode.restart();
            return;
        }
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(command);
        XeroCode.cover("play".equals(command) ? "Запуск мира…" : "Режим строительства…", null);
    }

    private void publish(String exitCommand) {
        closeOverlays();
        rememberView();
        saveScript();
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        mc.setScreen(new ExportScreen(script, this, exitCommand));
    }

    private void drawToast(DrawContext ctx) {
        if (status.isEmpty()) return;
        long age = System.currentTimeMillis() - statusAt;
        if (age > 2600) { status = ""; return; }
        int a = age > 2100 ? (int) (255 * (1 - (age - 2100) / 500.0)) : 255;
        a = Math.max(0, Math.min(255, a));
        int w = textRenderer.getWidth(status) + 24;
        int x = canvasLeft() + (width - canvasLeft() - w) / 2;
        int y = height - 34 - (barShown() ? BAR_H + 6 : 0);
        Draw.round(ctx, x, y, w, 20, 6, Draw.argb(a * 0xE0 / 255, Ui.HEAD));
        Draw.roundOutline(ctx, x, y, w, 20, 6, Draw.argb(a * 0x80 / 255, Ui.BORDER));
        ctx.drawText(textRenderer, status, x + 12, y + 6, Draw.argb(a, Theme.TEXT), false);
    }

    private void drawTooltips(DrawContext ctx, int mouseX, int mouseY) {
        if (editor != null && editor.contains(mouseX, mouseY)) return;

        if (mouseY < Theme.TOPBAR_H && mouseX >= canvasLeft()) {
            top.tooltip(ctx, mouseX, mouseY);
            return;
        }
        if (mouseX < canvasLeft()) {
            Palette.Entry e = palette.entryAt(mouseX, mouseY, height);
            if (e == null) return;
            if (e.action != null) actionTooltip(ctx, e.action, mouseX, mouseY);
            else if (e.category != null) {
                List<Text> lines = new ArrayList<>();
                lines.add(Text.literal(e.category.name));
                lines.add(Text.literal("§8" + e.category.count() + " действий"));
                ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
            }
            return;
        }
        if (hoverChip != null && hoverBox != null) { chipTooltip(ctx, mouseX, mouseY); return; }
        if (hoverBox != null)
            actionTooltip(ctx, hoverBox.node.action, hoverBox.node, mouseX, mouseY);
    }

    private void chipTooltip(DrawContext ctx, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        if (hoverChip.isPlus()) {
            lines.add(Text.literal("Добавить параметр"));
            lines.add(Text.literal("§8станет строчной переменной с этим именем"));
        } else if (hoverChip.isCell()) {
            Value p = hoverChip.value;
            lines.add(Text.literal(p == null || p.name.isBlank() ? "параметр без имени" : p.name));
            if (p != null) {
                lines.add(Text.literal("§8принимает: §7" + p.paramNote()));
                if (Value.ENUM.equals(p.typeKey)) {
                    StringBuilder sb = new StringBuilder();
                    for (Value.Elem e : p.elements) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(e.name);
                    }
                    if (sb.length() > 0) lines.add(Text.literal("§8варианты: §7" + sb));
                }
            }
            lines.add(Text.literal("§8ЛКМ — правка, ПКМ — удалить"));
            if (!chipValues(hoverBox, hoverChip).isEmpty())
                lines.add(Text.literal("§8" + copyHint()));
        } else if (hoverChip.isCondition()) {
            Script.Node cond = hoverBox.node.cond;
            lines.add(Text.literal(cond == null ? "Условие не выбрано" : cond.action.name));
            if (cond == null) {
                lines.add(Text.literal("§8блок работает по вложенному условию"));
            } else {
                lines.add(Text.literal("§8" + (cond.action.category == null
                        ? "" : cond.action.category.name)
                        + (cond.inverted() ? " §8· §7НЕ" : "")));
                for (String line : describe(cond.action.description))
                    lines.add(Text.literal("§7" + line));
            }
            lines.add(Text.literal("§8ЛКМ — выбрать условие, ПКМ — НЕ"));
        } else if (hoverChip.isArg()) {
            Catalog.Arg arg = Layout.chipNode(hoverBox.node).args().get(hoverChip.argIndex);
            lines.add(Text.literal(arg.purpose));
            lines.add(Text.literal("§8слот: §7" + arg.type
                    + (arg.list ? " §8· до " + arg.capacity + " значений" : "")));
            Value v = hoverChip.value;
            if (v != null) {
                lines.add(Text.literal("§8значение: §7" + Values.kindName(v.type)
                        + (v.note().isEmpty() ? "" : " §8· " + v.note())));
                if (Value.GAME_VALUE.equals(v.type)) {
                    Values.GameValue g = Values.gameValue(v.gameValue);
                    if (g != null) {
                        for (String line : describe(g.description)) lines.add(Text.literal("§7" + line));
                        if (!g.returns.isEmpty())
                            lines.add(Text.literal("§8возвращает: §7" + g.returns
                                    + (g.returnsNote.isEmpty() ? "" : " §8— " + g.returnsNote)));
                    }
                }
            }
            lines.add(Text.literal("§8ЛКМ — значение, ПКМ — очистить"));
            if (!chipValues(hoverBox, hoverChip).isEmpty())
                lines.add(Text.literal("§8" + copyHint()));
        } else {
            Script.Node owner = Layout.chipNode(hoverBox.node);
            Catalog.Setting s = owner.settings().get(hoverChip.settingIndex);
            lines.add(Text.literal(s.label));
            Value bound = owner.markerVar(hoverChip.settingIndex);
            if (bound != null && !bound.name.isBlank()) {
                Values.Scope sc = Values.scope(bound.scope);
                lines.add(Text.literal("§8привязана к переменной: §7" + bound.name
                        + (sc == null ? "" : " §8· " + sc.name())));
                lines.add(Text.literal("§8по умолчанию: §7"
                        + owner.marker(hoverChip.settingIndex)));
            } else {
                lines.add(Text.literal("§8" + s.options.size() + " вариантов"));
            }
        }
        ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private static final int TOOLTIP_W = 260;

    private int tooltipW() { return Math.max(120, Math.min(TOOLTIP_W, width - 40)); }

    private List<String> describe(String description) {
        List<String> out = new ArrayList<>();
        if (description == null || description.isBlank()) return out;
        int room = tooltipW();
        for (String part : description.split("(?=»)")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : piece.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textRenderer.getWidth(next) > room) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }

    private void actionTooltip(DrawContext ctx, Catalog.Action a, int mouseX, int mouseY) {
        actionTooltip(ctx, a, null, mouseX, mouseY);
    }

    private void actionTooltip(DrawContext ctx, Catalog.Action a, Script.Node node,
                               int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(a.name));
        if (a.category != null)
            lines.add(Text.literal("§8" + a.category.name
                    + (a.subcategory == null ? "" : " / " + a.subcategory)));
        for (String line : describe(a.description)) lines.add(Text.literal("§7" + line));
        if (a.unavailable) lines.add(Text.literal("§eНедоступно в этом мире"));
        if (!a.args.isEmpty()) {
            lines.add(Text.literal("§fАргументы:"));
            for (Catalog.Arg g : a.args)
                lines.add(Text.literal("§8• §7" + g.purpose + " §8— " + g.type
                        + (g.list ? " (до " + g.capacity + ")" : "")));
        }
        if (!a.settings.isEmpty()) {
            lines.add(Text.literal("§fНастройки:"));
            for (Catalog.Setting s : a.settings)
                lines.add(Text.literal("§8• §7" + s.label + " §8= " + s.def));
        }
        if (node != null && node.cond != null) {
            lines.add(Text.literal("§fУсловие:"));
            lines.add(Text.literal("§8• §7" + (node.cond.inverted() ? "НЕ " : "")
                    + node.cond.action.name));
            for (Catalog.Arg g : node.cond.action.args)
                lines.add(Text.literal("§8• §7" + g.purpose + " §8— " + g.type));
        } else if (node != null && Mapping.hasConditional(a)) {
            lines.add(Text.literal("§eУсловие не выбрано"));
        }
        if (node != null && (node.declares() || node.invokes())) functionTooltip(lines, node);
        if (node != null) {
            int target = node.settingIndex(Catalog.TARGET);
            int invert = node.settingIndex(Catalog.INVERT);
            if (target >= 0)
                lines.add(Text.literal("§fЦель: §7" + node.marker(target)));
            if (invert >= 0 && Catalog.INVERT_ON.equals(node.marker(invert)))
                lines.add(Text.literal("§fУсловие: §cНЕ выполнено"));
            if (target >= 0 || invert >= 0)
                lines.add(Text.literal("§8ПКМ — "
                        + (target >= 0 ? "цель" : "") + (target >= 0 && invert >= 0 ? " и " : "")
                        + (invert >= 0 ? "«НЕ»" : "")));
        }
        ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private void functionTooltip(List<Text> lines, Script.Node node) {
        boolean declares = node.declares();
        String name = declares ? Functions.nameOf(node) : Functions.targetOf(node);
        Script.Node decl = node;
        if (!declares) {
            Functions.Known known = Functions.of(script);
            Functions.Signature s = (node.isStart() ? known.processes() : known.functions()).get(name);
            if (s == null) {
                lines.add(Text.literal(name.isBlank()
                        ? "§eФункция не выбрана"
                        : "§eНа полотне нет " + (node.isStart() ? "процесса" : "функции") + " «"
                                + name + "»"));
                return;
            }
            decl = s.declaration();
            String shown = Layout.titleOf(s);
            lines.add(Text.literal("§f" + shown));
            if (!shown.equals(name)) lines.add(Text.literal("§8" + name));
        }
        if (declares) {
            Value display = Functions.displayOf(decl);
            if (display != null)
                lines.add(Text.literal("§8отображается как §7"
                        + McText.plain(display.text, display.parsing).trim()));
        }
        List<Value> params = Functions.parametersOf(decl);
        if (params.isEmpty()) {
            lines.add(Text.literal("§8без параметров"));
        } else {
            lines.add(Text.literal("§fПараметры:"));
            for (Value p : params)
                lines.add(Text.literal("§8• §7" + (p.name.isBlank() ? "без имени" : p.name)
                        + " §8— " + p.paramNote()));
        }
        List<String> desc = Layout.descAll(decl);
        if (!desc.isEmpty()) {
            lines.add(Text.literal("§fОписание:"));
            for (String d : desc) lines.add(Text.literal("§7" + d));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();

        if (condPicker != null) {
            condPicker.mouseClicked(click, doubled);
            if (condPicker.isClosed()) condPicker = null;
            return true;
        }
        if (backpack != null) {
            BackpackPanel panel = backpack;
            panel.mouseClicked(click, doubled);
            if (panel.isClosed() || backpack != panel) backpack = null;
            return true;
        }
        if (settings != null) {
            settings.mouseClicked(click, doubled);
            if (settings.isClosed()) closeSettings();
            return true;
        }
        if (exitPrompt) return exitPromptClicked(mx, my);
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
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
        if (drag != null && dragAwaitsClick) {
            if (button == 0 && mx >= canvasLeft() && my >= Theme.TOPBAR_H) {
                finishDrag(mx, my);
            } else {
                drag = null;
                snap = null;
                dragSnapshot = null;
                dragAwaitsClick = false;
                toast("отменено");
            }
            return true;
        }
        if (drag == null && carry == null && barClicked(mx, my)) return true;
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
        return canvasClicked(mx, my, button, click.modifiers());
    }

    private boolean paletteClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        search.setFocused(false);
        if (finder != null) finder.blur();
        if (my < Palette.HEADER_H) {
            if (Palette.hitSearchClear(mx, my) && !search.getText().isEmpty()) {
                search.setText("");
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
            if (!search.getText().isEmpty()) search.setText("");
            else palette.openCategory(-1);
            return true;
        }
        Palette.Entry e = palette.entryAt(mx, my, height);
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

    private boolean canvasClicked(double mx, double my, int button, int mods) {
        search.setFocused(false);
        if (carry != null) {
            if (button == 0) dropCarry(); else cancelCarry();
            return true;
        }
        boolean add = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean sub = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
        if (button == 0 && (add || sub)) {
            Layout.Box box = grabAt();
            if (box != null) {
                if (sub && box.root != null) { picked.remove(box.root.id); pickedChanged(); }
                else togglePicked(box);
                return true;
            }
            startBand(sub ? BAND_SUB : BAND_ADD);
            return true;
        }
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box box = l.boxes.get(i);
            if (!box.contains(mouseCanvasX, mouseCanvasY)) continue;
            Layout.Chip chip = button <= 1 ? box.chipAt(mouseCanvasX, mouseCanvasY) : null;
            if (chip != null) {
                if (button == 0 && !chipValues(box, chip).isEmpty()) {
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
            if (button == 0 && box.hitTarget(mouseCanvasX, mouseCanvasY)) {
                chooseTarget(box.node, box.targetSetting, (int) mx, (int) my);
                return true;
            }
            if (button == 0 && box.card != null && cardClicked(box, (int) mx, (int) my)) return true;
            if (button != 0) break;
            if (covered().contains(box.node)) startMove();
            else startDrag(box);
            return true;
        }
        if (button == 0 && !spaceHeld()) { startBand(BAND_NEW); return true; }
        panning = true;
        return true;
    }

    private boolean spaceHeld() {
        return client != null && client.getWindow() != null
                && InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_SPACE);
    }

    private void startBand(int mode) {
        closeOverlays();
        bandMode = mode;
        if (mode == BAND_NEW) clearPicked();
        banding = true;
        bandCount = picked.size();
        bandX0 = bandX1 = mouseCanvasX;
        bandY0 = bandY1 = mouseCanvasY;
        if (panHinted) return;
        panHinted = true;
        toast("рамка выделяет · полотно двигают правой кнопкой, средней или пробелом");
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
        pickedChanged();
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
                textRenderer, s.label, items, current, i -> {
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
                textRenderer, toScreenX(chip.x), toScreenY(chip.y + Layout.CHIP_H) + 2,
                width, height, namesUsed(false), namesUsed(true),
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
        condPicker = new CatalogPicker(textRenderer, width, height, "Условие блока",
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

        BlockMenu m = new BlockMenu(width, height, mx, my, textRenderer, icon, title, subtitle,
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

        m.gap();
        m.row(Draw.COPY, tail ? "Копировать стопку" : "Копировать",
                Settings.Hot.COPY, () -> copyBox(box, true));
        if (tail) m.row(Draw.COPY, "Копировать блок",
                Settings.Hot.COPY_ONE, () -> copyBox(box, false));
        m.row(Draw.CUT, tail ? "Вырезать стопку" : "Вырезать",
                Settings.Hot.CUT, () -> cutBox(box));
        m.row(Draw.PASTE, "Вставить под блок", Settings.Hot.PASTE,
                () -> pasteAt(box)).on(Clip.has());
        m.row(Draw.DUPLICATE, tail ? "Дублировать стопку" : "Дублировать",
                Settings.Hot.DUPLICATE, () -> duplicate(box, true));
        if (tail) m.row(Draw.DUPLICATE, "Дублировать блок",
                Settings.Hot.DUP_ONE, () -> duplicate(box, false));

        m.gap();
        m.row(Draw.PACK, tail ? "Стопку в рюкзак" : "В рюкзак",
                Settings.Hot.STASH, () -> stashBlock(box, true));
        if (tail) m.row(Draw.PACK, "Блок в рюкзак", null, () -> stashBlock(box, false));
        m.row(Draw.SELECT, covered().contains(box.node) ? "Убрать из выделения" : "Выделить",
                null, () -> togglePicked(box));
        if (!picked.isEmpty()) {
            String said = "  ·  " + Ui.plural(pieces().size(), "кусок", "куска", "кусков");
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
        ValueEditor open = new ValueEditor(node, argIndex, textRenderer, sx, sy, width, height,
                namesUsed(false), namesUsed(true));
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
        menu = Menu.picker(width, height, mx, my, textRenderer, title, items, current, i -> {
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
        menu = Menu.options(width, height, mx, my, textRenderer, s.label, s.options, current,
                i -> {
                    pushUndo();
                    node.markers.put(settingIndex, s.options.get(i));
                });
    }

    private void startDrag(Layout.Box box) {
        closeOverlays();
        heldRoot = box.root;
        pushUndo();
        drag = new ArrayList<>(box.owner.subList(box.index, box.owner.size()));
        box.owner.subList(box.index, box.owner.size()).clear();
        if (box.root != null && box.root.chain.isEmpty()) script.roots.remove(box.root);
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
        int n = Script.blocks(chain);
        String word = n % 10 == 1 && n % 100 != 11 ? "блок"
                : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? "блока" : "блоков";
        return n + " " + word;
    }

    private void duplicate(Layout.Box box, boolean wholeStack) {
        pushUndo();
        Script.Root r = new Script.Root(box.x + 24, box.y + 24);
        r.chain.addAll(chainOf(box, wholeStack));
        script.roots.add(r);
        toast(wholeStack ? "стопка продублирована" : "блок продублирован");
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
        if (box.root != null && box.root.chain.isEmpty()) script.roots.remove(box.root);
        toast("вырезано · " + blocksText(chain));
    }

    private void copyHovered(boolean wholeStack) {
        if (drag != null || carry != null) return;
        if (hoverBox == null) { toast("наведись на блок — скопирую его"); return; }
        copyBox(hoverBox, wholeStack);
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
        holdChain(chain, "вставлено · " + blocksText(chain) + " — клик по полотну поставит");
    }

    public void openMarket() {
        closeOverlays();
        rememberView();
        saveScript();
        XeroCode.canvasClosed();
        client.setScreen(new MarketScreen(script, this));
    }

    private void openBackpack() {
        closeOverlays();
        backpack = new BackpackPanel(textRenderer, width, height, this::takeFromBackpack);
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
        holdChain(chain, "«" + item.name + "» на курсоре — клик по полотну поставит");
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

    private static final int BAND_NEW = 0, BAND_ADD = 1, BAND_SUB = 2;

    private record Piece(Layout.Box head, Set<Script.Node> nodes, List<Layout.Box> boxes,
                         int x, int y, int w, int h, List<int[]> edge, List<int[]> halo) {}

    private List<Piece> pieceCache = List.of();
    private Map<Script.Root, List<Piece>> byRootCache = Map.of();
    private int pieceStamp = Integer.MIN_VALUE;
    private int blocksCache;
    private String saidCache = "";

    private Map<Script.Root, List<Piece>> byRoot() {
        pieces();
        return byRootCache;
    }

    private void pickedChanged() {
        pieceStamp = Integer.MIN_VALUE;
        pickedStamp = layoutStamp;
    }

    private void clearPicked() {
        if (picked.isEmpty()) return;
        picked.clear();
        pickedChanged();
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
        picked.removeIf(tail::contains);
        picked.add(box.node);
        pickedChanged();
    }

    private void unpick(Layout.Box box) {
        if (box == null || !covered().contains(box.node)) return;
        for (Piece p : pieces()) {
            if (!p.nodes().contains(box.node)) continue;
            picked.remove(p.head().node);
            pickedChanged();
            return;
        }
    }

    private void togglePicked(Layout.Box box) {
        if (box == null) return;
        if (covered().contains(box.node)) unpick(box); else pick(box);
    }

    private void prunePicked() {
        if (pickedStamp == layoutStamp) return;
        pickedStamp = layoutStamp;
        pieceStamp = Integer.MIN_VALUE;
        if (picked.isEmpty()) return;
        Set<Script.Node> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Layout.Box b : layout().boxes) live.add(b.node);
        picked.removeIf(n -> !live.contains(n));
    }

    private Set<Script.Node> covered() {
        pieces();
        return coveredCache;
    }

    private List<Piece> pieces() {
        Layout l = layout();
        if (pieceStamp == layoutStamp) return pieceCache;
        pieceStamp = layoutStamp;
        pieceCache = List.of();
        byRootCache = Map.of();
        coveredCache = Set.of();
        blocksCache = 0;
        saidCache = "";
        if (picked.isEmpty()) return pieceCache;
        List<Layout.Box> heads = new ArrayList<>(picked.size());
        for (Layout.Box b : l.boxes) if (picked.contains(b.node)) heads.add(b);
        Set<Script.Node> all = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Script.Node, Integer> owner = new IdentityHashMap<>();
        List<Set<Script.Node>> tails = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) {
            Layout.Box head = heads.get(i);
            Set<Script.Node> tail = Collections.newSetFromMap(new IdentityHashMap<>());
            tailNodes(head.owner, head.index, tail);
            tails.add(tail);
            all.addAll(tail);
            for (Script.Node n : tail) owner.put(n, i);
        }
        int[][] bounds = new int[heads.size()][];
        List<List<int[]>> rects = new ArrayList<>(heads.size());
        List<List<Layout.Box>> mine = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) {
            rects.add(new ArrayList<>());
            mine.add(new ArrayList<>());
            bounds[i] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE};
        }
        for (Layout.Box b : l.boxes) {
            Integer at = owner.get(b.node);
            if (at == null) continue;
            shapeOf(b, rects.get(at));
            mine.get(at).add(b);
            int[] r = bounds[at];
            r[0] = Math.min(r[0], b.x);
            r[1] = Math.min(r[1], b.y);
            r[2] = Math.max(r[2], b.x + b.w);
            r[3] = Math.max(r[3], b.bottom());
        }
        List<Piece> out = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) {
            if (rects.get(i).isEmpty()) continue;
            int[] r = bounds[i];
            out.add(new Piece(heads.get(i), tails.get(i), mine.get(i),
                    r[0], r[1], r[2] - r[0], r[3] - r[1],
                    Outline.of(rects.get(i), 3), Outline.of(rects.get(i), 5)));
        }
        out.sort((a, b) -> a.y() != b.y() ? a.y() - b.y() : a.x() - b.x());
        Map<Script.Root, List<Piece>> owners = new IdentityHashMap<>();
        for (Piece p : out)
            owners.computeIfAbsent(p.head().root, k -> new ArrayList<>()).add(p);
        pieceCache = out;
        byRootCache = owners;
        coveredCache = all;
        blocksCache = 0;
        for (Piece p : out) blocksCache += Script.blocks(chainOfPiece(p));
        saidCache = Ui.plural(out.size(), "кусок", "куска", "кусков")
                + " · " + Ui.plural(blocksCache, "блок", "блока", "блоков");
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

    private int pickedBlocks() {
        pieces();
        return blocksCache;
    }

    private static List<Script.Node> chainOfPiece(Piece p) {
        Layout.Box head = p.head();
        return head.owner.subList(head.index, head.owner.size());
    }

    private String pickedSaid() {
        pieces();
        return saidCache;
    }

    private void selectAll() {
        if (script.roots.isEmpty()) { toast("полотно пустое"); return; }
        closeOverlays();
        picked.clear();
        for (Script.Root r : script.roots) if (!r.chain.isEmpty()) picked.add(r.chain.get(0));
        pickedChanged();
        toast("выделено " + pickedSaid());
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

    private List<Script.Root> pickedAsRoots() {
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

    private void cutOut() {
        for (Piece p : pieces()) {
            Layout.Box head = p.head();
            head.owner.subList(head.index, head.owner.size()).clear();
        }
        script.roots.removeIf(r -> r.chain.isEmpty());
    }

    private void deletePicked() {
        if (picked.isEmpty()) return;
        String said = pickedSaid();
        pushUndo();
        cutOut();
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
        cutOut();
        clearPicked();
        hoverBox = null;
        toast("вырезано " + said);
    }

    private void duplicatePicked() {
        List<Script.Root> copies = pickedAsRoots();
        if (copies.isEmpty()) return;
        pushUndo();
        picked.clear();
        for (Script.Root r : copies) {
            r.x += 24;
            r.y += 24;
            script.roots.add(r);
            picked.add(r.chain.get(0));
        }
        pickedChanged();
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
                && mouseCanvasY >= toCanvasY(Theme.TOPBAR_H) && mouseCanvasY <= toCanvasY(height);
        double dx, dy;
        if (over) {
            dx = Math.round(mouseCanvasX - x0);
            dy = Math.round(mouseCanvasY - y0);
        } else {
            double cx = toCanvasX((canvasLeft() + canvasRight()) / 2.0);
            double cy = toCanvasY((Theme.TOPBAR_H + height) / 2.0);
            dx = Math.round(cx - (x0 + x1) / 2 - 70);
            dy = Math.round(cy - (y0 + y1) / 2 - 40);
        }
        pushUndo();
        picked.clear();
        for (Script.Root r : roots) {
            r.x += dx;
            r.y += dy;
            script.roots.add(r);
            picked.add(r.chain.get(0));
        }
        pickedChanged();
        toast(note == null ? "вставлено " + pickedSaid() : note);
    }

    private List<Layout.Box> bandHits() {
        double x0 = Math.min(bandX0, bandX1), x1 = Math.max(bandX0, bandX1);
        double y0 = Math.min(bandY0, bandY1), y1 = Math.max(bandY0, bandY1);
        if (x1 - x0 < 3 && y1 - y0 < 3) return List.of();
        List<Layout.Box> out = new ArrayList<>();
        Set<Script.Node> taken = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Layout.Box b : layout().boxes) {
            if (taken.contains(b.node)) continue;
            if (b.x + b.w < x0 || b.x > x1 || b.bottom() < y0 || b.y > y1) continue;
            out.add(b);
            tailNodes(b.owner, b.index, taken);
        }
        return out;
    }

    private void syncBand() {
        if (!banding) { bandCount = 0; return; }
        List<Layout.Box> hit = bandHits();
        if (bandMode == BAND_SUB) {
            int gone = 0;
            for (Piece p : pieces())
                for (Layout.Box b : hit)
                    if (p.nodes().contains(b.node)) { gone++; break; }
            bandCount = pieces().size() - gone;
            return;
        }
        int add = 0;
        Set<Script.Node> seen = covered();
        for (Layout.Box b : hit) if (!seen.contains(b.node)) add++;
        bandCount = pieces().size() + add;
    }

    private void applyBand() {
        banding = false;
        List<Layout.Box> hit = bandHits();
        if (hit.isEmpty()) return;
        Set<Script.Node> was = covered();
        if (bandMode == BAND_SUB) {
            for (Piece p : pieces())
                for (Layout.Box b : hit)
                    if (p.nodes().contains(b.node)) { picked.remove(p.head().node); break; }
        } else {
            Set<Script.Node> tails = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Layout.Box b : hit) tailNodes(b.owner, b.index, tails);
            picked.removeIf(tails::contains);
            for (Layout.Box b : hit) if (!was.contains(b.node)) picked.add(b.node);
        }
        pickedChanged();
        toast(picked.isEmpty() ? "выделение снято" : "выделено " + pickedSaid());
    }

    public interface ModulePick { void apply(List<Script.Root> roots); }

    public void pickForModule(Screen back, ModulePick done) {
        closeOverlays();
        drag = null;
        snap = null;
        carry = null;
        carryHeld = false;
        banding = false;
        cancelMove();
        moduleBack = back;
        moduleDone = done;
        clearPicked();
        toast("выдели код для модуля: обведи рамкой или Shift+клик по блоку");
    }

    private boolean modulePick() { return moduleDone != null; }

    private void finishModulePick(boolean take) {
        ModulePick done = moduleDone;
        Screen back = moduleBack;
        moduleDone = null;
        moduleBack = null;
        List<Script.Root> out = new ArrayList<>();
        if (take) out.addAll(pickedAsRoots());
        clearPicked();
        if (take && done != null) done.apply(out);
        if (back == null) return;
        closeOverlays();
        rememberView();
        saveScript();
        XeroCode.canvasClosed();
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        mc.setScreen(back);
    }

    private int barStamp = Integer.MIN_VALUE, barWidth;
    private int compactStamp = Integer.MIN_VALUE;
    private boolean compact;

    private static final int BAR_H = 26;
    private static final String[] BAR_PICKED = {"Копировать", "В рюкзак", "Удалить", "Снять"};
    private static final String[] BAR_MODULE = {"Готово", "Отмена"};

    private boolean barShown() {
        return drag == null && (modulePick() || banding || !picked.isEmpty());
    }

    private String[] barActs() { return modulePick() ? BAR_MODULE : BAR_PICKED; }

    private boolean barOn(int i) {
        if (modulePick()) return i != 0 || !picked.isEmpty();
        return !picked.isEmpty();
    }

    private String barText() {
        if (banding)
            return (bandMode == BAND_SUB ? "снимаю рамкой · останется " : "рамка · ")
                    + Ui.plural(bandCount, "кусок", "куска", "кусков");
        if (modulePick() && picked.isEmpty())
            return "выдели код для модуля: рамка мышью или Shift+клик";
        return (modulePick() ? "в модуль: " : "выделено: ") + pickedSaid();
    }

    private static final String[][] ICONS_PICKED =
            {Draw.COPY, Draw.PACK, Draw.TRASH, Draw.CROSS};
    private static final String[][] ICONS_MODULE = {Draw.CHECK, Draw.CROSS};

    private int barRoom() { return canvasRight() - canvasLeft() - 20; }

    private boolean barCompact() {
        String said = barText();
        int stamp = said.hashCode() * 31 + barRoom() * 7 + barActs().length;
        if (stamp == compactStamp) return compact;
        int w = 26 + textRenderer.getWidth(said);
        for (String s : barActs()) w += Ui.buttonW(textRenderer, s) + 6;
        compactStamp = stamp;
        compact = w > barRoom();
        return compact;
    }

    private int barBtnW(int i) {
        return barCompact() ? 16 : Ui.buttonW(textRenderer, barActs()[i]);
    }

    private int barW() {
        String said = barText();
        int stamp = said.hashCode() * 31 + barRoom() * 7 + barActs().length;
        if (stamp == barStamp) return barWidth;
        int w = 26 + textRenderer.getWidth(said);
        for (int i = 0; i < barActs().length; i++) w += barBtnW(i) + 6;
        barStamp = stamp;
        barWidth = Math.min(w, Math.max(120, barRoom()));
        return barWidth;
    }

    private int barX() { return canvasLeft() + (canvasRight() - canvasLeft() - barW()) / 2; }

    private int barY() { return Math.max(Theme.TOPBAR_H + 8, height - 12 - BAR_H); }

    private int barBtnX(int i) {
        int right = barX() + barW() - 8;
        for (int k = barActs().length - 1; k > i; k--) right -= barBtnW(k) + 6;
        return right - barBtnW(i);
    }

    private void drawBar(DrawContext ctx, int mouseX, int mouseY) {
        if (!barShown()) return;
        int bw = barW(), bx = barX(), by = barY(), by2 = by + (BAR_H - 16) / 2;
        boolean module = modulePick(), small = barCompact();
        Draw.shadow(ctx, bx, by, bw, BAR_H, Ui.R);
        Draw.card(ctx, bx, by, bw, BAR_H, Ui.R, Draw.argb(0xF2, Ui.PANEL),
                Draw.opaque(module ? Theme.ACCENT : Theme.OK));
        String[] acts = barActs();
        String[][] icons = module ? ICONS_MODULE : ICONS_PICKED;
        for (int i = 0; i < acts.length; i++) {
            if (small) Ui.iconButton(ctx, mouseX, mouseY, barBtnX(i), by2, 16, icons[i],
                    barKind(i), barOn(i));
            else Ui.button(ctx, textRenderer, mouseX, mouseY, barBtnX(i), by2, barBtnW(i), 16,
                    acts[i], barKind(i), barOn(i));
        }
        Draw.textFit(ctx, textRenderer, barText(), bx + 10, by + (BAR_H - Ui.TEXT_H) / 2,
                barBtnX(0) - bx - 16, Theme.TEXT_DIM, false);
    }

    private int barKind(int i) {
        if (modulePick()) return i == 0 ? Ui.ACCENT : Ui.GHOST;
        return i == 1 ? Ui.ACCENT : i == 2 ? Ui.DANGER : Ui.GHOST;
    }

    private boolean barClicked(double mx, double my) {
        if (!barShown()) return false;
        if (!Ui.hit(mx, my, barX(), barY(), barW(), BAR_H)) return false;
        int by2 = barY() + (BAR_H - 16) / 2;
        for (int i = 0; i < barActs().length; i++) {
            if (!Ui.hit(mx, my, barBtnX(i), by2, barBtnW(i), 16)) continue;
            if (barOn(i)) barAct(i);
            return true;
        }
        return true;
    }

    private void barAct(int i) {
        if (modulePick()) { finishModulePick(i == 0); return; }
        switch (i) {
            case 0 -> copyPicked();
            case 1 -> stashPicked();
            case 2 -> deletePicked();
            default -> clearPicked();
        }
    }

    private static final int POCKET_W = 152, POCKET_H = 34, POCKET_EDGE = 10;

    private int pocketW() {
        return Math.min(POCKET_W, Math.max(36, canvasRight() - canvasLeft() - 40));
    }

    private int pocketX() { return canvasRight() - pocketW() - POCKET_EDGE; }

    private int pocketY() {
        return Math.max(Theme.TOPBAR_H + 8, height - POCKET_H - POCKET_EDGE);
    }

    private boolean overPocket(double mx, double my) {
        return drag != null && Ui.hit(mx, my, pocketX(), pocketY(), pocketW(), POCKET_H);
    }

    private void drawPocket(DrawContext ctx, int mouseX, int mouseY) {
        if (drag == null) return;
        int px = pocketX(), py = pocketY(), pw = pocketW();
        boolean hot = overPocket(mouseX, mouseY);
        Draw.shadow(ctx, px, py, pw, POCKET_H, Ui.R);
        Draw.card(ctx, px, py, pw, POCKET_H, Ui.R,
                Draw.argb(hot ? 0xFF : 0xE0,
                        hot ? Draw.mix(Ui.PANEL, Theme.ACCENT, 0.22f) : Ui.PANEL),
                Draw.opaque(hot ? Theme.ACCENT : Ui.BORDER));
        int gx = px + 11, gy = py + (POCKET_H - Draw.glyphH(Draw.PACK)) / 2;
        Draw.glyph(ctx, Draw.PACK, gx, gy, hot ? Theme.ACCENT : Theme.TEXT_DIM);
        int tx = gx + Draw.glyphW(Draw.PACK) + 8;
        int room = px + pw - 9 - tx;
        if (room < 34) return;
        Draw.textFit(ctx, textRenderer, "В РЮКЗАК", tx, py + 8, room,
                hot ? Theme.TEXT : Theme.TEXT_DIM, false);
        Draw.textFit(ctx, textRenderer, hot ? "отпусти — уберу" : "перетащи сюда", tx, py + 19,
                room, Theme.TEXT_FAINT, false);
    }

    private void finishDrag(double mx, double my) {
        if (drag == null) return;
        dragAwaitsClick = false;
        if (overPocket(mx, my)) {
            List<Script.Node> chain = drag;
            drag = null;
            snap = null;
            dragSnapshot = null;
            stash(chain, "убрано в рюкзак");
            return;
        }
        if (mx < canvasLeft()) {
            if (dragFromPalette && !dragMoved) placeAtCenter();
            else toast(dragFromPalette ? "отменено" : "блок удалён");
            drag = null;
            snap = null;
            dragSnapshot = null;
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
        drag = null;
        snap = null;
        dragSnapshot = null;
    }

    private void deleteBlock(Layout.Box box) {
        pushUndo();
        List<Script.Node> body = new ArrayList<>(box.node.body);
        box.owner.remove(box.index);
        if (!body.isEmpty()) box.owner.addAll(box.index, body);
        if (box.root != null && box.root.chain.isEmpty()) script.roots.remove(box.root);
        toast("блок удалён");
    }

    private void deleteStack(Layout.Box box) {
        pushUndo();
        box.owner.subList(box.index, box.owner.size()).clear();
        if (box.root != null && box.root.chain.isEmpty()) script.roots.remove(box.root);
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

    private static List<Value> chipValues(Layout.Box box, Layout.Chip chip) {
        if (box == null || chip == null || (!chip.isArg() && !chip.isCell())) return List.of();
        Script.Node n = Layout.chipNode(box.node);
        if (chip.argIndex < 0 || chip.argIndex >= n.args().size()) return List.of();
        List<Value> all = n.values.get(chip.argIndex);
        if (all == null || all.isEmpty()) return List.of();
        if (chip.isCell())
            return chip.cell < all.size() && !all.get(chip.cell).isBlank()
                    ? List.of(all.get(chip.cell)) : List.of();
        List<Value> out = new ArrayList<>(all.size());
        for (Value v : all) if (!v.isBlank()) out.add(v);
        return out;
    }

    private boolean grabFromChip(Layout.Box box, Layout.Chip chip, boolean move) {
        List<Value> have = chipValues(box, chip);
        if (have.isEmpty()) return false;
        if (move) { take(box, chip, have, true); return true; }
        boolean nested = false;
        for (Value v : have) nested |= !inner(v).isEmpty();
        if (have.size() == 1 && !nested) { take(box, chip, have, false); return true; }
        chooseValues(box, chip, have);
        return true;
    }

    private static List<Value> inner(Value v) {
        if (!Value.ARRAY.equals(v.type)) return List.of();
        List<Value> out = new ArrayList<>();
        for (Value it : v.items) if (!it.isBlank()) out.add(it);
        return out.size() >= 2 ? out : List.of();
    }

    private void chooseValues(Layout.Box box, Layout.Chip chip, List<Value> have) {
        record Pick(Value value, boolean top, Menu.Item item) {}
        List<Pick> picks = new ArrayList<>();
        for (Value v : have) {
            picks.add(new Pick(v, true, Menu.Item.rich(v.label(), icon(v), v.note(), List.of())));
            for (Value in : inner(v))
                picks.add(new Pick(in, false,
                        Menu.Item.rich("· " + in.label(), icon(in), in.note(), List.of())));
        }
        List<Menu.Item> items = new ArrayList<>(picks.size());
        boolean[] on = new boolean[picks.size()];
        for (int i = 0; i < picks.size(); i++) {
            items.add(picks.get(i).item());
            on[i] = picks.get(i).top();
        }
        int sx = toScreenX(chip.x), sy = toScreenY(chip.y + Layout.CHIP_H) + 3;
        menu = Menu.multi(width, height, sx, sy, textRenderer, "ЧТО СКОПИРОВАТЬ", items, on,
                "Копировать", picked -> {
                    if (picked.isEmpty()) return;
                    List<Value> out = new ArrayList<>(picked.size());
                    for (int i : picked) out.add(picks.get(i).value());
                    take(box, chip, out, false);
                });
    }

    private static ItemStack icon(Value v) {
        ItemStack own = BlockView.itemIcon(v);
        if (!own.isEmpty()) return own;
        Values.Kind k = Values.kind(v.type);
        return k == null ? ItemStack.EMPTY : Catalog.stackOf(k.item());
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
                ? "значение скопировано — клик по слоту"
                : "скопировано значений: " + carry.size() + " — клик по слоту");
    }

    private String copyHint() {
        return Settings.get().label(Settings.Hot.DUPLICATE) + " — копировать, тянуть — перенести";
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

    private static boolean fits(Script.Node n, int argIndex, Value v) {
        if ("Параметр".equals(n.args().get(argIndex).type))
            return Value.PARAMETER.equals(v.type);
        if (n.declares()) {
            if (argIndex == Catalog.FN_NAME || argIndex == Catalog.FN_DESC
                    || argIndex == Catalog.FN_DISPLAY) return Value.TEXT.equals(v.type);
            if (argIndex == Catalog.FN_ICON) return Value.ITEM.equals(v.type);
        }
        return Values.EDITABLE.contains(v.type);
    }

    private boolean acceptsCarry(Layout.Box box, Layout.Chip chip) {
        if (carry == null || box == null || chip == null) return false;
        if (!chip.isArg() && !chip.isCell()) return false;
        Script.Node n = Layout.chipNode(box.node);
        if (chip.argIndex < 0 || chip.argIndex >= n.args().size()) return false;
        for (Value v : carry) if (!fits(n, chip.argIndex, v)) return false;
        return true;
    }

    private void dropCarry() {
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box b = l.boxes.get(i);
            if (!b.contains(mouseCanvasX, mouseCanvasY)) continue;
            Layout.Chip c = b.chipAt(mouseCanvasX, mouseCanvasY);
            if (c != null && acceptsCarry(b, c)) { placeCarry(b, c); return; }
            break;
        }
        cancelCarry();
    }

    private void placeCarry(Layout.Box box, Layout.Chip chip) {
        pushUndo(carrySnapshot);
        int put = putValues(box, chip, carry);
        int left = carry.size() - put;
        carry = null;
        carryHeld = false;
        carrySnapshot = null;
        toast(left > 0 ? "вставлено " + put + ", не влезло " + left : "вставлено");
    }

    private static int putValues(Layout.Box box, Layout.Chip chip, List<Value> values) {
        Script.Node n = Layout.chipNode(box.node);
        List<Value> dst = n.valuesOf(chip.argIndex);
        Catalog.Arg a = n.args().get(chip.argIndex);
        if (chip.isCell()) {
            while (dst.size() <= chip.cell) dst.add(Value.blank());
            dst.set(chip.cell, values.get(0).copy());
            return 1;
        }
        if (a.list) {
            int put = 0;
            for (Value v : values) {
                if (dst.size() >= a.capacity) break;
                dst.add(v.copy());
                put++;
            }
            return put;
        }
        dst.clear();
        dst.add(values.get(0).copy());
        return 1;
    }

    private boolean copyChip(boolean cut) {
        if (hoverBox == null || hoverChip == null) return false;
        List<Value> have = chipValues(hoverBox, hoverChip);
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
        for (Value v : values) if (fits(n, hoverChip.argIndex, v)) ok.add(v);
        if (ok.isEmpty()) {
            toast("такое значение в этот слот не кладётся");
            return true;
        }
        pushUndo();
        int put = putValues(hoverBox, hoverChip, ok);
        int left = ok.size() - put;
        toast(left > 0 ? "вставлено " + put + ", не влезло " + left
                : put == 1 ? "вставлено «" + ok.get(0).label() + "»" : "вставлено " + put);
        return true;
    }

    private static final int CARRY_MAX = 4;
    private static final int CARRY_MAX_W = 190, CARRY_GAP = 2, CARRY_MORE_H = 11;

    private void drawCarry(DrawContext ctx, int mouseX, int mouseY) {
        if (carry == null) return;
        ctx.createNewRootLayer();
        int shown = Math.min(carry.size(), CARRY_MAX);
        String more = carry.size() > shown ? "и ещё " + (carry.size() - shown) : null;

        int w = 0;
        for (int i = 0; i < shown; i++) {
            Value v = carry.get(i);
            String note = v.note();
            w = Math.max(w, Layout.CHIP_INK_X + textRenderer.getWidth(v.label()) + 7
                    + (note.isEmpty() ? 0 : textRenderer.getWidth(note) + 6));
        }
        if (more != null) w = Math.max(w, textRenderer.getWidth(more) + 22);
        w = Math.max(Layout.CHIP_MIN_W, Math.min(CARRY_MAX_W, w));

        int step = Layout.CHIP_H + CARRY_GAP;
        int h = shown * step - CARRY_GAP + (more == null ? 0 : CARRY_MORE_H);
        int edge = carryHeld ? 2 : 10;
        int x = carryHeld ? mouseX - w / 2 : mouseX + 11;
        int y = carryHeld ? mouseY - h / 2 : mouseY + 13;
        x = Math.max(edge, Math.min(x, width - w - 2));
        y = Math.max(Theme.TOPBAR_H + edge, Math.min(y, height - h - 3));

        for (int i = 0; i < shown; i++) carryPill(ctx, carry.get(i), x, y + i * step, w);
        if (more != null)
            Draw.textFit(ctx, textRenderer, more, x + Layout.CHIP_INK_X,
                    y + shown * step + 1, w - Layout.CHIP_INK_X - 4, Theme.TEXT_DIM, true);
        if (!carryHeld) carryBadge(ctx, x - 8, y - 8);
    }

    private void carryPill(DrawContext ctx, Value v, int x, int y, int w) {
        int tc = v.color();
        int ink = Draw.isLight(tc) ? 0x141821 : 0xFFFFFF;

        Draw.shadow(ctx, x, y, w, Layout.CHIP_H, 7);
        BlockView.pill(ctx, x, y, w, tc, true, carryHeld ? 0xE0 : 0xFF);

        ItemStack stack = BlockView.itemIcon(v);
        BlockView.badge(ctx, x, y, stack, Draw.opaque(Draw.shade(tc, -0.55f)));
        int textX = x + (stack.isEmpty() ? Layout.CHIP_INK_X : Layout.CHIP_ITEM_INK_X);

        int right = x + w - 6;
        String note = v.note();
        if (!note.isEmpty()) {
            int nw = textRenderer.getWidth(note);
            if (right - nw - textX > 24) {
                Draw.text(ctx, textRenderer, note, right - nw, y + 4,
                        Draw.shade(tc, -0.42f), false);
                right -= nw + 4;
            }
        }
        Draw.textFit(ctx, textRenderer, v.label(), textX, y + 4, right - textX, ink, false);
    }

    private void carryBadge(DrawContext ctx, int x, int y) {
        Draw.round(ctx, x, y, 11, 11, 3, Draw.opaque(Theme.ACCENT));
        Draw.roundOutline(ctx, x, y, 11, 11, 3, Draw.opaque(Draw.shade(Theme.ACCENT, -0.45f)));
        Draw.glyph(ctx, Draw.PLUS, x + 3, y + 3, Theme.ON_ACCENT);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (blockMenu != null && blockMenu.mouseDragged(click.y())) return true;
        if (palette.barDragging()) { palette.barDrag(click.y(), height); return true; }
        if (backpack != null) return backpack.mouseDragged(click, dx, dy);
        if (condPicker != null) return condPicker.mouseDragged(click, click.x(), click.y());
        if (settings != null) return settings.mouseDragged(click, dx, dy);
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
    public boolean mouseReleased(Click click) {
        palette.barRelease();
        if (menu != null) menu.mouseReleased();
        if (blockMenu != null) blockMenu.mouseReleased();
        if (backpack != null) { backpack.mouseReleased(); return true; }
        if (settings != null) { settings.mouseReleased(); return true; }
        if (banding) { applyBand(); return true; }
        if (moving) { finishMove(); return true; }
        panning = false;
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
        if (drag == null) return super.mouseReleased(click);
        if (dragAwaitsClick) { dragAwaitsClick = false; return true; }
        finishDrag(click.x(), click.y());
        return true;
    }

    private void placeAtCenter() {
        if (dragSnapshot != null) pushUndo(dragSnapshot);
        double cx = toCanvasX((canvasLeft() + width) / 2.0) - 70;
        double cy = toCanvasY((Theme.TOPBAR_H + height) / 2.0) - 20;
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
        if (condPicker != null) return condPicker.mouseScrolled(mx, my, vAmount);
        if (backpack != null) return backpack.mouseScrolled(mx, my, vAmount);
        if (settings != null) return settings.mouseScrolled(mx, my, vAmount);
        if (menu != null && menu.mouseScrolled(mx, my, vAmount)) return true;
        if (blockMenu != null) { blockMenu.mouseScrolled(mx, my, vAmount); return true; }
        if (editor != null && editor.mouseScrolled(mx, my, vAmount)) return true;
        if (finder != null && finder.mouseScrolled(mx, my, vAmount)) return true;
        if (map.hit(mx, my)) return true;
        if (mx < canvasLeft()) { palette.scrollBy(vAmount, height); return true; }
        if (my < Theme.TOPBAR_H) return true;
        if (hoverChip != null && hoverBox != null && hoverChip.isMarker()) {
            pushUndo();
            Layout.chipNode(hoverBox.node).cycleMarker(hoverChip.settingIndex, vAmount < 0);
            return true;
        }
        zoomTo(zoom * (vAmount > 0 ? 1.08 : 1 / 1.08), mx, my);
        return true;
    }

    private static final class Snap {
        final List<Script.Node> target;
        final int index, x, y, markX, markY, width;
        final boolean above;
        final Script.Root root;
        Snap(List<Script.Node> target, int index, int x, int y, int markX, int markY,
             int width, boolean above, Script.Root root) {
            this.target = target; this.index = index; this.x = x; this.y = y;
            this.markX = markX; this.markY = markY; this.width = width;
            this.above = above; this.root = root;
        }
    }

    private static double dist(double dx, double dy) { return Math.hypot(dx * 0.6, dy); }

    private Snap findSnap(Layout layout) {
        if (drag == null || drag.isEmpty()) return null;
        boolean hat = drag.get(0).isHat();
        double px = mouseCanvasX - dragOffX, py = mouseCanvasY - dragOffY;
        int payloadW = Layout.ofChain(drag, 0, 0, textRenderer).boxes.get(0).w;
        int payloadH = Layout.chainHeight(drag, textRenderer);

        Snap best = null;
        double bestDist = 44;
        for (Layout.Box box : layout.boxes) {
            if (!hat) {
                int by = box.bottom();
                double d = dist(px - box.x, py - by);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Snap(box.owner, box.index + 1, box.x, by,
                            box.x, by, payloadW, false, box.root);
                }
                if (box.node.wraps()) {
                    int ix = box.x + Layout.INDENT, iy = box.bodyTop();
                    double di = dist(px - ix, py - iy);
                    if (di < bestDist) {
                        bestDist = di;
                        best = new Snap(box.node.body, 0, ix, iy, ix, iy, payloadW, false, box.root);
                    }
                }
            }
            boolean rootChain = box.root != null && box.owner == box.root.chain;
            if (box.index == 0 && !box.node.isHat() && (!hat || rootChain)) {
                double d = dist(px - box.x, (py + payloadH) - box.y);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Snap(box.owner, 0, box.x, box.y - payloadH,
                            box.x, box.y, payloadW, true, box.root);
                }
            }
        }
        return best;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (condPicker != null) {
            condPicker.keyPressed(input);
            if (condPicker.isClosed()) condPicker = null;
            return true;
        }
        if (backpack != null) {
            BackpackPanel panel = backpack;
            panel.keyPressed(input);
            if (panel.isClosed() || backpack != panel) backpack = null;
            return true;
        }
        if (settings != null) {
            settings.keyPressed(input);
            if (settings.isClosed()) closeSettings();
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
        Settings.Hot hot = st.match(key, input.modifiers());
        if (hot != null && typing(hot)) hot = null;
        if (hot != null && (st.mods(hot) != 0 || !typingText()) && runHotkey(hot)) return true;
        if (finder != null) {
            boolean ate = finder.keyPressed(input);
            if (finder.isClosed()) closeFinder();
            if (ate) return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.getText().isEmpty()) search.setText("");
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
            if (modulePick()) { finishModulePick(false); return true; }
            if (drag != null) {
                drag = null;
                snap = null;
                dragAwaitsClick = false;
                toast("отменено");
                return true;
            }
            if (palette.hasCrumb()) {
                if (!search.getText().isEmpty()) search.setText("");
                else palette.openCategory(-1);
                return true;
            }
            askExit("build");
            return true;
        }
        return super.keyPressed(input);
    }

    private boolean typingText() {
        return search.isFocused() || (finder != null && finder.focused());
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
            case PASTE -> { if (!pasteChip()) pasteClip(); }
            case DELETE -> { if (picked.isEmpty()) deleteHovered(); else deletePicked(); }
            case DEL_STACK -> { if (picked.isEmpty()) deleteStackHovered(); else deletePicked(); }
            case SELECT -> selectAll();
            case BACKPACK -> openBackpack();
            case MARKET -> { if (modulePick()) finishModulePick(false); else openMarket(); }
            case STASH -> stashHovered();
            case PLAY -> askExit("play");
            case BUILD -> askExit("build");
            case RESTART -> askExit(XeroCode.RESTART);
            case SETTINGS -> openSettings();
            case MODE -> toOriginal();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (condPicker != null) { condPicker.charTyped(input); return true; }
        if (backpack != null) { backpack.charTyped(input); return true; }
        if (settings != null) { settings.charTyped(input); return true; }
        if (menu != null) return true;
        if (editor != null) return editor.charTyped(input);
        if (finder != null && finder.focused()) return finder.charTyped(input);
        if (!search.isFocused()) {
            String s = input.asString();
            if (s != null && !s.isBlank()) {
                search.setFocused(true);
                setFocused(search);
            }
        }
        return search.isFocused() && search.charTyped(input);
    }

    @Override
    public void close() {
        closeOverlays();
        rememberView();
        saveScript();
        XeroCode.canvasClosed();
        super.close();
    }

    public void rememberView() {
        script.viewX = panX;
        script.viewY = panY;
        script.viewZoom = zoom;
    }

    @Override
    public void removed() {
        Audio.release();
        if (settings != null) { settings.dispose(); settings = null; }
        rememberView();
        super.removed();
    }
}
