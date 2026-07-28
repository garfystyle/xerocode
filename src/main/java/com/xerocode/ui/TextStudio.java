package com.xerocode.ui;

import com.xerocode.Value;
import com.xerocode.Values;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class TextStudio {
    private static final int PAD = 10;
    private static final int HEAD_H = 26;
    private static final int FOOT_H = 26;
    private static final int CAP = 11;
    private static final int ROW = 16;
    private static final int INPUT_H = 22;
    private static final int SW_H = 15;
    private static final int PREV_ROW = 16;
    private static final int OUT_ROW = 14;
    private static final int SV_W = 120, SV_H = 44, HUE_H = 7;
    private static final int GUTTER = 12;
    private static final int SIGN_W = 90;

    public interface Done { void apply(String text, String parsing); }

    private static final List<String> MODES = List.of(
            McText.PLAIN, McText.LEGACY, McText.MINI, McText.JSON);

    private final TextRenderer tr;
    private int screenW, screenH;
    private final Done done;

    private String parsing;
    private final TextFieldWidget input, hex;
    private Ui.Chips modeChips;

    private float pickH = 0f, pickS = 0.85f, pickV = 1f;
    private int gradA = 0xFF5555, gradB = 0x5555FF;
    private int dragging;
    private Menu menu;
    private String flash = "";
    private long flashAt;

    private int x, y, w, h;
    private int lw, rw;
    private final Ui.Pane pane = new Ui.Pane();
    private final Ui.Bar bar = new Ui.Bar();
    private int lastMx, lastMy;
    private boolean one;
    private int inputY, metaY, bodyY, modeY, swY, pickY, decoY, gradY, prevY, expY, footY;
    private int hexBx, hexBy, hexBw, samBx, samBy, apBx, apBy, apBw;
    private boolean compact, closed, syncing;

    public TextStudio(TextRenderer tr, int screenW, int screenH,
                      String text, String parsing, Done done) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.parsing = parsing;
        this.done = done;

        input = field(text, "текст сообщения");
        hex = field("", "#RRGGBB");
        hex.setMaxLength(7);
        hex.setChangedListener(s -> {
            if (syncing) return;
            String h6 = McText.normaliseHex(s);
            if (h6 == null) return;
            float[] hsv = McText.rgbHsv(McText.hexRgb(h6));
            pickH = hsv[0];
            pickS = hsv[1];
            pickV = hsv[2];
        });
        setHexFromPicker();
        input.setFocused(true);
        input.setCursorToEnd(false);
        input.addFormatter((visible, offset) -> highlight(visible));

        layout();
    }

    private TextFieldWidget field(String text, String placeholder) {
        return Ui.field(tr, text, placeholder, 256);
    }

    public boolean isClosed() { return closed; }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        screenW = sw;
        screenH = sh;
        compact = false;
        menu = null;
        layout();
    }

    private int inner() { return w - PAD * 2; }

    private void layout() {
        w = Ui.fitW(screenW, 600);
        one = inner() < 320;
        lw = one ? inner() : (inner() - GUTTER) * 52 / 100;
        rw = one ? inner() : inner() - GUTTER - lw;
        int at = HEAD_H + 1 + 8;
        inputY = at;                       at += INPUT_H;
        metaY = at;                        at += 12 + 6;
        bodyY = at + 1;

        List<String> modeNames = new ArrayList<>();
        for (String m : MODES) modeNames.add(Values.parsingName(m));
        modeChips = new Ui.Chips(tr, modeNames, lw, ROW, 3);

        int left = bodyY + 3;
        modeY = left + CAP;                left = modeY + modeChips.height() + 6;
        swY = left + CAP;                  left = swY + SW_H + 4;
        if (compact || lw < SV_W + 8 + 26 + 56) {
            pickY = -1;
            hexBx = 0;      hexBy = left;  hexBw = Math.min(90, lw / 3);
            samBx = hexBw + 4;             samBy = left;
            apBx = samBx + 26;             apBy = left;   apBw = lw - apBx;
            left = left + ROW + 6;
        } else {
            pickY = left;
            hexBx = SV_W + 8;              hexBy = left;
            hexBw = Math.min(96, lw - hexBx);
            samBx = SV_W + 8;              samBy = left + ROW + 4;
            apBx = samBx + 26;             apBy = samBy;
            apBw = Math.min(96, lw - apBx);
            left = pickY + SV_H + 2 + HUE_H + 6;
        }
        decoY = left + CAP;                left = decoY + ROW + 6;
        gradY = left + CAP;                left = gradY + ROW;

        int right = one ? left + 8 : bodyY + 3;
        prevY = right + CAP;               right = prevY + previews() * PREV_ROW + 8;
        expY = right + CAP;                right = expY + 3 * OUT_ROW;

        int contentH = (one ? right : Math.max(left, right)) + 8;
        int natural = contentH + 1 + FOOT_H;
        h = Ui.fitH(screenH, natural);
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
        footY = h - FOOT_H;

        if (h < natural && !compact) { compact = true; layout(); return; }
        pane.fit(HEAD_H + 1, footY, contentH);
        placeFields();
    }

    private void placeFields() {
        input.setX(x + PAD + 7);
        input.setY(absY(inputY) + (INPUT_H - 12) / 2 + 2);
        input.setWidth(inner() - 14);
        hex.setX(leftX() + hexBx + 6);
        hex.setY(absY(hexBy) + (ROW - 12) / 2 + 2);
        hex.setWidth(hexBw - 12);
    }

    private int previews() { return compact ? 2 : 3; }

    private int absY(int rel) { return y + pane.at(rel); }
    private int leftX() { return x + PAD; }
    private int rightX() { return one ? x + PAD : x + PAD + lw + GUTTER; }

    private OrderedText highlight(String s) {
        List<OrderedText> out = new ArrayList<>();
        int plainInk = Theme.TEXT, tagInk = 0xB08CFF, dimInk = 0x707A8C;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int tokenLen = 0, tokenInk = plainInk;

            if (McText.LEGACY.equals(parsing) && (c == '&' || c == '§') && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                String h6 = i + 8 <= s.length() && next == '#'
                        ? McText.normaliseHex(s.substring(i + 2, i + 8)) : null;
                if (h6 != null) {
                    tokenLen = 8;
                    tokenInk = McText.hexRgb(h6);
                } else {
                    Formatting f = Formatting.byCode(next);
                    if (f != null) {
                        tokenLen = 2;
                        tokenInk = f.getColorValue() != null ? f.getColorValue() : tagInk;
                    }
                }
            } else if (McText.MINI.equals(parsing) && c == '<') {
                int end = s.indexOf('>', i);
                if (end > i) {
                    tokenLen = end - i + 1;
                    String body = s.substring(i + 1, end);
                    boolean closing = body.startsWith("/");
                    String name = (closing ? body.substring(1) : body).toLowerCase();
                    tokenInk = tagInk;
                    if (name.startsWith("#")) {
                        String h6 = McText.normaliseHex(name);
                        if (h6 != null) tokenInk = McText.hexRgb(h6);
                    } else {
                        for (McText.Colour col : McText.COLOURS)
                            if (col.name().equals(name)) { tokenInk = col.rgb(); break; }
                    }
                    if (closing) tokenInk = Draw.shade(tokenInk, -0.35f);
                }
            } else if (McText.JSON.equals(parsing)) {
                if (c == '"') {
                    int end = i + 1;
                    while (end < s.length() && (s.charAt(end) != '"' || s.charAt(end - 1) == '\\')) end++;
                    tokenLen = Math.min(s.length(), end + 1) - i;
                    tokenInk = 0x9CDCFE;
                } else if ("{}[],:".indexOf(c) >= 0) {
                    tokenLen = 1;
                    tokenInk = dimInk;
                }
            }

            if (tokenLen == 0) { buf.append(c); i++; continue; }
            flush(out, buf, plainInk);
            out.add(styled(s.substring(i, i + tokenLen), tokenInk));
            i += tokenLen;
        }
        flush(out, buf, plainInk);
        return out.isEmpty() ? OrderedText.EMPTY : OrderedText.concat(out);
    }

    private static void flush(List<OrderedText> out, StringBuilder buf, int rgb) {
        if (buf.isEmpty()) return;
        out.add(styled(buf.toString(), rgb));
        buf.setLength(0);
    }

    private static OrderedText styled(String s, int rgb) {
        return OrderedText.styledForwardsVisitedString(s,
                Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lastMx = mouseX;
        lastMy = mouseY;
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, Values.color(Value.TEXT));

        Draw.round(ctx, x + PAD, y + 8, 3, 10, 1, Draw.opaque(Values.color(Value.TEXT)));
        String len = input.getText().length() + " симв.";
        Draw.textFit(ctx, tr, "Редактор текста", x + PAD + 9, y + 9,
                w - 2 * PAD - 9 - 22 - tr.getWidth(len) - 6, Theme.TEXT, false);
        Draw.textRight(ctx, tr, len, x + w - PAD - 22, y + 9, Theme.TEXT_FAINT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, y + 6, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        ctx.enableScissor(x + 1, y + pane.top(), x + w - 1, y + footY);
        Ui.input(ctx, x + PAD, absY(inputY), inner(), INPUT_H, input.isFocused());
        input.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, input);
        String note = System.currentTimeMillis() - flashAt < 1800 && !flash.isEmpty() ? flash
                : McText.formattable(parsing)
                        ? "выделите кусок текста и нажмите цвет — он обернётся, а не вставится"
                        : "в режиме «" + Values.parsingName(parsing) + "» разметки нет";
        Draw.textFit(ctx, tr, note, x + PAD + 2, absY(metaY) + 2, inner() - 4,
                flash.isEmpty() ? Theme.TEXT_FAINT : Theme.OK, false);
        Ui.hairline(ctx, x + 1, absY(bodyY) - 1, w - 2);

        drawLeft(ctx, mouseX, mouseY, delta);
        drawRight(ctx, mouseX, mouseY);
        if (!one) Ui.vline(ctx, x + PAD + lw + GUTTER / 2, absY(bodyY) + 2,
                Math.min(h - bodyY - FOOT_H - 12, pane.contentH - bodyY - 12));
        ctx.disableScissor();
        pane.drawBar(ctx, bar, x + w - 4, y, lastMx, lastMy);

        Ui.hairline(ctx, x + 1, y + footY - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY);

        if (menu != null) {
            ctx.createNewRootLayer();
            menu.render(ctx, tr, mouseX, mouseY);
        }
    }

    private void drawLeft(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        boolean on = McText.formattable(parsing);

        Ui.caption(ctx, tr, "РЕЖИМ РАЗМЕТКИ", lx, absY(modeY) - CAP, lw);
        modeChips.render(ctx, tr, mouseX, mouseY, lx, absY(modeY), MODES.indexOf(parsing),
                Values.color(Value.TEXT));

        Ui.caption(ctx, tr, on ? "ЦВЕТ" : "ЦВЕТ · недоступен", lx, absY(swY) - CAP, lw);
        int swW = (lw - 15) / 16;
        for (int i = 0; i < McText.COLOURS.size(); i++) {
            McText.Colour c = McText.COLOURS.get(i);
            int cx = lx + i * (swW + 1);
            Ui.swatch(ctx, cx, absY(swY), swW, SW_H, c.rgb(), on,
                    on && Ui.hit(mouseX, mouseY, cx, absY(swY), swW, SW_H));
        }

        if (pickY >= 0) drawPicker(ctx, mouseX, mouseY);
        drawHexRow(ctx, mouseX, mouseY, delta);

        Ui.caption(ctx, tr, "ОФОРМЛЕНИЕ", lx, absY(decoY) - CAP, lw);
        int dw = (lw - 5 * 3) / 6;
        for (int i = 0; i < McText.DECOS.size(); i++) {
            McText.Deco d = McText.DECOS.get(i);
            int cx = lx + i * (dw + 3);
            boolean hov = on && Ui.hit(mouseX, mouseY, cx, absY(decoY), dw, ROW);
            Draw.round(ctx, cx, absY(decoY), dw, ROW, Ui.R_SM,
                    Draw.opaque(hov ? Ui.BTN_HOVER : Ui.BTN));
            int ink = on ? (hov ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT;
            if (d.code() == 'm') {
                int gx = cx + (dw - Draw.glyphW(Draw.STRIKE_TEXT)) / 2;
                int gy = absY(decoY) + (ROW - Draw.glyphH(Draw.STRIKE_TEXT)) / 2;
                Draw.glyph(ctx, Draw.STRIKE_TEXT, gx, gy, Draw.mix(ink, Ui.BTN, 0.45f));
                Draw.glyph(ctx, Draw.STRIKE_LINE, gx, gy, ink);
                continue;
            }
            if (d.code() == 'r') {
                Draw.glyph(ctx, Draw.RESET, cx + (dw - Draw.glyphW(Draw.RESET)) / 2,
                        absY(decoY) + (ROW - Draw.glyphH(Draw.RESET)) / 2, ink);
                continue;
            }
            Text label = Text.literal(d.label()).styled(s -> switch (d.code()) {
                case 'l' -> s.withBold(true);
                case 'o' -> s.withItalic(true);
                case 'n' -> s.withUnderline(true);
                default -> s;
            });
            ctx.drawText(tr, label, cx + (dw - tr.getWidth(d.label())) / 2,
                    absY(decoY) + (ROW - Ui.TEXT_H) / 2, Draw.opaque(ink), false);
        }

        boolean grad = McText.supportsGradient(parsing);
        Ui.caption(ctx, tr, "ГРАДИЕНТ", lx, absY(gradY) - CAP, lw);
        int gy = absY(gradY);
        Ui.swatch(ctx, lx, gy, 18, ROW, gradA, grad, grad && Ui.hit(mouseX, mouseY, lx, gy, 18, ROW));
        Draw.hgrad(ctx, lx + 19, gy + 3, 26, ROW - 6,
                Draw.opaque(grad ? gradA : Draw.shade(gradA, -0.5f)),
                Draw.opaque(grad ? gradB : Draw.shade(gradB, -0.5f)));
        Ui.swatch(ctx, lx + 46, gy, 18, ROW, gradB, grad,
                grad && Ui.hit(mouseX, mouseY, lx + 46, gy, 18, ROW));
        int bw = Math.max(52, (lw - 70 - 4) / 2);
        Ui.button(ctx, tr, mouseX, mouseY, lx + 68, gy, bw, ROW, "применить", Ui.GHOST, grad);
        Ui.button(ctx, tr, mouseX, mouseY, lx + 68 + bw + 4, gy, lw - 72 - bw, ROW, "радуга",
                Ui.GHOST, grad);
    }

    private void drawPicker(DrawContext ctx, int mouseX, int mouseY) {
        int lx = leftX(), sy = absY(pickY);
        Ui.svSquare(ctx, lx, sy, SV_W, SV_H, pickH, pickS, pickV, 3);
        Ui.hueBar(ctx, lx, sy + SV_H + 2, SV_W, HUE_H, pickH);
    }

    private void drawHexRow(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        Ui.input(ctx, lx + hexBx, absY(hexBy), hexBw, ROW, hex.isFocused());
        hex.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, hex);
        Ui.swatch(ctx, lx + samBx, absY(samBy), 22, ROW, pickRgb(), true, false);
        Ui.button(ctx, tr, mouseX, mouseY, lx + apBx, absY(apBy), apBw, ROW, "применить",
                Ui.GHOST, McText.formattable(parsing));
    }

    private int column(String[] labels, int lead, int trail) {
        int w = 0;
        for (String s : labels) w = Math.max(w, tr.getWidth(s));
        return lead + w + trail;
    }

    private static final String[] WHERE = {"в чате", "на табличке", "в имени"};

    private void drawRight(DrawContext ctx, int mouseX, int mouseY) {
        int px = rightX();
        Ui.caption(ctx, tr, "ПРЕДПРОСМОТР", px, absY(prevY) - CAP, rw);
        Text t = McText.preview(input.getText(), parsing);
        int width = tr.getWidth(McText.writePlain(McText.runs(input.getText(), parsing)));
        int col = column(WHERE, 5, 8);
        for (int i = 0; i < previews(); i++) {
            int ry = absY(prevY) + i * PREV_ROW;
            Draw.rect(ctx, px, ry, rw, PREV_ROW - 1, Draw.opaque(i % 2 == 0 ? 0x12151C : 0x151920));
            Draw.text(ctx, tr, WHERE[i], px + 5, ry + 4, Theme.TEXT_FAINT, false);
            int tx = px + col;
            int right = px + rw - (i == 1 ? 40 : 4);
            ctx.enableScissor(tx, ry, right, ry + PREV_ROW - 1);
            ctx.drawText(tr, t, tx, ry + 4, Draw.opaque(Theme.TEXT), i != 1);
            ctx.disableScissor();
            if (i == 1) {
                boolean over = width > SIGN_W;
                Draw.textRight(ctx, tr, width + "/" + SIGN_W, px + rw - 4, ry + 4,
                        over ? Theme.DANGER : Theme.TEXT_FAINT, false);
            }
        }

        Ui.caption(ctx, tr, "В ДРУГИХ РЕЖИМАХ · КЛИК КОПИРУЕТ", px, absY(expY) - CAP, rw);
        List<String> others = others();
        int mcol = column(new String[]{Values.parsingName(McText.PLAIN),
                Values.parsingName(McText.LEGACY), Values.parsingName(McText.MINI),
                Values.parsingName(McText.JSON)}, 5, 8);
        for (int i = 0; i < others.size(); i++) {
            int ry = absY(expY) + i * OUT_ROW;
            boolean hov = Ui.hit(mouseX, mouseY, px, ry, rw, OUT_ROW - 1);
            if (hov) Draw.round(ctx, px, ry, rw, OUT_ROW - 1, 3, Draw.opaque(0x232A36));
            Draw.text(ctx, tr, Values.parsingName(others.get(i)), px + 5, ry + 3,
                    hov ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, McText.convert(input.getText(), parsing, others.get(i)),
                    px + mcol, ry + 3, rw - (hov ? 22 : 6) - mcol,
                    hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (hov) Draw.glyph(ctx, Draw.COPY, px + rw - 14, ry + 2, Theme.TEXT_DIM);
        }
    }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY) {
        int fy = y + footY + (FOOT_H - ROW) / 2;
        Draw.textFit(ctx, tr, "Enter — сохранить · Esc — отменить · Tab — в поле HEX",
                x + PAD, fy + 4, inner() - 120, Theme.TEXT_FAINT, false);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 56, fy, 56, ROW, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 114, fy, 52, ROW, "Отмена", Ui.GHOST);
    }

    private List<String> others() {
        List<String> out = new ArrayList<>(MODES);
        out.remove(parsing);
        return out;
    }

    private int pickRgb() { return McText.hsvRgb(pickH, pickS, pickV); }

    private void setHexFromPicker() {
        syncing = true;
        hex.setText("#" + String.format("%06x", pickRgb() & 0xFFFFFF));
        syncing = false;
    }

    private void toast(String s) { flash = s; flashAt = System.currentTimeMillis(); }

    private void wrap(String open, String close) {
        String sel = input.getSelectedText();
        input.setFocused(true);
        hex.setFocused(false);
        input.write(sel.isEmpty() ? open : open + sel + close);
    }

    private void stripFormatting() {
        String sel = input.getSelectedText();
        input.setFocused(true);
        hex.setFocused(false);
        if (sel.isEmpty()) {
            input.setText(McText.plain(input.getText(), parsing));
            input.setCursorToEnd(false);
            toast("разметка убрана");
            return;
        }
        input.write(McText.plain(sel, parsing));
        toast("разметка убрана из выделения");
    }

    private void span(java.util.function.Function<String, String> markup) {
        String sel = input.getSelectedText();
        if (sel.isEmpty()) { toast("сначала выделите текст"); return; }
        input.setFocused(true);
        hex.setFocused(false);
        input.write(markup.apply(sel));
    }

    private void setMode(String mode) {
        if (mode.equals(parsing)) return;
        input.setText(McText.convert(input.getText(), parsing, mode));
        parsing = mode;
        input.setCursorToEnd(false);
        toast("переведено в «" + Values.parsingName(mode) + "»");
    }

    private void copy(String s) {
        MinecraftClient.getInstance().keyboard.setClipboard(s);
        toast("скопировано");
    }

    private void openColourMenu(boolean first) {
        List<String> names = new ArrayList<>();
        for (McText.Colour c : McText.COLOURS) names.add(c.name());
        menu = Menu.options(screenW, screenH, leftX() + (first ? 0 : 46), absY(gradY) + ROW + 2, tr,
                first ? "Начальный цвет" : "Конечный цвет", names, -1, i -> {
                    if (first) gradA = McText.COLOURS.get(i).rgb();
                    else gradB = McText.COLOURS.get(i).rgb();
                });
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (Ui.hit(mx, my, x + w - PAD - 14, y + 6, 14, 14)) { closed = true; return true; }
        if (bar.press(mx, my)) { pane.scroll = bar.follow(my, 1, pane.max()); return true; }
        if (!pane.inBody(my, y)) return footClicked(mx, my);

        if (Ui.hit(mx, my, x + PAD, absY(inputY), inner(), INPUT_H)) {
            input.setFocused(true);
            hex.setFocused(false);
            if (!input.mouseClicked(click, doubled)) input.onClick(click, doubled);
            return true;
        }

        int mi = modeChips.indexAt(mx, my, leftX(), absY(modeY));
        if (mi >= 0) { setMode(MODES.get(mi)); return true; }

        int lx = leftX();
        if (McText.formattable(parsing)) {
            int swW = (lw - 15) / 16;
            for (int i = 0; i < McText.COLOURS.size(); i++)
                if (Ui.hit(mx, my, lx + i * (swW + 1), absY(swY), swW, SW_H)) {
                    McText.Colour c = McText.COLOURS.get(i);
                    wrap(McText.colourTag(parsing, c), McText.colourClose(parsing, c));
                    return true;
                }
            int dw = (lw - 5 * 3) / 6;
            for (int i = 0; i < McText.DECOS.size(); i++)
                if (Ui.hit(mx, my, lx + i * (dw + 3), absY(decoY), dw, ROW)) {
                    McText.Deco d = McText.DECOS.get(i);
                    if (d.code() == 'r' && click.button() == 1) stripFormatting();
                    else wrap(McText.decoTag(parsing, d), McText.decoClose(parsing, d));
                    return true;
                }
        }

        if (pickerClicked(mx, my)) return true;

        if (Ui.hit(mx, my, lx + hexBx, absY(hexBy), hexBw, ROW)) {
            hex.setFocused(true);
            input.setFocused(false);
            if (!hex.mouseClicked(click, doubled)) hex.onClick(click, doubled);
            return true;
        }
        if (Ui.hit(mx, my, lx + apBx, absY(apBy), apBw, ROW)) {
            if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return true; }
            wrap(McText.hexTag(parsing, pickRgb()), McText.MINI.equals(parsing) ? "" : "&r");
            return true;
        }

        int gy = absY(gradY);
        if (McText.supportsGradient(parsing)) {
            if (Ui.hit(mx, my, lx, gy, 18, ROW)) {
                if (click.button() == 1) openColourMenu(true);
                else { gradA = pickRgb(); toast("начальный цвет взят из палитры"); }
                return true;
            }
            if (Ui.hit(mx, my, lx + 46, gy, 18, ROW)) {
                if (click.button() == 1) openColourMenu(false);
                else { gradB = pickRgb(); toast("конечный цвет взят из палитры"); }
                return true;
            }
            int bw = Math.max(52, (lw - 70 - 4) / 2);
            if (Ui.hit(mx, my, lx + 68, gy, bw, ROW)) {
                span(s -> McText.gradientMarkup(parsing, s, new int[]{gradA, gradB}));
                return true;
            }
            if (Ui.hit(mx, my, lx + 68 + bw + 4, gy, lw - 72 - bw, ROW)) {
                span(s -> McText.rainbowMarkup(parsing, s));
                return true;
            }
        }

        List<String> others = others();
        for (int i = 0; i < others.size(); i++)
            if (Ui.hit(mx, my, rightX(), absY(expY) + i * OUT_ROW, rw, OUT_ROW - 1)) {
                copy(McText.convert(input.getText(), parsing, others.get(i)));
                return true;
            }

        return footClicked(mx, my);
    }

    private boolean footClicked(int mx, int my) {
        int fy = y + footY + (FOOT_H - ROW) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 56, fy, 56, ROW)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 114, fy, 52, ROW)) { closed = true; return true; }
        return true;
    }

    private boolean pickerClicked(int mx, int my) {
        if (pickY < 0) return false;
        int lx = leftX(), sy = absY(pickY);
        if (Ui.hit(mx, my, lx, sy, SV_W, SV_H)) { dragging = 1; dragPicker(mx, my); return true; }
        int hy = sy + SV_H + 2;
        if (Ui.hit(mx, my, lx, hy - 1, SV_W, HUE_H + 2)) { dragging = 2; dragPicker(mx, my); return true; }
        return false;
    }

    private void dragPicker(double mx, double my) {
        int lx = leftX(), sy = absY(pickY);
        if (dragging == 1) {
            pickS = clamp01((mx - lx) / (SV_W - 1.0));
            pickV = 1f - clamp01((my - sy) / (SV_H - 1.0));
        } else if (dragging == 2) {
            pickH = clamp01((mx - lx) / (SV_W - 1.0));
        }
        setHexFromPicker();
    }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    private void finish() {
        done.apply(input.getText(), parsing);
        closed = true;
    }

    public boolean mouseDragged(Click click, double dx, double dy) {
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (bar.dragging()) { pane.scroll = bar.follow(click.y(), 1, pane.max()); return true; }
        if (dragging != 0) { dragPicker(click.x(), click.y()); return true; }
        if (hex.isFocused()) return hex.mouseDragged(click, dx, dy);
        return input.mouseDragged(click, dx, dy);
    }

    public boolean mouseReleased() {
        dragging = 0;
        bar.release();
        if (menu != null) menu.mouseReleased();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (menu != null) return menu.mouseScrolled(mx, my, amount);
        if (pane.max() > 0) {
            pane.wheel(amount);
            placeFields();
        }
        return true;
    }

    public boolean keyPressed(KeyInput in) {
        int key = in.key();
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { finish(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean toHex = input.isFocused();
            input.setFocused(!toHex);
            hex.setFocused(toHex);
            return true;
        }
        if (hex.isFocused()) return hex.keyPressed(in);
        return input.keyPressed(in);
    }

    public boolean charTyped(CharInput in) {
        if (menu != null) return true;
        if (hex.isFocused()) return hex.charTyped(in);
        return input.charTyped(in);
    }
}
