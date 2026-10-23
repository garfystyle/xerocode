package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.Backpack;
import com.xerocode.ModuleActions;
import com.xerocode.Modules;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;

public final class ModulePage implements ModulesScreen.Panel {
    private static final int BANNER_H = 88, AVA = 44, BTN_H = 20, PAD = 14;
    private static final int FOOTER = 30, STARS_H = 20;
    private static final String FIND = "Установить", LOOK = "Код", PACK = "В рюкзак";

    private final ModulesScreen owner;
    private final Font tr;
    private final Modules.Module module;

    private int x, y, w, h;
    private int scroll;
    private final Ui.Bar bar = new Ui.Bar();

    private final ModuleCode code;
    private final CodeStage stage = new CodeStage(0.25, 2.5, 1.0);
    private final BlockView.Look look = new BlockView.Look();

    private List<FormattedCharSequence> lines;
    private int linesW = -1;
    private boolean linesLight;

    public ModulePage(ModulesScreen owner, Modules.Module module) {
        this.owner = owner;
        this.tr = net.minecraft.client.Minecraft.getInstance().font;
        this.module = module;
        this.code = new ModuleCode(module, tr);
        code.fetch();
    }

    @Override
    public String title() { return module.label; }

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

    private int cut(int bh) { return Math.min(bh, Math.max(90, bh * 45 / 100)); }

    private int[] stageRect() {
        int by = y + topH(), bh = h - topH();
        if (split()) return new int[]{x + infoW() + 1, by, w - infoW() - 1, bh};
        int cut = cut(bh);
        return new int[]{x, by + bh - cut + 1, w, cut - 1};
    }

    @Override
    public void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        drawTop(ctx, mouseX, mouseY);
        int by = y + topH(), bh = h - topH();
        if (split()) {
            drawInfo(ctx, x, by, infoW(), bh, mouseX, mouseY);
            Ui.vline(ctx, x + infoW(), by, bh);
        } else {
            int cut = cut(bh);
            drawInfo(ctx, x, by, w, bh - cut, mouseX, mouseY);
            Ui.hairline(ctx, x, by + bh - cut, w);
        }
        int[] q = stageRect();
        drawPreview(ctx, q[0], q[1], q[2], q[3], mouseX, mouseY);
    }

    private String starsText() { return String.valueOf(ModuleActions.stars(module)); }

    private int starsInner() { return Draw.glyphW(Draw.STAR) + 4 + tr.width(starsText()); }

    private int starsW() { return starsInner() + 16; }

    private void drawTop(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ModuleArt.banner(ctx, module, x, y, w, BANNER_H, 0, 0, 0, 0, 0);
        Draw.rect(ctx, x, y + BANNER_H - 34, w, 34, Draw.argb(0x88, 0x000000));
        Draw.rect(ctx, x, y + BANNER_H, w, 1, Draw.argb(0x60, 0x000000));

        int ax = x + PAD, ay = y + BANNER_H - AVA / 2;
        Draw.round(ctx, ax - 3, ay - 3, AVA + 6, AVA + 6, 12, Draw.opaque(Ui.WELL));
        ModuleArt.icon(ctx, module, ax, ay, AVA);

        int tx = ax + AVA + 12;
        int room = w - (tx - x) - PAD - starsW() - 8;
        Draw.textScaled(ctx, tr, ModuleArt.title(tr, module, Math.max(10, room / 2), true),
                tx, y + BANNER_H - 26, 2, 0xFFFFFF, false);
        drawStars(ctx, mouseX, mouseY);

        int sy = y + BANNER_H + 8;
        int at = tx, end = x + w - PAD;
        String shown = ModuleActions.idOf(module) == null ? module.name : ModuleActions.idOf(module);
        int nameW = Math.min(160, Math.max(20, end - at));
        Draw.textFit(ctx, tr, shown, at, sy, nameW, Theme.TEXT_DIM, false);
        at += Math.min(nameW, tr.width(shown)) + 8;
        String ok = "проверенный";
        if (module.approved && at + Draw.glyphW(Draw.CHECK) + 3 + tr.width(ok) < end - 60) {
            at += ModuleArt.tick(ctx, at, sy, true);
            Draw.text(ctx, tr, ok, at, sy, Theme.OK, false);
            at += tr.width(ok) + 8;
        }
        String cat = module.categoryLabel();
        int cw = Draw.badgeWidth(tr, cat);
        if (at + cw < end - 60) {
            int color = ModuleArt.catColor(module.category);
            Draw.badge(ctx, tr, cat, at, sy - 2, Draw.argb(0x66, color), Draw.readable(color));
            at += cw + 6;
        }
        String when = module.when();
        if (!when.isEmpty() && at + tr.width(when) < end)
            Draw.text(ctx, tr, when, at, sy, Theme.TEXT_FAINT, false);
    }

    private int starsX() { return x + w - PAD - starsW(); }

    private int starsY() { return y + BANNER_H - 26; }

    private boolean overStars(double mx, double my) {
        return !Env.browser() && Ui.hit(mx, my, starsX(), starsY(), starsW(), STARS_H);
    }

    private void drawStars(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int sw = starsW(), sx = starsX(), sy = starsY();
        Draw.round(ctx, sx, sy, sw, STARS_H, 6, Draw.argb(overStars(mouseX, mouseY) ? 0xCC : 0x88, 0x000000));
        int at = sx + (sw - starsInner()) / 2;
        Draw.glyph(ctx, Draw.STAR, at, sy + (STARS_H - Draw.glyphH(Draw.STAR)) / 2,
                ModuleActions.starred(module) ? ModuleArt.STAR_ON : ModuleArt.STAR_OFF);
        Draw.text(ctx, tr, starsText(), at + Draw.glyphW(Draw.STAR) + 4,
                sy + (STARS_H - Ui.TEXT_H) / 2, 0xD8DEE9, false);
    }

    private List<FormattedCharSequence> lines(int room) {
        if (lines != null && linesW == room && linesLight == Theme.LIGHT) return lines;
        lines = ModuleArt.about(tr, module, room);
        if (lines.isEmpty()) lines.add(Draw.ordered("Автор ничего не написал."));
        linesW = room;
        linesLight = Theme.LIGHT;
        return lines;
    }

    private void drawInfo(GuiGraphicsExtractor ctx, int ix, int iy, int iw, int ih,
                          int mouseX, int mouseY) {
        int room = iw - PAD * 2 - 6;
        if (room < 40 || ih < 30) return;
        List<FormattedCharSequence> text = lines(room);
        int content = text.size() * 11 + 46;
        int max = Math.max(0, content - ih + PAD);
        scroll = Math.max(0, Math.min(max, scroll));

        ctx.enableScissor(ix, iy, ix + iw, iy + ih);
        int at = iy + PAD - scroll;
        for (FormattedCharSequence line : text) {
            if (at > iy + ih) break;
            if (at + 10 >= iy) Draw.text(ctx, tr, line, ix + PAD, at, Theme.TEXT_DIM, false);
            at += 11;
        }
        at += 8;
        Ui.hairline(ctx, ix + PAD, at, iw - PAD * 2);
        at += 8;
        String made = Backpack.when(module.createdAt);
        String facts = (module.version.isEmpty() ? "" : "версия " + module.version)
                + (made.isEmpty() ? "" : (module.version.isEmpty() ? "" : " · ") + "создан " + made);
        Draw.textFit(ctx, tr, facts, ix + PAD, at, iw - PAD * 2, Theme.TEXT_FAINT, false);
        at += 12;
        String counts = "установок " + module.installs + " · звёзд " + module.stars;
        Draw.textFit(ctx, tr, counts, ix + PAD, at, iw - PAD * 2, Theme.TEXT_FAINT, false);
        ctx.disableScissor();
        if (content > ih - PAD)
            bar.draw(ctx, ix + iw - 5, iy + 2, ih - 4, content + PAD, ih, scroll, mouseX, mouseY);
    }

    private int retryW() { return Ui.buttonW(tr, "Ещё раз"); }

    private void drawPreview(GuiGraphicsExtractor ctx, int qx, int qy, int qw, int qh,
                             int mouseX, int mouseY) {
        Draw.rect(ctx, qx, qy, qw, qh, Draw.opaque(Theme.CANVAS));
        if (!module.open) {
            Draw.textCenter(ctx, tr, "автор закрыл код модуля", qx, qy + qh / 2 - 10, qw, qw - 30,
                    Theme.TEXT_DIM, false);
            Draw.textCenter(ctx, tr, "поставить его можно только через /module", qx, qy + qh / 2 + 2,
                    qw, qw - 30, Theme.TEXT_FAINT, false);
            return;
        }
        int stageH = Math.max(0, qh - FOOTER);
        stage.place(qx, qy, qw, stageH);
        Layout l = code.layout();
        if (stageH < 24) {
            drawButtons(ctx, qx, qy + stageH, qw, mouseX, mouseY);
            return;
        }
        if (l == null) {
            String said = code.broken() ? code.trouble() : "гружу код модуля…";
            Draw.textCenter(ctx, tr, said, qx, qy + stageH / 2 - 4, qw, qw - 30,
                    code.broken() ? Theme.DANGER : Theme.TEXT_FAINT, false);
            if (!code.asking() && code.broken())
                Ui.button(ctx, tr, mouseX, mouseY, qx + (qw - retryW()) / 2, qy + stageH / 2 + 10,
                        retryW(), 18, "Ещё раз", Ui.GHOST);
        } else {
            if (stage.needsFit()) stage.fit(l, 12);
            look.hover = null;
            look.mx = Integer.MIN_VALUE;
            look.my = Integer.MIN_VALUE;
            stage.draw(ctx, tr, l, look);
            Draw.textRight(ctx, tr, stage.zoomText(), qx + qw - 8, qy + 6, Theme.TEXT_FAINT, false);
        }
        drawButtons(ctx, qx, qy + stageH, qw, mouseX, mouseY);
    }

    private int[] row(int bx, int bw) {
        int packX = bx + bw - PAD - Ui.buttonW(tr, PACK);
        int lookX = bw > 300 ? packX - 6 - Ui.buttonW(tr, LOOK) : -1;
        int edge = (lookX >= 0 ? lookX : packX) - 8;
        int findX = !Env.browser() && bx + PAD + Ui.buttonW(tr, FIND) <= edge ? bx + PAD : -1;
        return new int[]{packX, lookX, findX};
    }

    private void drawButtons(GuiGraphicsExtractor ctx, int bx, int by, int bw, int mouseX, int mouseY) {
        Ui.hairline(ctx, bx, by, bw);
        int cy = by + 5;
        int[] at = row(bx, bw);
        Ui.button(ctx, tr, mouseX, mouseY, at[0], cy, Ui.buttonW(tr, PACK), BTN_H, PACK, Ui.GHOST,
                code.ready());
        if (at[1] >= 0)
            Ui.button(ctx, tr, mouseX, mouseY, at[1], cy, Ui.buttonW(tr, LOOK), BTN_H, LOOK, Ui.GHOST,
                    code.ready());
        if (at[2] >= 0)
            Ui.button(ctx, tr, mouseX, mouseY, at[2], cy, Ui.buttonW(tr, FIND), BTN_H, FIND, Ui.GHOST);
    }

    @Override
    public String action() {
        if (!module.open) return Env.browser() ? "Код закрыт" : FIND;
        return code.ready() ? "На полотно" : "Гружу…";
    }

    @Override
    public boolean actionOn() { return module.open ? code.ready() : !Env.browser(); }

    @Override
    public void act() {
        if (!module.open && !Env.browser()) owner.installOfficial(module);
        else take(false);
    }

    @Override
    public String hint() {
        if (!module.open) return "код закрыт — его можно только установить в мир";
        if (code.broken()) return code.trouble();
        return "";
    }

    @Override
    public boolean click(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (overStars(mx, my)) {
            if (ModuleActions.starred(module)) owner.toast("звезда уже стоит");
            else owner.starOfficial(module);
            return true;
        }
        int[] q = stageRect();
        int qx = q[0], qy = q[1], qw = q[2], qh = q[3];
        int footY = qy + qh - FOOTER;
        if (module.open && my >= footY && my < qy + qh && mx >= qx)
            return footClicked(mx, my, qx, footY + 5, qw);
        if (module.open && code.broken() && !code.asking() && qh - FOOTER >= 24
                && code.layout() == null && Ui.hit(mx, my, qx + (qw - retryW()) / 2,
                qy + (qh - FOOTER) / 2 + 10, retryW(), 18)) {
            code.again();
            return true;
        }
        if (module.open && mx >= qx && my >= qy && my < footY) {
            if (doubled) stage.fit(code.layout(), 12);
            else stage.grab();
            return true;
        }
        bar.grabbed(mx, my, 1, 4000, v -> scroll = v);
        return true;
    }

    private boolean footClicked(double mx, double my, int bx, int cy, int bw) {
        int[] at = row(bx, bw);
        if (code.ready() && Ui.hit(mx, my, at[0], cy, Ui.buttonW(tr, PACK), BTN_H)) {
            take(true);
            return true;
        }
        if (code.ready() && at[1] >= 0 && Ui.hit(mx, my, at[1], cy, Ui.buttonW(tr, LOOK), BTN_H)) {
            owner.openLook(module, code.handlers());
            return true;
        }
        if (at[2] >= 0 && Ui.hit(mx, my, at[2], cy, Ui.buttonW(tr, FIND), BTN_H))
            owner.installOfficial(module);
        return true;
    }

    private void take(boolean toBackpack) {
        if (!code.ready()) { code.fetch(); return; }
        owner.install(module, code.handlers(), toBackpack);
    }

    @Override
    public boolean drag(MouseButtonEvent click, double dx, double dy) {
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
        if (module.open && stage.over(mx, my) && code.layout() != null) {
            stage.wheel(mx, my, amount);
            return true;
        }
        scroll = Math.max(0, scroll - Ui.px(amount, 30));
        return true;
    }

    @Override
    public boolean key(KeyEvent in) {
        switch (in.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> act();
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> stage.fit(code.layout(), 12);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> stage.step(1.12);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> stage.step(1 / 1.12);
            default -> { return false; }
        }
        return true;
    }
}
