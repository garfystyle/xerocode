package com.xerocode.ui;

import com.xerocode.Audio;
import com.xerocode.Catalog;
import com.xerocode.Functions;
import com.xerocode.History;
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
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditorScreen extends Screen {
    private final Script script;
    private final Palette palette = new Palette();
    private TextFieldWidget search;

    private double panX = 60, panY = 50, zoom = 1.0;
    private boolean viewRestored;
    private boolean panning, draggingSearch, resizingPalette;
    private double paletteDrag;
    private double mouseCanvasX, mouseCanvasY;

    private List<Script.Node> drag;
    private double dragOffX, dragOffY;
    private boolean dragFromPalette, dragMoved;
    private String dragSnapshot;
    private Snap snap;

    private Menu menu;
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

    private int canvasLeft() { return Theme.PALETTE_W; }
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
        search.setWidth(Palette.searchTextW());
        search.setX(Palette.searchTextX());
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
        if (settings != null) settings.resize(width, height);
        if (condPicker != null) condPicker.resize(width, height);
        menu = null;
        topStamp = Integer.MIN_VALUE;
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
        if (History.undo(script)) { revision++; toast("отменено"); }
    }

    private void redo() {
        closeOverlays();
        if (History.redo(script)) { revision++; toast("возвращено"); }
    }

    private void closeOverlays() {
        if (settings != null) { settings.dispose(); settings = null; }
        finishEditor();
        menu = null;
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
            topStamp = Integer.MIN_VALUE;
            search.setEditableColor(Draw.opaque(Theme.TEXT));
        }
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        mouseCanvasX = toCanvasX(mouseX);
        mouseCanvasY = toCanvasY(mouseY);

        layout = layout();
        updateHover(mouseX, mouseY);
        snap = drag == null ? null : findSnap(layout);

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
                if (visible(box, vx0, vy0, vx1, vy1)) drawBlockShadow(ctx, box);
            }
        }
        Draw.batch(null);

        for (Layout.Chunk chunk : layout.chunks) {
            if (!chunk.visible(vx0, vy0, vx1, vy1)) continue;
            Draw.batch(Batch.open(ctx, canvasArea, canvasArea, 512));
            for (int i = chunk.from; i < chunk.to; i++) {
                Layout.Box box = layout.boxes.get(i);
                if (visible(box, vx0, vy0, vx1, vy1)) drawBlock(ctx, box);
            }
            Draw.batch(null);
        }

        if (snap != null || drag != null) {
            Draw.batch(Batch.open(ctx, canvasArea, canvasArea, 512));
            if (snap != null) drawSnapMark(ctx);
            if (drag != null) drawDragged(ctx);
            Draw.batch(null);
        }

        m.popMatrix();
        ctx.disableScissor();
        SmoothText.clip(null);

        palette.render(ctx, textRenderer, mouseX, mouseY, height);
        search.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, textRenderer, search);
        drawTopBar(ctx, mouseX, mouseY);
        drawSplitter(ctx, mouseX);

        ctx.createNewRootLayer();
        drawToast(ctx);
        if (editor != null) editor.render(ctx, mouseX, mouseY, delta);
        if (menu != null) menu.render(ctx, textRenderer, mouseX, mouseY);
        else if (!exitPrompt && settings == null) drawTooltips(ctx, mouseX, mouseY);
        if (exitPrompt) drawExitPrompt(ctx, mouseX, mouseY);
        if (condPicker != null) {
            ctx.createNewRootLayer();
            condPicker.render(ctx, mouseX, mouseY, delta);
        }
        if (settings != null) settings.render(ctx, mouseX, mouseY, delta);
    }

    private static boolean visible(Layout.Box b, double x0, double y0, double x1, double y1) {
        return b.x + b.w >= x0 && b.x <= x1 && b.bottom() >= y0 && b.y <= y1;
    }

    private void updateHover(int mouseX, int mouseY) {
        hoverBox = null;
        hoverChip = null;
        if (drag != null || menu != null || mouseX < canvasLeft() || mouseY < Theme.TOPBAR_H) return;
        if (editor != null && editor.contains(mouseX, mouseY)) return;
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

    private static int blockColor(Script.Node n) {
        int base = n.action.category == null ? 0x7A7A7A : n.action.category.color;
        return n.action.unavailable ? Draw.mix(base, 0x8A8A8A, 0.4f) : base;
    }

    private void drawBlockShadow(DrawContext ctx, Layout.Box box) {
        Draw.shadow(ctx, box.x, box.y + box.hatH, box.w, box.headerH - box.hatH, 1);
        if (box.node.wraps()) Draw.shadow(ctx, box.x, box.armY(), box.w, Layout.ARM_H, 1);
    }

    private static int rampAt(int y0, int span, int top, int bottom, int y) {
        if (span <= 1) return top;
        float t = (y - y0) / (float) span;
        return Draw.mixArgb(top, bottom, Math.max(0f, Math.min(1f, t)));
    }

    private void drawBlock(DrawContext ctx, Layout.Box box) {
        Script.Node n = box.node;
        boolean hovered = hoverBox == box && hoverChip == null && drag == null;
        boolean grad = Settings.gradient();
        int base = hovered ? blockColor(n) : 0;
        int top = hovered ? Draw.opaque(Draw.shade(base, grad ? 0.28f : 0.16f)) : box.top;
        int bottom = hovered ? (grad ? Draw.opaque(Draw.shade(base, 0.02f)) : top) : box.bottom;
        int border = hovered ? Draw.opaque(Draw.shade(base, -0.28f)) : box.border;
        boolean wraps = n.wraps();
        int headerBottom = box.y + box.headerH;

        int y0 = box.y + box.hatH;
        int span = Math.max(1, box.totalH - box.hatH - 1);

        if (wraps) {
            int armY = box.armY();
            int spineY = headerBottom - 1, spineH = armY - headerBottom + 2;
            Draw.blockShape(ctx, box.x, spineY, Layout.INDENT + 1, spineH, 0, 0,
                    rampAt(y0, span, top, bottom, spineY),
                    rampAt(y0, span, top, bottom, spineY + spineH - 1), border);
            Draw.blockShape(ctx, box.x, armY, box.w, Layout.ARM_H, box.coverFrom, box.coverTo,
                    rampAt(y0, span, top, bottom, armY + 1),
                    rampAt(y0, span, top, bottom, armY + Layout.ARM_H - 2), border);
            Draw.rect(ctx, box.x + 1, armY, Layout.INDENT - 1, 1,
                    rampAt(y0, span, top, bottom, armY));
        }

        Draw.blockShape(ctx, box.x, box.y, box.w, box.headerH,
                wraps ? box.mouthFrom : box.coverFrom, wraps ? box.mouthTo : box.coverTo,
                rampAt(y0, span, top, bottom, box.y + 1),
                rampAt(y0, span, top, bottom, headerBottom - 2), border);
        if (wraps) Draw.rect(ctx, box.x + 1, headerBottom - 1, Layout.INDENT - 1, 1,
                rampAt(y0, span, top, bottom, headerBottom - 1));

        if (n.isHat())
            Draw.rect(ctx, box.x + 4, box.y + 2, box.w - 8, 3, Draw.argb(0x4D, 0xFFFFFF));
        else
            Draw.rect(ctx, box.x + 1, box.y + 1, box.w - 2, 1, Draw.argb(0x2E, 0xFFFFFF));

        int iconY = box.y + box.hatH + 5;
        boolean lightHead = hovered
                ? Draw.isLight(Draw.shade(base, grad ? 0.28f : 0.16f)) : box.lightHead;
        int ink = hovered ? (lightHead ? 0x141821 : 0xFFFFFF) : box.ink;
        if (box.card != null) {
            drawCard(ctx, box, ink, lightHead, top);
            for (Layout.Chip chip : box.chips) drawChip(ctx, box, chip);
            return;
        }
        ctx.drawItem(n.action.icon(), box.x + Layout.PAD - 1, iconY);
        Draw.text(ctx, textRenderer, box.title, box.x + Layout.PAD + 20, iconY + 4, ink, !lightHead);
        if (box.target != null)
            Draw.text(ctx, textRenderer, box.target, box.targetX, iconY + 4,
                    Draw.argb(0xC4, ink), !lightHead);
        if (n.action.unavailable)
            Draw.glyph(ctx, Draw.WARN, box.x + box.w - Layout.PAD - 5, iconY + 5,
                    lightHead ? 0x7A5300 : 0xFFE066);

        for (Layout.Chip chip : box.chips) drawChip(ctx, box, chip);
    }

    private void drawChip(DrawContext ctx, Layout.Box box, Layout.Chip chip) {
        if (chip.isPlus()) drawPlusChip(ctx, chip);
        else if (chip.isCondition()) drawConditionChip(ctx, box, chip);
        else if (chip.isArg() || chip.isCell()) drawArgChip(ctx, box, chip);
        else drawMarkerChip(ctx, box, chip);
        if (hoverChip == chip) Draw.roundOutline(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H,
                Layout.CHIP_H / 2, Draw.argb(0xAA, 0xFFFFFF));
    }

    private void drawCard(DrawContext ctx, Layout.Box box, int ink, boolean lightHead, int head) {
        Layout.Card c = box.card;
        int mute = Draw.mix(ink, head, 0.42f);
        int soft = Draw.mix(ink, head, 0.30f);

        if (!c.icon.isEmpty()) {
            if (c.iconSize == 16) {
                ctx.drawItem(c.icon, c.iconX, c.iconY);
            } else {
                Matrix3x2fStack m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(c.iconX, c.iconY);
                m.scale(c.iconSize / 16f, c.iconSize / 16f);
                ctx.drawItem(c.icon, 0, 0);
                m.popMatrix();
            }
        }
        if (c.verb != null)
            Draw.text(ctx, textRenderer, c.verb, c.verbX, c.nameY, soft, !lightHead);
        Draw.textScaled(ctx, textRenderer, c.name, c.nameX, c.nameY, c.scale,
                c.named ? ink : mute, !lightHead);
        if (c.id != null)
            Draw.text(ctx, textRenderer, c.id, c.idX, c.idY, mute, !lightHead);
        if (c.kind != null)
            Draw.text(ctx, textRenderer, c.kind, c.kindX, c.kindY, mute, !lightHead);
        if (c.missing)
            Draw.glyph(ctx, Draw.WARN, box.x + box.w - Layout.PAD - 5, c.nameY + (c.scale > 1 ? 4 : 0),
                    lightHead ? 0x7A5300 : 0xFFE066);
        for (int i = 0; i < c.desc.size(); i++)
            Draw.text(ctx, textRenderer, c.desc.get(i), c.descX,
                    c.descY + i * Layout.DESC_H, soft, !lightHead);
        if (hoverBox == box && hoverChip == null && drag == null) {
            if (c.hitName(mouseCanvasX, mouseCanvasY)) {
                int from = c.verb == null ? c.nameX : c.verbX;
                Draw.rect(ctx, from, c.nameY + 8 * c.scale, c.nameX + c.nameW - from, 1,
                        Draw.opaque(mute));
            }
            if (c.hitId(mouseCanvasX, mouseCanvasY))
                Draw.rect(ctx, c.idX, c.idY + 8, c.idW, 1, Draw.opaque(mute));
            if (c.hitDesc(mouseCanvasX, mouseCanvasY))
                Draw.rect(ctx, c.descX, c.descY + c.desc.size() * Layout.DESC_H - 2, c.descW, 1,
                        Draw.opaque(mute));
            if (c.hitIcon(mouseCanvasX, mouseCanvasY))
                Draw.roundOutline(ctx, c.iconX - 2, c.iconY - 2, c.iconSize + 4, c.iconSize + 4, 3,
                        Draw.argb(0x99, ink));
        }
        if (c.sepY > 0) {
            Draw.rect(ctx, box.x + Layout.PAD, c.sepY, box.w - Layout.PAD * 2, 1,
                    Draw.argb(0x3A, 0x000000));
            Draw.rect(ctx, box.x + Layout.PAD, c.sepY + 1, box.w - Layout.PAD * 2, 1,
                    Draw.argb(0x22, 0xFFFFFF));
        }
    }

    private void drawPlusChip(DrawContext ctx, Layout.Chip chip) {
        Draw.pill(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H, chip.border);
        Draw.pillGrad(ctx, chip.x + 1, chip.y + 1, chip.w - 2, Layout.CHIP_H - 2,
                chip.top, chip.bottom);
        Draw.glyph(ctx, Draw.PLUS, chip.x + 5, chip.y + 5, chip.ink);
        Draw.text(ctx, textRenderer, chip.fitted, chip.x + 16, chip.y + 4, chip.ink, false);
    }

    private void drawArgChip(DrawContext ctx, Layout.Box box, Layout.Chip chip) {
        Draw.pill(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H, chip.border);
        Draw.pillGrad(ctx, chip.x + 1, chip.y + 1, chip.w - 2, Layout.CHIP_H - 2,
                chip.top, chip.bottom);
        chipBadge(ctx, chip, chip.icon, chip.dot);

        int textRight = chip.x + chip.w - 6;
        if (chip.count != null) {
            Draw.text(ctx, textRenderer, chip.count, textRight - chip.countW, chip.y + 4,
                    chip.dim, false);
            textRight -= chip.countW + 5;
        }
        if (chip.note != null) {
            Draw.text(ctx, textRenderer, chip.note, textRight - chip.noteW, chip.y + 4,
                    chip.dim, false);
            textRight -= chip.noteW + 4;
        }
        Draw.text(ctx, textRenderer, chip.fitted,
                chip.x + (chip.icon.isEmpty() ? Layout.CHIP_INK_X : Layout.CHIP_ITEM_INK_X),
                chip.y + 4, chip.ink, false);
    }

    private static void chipBadge(DrawContext ctx, Layout.Chip chip, ItemStack icon, int dot) {
        if (icon == null || icon.isEmpty()) {
            Draw.dot(ctx, chip.x + 5, chip.y + 5, dot);
            return;
        }
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(chip.x + 2f, chip.y + 2f);
        m.scale(11 / 16f, 11 / 16f);
        ctx.drawItem(icon, 0, 0);
        m.popMatrix();
    }

    private static int chipPill(DrawContext ctx, Layout.Chip chip, int face, boolean tinted) {
        boolean grad = Settings.gradient();
        int top = tinted ? Draw.shade(face, grad ? 0.12f : 0.02f) : Theme.MARKER_TOP;
        int bottom = tinted ? Draw.shade(face, grad ? -0.10f : 0.02f)
                : grad ? Theme.MARKER_BOTTOM : Theme.MARKER_TOP;
        Draw.pill(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H,
                Draw.opaque(tinted ? Draw.shade(face, -0.5f) : Theme.MARKER_BORDER));
        Draw.pillGrad(ctx, chip.x + 1, chip.y + 1, chip.w - 2, Layout.CHIP_H - 2,
                Draw.opaque(top), Draw.opaque(bottom));
        return top;
    }

    private void drawConditionChip(DrawContext ctx, Layout.Box box, Layout.Chip chip) {
        Script.Node cond = box.node.cond;
        boolean set = cond != null;
        int face = set && cond.action.category != null
                ? cond.action.category.color : Theme.MARKER_TOP;
        int top = chipPill(ctx, chip, face, set);
        chipBadge(ctx, chip, set && !cond.action.item.isEmpty()
                ? Catalog.stackOf(cond.action.item) : null, Draw.opaque(Draw.shade(face, -0.55f)));
        int ink = Draw.isLight(top) ? 0x141821 : set ? 0xFFFFFF : Theme.TEXT_DIM;
        Draw.text(ctx, textRenderer, chip.fitted, chip.x + 15, chip.y + 4, ink, false);
    }

    private void drawMarkerChip(DrawContext ctx, Layout.Box box, Layout.Chip chip) {
        boolean bound = Layout.markerBound(Layout.chipNode(box.node), chip.settingIndex);
        int face = bound ? Values.color(Value.VARIABLE) : Theme.MARKER_TOP;
        int top = chipPill(ctx, chip, face, bound);
        int ink = Draw.isLight(top) ? 0x141821 : bound ? 0xFFFFFF : Theme.TEXT;
        if (bound) Draw.dot(ctx, chip.x + 5, chip.y + 5, Draw.opaque(Draw.shade(face, -0.55f)));
        Draw.text(ctx, textRenderer, chip.fitted, chip.x + (bound ? 13 : 8), chip.y + 4,
                ink, false);
        Draw.glyph(ctx, Draw.CARET_DOWN, chip.x + chip.w - 11, chip.y + 6,
                bound ? Draw.argb(0xCC, ink) : Draw.opaque(Theme.TEXT_DIM));
    }

    private void drawDragged(DrawContext ctx) {
        int px = (int) Math.round(mouseCanvasX - dragOffX);
        int py = (int) Math.round(mouseCanvasY - dragOffY);
        Layout l = Layout.ofChain(drag, px, py, textRenderer);
        for (Layout.Box b : l.boxes) drawBlockShadow(ctx, b);
        for (Layout.Box b : l.boxes) drawBlock(ctx, b);
    }

    private void drawSnapMark(DrawContext ctx) {
        Layout ghost = Layout.ofChain(drag, snap.x, snap.y, textRenderer);
        if (ghost.boxes.isEmpty()) return;
        Layout.Box b = ghost.boxes.get(0);
        Draw.blockSilhouette(ctx, b.x, b.y, b.w, b.headerH, Draw.argb(0x66, 0xC3DEFF));
    }

    private static final int B_UNDO = 1, B_REDO = 2, B_PLAY = 3, B_BUILD = 4, B_CLEAR = 5,
            B_ZOOM_OUT = 6, B_ZOOM_IN = 7, B_FIT = 8,
            B_ORIGINAL = 9, B_CANVAS = 10, B_SETTINGS = 11, B_UPLOAD = 12, B_MORE = 13,
            B_ZOOM_LABEL = 14;

    private static final class TopBtn {
        int id, x, w;
        String[] icon;
        String label, tip;
        boolean enabled = true;
        boolean separatorAfter;
        boolean active;
        boolean joinLeft, joinRight;
    }

    private TopBtn btn(int id, String[] icon, String tip) {
        return btn(id, icon, null, tip);
    }

    private TopBtn btn(int id, String[] icon, String label, String tip) {
        TopBtn b = new TopBtn();
        b.id = id; b.icon = icon; b.label = label; b.tip = tip;
        b.w = 8 + (icon == null ? 0 : Draw.glyphW(icon) + (label == null ? 0 : 5))
                + (label == null ? 0 : textRenderer.getWidth(label)) + 8;
        return b;
    }

    private List<TopBtn> topCache;
    private int topStamp = Integer.MIN_VALUE;
    private List<TopBtn> topButtons() {
        int stamp = width * 31 + canvasLeft() * 7 + countNodes() * 64
                + (History.canUndo() ? 1 : 0) + (History.canRedo() ? 2 : 0)
                + (script.roots.isEmpty() ? 0 : 4)
                + (Settings.canvasMode() ? 8 : 0);
        if (topCache != null && stamp == topStamp) return topCache;
        List<TopBtn> list = buildTop(0, true);
        if (!topFits(list)) list = buildTop(0, false);
        for (int drop = 1; !topFits(list) && drop <= HIDE_ORDER.length; drop++)
            list = buildTop(drop, false);
        topStamp = stamp;
        topCache = list;
        return list;
    }

    private static final int[] HIDE_ORDER = {B_CLEAR, B_BUILD, B_PLAY, B_UPLOAD, B_REDO,
            B_UNDO, B_FIT, B_ZOOM_LABEL, B_ZOOM_OUT, B_ORIGINAL};

    private boolean zoomLabelShown = true;

    private boolean infoShown() { return width - canvasLeft() >= 400; }

    private final List<TopBtn> hiddenTop = new ArrayList<>();

    private void openTopMenu(int mx, int my) {
        List<Menu.Item> items = new ArrayList<>();
        List<TopBtn> acts = new ArrayList<>(hiddenTop);
        for (TopBtn b : acts) {
            String label = b.tip == null ? "" : b.tip;
            int nl = label.indexOf('\n');
            if (nl >= 0) label = label.substring(0, nl);
            Menu.Item item = new Menu.Item(label.trim(), b.id == B_CLEAR, b.icon);
            item.enabled = b.enabled;
            items.add(item);
        }
        menu = Menu.actions(width, height, mx, my, textRenderer, items,
                i -> { if (i >= 0 && i < acts.size()) onTopButton(acts.get(i).id); });
    }

    private boolean topFits(List<TopBtn> list) {
        int leftEnd = canvasLeft(), rightStart = width;
        for (TopBtn b : list) {
            boolean right = b.id == B_ZOOM_OUT || b.id == B_ZOOM_IN || b.id == B_FIT
                    || b.id == B_SETTINGS;
            if (right) rightStart = Math.min(rightStart, b.x);
            else leftEnd = Math.max(leftEnd, b.x + b.w);
        }
        return leftEnd + 10 <= rightStart;
    }

    private List<TopBtn> buildTop(int drop, boolean modeLabels) {
        List<TopBtn> list = new ArrayList<>();
        TopBtn undo = btn(B_UNDO, Draw.UNDO, "Отменить  " + hotkey(Settings.Hot.UNDO));
        undo.enabled = History.canUndo();
        TopBtn redo = btn(B_REDO, Draw.REDO, "Вернуть  " + hotkey(Settings.Hot.REDO));
        redo.enabled = History.canRedo();
        redo.separatorAfter = true;
        list.add(undo);
        list.add(redo);
        list.add(btn(B_PLAY, Draw.PLAY, "Игра  " + hotkey(Settings.Hot.PLAY)
                + "\nЗапустить мир и проверить код"));
        list.add(btn(B_BUILD, Draw.BRICKS, "Строительство  " + hotkey(Settings.Hot.BUILD)
                        + "\nВернуться строить мир"));
        TopBtn clear = btn(B_CLEAR, Draw.TRASH, "Очистить полотно");
        clear.enabled = !script.roots.isEmpty();
        list.add(clear);
        TopBtn upload = btn(B_UPLOAD, Draw.UPLOAD, "Сохранить на сервер  " + hotkey(Settings.Hot.UPLOAD)
                        + "\nЗаписать код полотна блоками в мир");
        upload.enabled = !script.roots.isEmpty();
        upload.separatorAfter = true;
        list.add(upload);

        boolean canvasMode = Settings.canvasMode();
        TopBtn original = btn(B_ORIGINAL, Draw.BRICKS, modeLabels ? "3D" : null,
                "3D-кодинг  " + hotkey(Settings.Hot.MODE)
                        + "\nЗакрыть полотно и собирать код блоками в мире");
        original.active = !canvasMode;
        original.joinRight = true;
        TopBtn canvas = btn(B_CANVAS, Draw.CANVAS, modeLabels ? "2D" : null,
                "2D-кодинг\nСобирать код на полотне, как сейчас");
        canvas.active = canvasMode;
        canvas.joinLeft = true;
        list.add(original);
        list.add(canvas);

        List<TopBtn> right = new ArrayList<>();
        right.add(btn(B_ZOOM_OUT, Draw.MINUS, "Отдалить"));
        right.add(btn(B_ZOOM_IN, Draw.PLUS, "Приблизить"));
        right.add(btn(B_FIT, Draw.FIT, "Показать всё  " + hotkey(Settings.Hot.FIT)));
        TopBtn gear = btn(B_SETTINGS, Draw.GEAR, "Настройки  " + hotkey(Settings.Hot.SETTINGS)
                        + "\nГорячие клавиши и внешний вид");

        hiddenTop.clear();
        zoomLabelShown = true;
        for (int i = 0; i < drop && i < HIDE_ORDER.length; i++) {
            if (HIDE_ORDER[i] == B_ZOOM_LABEL) zoomLabelShown = false;
            TopBtn hide = find(list, HIDE_ORDER[i]);
            if (hide == null) hide = find(right, HIDE_ORDER[i]);
            if (hide != null) hiddenTop.add(hide);
            if (HIDE_ORDER[i] == B_ZOOM_OUT) {
                TopBtn zoomIn = find(right, B_ZOOM_IN);
                if (zoomIn != null) hiddenTop.add(zoomIn);
            }
        }
        list.removeAll(hiddenTop);
        right.removeAll(hiddenTop);
        if (find(list, B_ORIGINAL) == null) list.remove(find(list, B_CANVAS));
        if (find(list, B_CANVAS) == null) for (TopBtn b : list) b.joinRight = false;
        if (!hiddenTop.isEmpty()) {
            TopBtn more = btn(B_MORE, Draw.CARET_DOWN, "Ещё");
            more.separatorAfter = true;
            list.add(0, more);
        }
        for (int i = 0; i < list.size(); i++)
            list.get(i).separatorAfter = list.get(i).separatorAfter && i < list.size() - 1;

        int x = canvasLeft() + 8;
        for (TopBtn b : list) {
            b.x = x;
            x += b.w + (b.separatorAfter ? 11 : b.joinRight ? 0 : 4);
        }

        boolean zoomShown = find(right, B_ZOOM_OUT) != null && zoomLabelShown;
        int zoomLabelW = zoomShown ? textRenderer.getWidth("999%") + 6 : 0;
        int total = zoomLabelW + gear.w + (right.isEmpty() ? 0 : 11);
        for (TopBtn b : right) total += b.w + 4;
        int rx = width - 12 - (infoShown() ? infoWidth() + 12 : 0) - total;
        for (TopBtn b : right) {
            b.x = rx;
            rx += b.w + 4 + (b.id == B_ZOOM_OUT ? zoomLabelW + 4 : 0);
        }
        if (!right.isEmpty()) {
            right.get(right.size() - 1).separatorAfter = true;
            rx += 7;
        }
        gear.x = rx;
        right.add(gear);
        list.addAll(right);
        return list;
    }

    private static TopBtn find(List<TopBtn> list, int id) {
        for (TopBtn b : list) if (b.id == id) return b;
        return null;
    }

    private static String hotkey(Settings.Hot hot) {
        return Settings.get().label(hot);
    }

    private String infoText() { return "блоков: " + countNodes(); }
    private int infoWidth() { return textRenderer.getWidth(infoText()); }

    private void drawTopBar(DrawContext ctx, int mouseX, int mouseY) {
        ScreenRect area = new ScreenRect(canvasLeft(), 0, width - canvasLeft(), Theme.TOPBAR_H);
        Draw.batch(Batch.open(ctx, null, area, 512));
        Draw.rect(ctx, canvasLeft(), 0, width - canvasLeft(), Theme.TOPBAR_H,
                Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, canvasLeft(), Theme.TOPBAR_H - 1, width - canvasLeft(), 1,
                Draw.opaque(Theme.LINE));

        List<TopBtn> buttons = topButtons();
        TopBtn joinFirst = null, joinLast = null;
        for (TopBtn b : buttons) {
            if (b.joinRight) joinFirst = b;
            if (b.joinLeft) joinLast = b;
        }
        boolean outlined = Settings.outlined();
        int r = Settings.radius(18);

        for (TopBtn b : buttons) {
            boolean hover = b.enabled && hitBtn(b, mouseX, mouseY);
            boolean joined = b.joinLeft || b.joinRight;
            int fill = hover ? Theme.SURFACE_HOVER : Theme.SURFACE;
            if (b.active) fill = Draw.mix(Ui.BTN_ON, Theme.ACCENT, hover ? 0.35f : 0.22f);
            if (!b.enabled) fill = Theme.PANEL_RAISED;
            int rl = b.joinLeft ? 0 : r, rr = b.joinRight ? 0 : r;
            if (outlined && !(joined && b.active)) {
                Draw.roundRect(ctx, b.x, 6, b.w, 18, rl, rr, rr, rl,
                        Draw.argb(hover ? 0x66 : 0x28, fill));
                if (!joined) Draw.roundOutline(ctx, b.x, 6, b.w, 18, r,
                        Draw.opaque(Draw.shade(fill, hover || b.active ? 0.62f : 0.42f)));
            } else {
                Draw.roundRect(ctx, b.x, 6, b.w, 18, rl, rr, rr, rl, Draw.opaque(fill));
            }
            if (b.active && !outlined) Draw.rect(ctx, b.x + 4, 22, b.w - 8, 1,
                    Draw.opaque(Theme.ACCENT));
            int color = !b.enabled ? Theme.TEXT_FAINT
                    : b.active ? Theme.ON_ACCENT : hover ? Theme.TEXT : Theme.TEXT_DIM;
            int gx = b.x + 8;
            if (b.icon != null) {
                Draw.glyph(ctx, b.icon, gx, 6 + (18 - Draw.glyphH(b.icon)) / 2, color);
                gx += Draw.glyphW(b.icon) + 5;
            }
            if (b.label != null) Draw.text(ctx, textRenderer, b.label, gx, 11, color, false);
            if (b.separatorAfter)
                Draw.rect(ctx, b.x + b.w + 5, 8, 1, 14, Draw.opaque(Theme.LINE));
            if (b.id == B_ZOOM_OUT && zoomLabelShown) {
                String z = Math.round(zoom * 100) + "%";
                Draw.text(ctx, textRenderer, z,
                        b.x + b.w + 4 + (textRenderer.getWidth("999%") + 6 - textRenderer.getWidth(z)) / 2,
                        11, Theme.TEXT_DIM, false);
            }
        }
        if (joinFirst != null && joinLast != null) {
            int gx = joinFirst.x, gw = joinLast.x + joinLast.w - gx;
            if (outlined) Draw.roundOutline(ctx, gx, 6, gw, 18, r, Draw.opaque(Ui.BORDER));
            Draw.rect(ctx, joinLast.x, 7, 1, 16,
                    Draw.argb(outlined ? 0xFF : 0x66, outlined ? Ui.BORDER : 0x000000));
        }
        Draw.batch(null);
        if (infoShown())
            Draw.textRight(ctx, textRenderer, infoText(), width - 12, 11, Theme.TEXT_FAINT, false);
    }

    private static boolean hitBtn(TopBtn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w && my >= 6 && my < 24;
    }

    private int nodeCount = -1;

    private int countNodes() {
        if (nodeCount < 0) nodeCount = count(script.roots);
        return nodeCount;
    }

    private int count(List<Script.Root> roots) {
        int n = 0;
        for (Script.Root r : roots) n += countChain(r.chain);
        return n;
    }

    private int countChain(List<Script.Node> chain) {
        int n = 0;
        for (Script.Node k : chain) n += 1 + countChain(k.body);
        return n;
    }

    private void onTopButton(int id) {
        switch (id) {
            case B_UNDO -> undo();
            case B_REDO -> redo();
            case B_PLAY -> askExit("play");
            case B_BUILD -> askExit("build");
            case B_CLEAR -> {
                if (script.roots.isEmpty()) return;
                closeOverlays();
                pushUndo();
                script.roots.clear();
                toast("полотно очищено");
            }
            case B_ZOOM_OUT -> zoomTo(zoom / 1.15, (canvasLeft() + width) / 2.0, (Theme.TOPBAR_H + height) / 2.0);
            case B_ZOOM_IN -> zoomTo(zoom * 1.15, (canvasLeft() + width) / 2.0, (Theme.TOPBAR_H + height) / 2.0);
            case B_FIT -> fitView();
            case B_UPLOAD -> publish(null);
            case B_SETTINGS -> openSettings();
            case B_ORIGINAL -> toOriginal();
            case B_CANVAS -> { }
            default -> { }
        }
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
        double before = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target));
        double cx = (aroundX - canvasLeft() - panX) / before, cy = (aroundY - Theme.TOPBAR_H - panY) / before;
        panX = aroundX - canvasLeft() - cx * zoom;
        panY = aroundY - Theme.TOPBAR_H - cy * zoom;
    }

    private void fitView() {
        if (script.roots.isEmpty()) { zoom = 1; panX = 60; panY = 50; toast("масштаб 100%"); return; }
        Layout l = Layout.of(script, textRenderer);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Layout.Box b : l.boxes) {
            minX = Math.min(minX, b.x);
            minY = Math.min(minY, b.y);
            maxX = Math.max(maxX, b.x + b.w);
            maxY = Math.max(maxY, b.bottom());
        }
        int vw = Math.max(40, width - canvasLeft() - 40);
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
        topStamp = Integer.MIN_VALUE;
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
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(command);
        XeroCode.canvasClosed();
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
        int y = height - 34;
        Draw.round(ctx, x, y, w, 20, 6, Draw.argb(a * 0xE0 / 255, Ui.HEAD));
        Draw.roundOutline(ctx, x, y, w, 20, 6, Draw.argb(a * 0x80 / 255, Ui.BORDER));
        ctx.drawText(textRenderer, status, x + 12, y + 6, Draw.argb(a, Theme.TEXT), false);
    }

    private void drawTooltips(DrawContext ctx, int mouseX, int mouseY) {
        if (editor != null && editor.contains(mouseX, mouseY)) return;

        if (mouseY < Theme.TOPBAR_H && mouseX >= canvasLeft()) {
            for (TopBtn b : topButtons())
                if (hitBtn(b, mouseX, mouseY)) {
                    List<Text> tip = new ArrayList<>();
                    String[] parts = b.tip.split("\n");
                    tip.add(Text.literal(parts[0]));
                    for (int i = 1; i < parts.length; i++) tip.add(Text.literal("§7" + parts[i]));
                    ctx.drawTooltip(textRenderer, tip, mouseX, mouseY);
                    return;
                }
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
            lines.add(Text.literal("§8ЛКМ — список, колесо — перебрать"));
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
        if (editor != null) {
            boolean inside = editor.mouseClicked(click, doubled);
            if (editor.isClosed()) finishEditor();
            if (inside) return true;
        }

        if (button == 0 && overSplitter(mx)) {
            resizingPalette = true;
            paletteDrag = Theme.PALETTE_W;
            search.setFocused(false);
            return true;
        }
        if (my < Theme.TOPBAR_H && mx >= canvasLeft()) {
            for (TopBtn b : topButtons())
                if (b.enabled && hitBtn(b, mx, my)) {
                    if (b.id == B_MORE) openTopMenu(b.x, Theme.TOPBAR_H);
                    else onTopButton(b.id);
                    return true;
                }
            return true;
        }
        if (mx < canvasLeft()) return paletteClicked(click, doubled);
        return canvasClicked(mx, my, button);
    }

    private boolean paletteClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        search.setFocused(false);
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

    private boolean canvasClicked(double mx, double my, int button) {
        search.setFocused(false);
        Layout l = layout();
        for (int i = l.boxes.size() - 1; i >= 0; i--) {
            Layout.Box box = l.boxes.get(i);
            if (!box.contains(mouseCanvasX, mouseCanvasY)) continue;
            Layout.Chip chip = button <= 1 ? box.chipAt(mouseCanvasX, mouseCanvasY) : null;
            if (chip != null) { chipClicked(box, chip, button); return true; }
            if (!box.hitGrab(mouseCanvasX, mouseCanvasY)) continue;
            if (button == 1) { openBlockMenu(box, (int) mx, (int) my); return true; }
            if (button == 0 && box.hitTarget(mouseCanvasX, mouseCanvasY)) {
                chooseTarget(box.node, box.targetSetting, (int) mx, (int) my);
                return true;
            }
            if (button == 0 && box.card != null && cardClicked(box, (int) mx, (int) my)) return true;
            if (button != 0) break;
            startDrag(box);
            return true;
        }
        panning = true;
        return true;
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
        List<Menu.Item> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        int target = node.settingIndex(Catalog.TARGET);
        int invert = node.settingIndex(Catalog.INVERT);
        if (target >= 0) {
            items.add(new Menu.Item("Цель: " + node.marker(target), false, Draw.LOOK));
            acts.add(() -> chooseTarget(node, target, mx, my));
        }
        if (invert >= 0) {
            items.add(new Menu.Item(Catalog.INVERT_ON.equals(node.marker(invert))
                    ? "Снять «НЕ»" : "Условие «НЕ»", false, Draw.STRIKE_TEXT));
            acts.add(() -> { pushUndo(); node.cycleMarker(invert, true); });
        }
        if (node.declares()) {
            String what = node.isProcess() ? "процесса" : "функции";
            items.add(new Menu.Item("Имя " + what + "…", false, Draw.STRIKE_TEXT));
            acts.add(() -> openValue(node, Catalog.FN_NAME, mx, my, false, -1));
            items.add(new Menu.Item("Добавить параметр", false, Draw.PLUS));
            acts.add(() -> openValue(node, Catalog.FN_PARAMS, mx, my, true, -1));
            items.add(new Menu.Item("Отображаемое имя…", false, Draw.STRIKE_TEXT));
            acts.add(() -> openValue(node, Catalog.FN_DISPLAY, mx, my, false, -1));
            items.add(new Menu.Item("Описание…", false, Draw.WINDOW));
            acts.add(() -> openValue(node, Catalog.FN_DESC, mx, my, false, -1));
            items.add(new Menu.Item("Значок…", false, Draw.BRICKS));
            acts.add(() -> openValue(node, Catalog.FN_ICON, mx, my, false, -1));
        } else if (node.invokes()) {
            items.add(new Menu.Item(node.isStart() ? "Выбрать процесс…" : "Выбрать функцию…",
                    false, Draw.LOOK));
            acts.add(() -> chooseFunction(node, mx, my));
        }
        items.add(new Menu.Item("Дублировать блок", false, Draw.PLUS));
        acts.add(() -> duplicate(box, false));
        items.add(new Menu.Item("Дублировать стопку", false, Draw.PLUS));
        acts.add(() -> duplicate(box, true));
        items.add(new Menu.Item("Удалить блок", true, Draw.CROSS));
        acts.add(() -> deleteBlock(box));
        items.add(new Menu.Item("Удалить стопку", true, Draw.TRASH));
        acts.add(() -> deleteStack(box));
        menu = Menu.actions(width, height, mx, my, textRenderer, items,
                i -> { if (i >= 0 && i < acts.size()) acts.get(i).run(); });
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

    private void duplicate(Layout.Box box, boolean wholeStack) {
        pushUndo();
        Script.Root r = new Script.Root(box.x + 24, box.y + 24);
        if (wholeStack) {
            for (int i = box.index; i < box.owner.size(); i++) r.chain.add(box.owner.get(i).copy());
        } else {
            r.chain.add(box.node.copy());
        }
        script.roots.add(r);
        toast(wholeStack ? "стопка скопирована" : "блок скопирован");
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

    private void duplicateHovered() {
        if (hoverBox == null || drag != null) return;
        duplicate(hoverBox, true);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (palette.barDragging()) { palette.barDrag(click.y(), height); return true; }
        if (condPicker != null) return condPicker.mouseDragged(click, click.x(), click.y());
        if (settings != null) return settings.mouseDragged(click.x(), click.y());
        if (resizingPalette) {
            paletteDrag += dx;
            setPaletteWidth((int) Math.round(paletteDrag));
            return true;
        }
        if (editor != null) return editor.mouseDragged(click, dx, dy);
        if (draggingSearch) return search.mouseDragged(click, dx, dy);
        if (drag != null) { dragMoved = true; return true; }
        if (panning) { panX += dx; panY += dy; return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        palette.barRelease();
        if (menu != null) menu.mouseReleased();
        if (settings != null) { settings.mouseReleased(); return true; }
        panning = false;
        draggingSearch = false;
        resizingPalette = false;
        if (editor != null) editor.mouseReleased();
        if (drag == null) return super.mouseReleased(click);

        if (click.x() < canvasLeft()) {
            if (dragFromPalette && !dragMoved) placeAtCenter();
            else toast(dragFromPalette ? "отменено" : "блок удалён");
            drag = null;
            snap = null;
            dragSnapshot = null;
            return true;
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
        if (settings != null) return settings.mouseScrolled(mx, my, vAmount);
        if (menu != null && menu.mouseScrolled(mx, my, vAmount)) return true;
        if (editor != null && editor.mouseScrolled(mx, my, vAmount)) return true;
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
        if (settings != null) {
            settings.keyPressed(input);
            if (settings.isClosed()) closeSettings();
            return true;
        }
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { menu = null; return true; }
            return true;
        }
        if (editor != null) {
            editor.keyPressed(input);
            if (editor.isClosed()) finishEditor();
            return true;
        }
        Settings st = Settings.get();
        Settings.Hot hot = st.match(key, input.modifiers());
        if (hot != null && (st.mods(hot) != 0 || !search.isFocused()) && runHotkey(hot)) return true;
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
            if (drag != null) { drag = null; snap = null; toast("отменено"); return true; }
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

    private boolean runHotkey(Settings.Hot hot) {
        switch (hot) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE -> { saveScript(); toast("сохранено"); }
            case UPLOAD -> publish(null);
            case SEARCH -> { search.setFocused(true); setFocused(search); }
            case FIT -> fitView();
            case DUPLICATE -> duplicateHovered();
            case DELETE -> deleteHovered();
            case PLAY -> askExit("play");
            case BUILD -> askExit("build");
            case SETTINGS -> openSettings();
            case MODE -> toOriginal();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (condPicker != null) { condPicker.charTyped(input); return true; }
        if (settings != null) { settings.charTyped(input); return true; }
        if (menu != null) return true;
        if (editor != null) return editor.charTyped(input);
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
