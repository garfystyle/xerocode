package com.xerocode.ui;

import com.xerocode.Backpack;
import com.xerocode.Functions;
import com.xerocode.Script;
import com.xerocode.Search;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BackpackPanel extends PickerPanel {
    public interface Taken { void apply(Backpack.Item item); }

    private static final int ROW_H = 24;
    private static final int LIST_MIN = 190;
    private static final int DET_WANT = 232;
    private static final int BTN_H = 16, ICON = 16;

    private final Taken done;
    private final String takeLabel;

    private List<Backpack.Item> hits = List.of();
    private String category;
    private String selected = "";
    private int scroll, rows;
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Grab hold = new Ui.Grab();

    private TextFieldWidget nameField;
    private String renameId = "";
    private String confirmId = "";

    private Layout preview;
    private String previewId = "";
    private int thumbX, thumbY, thumbW, thumbH;

    private boolean viewing;
    private final CodeStage stage = new CodeStage(VIEW_MIN, VIEW_MAX, 1.0);
    private final BlockView.Look vlook = new BlockView.Look();

    public BackpackPanel(TextRenderer tr, int screenW, int screenH, Taken done) {
        this(tr, screenW, screenH, done, "", "");
    }

    public BackpackPanel(TextRenderer tr, int screenW, int screenH, Taken done,
                         String takeLabel, String startId) {
        super(tr, screenW, screenH, Theme.ACCENT);
        this.done = done;
        this.takeLabel = takeLabel == null ? "" : takeLabel;
        List<Backpack.Item> have = Backpack.all();
        if (!have.isEmpty()) selected = have.get(0).id;
        if (startId != null && byId(startId) != null) selected = startId;
        layout();
        refresh(false);
        show(indexOf(selected));
    }

    @Override
    protected void layout() {
        int panelW = Ui.fitW(screenW, 660);
        int measured = tr.getWidth("Всё") + 46;
        for (String c : Backpack.categories().keySet())
            measured = Math.max(measured, tr.getWidth(c) + 46);
        int rail = railW(panelW, measured);
        int det = Math.min(DET_WANT, panelW - rail - Math.min(LIST_MIN, panelW * 45 / 100));
        det = det < 150 ? 0 : det;

        int wanted = Ui.fitH(screenH, HEAD_H + 2 + 11 * ROW_H + FOOT_H);
        this.rows = Math.max(3, (wanted - HEAD_H - FOOT_H - 2) / ROW_H);
        place(panelW, HEAD_H + 2 + rows * ROW_H + FOOT_H, rail, det, "найти в рюкзаке…");
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        preview = null;
        previewId = "";
        thumbW = 0;
        if (viewing) fitViewer();
    }

    @Override
    protected int bodyH() { return rows * ROW_H; }

    private int listX() { return railX() + railW + 1; }
    private int listW() { return (detW == 0 ? x + w - 1 : detX()) - listX(); }

    @Override
    protected String title() {
        Backpack.Item it = chosen();
        if (viewing && it != null) return "ОСМОТР  ·  " + it.name;
        int all = Backpack.count();
        return all == 0 ? "РЮКЗАК КОДА" : "РЮКЗАК КОДА  ·  " + all;
    }

    @Override
    protected boolean searchShown() { return !viewing; }

    @Override
    protected void refresh(boolean resetScroll) {
        String q = search == null ? "" : search.getText().trim();
        List<Backpack.Item> pool = new ArrayList<>();
        for (Backpack.Item i : Backpack.all())
            if (category == null || category.equals(i.category())) pool.add(i);
        hits = q.isEmpty() ? pool
                : Search.rank(pool, q, pool.size(), i -> new Search.Fields(
                        i.name, i.searchText(), i.category(), ""));
        if (resetScroll) scroll = 0;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private int maxScroll() { return Math.max(0, hits.size() - rows); }

    private Backpack.Item byId(String id) {
        for (Backpack.Item i : Backpack.all()) if (i.id.equals(id)) return i;
        return null;
    }

    private Backpack.Item chosen() { return byId(selected); }

    private Backpack.Item focused() {
        return hovered >= 0 && hovered < hits.size() ? hits.get(hovered) : chosen();
    }

    private int indexOf(String id) {
        for (int i = 0; i < hits.size(); i++) if (hits.get(i).id.equals(id)) return i;
        return -1;
    }

    private void show(int i) {
        if (i < 0) return;
        if (i < scroll) scroll = i;
        else if (i >= scroll + rows) scroll = i - rows + 1;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private void pick(String id) {
        if (!id.equals(selected)) commitRename();
        selected = id;
        confirmId = "";
    }

    private void move(int delta) {
        if (hits.isEmpty()) return;
        int i = indexOf(selected);
        i = i < 0 ? (delta > 0 ? 0 : hits.size() - 1)
                : Math.max(0, Math.min(hits.size() - 1, i + delta));
        pick(hits.get(i).id);
        show(i);
    }

    @Override
    protected List<RailRow> railRows() {
        List<RailRow> out = new ArrayList<>();
        out.add(new RailRow(null, "Всё", Backpack.count()));
        for (Map.Entry<String, Integer> e : Backpack.categories().entrySet())
            out.add(new RailRow(null, e.getKey(), e.getValue()));
        return out;
    }

    @Override
    protected int railRowH() { return 16; }

    @Override
    protected int railActive() {
        if (category == null) return 0;
        int i = 1;
        for (String c : Backpack.categories().keySet()) {
            if (category.equals(c)) return i;
            i++;
        }
        return -1;
    }

    @Override
    protected void railChosen(int index) {
        commitRename();
        category = index == 0 ? null
                : new ArrayList<>(Backpack.categories().keySet()).get(index - 1);
        refresh(true);
        show(indexOf(selected));
    }

    @Override
    protected void drawBody(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int lx = listX(), ly = bodyY(), lw = listW(), lh = bodyH();
        Draw.rect(ctx, lx, ly, lw, lh, Draw.opaque(Ui.WELL));
        if (hits.isEmpty()) { drawEmpty(ctx, lx, ly, lw, lh); return; }

        ctx.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i < 0 || i >= hits.size()) break;
            drawRow(ctx, hits.get(i), i, lx, ly + r * ROW_H, lw);
        }
        ctx.disableScissor();
        bar.draw(ctx, lx + lw - 4, ly + 1, lh - 2, hits.size() * ROW_H, lh, scroll * ROW_H,
                lastMx, lastMy);
    }

    private void drawRow(DrawContext ctx, Backpack.Item it, int i, int lx, int ry, int lw) {
        boolean on = it.id.equals(selected);
        if (on) {
            Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.BTN_ON));
            Draw.rect(ctx, lx, ry, 2, ROW_H, Draw.opaque(Theme.ACCENT));
        } else if (i == hovered) {
            Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.BTN_HOVER));
        } else if (i % 2 == 1) {
            Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.WELL));
        }
        Draw.rect(ctx, lx + 4, ry + 4, 2, ROW_H - 8, Draw.opaque(Draw.shade(it.color(), -0.1f)));
        ctx.drawItem(it.icon(), lx + 10, ry + 4);

        if (it.id.equals(renameId) && nameField != null) {
            int fx = lx + 30, fw = lw - 36;
            Ui.input(ctx, fx, ry + 3, fw, 18, true);
            Ui.width(nameField, fw - 12);
            nameField.setX(fx + 6);
            nameField.setY(ry + 9);
            nameField.render(ctx, lastMx, lastMy, 0);
            Ui.placeholder(ctx, tr, nameField);
            return;
        }

        String when = Backpack.when(it.at);
        int whenW = when.isEmpty() ? 0 : tr.getWidth(when) + 8;
        Draw.textFit(ctx, tr, it.name, lx + 30, ry + 4, lw - 36 - whenW,
                on || i == hovered ? Theme.TEXT : Theme.TEXT_DIM, false);
        Draw.textFit(ctx, tr, it.subtitle(), lx + 30, ry + 14, lw - 36 - whenW,
                Theme.TEXT_FAINT, false);
        if (!when.isEmpty())
            Draw.textRight(ctx, tr, when, lx + lw - 8, ry + 4, Theme.TEXT_FAINT, false);
    }

    private void drawEmpty(DrawContext ctx, int lx, int ly, int lw, int lh) {
        boolean searching = !Backpack.all().isEmpty();
        int cy = ly + Math.max(10, lh / 2 - 26);
        Draw.glyph(ctx, Draw.PACK, lx + (lw - Draw.glyphW(Draw.PACK)) / 2, cy, Theme.LINE);
        cy += Draw.glyphH(Draw.PACK) + 8;
        Draw.textCenter(ctx, tr, searching ? "ничего не найдено" : "Рюкзак пуст",
                lx, cy, lw, lw - 20, Theme.TEXT_DIM, false);
        if (searching) return;
        cy += 14;
        Draw.textCenter(ctx, tr, "правый клик по блоку → «Стопку в рюкзак»",
                lx, cy, lw, lw - 20, Theme.TEXT_FAINT, false);
        Draw.textCenter(ctx, tr, "или перетащи стопку в карман внизу справа",
                lx, cy + 11, lw, lw - 20, Theme.TEXT_FAINT, false);
    }

    @Override
    protected void drawDetails(DrawContext ctx) {
        if (viewing) { drawViewer(ctx); return; }
        thumbW = 0;
        if (!detailsFrame(ctx)) return;
        Backpack.Item it = focused();
        if (it == null) {
            detailsEmpty(ctx, Backpack.all().isEmpty()
                    ? "Сюда складываются куски кода: событие со всем телом, одна стопка, один блок."
                    : "Выбери кусок слева — здесь будет его вид.");
            return;
        }
        int at = detailsHead(ctx, it.icon(), it.name, it.subtitle(),
                Draw.readable(it.color()));
        String when = Backpack.when(it.at);
        if (!when.isEmpty()) {
            Draw.textFit(ctx, tr, "убрано " + when, detailsX(), at, detailsInner() - 12,
                    Theme.TEXT_FAINT, false);
            at += 12;
        }
        thumbX = detailsX();
        thumbY = at;
        thumbW = detailsInner();
        thumbH = detailsBottom() - at;
        if (thumbH < 24) { thumbW = 0; return; }
        boolean hot = it.id.equals(selected)
                && Ui.hit(lastMx, lastMy, thumbX, thumbY, thumbW, thumbH);
        drawPreview(ctx, it, thumbX, thumbY, thumbW, thumbH);
        Draw.glyph(ctx, Draw.SEARCH, thumbX + thumbW - Draw.glyphW(Draw.SEARCH) - 3, thumbY + 3,
                hot ? Theme.ACCENT : Theme.LINE);
        if (!hot) return;
        Draw.roundOutline(ctx, thumbX - 2, thumbY - 2, thumbW + 4, thumbH + 4, 3,
                Draw.argb(0x99, Theme.ACCENT));
    }

    private Layout previewOf(Backpack.Item it) {
        if (it.id.equals(previewId) && preview != null) return preview;
        Script tmp = new Script();
        for (Backpack.Part part : it.parts) {
            Script.Root root = new Script.Root(part.x, part.y);
            root.chain.addAll(part.chain);
            tmp.roots.add(root);
        }
        Functions.rebuild(tmp);
        preview = Layout.of(tmp, tr);
        previewId = it.id;
        return preview;
    }

    private void drawPreview(DrawContext ctx, Backpack.Item it, int px, int py, int pw, int ph) {
        if (pw < 30 || ph < 24) return;
        Layout l = previewOf(it);
        if (l.boxes.isEmpty()) return;
        int[] b = l.bounds(2);
        float scale = Math.max(0.22f,
                Math.min(1f, Math.min(pw / (float) b[2], ph / (float) b[3])));
        int ox = px + Math.max(0, (pw - Math.round(b[2] * scale)) / 2);
        int oy = py + Math.max(0, Math.min(6, (ph - Math.round(b[3] * scale)) / 2));

        ScreenRect area = new ScreenRect(px, py, pw, ph);
        ctx.enableScissor(px, py, px + pw, py + ph);
        SmoothText.clip(area);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(scale, scale);
        m.translate(-b[0], -b[1]);
        BlockView.paint(ctx, tr, l, area, BlockView.Look.PLAIN);
        m.popMatrix();
        SmoothText.clip(null);
        ctx.disableScissor();
    }

    private static final int VIEW_HEAD = 20, VIEW_PAD = 12;
    private static final double VIEW_MIN = 0.25, VIEW_MAX = 3.0;

    private int viewX() { return x + 1; }
    private int viewY() { return bodyY(); }
    private int viewW() { return w - 2; }
    private int stageY() { return viewY() + VIEW_HEAD; }
    private int stageH() { return bodyH() - VIEW_HEAD; }

    private void openViewer() {
        if (chosen() == null) return;
        commitRename();
        viewing = true;
        stage.release();
        thumbW = 0;
        if (search != null) search.setFocused(false);
        fitViewer();
    }

    private void closeViewer() {
        viewing = false;
        stage.release();
        if (search != null) search.setFocused(true);
    }

    private void fitViewer() {
        Backpack.Item it = chosen();
        if (it == null) return;
        stage.place(viewX(), stageY(), viewW(), stageH());
        stage.fit(previewOf(it), VIEW_PAD);
    }

    private void zoomStep(double factor) {
        stage.step(factor);
        stage.keepOnScreen(preview, 40);
    }

    private void pan(double dx, double dy) {
        stage.drag(dx, dy);
        stage.keepOnScreen(preview, 40);
    }

    private int zoomLabelW() { return tr.getWidth("999%") + 4; }

    private int zoomLabelX() { return viewBtnX(0) - zoomLabelW() - 4; }

    private int viewBtnX(int i) {
        int right = viewX() + viewW() - 4;
        return right - ICON * (3 - i) - 2 * (2 - i);
    }

    private int backW() { return Ui.buttonW(tr, Draw.CHEVRON_LEFT, "Назад"); }

    private void drawViewer(DrawContext ctx) {
        Backpack.Item it = chosen();
        if (it == null) { closeViewer(); return; }
        int vx = viewX(), vy = viewY(), vw = viewW();
        Draw.rect(ctx, vx, vy, vw, bodyH(), Draw.opaque(Ui.WELL));

        Layout l = previewOf(it);
        stage.place(vx, stageY(), vw, stageH());
        if (stage.needsFit()) stage.fit(l, VIEW_PAD);
        Layout.Box vhover = stage.boxAt(l, lastMx, lastMy);
        vlook.hover = vhover;
        vlook.mx = Integer.MIN_VALUE;
        vlook.my = Integer.MIN_VALUE;
        stage.draw(ctx, tr, l, vlook);

        Draw.rect(ctx, vx, vy, vw, VIEW_HEAD, Draw.opaque(Ui.RAIL));
        Ui.hairline(ctx, vx, vy + VIEW_HEAD, vw);
        int bw = backW();
        Ui.glyphButton(ctx, tr, lastMx, lastMy, vx + 4, vy + 2, bw, ICON,
                Draw.CHEVRON_LEFT, "Назад", Ui.GHOST, true);
        int hintW = zoomLabelX() - (vx + 8 + bw) - 8;
        if (hintW > 120)
            Draw.textFit(ctx, tr, it.blocksText(),
                    vx + 8 + bw, vy + (VIEW_HEAD - Ui.TEXT_H) / 2, hintW,
                    Theme.TEXT_FAINT, false);
        Draw.textRight(ctx, tr, stage.zoomText(),
                zoomLabelX() + zoomLabelW() - 2, vy + (VIEW_HEAD - Ui.TEXT_H) / 2,
                Theme.TEXT_FAINT, false);
        Ui.iconButton(ctx, lastMx, lastMy, viewBtnX(0), vy + 2, ICON, Draw.MINUS, Ui.GHOST,
                !stage.atMin());
        Ui.iconButton(ctx, lastMx, lastMy, viewBtnX(1), vy + 2, ICON, Draw.PLUS, Ui.GHOST,
                !stage.atMax());
        Ui.iconButton(ctx, lastMx, lastMy, viewBtnX(2), vy + 2, ICON, Draw.FIT, Ui.GHOST, true);

        String said = vhover == null ? null : blockLine(vhover);
        if (said != null) {
            int tw = Math.min(vw - 16, tr.getWidth(said) + 14);
            int ty = stageY() + stageH() - 20;
            Draw.round(ctx, vx + 8, ty, tw, 15, 4, Draw.argb(0xD8, Ui.HEAD));
            Draw.roundOutline(ctx, vx + 8, ty, tw, 15, 4, Draw.argb(0x88, Ui.BORDER));
            Draw.textFit(ctx, tr, said, vx + 15, ty + 4, tw - 14, Theme.TEXT, false);
        }
    }

    private static String blockLine(Layout.Box box) {
        String name = (box.node.inverted() ? Layout.INVERT_PREFIX : "") + box.node.action.name;
        String cat = box.node.action.category == null ? "" : box.node.action.category.name;
        return cat.isEmpty() ? name : name + "  ·  " + cat;
    }

    private boolean viewerClicked(int mx, int my, boolean doubled) {
        int vy = viewY();
        if (Ui.hit(mx, my, viewX() + 4, vy + 2, backW(), ICON)) { closeViewer(); return true; }
        if (Ui.hit(mx, my, viewBtnX(0), vy + 2, ICON, ICON)) { zoomStep(1 / 1.25); return true; }
        if (Ui.hit(mx, my, viewBtnX(1), vy + 2, ICON, ICON)) { zoomStep(1.25); return true; }
        if (Ui.hit(mx, my, viewBtnX(2), vy + 2, ICON, ICON)) { fitViewer(); return true; }
        if (my < stageY()) return true;
        if (doubled) fitViewer(); else stage.grab();
        return true;
    }

    private boolean renaming() { return !renameId.isEmpty() && nameField != null; }

    private void startRename(Backpack.Item it) {
        if (it == null) return;
        commitRename();
        renameId = it.id;
        nameField = Ui.field(tr, it.name, "имя куска", Backpack.NAME_MAX);
        nameField.setFocused(true);
        if (search != null) search.setFocused(false);
        show(indexOf(it.id));
    }

    private void commitRename() {
        if (!renaming()) { renameId = ""; nameField = null; return; }
        Backpack.rename(byId(renameId), nameField.getText());
        renameId = "";
        nameField = null;
        previewId = "";
    }

    private void cancelRename() {
        renameId = "";
        nameField = null;
    }

    private void deleteChosen() {
        Backpack.Item it = chosen();
        if (it == null) return;
        if (!it.id.equals(confirmId)) { confirmId = it.id; return; }
        int at = indexOf(it.id);
        cancelRename();
        Backpack.remove(it);
        confirmId = "";
        preview = null;
        previewId = "";
        refresh(false);
        selected = hits.isEmpty() ? "" : hits.get(Math.max(0, Math.min(hits.size() - 1, at))).id;
        show(indexOf(selected));
    }

    private String deleteLabel() {
        Backpack.Item it = chosen();
        return it != null && it.id.equals(confirmId) ? "Точно?" : "Удалить";
    }

    private int deleteW() {
        return Math.max(Ui.buttonW(tr, "Удалить"), Ui.buttonW(tr, "Точно?"));
    }

    private String renameLabel(int room) {
        if (renaming()) return "Готово";
        String full = "Переименовать";
        return ICON + 6 + Ui.buttonW(tr, full) + 6 + deleteW() <= room ? full : "Имя";
    }

    private int renameW(int room) { return Ui.buttonW(tr, renameLabel(room)); }

    private int footerRoom() { return w - 2 * PAD - finishW() - 74; }

    private boolean inspectButton() { return !viewing && chosen() != null; }

    private int renameX() { return x + PAD + ICON + 6; }

    private boolean itemButtons(int room) {
        return inspectButton() && ICON + 6 + renameW(room) + 6 + deleteW() <= room;
    }

    @Override
    protected void drawFooterLeft(DrawContext ctx, int mouseX, int mouseY, int room) {
        int fy = footY2();
        if (!inspectButton()) { super.drawFooterLeft(ctx, mouseX, mouseY, room); return; }
        Ui.iconButton(ctx, mouseX, mouseY, x + PAD, fy, ICON, Draw.SEARCH, Ui.GHOST, true);
        if (!itemButtons(room)) {
            Draw.textFit(ctx, tr, footerHint(), renameX(), fy + 4, room - ICON - 6,
                    Theme.TEXT_FAINT, false);
            return;
        }
        int rw = renameW(room);
        Ui.button(ctx, tr, mouseX, mouseY, renameX(), fy, rw, BTN_H, renameLabel(room), Ui.GHOST);
        Ui.button(ctx, tr, mouseX, mouseY, renameX() + rw + 6, fy, deleteW(), BTN_H,
                deleteLabel(), Ui.DANGER);
    }

    @Override
    protected String footerHint() {
        if (renaming()) return "Enter — сохранить имя, Esc — отменить";
        if (Backpack.all().isEmpty()) return "рюкзак один на все миры";
        return "";
    }

    @Override
    protected String finishLabel() { return takeLabel.isEmpty() ? "Достать" : takeLabel; }

    @Override
    protected boolean canFinish() { return chosen() != null; }

    @Override
    protected void finish() {
        Backpack.Item it = chosen();
        if (it == null) return;
        commitRename();
        done.apply(it);
        closed = true;
    }

    @Override
    protected int indexAt(double mx, double my) {
        if (viewing) return -1;
        int lx = listX(), ly = bodyY();
        if (mx < lx || mx >= lx + listW() || my < ly || my >= ly + bodyH()) return -1;
        int i = scroll + (int) ((my - ly) / ROW_H);
        return i >= 0 && i < hits.size() ? i : -1;
    }

    @Override
    protected boolean bodyClicked(Click click, boolean doubled, int mx, int my) {
        int fy = footY2(), room = footerRoom();
        if (inspectButton() && Ui.hit(mx, my, x + PAD, fy, ICON, BTN_H)) {
            openViewer();
            return true;
        }
        if (itemButtons(room)) {
            int rw = renameW(room);
            if (Ui.hit(mx, my, renameX(), fy, rw, BTN_H)) {
                if (renaming()) commitRename(); else startRename(chosen());
                return true;
            }
            if (Ui.hit(mx, my, renameX() + rw + 6, fy, deleteW(), BTN_H)) {
                deleteChosen();
                return true;
            }
        }
        if (thumbW > 0 && Ui.hit(mx, my, thumbX, thumbY, thumbW, thumbH)) {
            openViewer();
            return true;
        }
        if (renaming() && nameField != null
                && Ui.hit(mx, my, nameField.getX() - 6, nameField.getY() - 6,
                        nameField.getWidth() + 12, 18)) {
            if (!nameField.mouseClicked(click, doubled)) nameField.onClick(click, doubled);
            hold.take(nameField);
            return true;
        }
        if (bar.grabbed(mx, my, ROW_H, maxScroll(), v -> scroll = v)) return true;
        int i = indexAt(mx, my);
        if (i >= 0) {
            Backpack.Item it = hits.get(i);
            if (doubled && it.id.equals(selected) && !renaming()) { finish(); return true; }
            pick(it.id);
            return true;
        }
        commitRename();
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (viewing && Ui.hit(mx, my, viewX(), viewY(), viewW(), bodyH()))
            return viewerClicked(mx, my, doubled);
        if (renaming() && Ui.hit(mx, my, searchX(), y + 6, searchW(), 16)) commitRename();
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (stage.panning()) {
            stage.drag(dx, dy);
            stage.keepOnScreen(preview, 40);
            return true;
        }
        if (bar.dragged(click.y(), ROW_H, maxScroll(), v -> scroll = v)) return true;
        if (hold.drag(click, dx, dy)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public void mouseReleased() {
        bar.release();
        hold.release();
        stage.release();
        super.mouseReleased();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (viewing) {
            if (Ui.hit((int) mx, (int) my, viewX(), stageY(), viewW(), stageH()))
                zoomStep(amount > 0 ? 1.12 : 1 / 1.12);
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 3));
        return true;
    }

    @Override
    protected boolean bodyKey(KeyInput in) {
        if (viewing) {
            switch (in.key()) {
                case GLFW.GLFW_KEY_ESCAPE -> closeViewer();
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoomStep(1.25);
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoomStep(1 / 1.25);
                case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> fitViewer();
                case GLFW.GLFW_KEY_UP -> pan(0, 24);
                case GLFW.GLFW_KEY_DOWN -> pan(0, -24);
                case GLFW.GLFW_KEY_LEFT -> pan(24, 0);
                case GLFW.GLFW_KEY_RIGHT -> pan(-24, 0);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finish();
                default -> { }
            }
            return true;
        }
        if (renaming()) {
            switch (in.key()) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitRename();
                case GLFW.GLFW_KEY_ESCAPE -> cancelRename();
                default -> nameField.keyPressed(in);
            }
            return true;
        }
        switch (in.key()) {
            case GLFW.GLFW_KEY_DOWN -> { move(1); return true; }
            case GLFW.GLFW_KEY_UP -> { move(-1); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { move(rows); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { move(-rows); return true; }
            case GLFW.GLFW_KEY_F2 -> { startRename(chosen()); return true; }
            case GLFW.GLFW_KEY_DELETE -> {
                if (search != null && search.isFocused() && !search.getText().isEmpty())
                    return false;
                deleteChosen();
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public boolean charTyped(CharInput in) {
        if (viewing) return true;
        if (renaming()) return nameField.charTyped(in);
        return super.charTyped(in);
    }
}
