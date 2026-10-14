package com.xerocode.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Value;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class LocationScreen extends Screen {
    private static final int WANT_W = 178, PAD = 8, HEAD_H = 22, CAP_H = 18, ROW_H = 16,
            ROW_GAP = 3, SEC_GAP = 8, GRID_H = 16, BTN_H = 18, ICON = 18, BADGE = 16, PM = 16,
            BAR_W = 6, CHIP_H = 18, KEY_H = 12, WORDS_H = 12;
    private static final double SCRUB_PX = 6;

    private static final int[] INK = {LocationPick.AX_X, LocationPick.AX_Y, LocationPick.AX_Z,
            LocationPick.ROT, LocationPick.ROT};
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final String[] AXIS_TIP = {
            "восток (+) и запад (−)", "высота: вверх (+) и вниз (−)", "юг (+) и север (−)"};
    private static final String[] AXIS_DRAG = {"восток ↔ запад", "вверх ↔ вниз", "север ↔ юг"};
    private static final String[] DIRS = {"юг", "юго-запад", "запад", "северо-запад", "север",
            "северо-восток", "восток", "юго-восток"};
    private static final String TURN = "поворот", TILT = "наклон";

    private enum Act {
        CENTER("В центр блока", "X и Z — на середину блока, высота та же"),
        ME("Где я стою", "точка туда, где стоит твой персонаж, взгляд — его"),
        SHOW("Показать точку", "подлететь камерой к точке · F"),
        CAMERA("Как смотрит камера", "точка посмотрит туда же, куда сейчас камера"),
        EYE("Глазами игрока", "встать в точку и оглядеться: так увидит игрок после телепорта"),
        RESET("Сбросить поворот", "поворот 0 и наклон 0: на юг, прямо");

        final String title, text;
        Act(String title, String text) { this.title = title; this.text = text; }
    }

    private final List<EditBox> fields = new ArrayList<>();
    private final Ui.Grab grab = new Ui.Grab();
    private int focus = -1;
    private boolean edited;

    private int x, y, w, h, inner, compass;
    private boolean words;
    private int coordsY, rowsY, lookY, compassY, gridY, footY;

    private int scrub = -1;
    private double scrubX0, scrubFrom;
    private boolean scrubbing, compassDrag, pitchDrag;

    private boolean looking;
    private int lookButton = -1;
    private double rawX0, rawY0, rawX, rawY;

    private int offX, offY, offW, offH;
    private int exitX, exitY, exitW, exitH;
    private String tipTitle, tipText;
    private int tipY;
    private int mx, my;

    private static Field mouseX, mouseY;
    private static boolean mouseFieldsTried;

    public LocationScreen() { super(Component.literal("Местоположение")); }

    public boolean typing() { return focus >= 0; }

    @Override
    protected void init() {
        if (!fields.isEmpty()) return;
        for (int i = 0; i < 5; i++) fields.add(numeric());
    }

    private EditBox numeric() {
        EditBox f = new EditBox(font, 0, 0, 40, 12, Component.literal("0")) {
            @Override
            public void insertText(String text) {
                StringBuilder kept = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (Character.isDigit(c) || c == '.' || c == ',' || c == '-') kept.append(c);
                }
                super.insertText(kept.toString());
            }
        };
        f.setBordered(false);
        f.setTextShadow(false);
        f.setTextColor(Draw.opaque(Theme.TEXT));
        f.setMaxLength(24);
        return f;
    }

    private void layout() {
        int m = Ui.margin(width);
        w = Math.max(60, Math.min(WANT_W, width - m * 2));
        inner = w - PAD * 2;
        compass = 46;
        words = true;
        h = HEAD_H + PAD + CAP_H + 3 + rowsH() + SEC_GAP + CAP_H + 3 + compass + WORDS_H + SEC_GAP
                + GRID_H + 10 + BTN_H + PAD;
        if (h > height - m * 2) {
            compass = 34;
            words = false;
            h -= 12 + WORDS_H;
        }
        x = width - w - m;
        y = Math.max(2, Math.min(m, height - h - 2));
        coordsY = y + HEAD_H + PAD;
        rowsY = coordsY + CAP_H + 3;
        lookY = rowsY + rowsH() + SEC_GAP;
        compassY = lookY + CAP_H + 3;
        gridY = compassY + compass + (words ? WORDS_H : 0) + SEC_GAP;
        footY = gridY + GRID_H + 10;
    }

    private static int rowsH() { return ROW_H * 3 + ROW_GAP * 2; }

    private int rowY(int i) { return rowsY + i * (ROW_H + ROW_GAP); }

    private int boxX() { return x + PAD + BADGE + 3; }

    private int boxW() { return inner - BADGE - 3 - PM * 2 - 2 - 3; }

    private int minusX() { return x + w - PAD - PM * 2 - 2; }

    private int plusX() { return x + w - PAD - PM; }

    private int barX() { return x + PAD + compass + 5; }

    private int lookColX() { return barX() + BAR_W + 7; }

    private int lookColW() { return x + w - PAD - lookColX(); }

    private int lookBoxY(int i) {
        return compassY + Math.max(0, (compass - ROW_H * 2 - ROW_GAP) / 2) + (i - 3) * (ROW_H + ROW_GAP);
    }

    private int captionW() { return Math.max(font.width(TURN), font.width(TILT)); }

    private int segX() { return x + PAD + font.width("СЕТКА") + 8; }

    private int half() { return (inner - 4) / 2; }

    private int undoX() { return x + w - PAD - 16 * 2 - 3; }

    private int iconX(Act a) {
        int k = a.ordinal() % 3;
        return x + w - PAD - (3 - k) * ICON - (2 - k) * 2;
    }

    private int iconY(Act a) { return a.ordinal() < 3 ? coordsY : lookY; }

    private Act actAt(double px, double py) {
        for (Act a : Act.values())
            if (Ui.hit(px, py, iconX(a), iconY(a), ICON, ICON)) return a;
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (!LocationPick.active()) return;
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        SmoothText.clip(null);
        Draw.batch(null);
        mx = mouseX;
        my = mouseY;
        layout();
        if (looking) followLook();
        LocationPick.frameSync();
        syncFields();
        tipTitle = null;

        boolean overPanel = Ui.hit(mx, my, x, y, w, h);
        boolean busy = looking || scrub >= 0 || compassDrag || pitchDrag;
        if (LocationPick.dragging() != LocationPick.NONE)
            LocationPick.dragTo(mx, my, width, height, shift());
        int hov = LocationPick.NONE;
        if (busy || overPanel || overOverlay(mx, my)) LocationPick.hoverNone();
        else if (LocationPick.dragging() == LocationPick.NONE) hov = LocationPick.hover(mx, my, width, height);

        offW = 0;
        exitW = 0;
        if (LocationPick.eyeMode()) eyeOverlay(ctx);
        else worldOverlay(ctx);

        ctx.nextStratum();
        panel(ctx, delta);
        hintBar(ctx, hov, overPanel);
        cursor(ctx, overPanel, hov);
        if (tipTitle != null && !busy && LocationPick.dragging() == LocationPick.NONE) {
            ctx.nextStratum();
            drawTip(ctx);
        }
    }

    private boolean overOverlay(double px, double py) {
        return offW > 0 && Ui.hit(px, py, offX, offY, offW, offH)
                || exitW > 0 && Ui.hit(px, py, exitX, exitY, exitW, exitH);
    }

    private int worldRight() { return x - 6; }

    private void worldOverlay(GuiGraphicsExtractor ctx) {
        double[] at = LocationPick.pointOnScreen(width, height);
        boolean seen = at[2] > 0.05 && at[0] >= 0 && at[0] < worldRight() && at[1] >= 0 && at[1] < height;
        if (!seen) offscreen(ctx, at);
        else pointChip(ctx);
        if (LocationPick.dragging() != LocationPick.NONE) dragReadout(ctx);
    }

    private void pointChip(GuiGraphicsExtractor ctx) {
        LocationPick.Info info = LocationPick.info();
        if (info == null) return;
        double[] at = LocationPick.labelOnScreen(width, height);
        if (at[2] <= 0.05) return;
        boolean icon = !info.icon().isEmpty();
        String text = Draw.fit(font, info.text(), 150);
        int cw = (icon ? 20 : 6) + font.width(text) + 6;
        int cx = (int) Math.round(at[0]) - cw / 2;
        int cy = (int) Math.round(at[1]) - CHIP_H;
        cx = Math.max(4, Math.min(cx, worldRight() - cw));
        cy = Math.max(4, Math.min(cy, height - CHIP_H - 30));
        int edge = info.color() == Theme.DANGER ? Draw.mix(Ui.BORDER, Theme.DANGER, 0.7f) : Ui.BORDER;
        Draw.card(ctx, cx, cy, cw, CHIP_H, Ui.R_SM, Draw.opaque(Ui.PANEL), Draw.opaque(edge));
        if (icon) Draw.item(ctx, info.icon(), cx + 2, cy + 1, 16);
        Draw.text(ctx, font, text, cx + (icon ? 20 : 6), cy + (CHIP_H - Ui.TEXT_H) / 2, info.color(), false);
    }

    private void offscreen(GuiGraphicsExtractor ctx, double[] at) {
        String label = "к точке";
        offW = font.width(label) + 22;
        offH = 18;
        double cx = worldRight() / 2.0, cy = height / 2.0;
        double dx = at[0] - cx, dy = at[1] - cy;
        double n = Math.max(1.0E-6, Math.hypot(dx, dy));
        dx /= n;
        dy /= n;
        double rx = Math.max(1, cx - offW / 2.0 - 8), ry = Math.max(1, cy - offH / 2.0 - 30);
        double k = Math.min(rx / Math.max(1.0E-6, Math.abs(dx)), ry / Math.max(1.0E-6, Math.abs(dy)));
        offX = (int) Math.round(cx + dx * k - offW / 2.0);
        offY = (int) Math.round(cy + dy * k - offH / 2.0);
        boolean hov = Ui.hit(mx, my, offX, offY, offW, offH);
        Draw.card(ctx, offX, offY, offW, offH, Ui.R_SM,
                Draw.opaque(hov ? Ui.BTN_HOVER : Ui.PANEL), Draw.opaque(Theme.ACCENT));
        double tx = offX + 9 + dx * 3, ty = offY + offH / 2.0 + dy * 3;
        int ink = Draw.opaque(Theme.ACCENT);
        for (int side = -1; side <= 1; side += 2) {
            double bx = -dx * 0.6 - dy * 0.8 * side, by = -dy * 0.6 + dx * 0.8 * side;
            line(ctx, tx, ty, tx + bx * 5, ty + by * 5, 1, ink);
        }
        line(ctx, tx, ty, tx - dx * 6, ty - dy * 6, 1, ink);
        Draw.text(ctx, font, label, offX + 17, offY + (offH - Ui.TEXT_H) / 2,
                hov ? Theme.TEXT : Theme.TEXT_DIM, false);
        if (hov) tip("Точка за кадром", "клик — подлететь к ней · F", offY);
    }

    private void dragReadout(GuiGraphicsExtractor ctx) {
        int d = LocationPick.dragging();
        double[] v = LocationPick.values();
        String s;
        int color = Theme.TEXT;
        if (d >= LocationPick.AX && d <= LocationPick.AZ) {
            double from = d == 0 ? LocationPick.dragStart().x : d == 1 ? LocationPick.dragStart().y
                    : LocationPick.dragStart().z;
            double delta = Math.round((v[d] - from) * 1000.0) / 1000.0;
            s = AXES[d] + "  " + fmt(d, v[d]) + "   " + (delta >= 0 ? "+" : "−") + fmt(d, Math.abs(delta));
            color = Draw.mix(INK[d], 0xFFFFFF, 0.25f);
        } else if (d == LocationPick.LOOK) {
            s = "на " + direction(v[3]) + " · " + tilt(v[4]);
            color = LocationPick.ROT;
        } else {
            s = fmt(0, v[0]) + "   " + fmt(1, v[1]) + "   " + fmt(2, v[2]);
        }
        int cw = font.width(s) + 12;
        int cx = Math.max(4, Math.min(mx + 14, worldRight() - cw)), cy = Math.max(4, Math.min(my + 14, height - 44));
        Draw.card(ctx, cx, cy, cw, 15, Ui.R_SM, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        Draw.text(ctx, font, s, cx + 6, cy + 4, color, false);
    }

    private void eyeOverlay(GuiGraphicsExtractor ctx) {
        int cx = width / 2, cy = height / 2;
        int c = Draw.argb(0xD0, 0xFFFFFF);
        Draw.rect(ctx, cx - 6, cy, 4, 1, c);
        Draw.rect(ctx, cx + 3, cy, 4, 1, c);
        Draw.rect(ctx, cx, cy - 6, 1, 4, c);
        Draw.rect(ctx, cx, cy + 3, 1, 4, c);

        String title = "ГЛАЗАМИ ИГРОКА", sub = "так игрок увидит мир после телепорта", exit = "Выйти";
        exitW = Ui.buttonW(font, exit);
        exitH = 18;
        int room = Math.max(60, worldRight() - 16);
        int textW = Math.min(room - exitW - 26, Math.max(font.width(title), font.width(sub)));
        int bw = textW + exitW + 26, bh = 34;
        int bx = Math.max(8, (worldRight() - bw) / 2), by = 8;
        Draw.card(ctx, bx, by, bw, bh, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        Draw.rect(ctx, bx + Ui.R, by + 1, bw - Ui.R * 2, 1, Ui.sheen());
        Draw.textFit(ctx, font, title, bx + 10, by + 7, textW, Theme.ACCENT, false);
        Draw.textFit(ctx, font, sub, bx + 10, by + 19, textW, Theme.TEXT_DIM, false);
        exitX = bx + bw - 8 - exitW;
        exitY = by + (bh - exitH) / 2;
        Ui.button(ctx, font, mx, my, exitX, exitY, exitW, exitH, exit, Ui.GHOST);
    }

    private void panel(GuiGraphicsExtractor ctx, float delta) {
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, Theme.ACCENT);
        Draw.glyph(ctx, Draw.PIN, x + PAD, y + (HEAD_H - Draw.glyphH(Draw.PIN)) / 2, Theme.ACCENT);
        Draw.textFit(ctx, font, "МЕСТОПОЛОЖЕНИЕ", x + PAD + 9, y + (HEAD_H - Ui.TEXT_H) / 2 + 1,
                undoX() - x - PAD - 13, Theme.TEXT, false);
        int uy = y + (HEAD_H - 16) / 2;
        if (Ui.iconButton(ctx, mx, my, undoX(), uy, 16, Draw.UNDO, Ui.GHOST, LocationPick.canUndo()))
            tip("Отменить", "Ctrl+Z", uy);
        if (Ui.iconButton(ctx, mx, my, undoX() + 19, uy, 16, Draw.REDO, Ui.GHOST, LocationPick.canRedo()))
            tip("Вернуть", "Ctrl+Y", uy);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        Ui.caption(ctx, font, "КООРДИНАТЫ", x + PAD, coordsY + (CAP_H - Ui.TEXT_H) / 2,
                iconX(Act.CENTER) - x - PAD - 4);
        Ui.caption(ctx, font, "ВЗГЛЯД", x + PAD, lookY + (CAP_H - Ui.TEXT_H) / 2,
                iconX(Act.CAMERA) - x - PAD - 4);
        for (Act a : Act.values()) actButton(ctx, a);

        double[] v = LocationPick.values();
        for (int i = 0; i < 3; i++) coordRow(ctx, i, delta);
        compassBlock(ctx, v, delta);

        Ui.caption(ctx, font, "СЕТКА", x + PAD, gridY + (GRID_H - Ui.TEXT_H) / 2, segX() - x - PAD);
        List<String> labels = new ArrayList<>();
        for (LocationPick.Grid g : LocationPick.Grid.values()) labels.add(g.label);
        int segW = x + w - PAD - segX();
        int seg = Ui.segmented(ctx, font, mx, my, segX(), gridY, segW, GRID_H, labels,
                LocationPick.grid().ordinal(), Theme.ACCENT);
        if (seg >= 0 || Ui.hit(mx, my, x + PAD, gridY, segX() - x - PAD, GRID_H))
            tip("Сетка", "куда прилипает точка при клике и перетаскивании · Shift — без сетки", gridY);

        Ui.button(ctx, font, mx, my, x + PAD, footY, half(), BTN_H, "Отмена", Ui.GHOST);
        Ui.glyphButton(ctx, font, mx, my, x + PAD + half() + 4, footY, inner - half() - 4, BTN_H,
                Draw.CHECK, "Готово", Ui.ACCENT, true);
    }

    private void actButton(GuiGraphicsExtractor ctx, Act a) {
        int ax = iconX(a), ay = iconY(a);
        boolean on = a == Act.EYE && LocationPick.eyeMode();
        boolean hov = Ui.button(ctx, font, mx, my, ax, ay, ICON, ICON, "", on ? Ui.ACTIVE : Ui.GHOST);
        switch (a) {
            case CENTER -> Draw.item(ctx, new ItemStack(Items.TARGET), ax + 1, ay + 1, 16);
            case ME -> face(ctx, ax, ay);
            case SHOW -> Draw.item(ctx, new ItemStack(Items.SPYGLASS), ax + 1, ay + 1, 16);
            case CAMERA -> Draw.item(ctx, new ItemStack(Items.COMPASS), ax + 1, ay + 1, 16);
            case EYE -> Draw.item(ctx, new ItemStack(Items.ENDER_EYE), ax + 1, ay + 1, 16);
            case RESET -> Draw.glyph(ctx, Draw.RESET, ax + (ICON - Draw.glyphW(Draw.RESET)) / 2,
                    ay + (ICON - Draw.glyphH(Draw.RESET)) / 2, hov ? Theme.TEXT : Theme.TEXT_DIM);
        }
        if (hov) tip(a == Act.EYE && on ? "Выйти из глаз игрока" : a.title, a.text, ay);
    }

    private void face(GuiGraphicsExtractor ctx, int ax, int ay) {
        Identifier skin = null;
        try {
            if (minecraft.player != null && minecraft.player.getSkin() != null
                    && minecraft.player.getSkin().body() != null)
                skin = minecraft.player.getSkin().body().texturePath();
        } catch (RuntimeException ignored) {
            skin = null;
        }
        if (skin == null) {
            Draw.item(ctx, new ItemStack(Items.ARMOR_STAND), ax + 1, ay + 1, 16);
            return;
        }
        PlayerFaceExtractor.extractRenderState(ctx, skin, ax + 3, ay + 3, ICON - 6, true, false, -1);
    }

    private void coordRow(GuiGraphicsExtractor ctx, int i, float delta) {
        int ry = rowY(i), bx = x + PAD;
        boolean hot = scrub == i || Ui.hit(mx, my, bx, ry, BADGE, ROW_H);
        int c = INK[i];
        Draw.round(ctx, bx, ry, BADGE, ROW_H, Ui.R_SM,
                Draw.opaque(Draw.mix(Ui.WELL, c, hot ? 0.62f : 0.36f)));
        Draw.textCenter(ctx, font, AXES[i], bx, ry + (ROW_H - Ui.TEXT_H) / 2, BADGE, BADGE,
                hot ? 0xFFFFFF : Draw.mix(c, 0xFFFFFF, 0.45f), false);
        if (hot && scrub < 0) tip(AXES[i] + " — " + AXIS_TIP[i], "тяни вбок или крути колесо", ry);
        Ui.input(ctx, boxX(), ry, boxW(), ROW_H, focus == i);
        drawField(ctx, i, boxX() + 5, ry + 4, boxW() - 10, delta);
        String step = Value.num(LocationPick.grid().step);
        if (Ui.iconButton(ctx, mx, my, minusX(), ry, PM, Draw.MINUS, Ui.GHOST, true))
            tip("−" + step, AXES[i] + " на шаг сетки меньше", ry);
        if (Ui.iconButton(ctx, mx, my, plusX(), ry, PM, Draw.PLUS, Ui.GHOST, true))
            tip("+" + step, AXES[i] + " на шаг сетки больше", ry);
    }

    private void drawField(GuiGraphicsExtractor ctx, int i, int fx, int fy, int fw, float delta) {
        EditBox f = fields.get(i);
        if (f.getX() != fx) f.setX(fx);
        if (f.getY() != fy) f.setY(fy);
        Ui.width(f, Math.max(10, fw));
        f.extractRenderState(ctx, mx, my, delta);
        Ui.placeholder(ctx, font, f);
    }

    private void compassBlock(GuiGraphicsExtractor ctx, double[] v, float delta) {
        int r = compass / 2, ccx = x + PAD + r, ccy = compassY + r;
        boolean hot = compassDrag || inCompass(mx, my);
        Draw.card(ctx, x + PAD, compassY, compass, compass, r, Draw.opaque(Ui.WELL),
                Draw.opaque(hot ? Draw.mix(Ui.LINE_IN, LocationPick.ROT, 0.45f) : Ui.LINE_IN));
        int tick = Draw.opaque(Draw.mix(Ui.WELL, Theme.TEXT_FAINT, 0.7f));
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            Draw.rect(ctx, (int) Math.round(ccx + Math.sin(a) * (r - 4)),
                    (int) Math.round(ccy - Math.cos(a) * (r - 4)), 1, 1, tick);
        }
        double yaw = Math.toRadians(v[3]);
        double reach = (r - 4) * Math.max(0.3, Math.cos(Math.toRadians(v[4])));
        double nx = ccx - Math.sin(yaw) * reach, ny = ccy + Math.cos(yaw) * reach;
        int needle = Draw.opaque(hot ? Draw.mix(LocationPick.ROT, 0xFFFFFF, 0.3f) : LocationPick.ROT);
        line(ctx, ccx, ccy, nx, ny, 2, needle);
        Draw.round(ctx, (int) Math.round(nx) - 2, (int) Math.round(ny) - 2, 4, 4, 2, needle);
        Draw.round(ctx, ccx - 2, ccy - 2, 4, 4, 2, Draw.opaque(Theme.TEXT));
        String[] marks = {"С", "В", "Ю", "З"};
        int off = compass >= 40 ? 9 : 7;
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90);
            int lw = font.width(marks[k]);
            int lx = (int) Math.round(ccx + Math.sin(a) * (r - off) - lw / 2.0);
            int ly = (int) Math.round(ccy - Math.cos(a) * (r - off) - 3.5);
            Draw.text(ctx, font, marks[k], lx, ly, k == 0 ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
        }
        if (hot && !compassDrag)
            tip("Куда смотрит: " + direction(v[3]) + ", " + tilt(v[4]),
                    "тяни стрелку по кругу · колесо — по 15° · Shift — плавно", compassY);

        int bx = barX();
        boolean barHot = pitchDrag || Ui.hit(mx, my, bx - 3, compassY, BAR_W + 6, compass);
        Draw.card(ctx, bx, compassY, BAR_W, compass, BAR_W / 2, Draw.opaque(Ui.WELL),
                Draw.opaque(barHot ? Draw.mix(Ui.LINE_IN, LocationPick.ROT, 0.45f) : Ui.LINE_IN));
        Draw.rect(ctx, bx - 2, compassY + compass / 2, BAR_W + 4, 1, Draw.opaque(Ui.LINE_IN));
        int ky = compassY + 2 + (int) Math.round((v[4] + 90) / 180.0 * (compass - 5));
        Draw.pill(ctx, bx - 2, ky - 1, BAR_W + 4, 4,
                Draw.opaque(barHot ? Draw.mix(LocationPick.ROT, 0xFFFFFF, 0.3f) : LocationPick.ROT));
        if (barHot && !pitchDrag)
            tip("Наклон", "вверх — выше, вниз — ниже · колесо — по 15° · Shift — плавно", compassY);

        int lx = lookColX(), lw = lookColW(), cw = captionW();
        for (int i = 3; i < 5; i++) {
            int by = lookBoxY(i);
            Ui.input(ctx, lx, by, lw, ROW_H, focus == i);
            Draw.text(ctx, font, i == 3 ? TURN : TILT, lx + 5, by + (ROW_H - Ui.TEXT_H) / 2,
                    Draw.mix(LocationPick.ROT, Theme.TEXT_DIM, 0.35f), false);
            drawField(ctx, i, lx + 5 + cw + 5, by + 4, lw - cw - 15, delta);
        }
        if (words)
            Draw.textFit(ctx, font, "на " + direction(v[3]) + " · " + tilt(v[4]), x + PAD,
                    compassY + compass + 4, inner, Theme.TEXT_DIM, false);
    }

    private boolean inCompass(double px, double py) {
        double r = compass / 2.0;
        return Math.hypot(px - (x + PAD + r), py - (compassY + r)) <= r + 1;
    }

    private static void line(GuiGraphicsExtractor ctx, double x0, double y0, double x1, double y1,
                             int size, int argb) {
        int n = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0)));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= n; i++) {
            int px = (int) Math.round(x0 + (x1 - x0) * i / n - (size - 1) / 2.0);
            int py = (int) Math.round(y0 + (y1 - y0) * i / n - (size - 1) / 2.0);
            if (px == lastX && py == lastY) continue;
            lastX = px;
            lastY = py;
            Draw.rect(ctx, px, py, size, size, argb);
        }
    }

    private static String direction(double yaw) {
        return DIRS[Math.floorMod((int) Math.round(Mth.wrapDegrees(yaw) / 45.0), 8)];
    }

    private static String tilt(double pitch) {
        if (pitch < -60) return "вверх";
        if (pitch < -15) return "чуть вверх";
        if (pitch <= 15) return "прямо";
        if (pitch <= 60) return "чуть вниз";
        return "вниз";
    }

    private void hintBar(GuiGraphicsExtractor ctx, int hov, boolean overPanel) {
        String[][] items = hints(hov, overPanel);
        int room = worldRight() - 16;
        int n = items.length, total;
        while (true) {
            total = 0;
            for (int i = 0; i < n; i++) total += keyW(items[i][0]) + 4 + font.width(items[i][1]) + (i > 0 ? 12 : 0);
            if (total <= room || n <= 1) break;
            n--;
        }
        if (total > room) return;
        int bh = KEY_H + 8, by = height - bh - 6;
        int bx = Math.max(4, (worldRight() - total) / 2 - 8);
        Draw.card(ctx, bx, by, total + 16, bh, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        int cx = bx + 8, ky = by + 4;
        for (int i = 0; i < n; i++) {
            if (i > 0) cx += 12;
            int kw = keyW(items[i][0]);
            Draw.card(ctx, cx, ky, kw, KEY_H, 3, Draw.opaque(Ui.BTN), Draw.opaque(Ui.LINE_IN));
            Draw.text(ctx, font, items[i][0], cx + 4, ky + 2, Theme.TEXT, false);
            cx += kw + 4;
            Draw.text(ctx, font, items[i][1], cx, ky + 2, Theme.TEXT_DIM, false);
            cx += font.width(items[i][1]);
        }
    }

    private int keyW(String key) { return font.width(key) + 8; }

    private String[][] hints(int hov, boolean overPanel) {
        int d = LocationPick.dragging();
        if (LocationPick.eyeMode()) return new String[][]{
                {"мышь", "повернуть голову"}, {"WASD", "шагать"}, {"Пробел", "выше"},
                {"Shift", "ниже"}, {"Esc", "выйти из глаз"}};
        if (d >= LocationPick.AX && d <= LocationPick.AZ) return new String[][]{
                {"мышь", AXIS_DRAG[d]}, {"Shift", "без сетки"}, {"ПКМ", "отменить"}};
        if (d == LocationPick.MOVE) return new String[][]{
                {"мышь", "переставить по блокам"}, {"Shift", "без сетки"}, {"ПКМ", "отменить"}};
        if (d == LocationPick.LOOK) return new String[][]{
                {"мышь", "навести взгляд на блок"}, {"Shift", "плавно"}, {"ПКМ", "отменить"}};
        if (!overPanel && hov >= LocationPick.AX && hov <= LocationPick.AZ) return new String[][]{
                {"ЛКМ", "тянуть: " + AXIS_DRAG[hov]}, {"ПКМ", "осмотреться"}};
        if (!overPanel && hov == LocationPick.MOVE) return new String[][]{
                {"ЛКМ", "тянуть точку по блокам"}, {"ПКМ", "осмотреться"}};
        if (!overPanel && hov == LocationPick.LOOK) return new String[][]{
                {"ЛКМ", "тянуть: куда смотреть"}, {"ПКМ", "осмотреться"}};
        return new String[][]{
                {"ЛКМ", "поставить на блок"}, {"ПКМ", "осмотреться"}, {"WASD", "лететь"},
                {"колесо", "ближе"}, {"F", "к точке"}, {"Ctrl+Z", "отменить"}};
    }

    private void cursor(GuiGraphicsExtractor ctx, boolean overPanel, int hov) {
        if (looking) return;
        int d = LocationPick.dragging();
        if (scrub >= 0) { ctx.requestCursor(CursorTypes.RESIZE_EW); return; }
        if (compassDrag || pitchDrag) { ctx.requestCursor(CursorTypes.POINTING_HAND); return; }
        if (d != LocationPick.NONE) { ctx.requestCursor(handleCursor(d)); return; }
        if (overPanel) {
            for (int i = 0; i < 3; i++) {
                if (Ui.hit(mx, my, x + PAD, rowY(i), BADGE, ROW_H)) {
                    ctx.requestCursor(CursorTypes.RESIZE_EW);
                    return;
                }
                if (Ui.hit(mx, my, boxX(), rowY(i), boxW(), ROW_H)) {
                    ctx.requestCursor(CursorTypes.IBEAM);
                    return;
                }
            }
            for (int i = 3; i < 5; i++)
                if (Ui.hit(mx, my, lookColX(), lookBoxY(i), lookColW(), ROW_H)) {
                    ctx.requestCursor(CursorTypes.IBEAM);
                    return;
                }
            if (inCompass(mx, my) || Ui.hit(mx, my, barX() - 3, compassY, BAR_W + 6, compass))
                ctx.requestCursor(CursorTypes.POINTING_HAND);
            return;
        }
        if (overOverlay(mx, my)) return;
        if (LocationPick.eyeMode()) { ctx.requestCursor(CursorTypes.RESIZE_ALL); return; }
        if (hov != LocationPick.NONE) { ctx.requestCursor(handleCursor(hov)); return; }
        if (LocationPick.overBlock()) ctx.requestCursor(CursorTypes.CROSSHAIR);
    }

    private CursorType handleCursor(int handle) {
        if (handle == LocationPick.MOVE) return CursorTypes.RESIZE_ALL;
        if (handle == LocationPick.LOOK) return CursorTypes.POINTING_HAND;
        double[] s = LocationPick.axisOnScreen(handle, width, height);
        return Math.abs(s[0]) >= Math.abs(s[1]) ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_NS;
    }

    private void tip(String title, String text, int anchorY) {
        tipTitle = title;
        tipText = text;
        tipY = anchorY;
    }

    private void drawTip(GuiGraphicsExtractor ctx) {
        int maxW = Math.min(200, Math.max(80, x - 12));
        List<String> lines = tipText == null || tipText.isEmpty() ? List.of()
                : Ui.wrap(font, tipText, maxW - 12, 3);
        int tw = font.width(tipTitle);
        for (String l : lines) tw = Math.max(tw, font.width(l));
        tw = Math.min(maxW, tw + 12);
        int th = 8 + 9 + lines.size() * 10 + 2;
        int tx = x - 6 - tw;
        if (tx < 4) tx = 4;
        int ty = Math.max(4, Math.min(tipY, height - th - 4));
        Draw.card(ctx, tx, ty, tw, th, Ui.R_SM, Draw.opaque(Theme.PANEL_RAISED), Draw.opaque(Ui.BORDER));
        Draw.textFit(ctx, font, tipTitle, tx + 6, ty + 5, tw - 12, Theme.TEXT, false);
        for (int i = 0; i < lines.size(); i++)
            Draw.textFit(ctx, font, lines.get(i), tx + 6, ty + 16 + i * 10, tw - 12, Theme.TEXT_DIM, false);
    }

    private static String fmt(int i, double v) {
        if (i < 3) return Value.num(Math.round(v * 1000.0) / 1000.0 + 0.0);
        return Value.num(Math.round(v * 10.0) / 10.0 + 0.0);
    }

    private static Double parse(String s) {
        String t = s.trim().replace(',', '.');
        if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("-.")) return null;
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void syncFields() {
        double[] v = LocationPick.values();
        for (int i = 0; i < fields.size(); i++) {
            if (i == focus) continue;
            EditBox f = fields.get(i);
            String want = fmt(i, v[i]);
            if (f.getValue().equals(want)) continue;
            f.setValue(want);
            f.moveCursorToStart(false);
        }
    }

    private void focus(int i, boolean all) {
        if (i != focus) {
            if (focus >= 0 && edited) LocationPick.settle();
            focus = i;
            edited = false;
        }
        for (int k = 0; k < fields.size(); k++) {
            EditBox f = fields.get(k);
            f.setFocused(k == focus);
            if (k != focus) f.setHighlightPos(f.getCursorPosition());
        }
        if (focus >= 0 && all) {
            EditBox f = fields.get(focus);
            f.moveCursorToEnd(false);
            f.setHighlightPos(0);
        }
    }

    private void unfocus() { focus(-1, false); }

    private void applyField(int i) {
        Double v = parse(fields.get(i).getValue());
        if (v == null) return;
        if (!edited) {
            LocationPick.push();
            edited = true;
        }
        LocationPick.set(i, v);
    }

    private boolean shift() {
        return held(GLFW.GLFW_KEY_LEFT_SHIFT) || held(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean ctrl() {
        return held(GLFW.GLFW_KEY_LEFT_CONTROL) || held(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean held(int key) {
        return minecraft != null && minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow(), key);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!LocationPick.active() || looking) return true;
        layout();
        double px = click.x(), py = click.y();
        int b = click.button();
        boolean overPanel = Ui.hit(px, py, x, y, w, h);
        boolean busy = scrub >= 0 || compassDrag || pitchDrag;
        if (b == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (LocationPick.dragging() != LocationPick.NONE) LocationPick.cancelDrag();
            else if (!overPanel && !busy) beginLook(b);
            return true;
        }
        if (b == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!overPanel && !busy) beginLook(b);
            return true;
        }
        if (b != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (overPanel) {
            panelClick(click, doubled);
            return true;
        }
        unfocus();
        if (exitW > 0 && Ui.hit(px, py, exitX, exitY, exitW, exitH)) {
            LocationPick.toggleEye();
            return true;
        }
        if (LocationPick.eyeMode()) {
            beginLook(b);
            return true;
        }
        if (offW > 0 && Ui.hit(px, py, offX, offY, offW, offH)) {
            LocationPick.frame();
            return true;
        }
        LocationPick.press(px, py, width, height);
        return true;
    }

    private void panelClick(MouseButtonEvent click, boolean doubled) {
        double px = click.x(), py = click.y();
        int uy = y + (HEAD_H - 16) / 2;
        if (Ui.hit(px, py, undoX(), uy, 16, 16)) { unfocus(); LocationPick.undo(); return; }
        if (Ui.hit(px, py, undoX() + 19, uy, 16, 16)) { unfocus(); LocationPick.redo(); return; }
        Act a = actAt(px, py);
        if (a != null) {
            unfocus();
            run(a);
            return;
        }
        for (int i = 0; i < 3; i++) {
            int ry = rowY(i);
            if (Ui.hit(px, py, x + PAD, ry, BADGE, ROW_H)) {
                unfocus();
                scrub = i;
                scrubX0 = px;
                scrubFrom = LocationPick.values()[i];
                scrubbing = false;
                return;
            }
            if (Ui.hit(px, py, boxX(), ry, boxW(), ROW_H)) {
                clickField(i, click, doubled);
                return;
            }
            if (Ui.hit(px, py, minusX(), ry, PM, PM)) { unfocus(); LocationPick.nudge(i, -1); return; }
            if (Ui.hit(px, py, plusX(), ry, PM, PM)) { unfocus(); LocationPick.nudge(i, 1); return; }
        }
        for (int i = 3; i < 5; i++)
            if (Ui.hit(px, py, lookColX(), lookBoxY(i), lookColW(), ROW_H)) {
                clickField(i, click, doubled);
                return;
            }
        if (inCompass(px, py)) {
            unfocus();
            LocationPick.push();
            compassDrag = true;
            compassAt(px, py);
            return;
        }
        if (Ui.hit(px, py, barX() - 3, compassY, BAR_W + 6, compass)) {
            unfocus();
            LocationPick.push();
            pitchDrag = true;
            pitchAt(py);
            return;
        }
        int segW = x + w - PAD - segX();
        int seg = Ui.segmentAt(px, py, segX(), gridY, segW, GRID_H, LocationPick.Grid.values().length);
        if (seg >= 0) {
            LocationPick.grid(LocationPick.Grid.values()[seg]);
            return;
        }
        if (Ui.hit(px, py, x + PAD, footY, half(), BTN_H)) { LocationPick.cancel(); return; }
        if (Ui.hit(px, py, x + PAD + half() + 4, footY, inner - half() - 4, BTN_H)) {
            unfocus();
            LocationPick.done();
            return;
        }
        unfocus();
    }

    private void clickField(int i, MouseButtonEvent click, boolean doubled) {
        if (focus != i) {
            focus(i, true);
            return;
        }
        EditBox f = fields.get(i);
        if (!f.mouseClicked(click, doubled)) f.onClick(click, doubled);
        grab.take(f);
    }

    private void run(Act a) {
        switch (a) {
            case CENTER -> LocationPick.centerInBlock();
            case ME -> LocationPick.toPlayer();
            case SHOW -> LocationPick.frame();
            case CAMERA -> LocationPick.lookLikeCamera();
            case EYE -> LocationPick.toggleEye();
            case RESET -> LocationPick.resetLook();
        }
    }

    private void compassAt(double px, double py) {
        double r = compass / 2.0;
        double dx = px - (x + PAD + r), dy = py - (compassY + r);
        if (Math.hypot(dx, dy) < 2) return;
        double yaw = Math.toDegrees(Math.atan2(-dx, dy));
        LocationPick.set(3, shift() ? Math.round(yaw) : Math.round(yaw / 15.0) * 15.0);
    }

    private void pitchAt(double py) {
        double t = Mth.clamp((py - compassY - 2) / (double) (compass - 5), 0, 1);
        double pitch = -90 + t * 180;
        LocationPick.set(4, shift() ? Math.round(pitch) : Math.round(pitch / 15.0) * 15.0);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (looking || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        double px = click.x(), py = click.y();
        if (scrub >= 0) {
            double moved = px - scrubX0;
            if (!scrubbing && Math.abs(moved) < 3) return true;
            if (!scrubbing) {
                scrubbing = true;
                LocationPick.push();
            }
            double step = shift() ? 0.1 : ctrl() ? 1 : LocationPick.grid().step;
            long n = Math.round(moved / SCRUB_PX);
            LocationPick.set(scrub, Math.round((scrubFrom + n * step) * 1000.0) / 1000.0);
            return true;
        }
        if (compassDrag) { compassAt(px, py); return true; }
        if (pitchDrag) { pitchAt(py); return true; }
        if (LocationPick.dragging() != LocationPick.NONE) {
            LocationPick.dragTo(px, py, width, height, shift());
            return true;
        }
        grab.drag(click, dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        int b = click.button();
        if (looking) {
            if (b == lookButton) endLook();
            return true;
        }
        if (b != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (scrub >= 0) {
            if (scrubbing) LocationPick.settle();
            else focus(scrub, true);
            scrub = -1;
            scrubbing = false;
        }
        if (compassDrag || pitchDrag) {
            compassDrag = false;
            pitchDrag = false;
            LocationPick.settle();
        }
        LocationPick.release();
        grab.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double px, double py, double horizontal, double vertical) {
        if (looking || vertical == 0 || !LocationPick.active()) return true;
        layout();
        int dir = vertical > 0 ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            if (Ui.hit(px, py, x + PAD, rowY(i), boxX() + boxW() - x - PAD, ROW_H)) {
                if (focus == i) unfocus();
                LocationPick.nudge(i, dir);
                return true;
            }
        }
        double step = shift() ? 1 : 15;
        double[] v = LocationPick.values();
        if (inCompass(px, py) || Ui.hit(px, py, lookColX(), lookBoxY(3), lookColW(), ROW_H)) {
            if (focus == 3) unfocus();
            rotate(3, v[3] + dir * step, step);
            return true;
        }
        if (Ui.hit(px, py, barX() - 3, compassY, BAR_W + 6, compass)
                || Ui.hit(px, py, lookColX(), lookBoxY(4), lookColW(), ROW_H)) {
            if (focus == 4) unfocus();
            rotate(4, v[4] - dir * step, step);
            return true;
        }
        if (Ui.hit(px, py, x, y, w, h)) return true;
        LocationPick.dolly(dir, px, py, width, height, ctrl());
        return true;
    }

    private static void rotate(int i, double to, double step) {
        LocationPick.push();
        LocationPick.set(i, Math.round(to / step) * step);
        LocationPick.settle();
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!LocationPick.active()) return true;
        int key = input.key();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (looking) endLook();
            else if (LocationPick.dragging() != LocationPick.NONE) LocationPick.cancelDrag();
            else if (focus >= 0) unfocus();
            else if (LocationPick.eyeMode()) LocationPick.toggleEye();
            else LocationPick.cancel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (focus >= 0) unfocus();
            else LocationPick.done();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            int next = focus < 0 ? (shift ? 4 : 0) : Math.floorMod(focus + (shift ? -1 : 1), 5);
            focus(next, true);
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            unfocus();
            if (key == GLFW.GLFW_KEY_Y || shift) LocationPick.redo();
            else LocationPick.undo();
            return true;
        }
        if (focus >= 0) {
            if (fields.get(focus).keyPressed(input)) applyField(focus);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && !ctrl) LocationPick.frame();
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (focus >= 0 && fields.get(focus).charTyped(input)) applyField(focus);
        return true;
    }

    private void beginLook(int button) {
        unfocus();
        var window = minecraft.getWindow();
        double[] xs = new double[1], ys = new double[1];
        GLFW.glfwGetCursorPos(window.handle(), xs, ys);
        rawX0 = rawX = xs[0];
        rawY0 = rawY = ys[0];
        InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_DISABLED, rawX, rawY);
        looking = true;
        lookButton = button;
        LocationPick.hoverNone();
    }

    private void followLook() {
        long handle = minecraft.getWindow().handle();
        if (GLFW.glfwGetMouseButton(handle, lookButton) != GLFW.GLFW_PRESS) {
            endLook();
            return;
        }
        double[] xs = new double[1], ys = new double[1];
        GLFW.glfwGetCursorPos(handle, xs, ys);
        double dx = xs[0] - rawX, dy = ys[0] - rawY;
        rawX = xs[0];
        rawY = ys[0];
        if (dx != 0 || dy != 0) LocationPick.turnCamera(dx, dy);
    }

    private void endLook() {
        if (!looking) return;
        looking = false;
        InputConstants.grabOrReleaseMouse(minecraft.getWindow(), GLFW.GLFW_CURSOR_NORMAL, rawX0, rawY0);
        placeMouse(rawX0, rawY0);
    }

    private void placeMouse(double rx, double ry) {
        try {
            if (!mouseFieldsTried) {
                mouseFieldsTried = true;
                mouseX = MouseHandler.class.getDeclaredField("xpos");
                mouseY = MouseHandler.class.getDeclaredField("ypos");
                mouseX.setAccessible(true);
                mouseY.setAccessible(true);
            }
            if (mouseX == null || mouseY == null) return;
            mouseX.setDouble(minecraft.mouseHandler, rx);
            mouseY.setDouble(minecraft.mouseHandler, ry);
        } catch (ReflectiveOperationException | RuntimeException e) {
            mouseX = null;
            mouseY = null;
        }
    }

    @Override
    public void removed() {
        endLook();
        if (focus >= 0 && edited) LocationPick.settle();
        focus = -1;
        scrub = -1;
        compassDrag = false;
        pitchDrag = false;
        LocationPick.release();
        super.removed();
    }

    @Override
    public void onClose() {
        LocationPick.cancel();
    }
}
