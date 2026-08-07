package com.xerocode.ui;

import com.xerocode.Json;
import com.xerocode.Market;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class MarketPage implements MarketScreen.Panel {
    private static final int BANNER_H = 88, AVA = 44, BTN_H = 20, PAD = 14;
    private static final int FOOTER = 30;

    private final MarketScreen owner;
    private final TextRenderer tr;
    private Market.Module module;

    private int x, y, w, h;
    private int scroll;
    private final Ui.Bar bar = new Ui.Bar();

    private final ModuleCode code;
    private String busy = "";

    private final CodeStage stage = new CodeStage(0.25, 2.5, 1.0);
    private final BlockView.Look look = new BlockView.Look();

    private boolean confirmDelete;

    public MarketPage(MarketScreen owner, Market.Module module) {
        this.owner = owner;
        this.tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        this.module = module;
        this.code = new ModuleCode(module.id, tr);
        refresh();
        code.fetch();
    }

    private void refresh() {
        Market.one(module.id, fresh -> {
            module = fresh;
            code.forget();
        }, (said, why) -> trouble = said);
    }

    private String trouble = "";

    @Override
    public String title() { return module.name; }

    @Override
    public void place(int px0, int py0, int pw, int ph) {
        this.x = px0;
        this.y = py0;
        this.w = pw;
        this.h = ph;
        code.forget();
    }

    private boolean split() { return w >= 620; }

    private int infoW() { return split() ? Math.max(220, w * 45 / 100) : w; }

    private int topH() { return BANNER_H + 34; }

    private int[] stageRect() {
        int by = y + topH(), bh = h - topH();
        if (split()) return new int[]{x + infoW() + 1, by, w - infoW() - 1, bh};
        int cut = Math.max(90, bh * 45 / 100);
        return new int[]{x, by + bh - cut + 1, w, cut - 1};
    }

    @Override
    public void draw(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        drawTop(ctx, mouseX, mouseY);
        int by = y + topH(), bh = h - topH();
        if (split()) {
            drawInfo(ctx, x, by, infoW(), bh, mouseX, mouseY);
            Ui.vline(ctx, x + infoW(), by, bh);
        } else {
            int cut = Math.max(90, bh * 45 / 100);
            drawInfo(ctx, x, by, w, bh - cut, mouseX, mouseY);
            Ui.hairline(ctx, x, by + bh - cut, w);
        }
        int[] q = stageRect();
        drawPreview(ctx, q[0], q[1], q[2], q[3], mouseX, mouseY);
    }

    private void drawTop(DrawContext ctx, int mouseX, int mouseY) {
        MarketArt.banner(ctx, module.banner, x, y, w, BANNER_H,
                MarketArt.catColor(module.cat), 0, 0, 0, 0, 0);
        Draw.rect(ctx, x, y + BANNER_H - 34, w, 34, Draw.argb(0x88, 0x000000));
        Draw.rect(ctx, x, y + BANNER_H, w, 1, Draw.argb(0x60, 0x000000));

        int ax = x + PAD, ay = y + BANNER_H - AVA / 2;
        Draw.round(ctx, ax - 3, ay - 3, AVA + 6, AVA + 6, 12, Draw.opaque(Ui.WELL));
        MarketArt.moduleIcon(ctx, module, ax, ay, AVA, tr);

        int tx = ax + AVA + 12;
        int room = w - (tx - x) - PAD - likeW() - 8;
        Draw.textScaled(ctx, tr, Draw.ordered(Draw.fit(tr, module.name, room / 2)),
                tx, y + BANNER_H - 26, 2, 0xFFFFFF, false);

        int sy = y + BANNER_H + 8;
        int at = tx;
        at += MarketArt.author(ctx, tr, module.author, module.authorIcon, at, sy - 1, 10);
        Draw.textFit(ctx, tr, module.author, at, sy, 120, Theme.TEXT_DIM, false);
        at += Math.min(120, tr.getWidth(module.author)) + 4;
        at += MarketArt.tick(ctx, at, sy, module.authorOk);
        at += 4;
        int cw = Draw.badgeWidth(tr, module.cat);
        if (at + cw < x + w - PAD - 90) {
            Draw.badge(ctx, tr, module.cat, at, sy - 2,
                    Draw.argb(0x66, MarketArt.catColor(module.cat)),
                    Draw.readable(MarketArt.catColor(module.cat)));
            at += cw + 6;
        }
        String when = module.when();
        if (!when.isEmpty() && at + tr.getWidth(when) < x + w - PAD - 60)
            Draw.textFit(ctx, tr, when, at, sy, 120, Theme.TEXT_FAINT, false);

        drawLike(ctx, mouseX, mouseY);
    }

    private static final int LIKE_H = 20;

    private int likeInner() {
        return Draw.glyphW(Draw.HEART) + 4 + tr.getWidth(String.valueOf(module.likes));
    }

    private int likeW() { return likeInner() + 16; }

    private int likeX() { return x + w - PAD - likeW(); }

    private void drawLike(DrawContext ctx, int mouseX, int mouseY) {
        int lx = likeX(), ly = y + BANNER_H - 26, lw = likeW();
        boolean hot = Ui.hit(mouseX, mouseY, lx, ly, lw, LIKE_H);
        Draw.round(ctx, lx, ly, lw, LIKE_H, 6, Draw.argb(hot ? 0xCC : 0x88, 0x000000));
        int ink = module.liked ? Theme.DANGER : (hot ? 0xFFFFFF : 0xD8DEE9);
        int at = lx + (lw - likeInner()) / 2;
        Draw.glyph(ctx, Draw.HEART, at, ly + (LIKE_H - Draw.glyphH(Draw.HEART)) / 2, ink);
        Draw.text(ctx, tr, String.valueOf(module.likes), at + Draw.glyphW(Draw.HEART) + 4,
                ly + (LIKE_H - Ui.TEXT_H) / 2, ink, false);
    }

    private List<String> lines(int room) {
        List<String> out = new ArrayList<>();
        if (!module.summary.isEmpty()) out.addAll(Ui.wrap(tr, module.summary, room, 3));
        if (!module.descr.isEmpty()) {
            if (!out.isEmpty()) out.add("");
            for (String para : module.descr.split("\n")) {
                if (para.isBlank()) out.add("");
                else out.addAll(Ui.wrap(tr, para, room, 40));
            }
        }
        if (out.isEmpty()) out.add("Автор ничего не написал.");
        return out;
    }

    private int infoContentH(int room) {
        int n = lines(room).size();
        return n * 11 + 46 + (module.tags.isEmpty() ? 0 : 20);
    }

    private void drawInfo(DrawContext ctx, int ix, int iy, int iw, int ih,
                          int mouseX, int mouseY) {
        int room = iw - PAD * 2 - 6;
        if (room < 40 || ih < 30) return;
        int content = infoContentH(room);
        int max = Math.max(0, content - ih + PAD);
        scroll = Math.max(0, Math.min(max, scroll));

        ctx.enableScissor(ix, iy, ix + iw, iy + ih);
        int at = iy + PAD - scroll;
        for (String line : lines(room)) {
            if (at > iy + ih) break;
            if (at + 10 >= iy) {
                boolean lead = line.equals(module.summary);
                Draw.text(ctx, tr, line, ix + PAD, at, lead ? Theme.TEXT : Theme.TEXT_DIM, false);
            }
            at += 11;
        }
        at += 8;
        if (!module.tags.isEmpty()) {
            int tx = ix + PAD;
            for (String tag : module.tags) {
                int tw = Draw.badgeWidth(tr, tag);
                if (tx + tw > ix + iw - PAD) break;
                Draw.badge(ctx, tr, tag, tx, at, Draw.opaque(Ui.BTN), Theme.TEXT_DIM);
                tx += tw + 5;
            }
            at += 20;
        }
        Ui.hairline(ctx, ix + PAD, at, iw - PAD * 2);
        at += 8;
        String facts = module.blocksText() + " · "
                + Ui.plural(module.roots, "стопка", "стопки", "стопок") + " · " + module.sizeText()
                + (module.version > 1 ? " · версия " + module.version : "");
        Draw.textFit(ctx, tr, facts, ix + PAD, at, iw - PAD * 2, Theme.TEXT_FAINT, false);
        at += 12;
        String counts = "скачали " + module.downloads + " · отметили " + module.likes;
        Draw.textFit(ctx, tr, counts, ix + PAD, at, iw - PAD * 2, Theme.TEXT_FAINT, false);
        ctx.disableScissor();
        if (content > ih - PAD)
            bar.draw(ctx, ix + iw - 5, iy + 2, ih - 4, content + PAD, ih, scroll, mouseX, mouseY);
    }

    private int retryW() { return Ui.buttonW(tr, "Ещё раз"); }

    private void drawPreview(DrawContext ctx, int qx, int qy, int qw, int qh,
                             int mouseX, int mouseY) {
        int stageH = qh - FOOTER;
        stage.place(qx, qy, qw, stageH);
        Draw.rect(ctx, qx, qy, qw, qh, Draw.opaque(Theme.CANVAS));
        Layout l = code.layout();
        if (l == null) {
            String said = code.broken() ? code.trouble()
                    : code.asking() ? "гружу код модуля…" : "гружу…";
            Draw.textCenter(ctx, tr, said, qx, qy + stageH / 2 - 4, qw, qw - 30,
                    code.broken() ? Theme.DANGER : Theme.TEXT_FAINT, false);
            if (!code.asking() && code.broken())
                Ui.button(ctx, tr, mouseX, mouseY, qx + (qw - retryW()) / 2, qy + stageH / 2 + 10,
                        retryW(), 18, "Ещё раз", Ui.GHOST);
            drawButtons(ctx, qx, qy + stageH, qw, mouseX, mouseY);
            return;
        }
        if (stage.needsFit()) stage.fit(l, 12);
        look.hover = null;
        look.mx = Integer.MIN_VALUE;
        look.my = Integer.MIN_VALUE;
        stage.draw(ctx, tr, l, look);
        Draw.textRight(ctx, tr, stage.zoomText(), qx + qw - 8, qy + 6, Theme.TEXT_FAINT, false);
        drawButtons(ctx, qx, qy + stageH, qw, mouseX, mouseY);
    }

    @Override
    public String action() { return code.ready() ? "На полотно" : "Гружу…"; }

    @Override
    public boolean actionOn() { return code.ready(); }

    @Override
    public void act() { take(false); }

    private int packW() { return Ui.buttonW(tr, "В рюкзак"); }

    private boolean manages() { return module.mine || Market.admin(); }

    private String sideLabel() {
        if (!manages()) return "Пожаловаться";
        return confirmDelete ? "Точно удалить?" : "Удалить";
    }

    private int sideW() { return Ui.buttonW(tr, sideLabel()); }

    private int editW() { return manages() ? Ui.buttonW(tr, "Изменить") + 6 : 0; }

    private int lookW() { return Ui.buttonW(tr, "Осмотр"); }

    private void drawButtons(DrawContext ctx, int bx, int by, int bw, int mouseX, int mouseY) {
        Ui.hairline(ctx, bx, by, bw);
        int cy = by + 5;
        int pw = packW(), lw = lookW();
        Ui.button(ctx, tr, mouseX, mouseY, bx + bw - PAD - pw, cy, pw, BTN_H,
                "В рюкзак", Ui.GHOST, code.ready());
        if (bw > 300)
            Ui.button(ctx, tr, mouseX, mouseY, bx + bw - PAD - pw - 6 - lw, cy, lw, BTN_H,
                    "Осмотр", Ui.GHOST, code.ready());
        int left = bx + PAD;
        if (manages()) {
            int ew = editW() - 6;
            Ui.button(ctx, tr, mouseX, mouseY, left, cy, ew, BTN_H, "Изменить", Ui.GHOST);
            left += ew + 6;
        }
        int sw = sideW();
        if (left + sw <= bx + bw - PAD - pw - (bw > 300 ? lw + 6 : 0) - 8)
            Ui.button(ctx, tr, mouseX, mouseY, left, cy, sw, BTN_H, sideLabel(),
                    manages() ? Ui.DANGER : Ui.GHOST, true);
    }

    @Override
    public String hint() {
        if (!busy.isEmpty()) return busy;
        if (!trouble.isEmpty()) return trouble;
        if (module.mine && module.reports > 0)
            return "на модуль пожаловались " + module.reports + " раз — стоит проверить";
        return "";
    }

    @Override
    public boolean click(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int[] q = stageRect();
        int qx = q[0], qy = q[1], qw = q[2], qh = q[3];
        int footY = qy + qh - FOOTER;

        if (Ui.hit(mx, my, likeX(), y + BANNER_H - 26, likeW(), 20)) {
            toggleLike();
            return true;
        }
        if (my >= footY && my < qy + qh && mx >= qx)
            return footClicked(mx, my, qx, footY + 5, qw);
        if (code.broken() && Ui.hit(mx, my, qx + (qw - retryW()) / 2,
                qy + (qh - FOOTER) / 2 + 10, retryW(), 18)) {
            trouble = "";
            code.again();
            return true;
        }
        if (mx >= qx && my >= qy && my < footY) {
            if (doubled) stage.fit(code.layout(), 12);
            else stage.grab();
            return true;
        }
        bar.grabbed(mx, my, 1, 4000, v -> scroll = v);
        return true;
    }

    private boolean footClicked(double mx, double my, int bx, int cy, int bw) {
        int pw = packW(), lw = lookW();
        if (Ui.hit(mx, my, bx + bw - PAD - pw, cy, pw, BTN_H)) {
            take(true);
            return true;
        }
        if (bw > 300 && code.ready()
                && Ui.hit(mx, my, bx + bw - PAD - pw - 6 - lw, cy, lw, BTN_H)) {
            owner.openLook(module, code.payload());
            return true;
        }
        int left = bx + PAD;
        if (manages()) {
            int ew = editW() - 6;
            if (Ui.hit(mx, my, left, cy, ew, BTN_H)) {
                owner.openForm(module);
                return true;
            }
            left += ew + 6;
        }
        if (Ui.hit(mx, my, left, cy, sideW(), BTN_H)) {
            if (manages()) remove();
            else complain(left, cy);
            return true;
        }
        return true;
    }

    private void take(boolean toBackpack) {
        if (!code.ready()) { code.fetch(); return; }
        owner.install(module, code.payload(), toBackpack);
    }

    private void toggleLike() {
        if (Market.me() == null) {
            Market.hello();
            owner.toast("сначала заведём аккаунт — это одно нажатие");
            return;
        }
        if (module.mine) {
            owner.toast("свой модуль отметить нельзя");
            return;
        }
        boolean want = !module.liked;
        module.liked = want;
        module.likes = Math.max(0, module.likes + (want ? 1 : -1));
        Market.like(module.id, want, answer -> {
            module.likes = Json.num(answer, "likes");
            module.liked = want;
        }, (said, why) -> {
            module.liked = !want;
            module.likes = Math.max(0, module.likes + (want ? -1 : 1));
            owner.toast(said);
        });
    }

    private void complain(int mx, int my) {
        if (Market.me() == null) {
            Market.hello();
            owner.toast("сначала заведём аккаунт — это одно нажатие");
            return;
        }
        List<Menu.Item> items = new ArrayList<>();
        List<String> reasons = Market.reasons();
        for (String reason : reasons) items.add(new Menu.Item(reason, false, null));
        owner.openMenu(Menu.actions(owner.width, owner.height, mx, my, tr, items, i -> {
            if (i < 0 || i >= reasons.size()) return;
            busy = "отправляю жалобу…";
            Market.report(module.id, reasons.get(i), () -> {
                busy = "";
                owner.toast("жалоба отправлена — смотрители посмотрят");
            }, (said, why) -> {
                busy = "";
                owner.toast(said);
            });
        }));
    }

    private void remove() {
        if (!confirmDelete) { confirmDelete = true; return; }
        confirmDelete = false;
        busy = "удаляю…";
        Market.drop(module.id, () -> {
            busy = "";
            owner.toast("модуль снят с витрины");
            owner.showList();
            owner.reload();
        }, (said, why) -> {
            busy = "";
            owner.toast(said);
        });
    }

    @Override
    public boolean drag(Click click, double dx, double dy) {
        if (!stage.panning()) return bar.dragged(click.y(), 1, 4000, v -> scroll = v);
        stage.drag(dx, dy);
        return true;
    }

    @Override
    public void release() {
        stage.release();
        bar.release();
    }

    @Override
    public boolean wheel(double mx, double my, double amount) {
        if (stage.over(mx, my) && code.layout() != null) {
            stage.wheel(mx, my, amount);
            return true;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 30);
        return true;
    }

    @Override
    public boolean key(KeyInput in) {
        switch (in.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> take(false);
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> stage.fit(code.layout(), 12);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> stage.step(1.12);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> stage.step(1 / 1.12);
            default -> { return false; }
        }
        return true;
    }
}
