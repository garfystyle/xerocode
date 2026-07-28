package com.xerocode.ui;

import com.xerocode.Symbols;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class TextStudio {
    private static final int PAD = 10, HEAD_H = 26, FOOT_H = 26, GUTTER = 12;
    private static final int CAP = 11, ROW = 16, INPUT_H = 24, TAB_H = 17, TOOL_H = 20;
    private static final int SW_H = 15, SV_H = 56, SL_H = 10, SL_GAP = 5;
    private static final int CELL = 17, PREV_ROW = 17, OUT_ROW = 15, SIGN_W = 90;
    private static final int HEX_W = 92, DECO_W = 22, SEARCH_H = 18, CAT_H = 14;
    private static final int RECENT_MAX = 12, UNDO_MAX = 64;

    private static final String SEP = "\0";
    private static final String TITLE = "Редактор текста";
    private static final String[] WHERE = {"в чате", "на табличке", "в имени"};
    private static final List<String> MODES = List.of(
            McText.PLAIN, McText.LEGACY, McText.MINI, McText.JSON);
    private static final List<String> TABS = List.of("Цвет", "Символы");
    private static final List<Integer> RECENT = new ArrayList<>();

    public interface Done { void apply(String text, String parsing); }

    private final TextRenderer tr;
    private final Done done;
    private int screenW, screenH;

    private String parsing;
    private final TextFieldWidget input, hex, search;
    private Ui.Chips modeChips, tabChips, catChips;

    private float pickH = 0f, pickS = 0.85f, pickV = 1f;
    private int gradA = 0xFF5555, gradB = 0x5555FF;
    private int tab, cat, gridScroll, dragging;
    private Menu menu;
    private String flash = "";
    private long flashAt, lastTyped;

    private final Deque<String[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    private int x, y, w, h, lw, rw;
    private final Ui.Pane pane = new Ui.Pane();
    private final Ui.Bar bar = new Ui.Bar();
    private boolean one, compact, closed, syncing, headMode, toolRow2;
    private int modeY, inputY, toolY, hintY, bodyY, bodyH, tabsY;
    private int swY, pickY, hexY, recentY, svW;
    private int searchY, catsY, gridY, gridRows, symHintY;
    private int prevY, expY, footY;
    private int decoW, decoAt, gradAt, gradRow, gradBtnW, rainBtnW;
    private int[][] cells = new int[0][];
    private int gridTotal;

    private String tintKey = SEP, prevKey = SEP, shownKey = SEP;
    private int[] tint = new int[0];
    private Text preview = Text.empty();
    private int plainW;
    private List<String> otherModes = List.of();
    private final List<String> otherText = new ArrayList<>();
    private List<Symbols.Sym> shown = List.of();

    public TextStudio(TextRenderer tr, int screenW, int screenH,
                      String text, String parsing, Done done) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.parsing = parsing;
        this.done = done;

        input = Ui.field(tr, text, "текст сообщения", Ui.TEXT_MAX);
        hex = Ui.field(tr, "", "#RRGGBB", 7);
        search = Ui.field(tr, "", "поиск символа", 32);
        hex.setChangedListener(s -> {
            if (syncing) return;
            String h6 = McText.normaliseHex(s);
            if (h6 != null) fromRgb(McText.hexRgb(h6));
        });
        setHexFromPicker();
        focus(input);
        input.addFormatter(this::highlight);

        layout();
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

    private int previews() { return compact ? 2 : 3; }

    private boolean recents() { return !compact; }

    private int svH() { return compact ? 42 : SV_H; }

    private void layout() {
        w = Ui.fitW(screenW, 660);
        one = inner() < 340;
        lw = one ? inner() : (inner() - GUTTER) * 54 / 100;
        rw = one ? inner() : inner() - GUTTER - lw;

        List<String> modeNames = new ArrayList<>();
        for (String m : MODES) modeNames.add(Values.parsingName(m));
        modeChips = new Ui.Chips(tr, modeNames, inner(), TAB_H, 3);
        tabChips = new Ui.Chips(tr, TABS, lw, TAB_H, 3);
        catChips = new Ui.Chips(tr, Symbols.categories(), lw, CAT_H, 3);

        int headRoom = inner() - tr.getWidth(TITLE) - 9 - 14
                - tr.getWidth("0000 симв.") - 14 - 22;
        headMode = modeChips.rows == 1 && modeChips.width() <= headRoom;

        int at = HEAD_H + 1 + 8;
        modeY = -1;
        if (!headMode) {
            modeY = at;
            at += modeChips.height() + 8;
        }
        inputY = at;
        at += INPUT_H + 5;

        decoW = McText.DECOS.size() * DECO_W + (McText.DECOS.size() - 1) * 3;
        gradBtnW = Ui.buttonW(tr, "градиент");
        rainBtnW = Ui.buttonW(tr, "радуга");
        int gradW = 62 + 8 + gradBtnW + 4 + rainBtnW;
        toolRow2 = decoW + 8 + gradW > inner();
        gradRow = toolRow2 ? 1 : 0;
        int used = toolRow2 ? Math.max(decoW, gradW) : decoW + 8 + gradW;
        if (toolRow2) {
            decoAt = (inner() - decoW) / 2;
            gradAt = (inner() - gradW) / 2;
        } else {
            decoAt = (inner() - used) / 2;
            gradAt = decoAt + decoW + 8;
        }
        int toolH = toolRow2 ? TOOL_H * 2 + 4 : TOOL_H;

        toolY = at;
        at += toolH;
        hintY = at + 5;
        at += 5 + 11 + 6;
        bodyY = at + 1;

        tabsY = bodyY + 5;
        int top = tabsY + TAB_H + 8;

        swY = top + CAP;
        int c = swY + SW_H + 8;
        svW = Math.min(140, lw * 44 / 100);
        pickY = c;
        c += svH() + 8;
        hexY = c;
        c += ROW + 8;
        recentY = c + CAP;
        if (recents()) c += CAP + 12;
        int colourH = c - bodyY;

        searchY = top;
        int s = searchY + SEARCH_H + 6;
        catsY = s;
        s += catChips.height() + 6;
        gridY = s;
        gridRows = Math.max(2, (colourH - (gridY - bodyY) - 12) / CELL);
        s += gridRows * CELL + 2;
        symHintY = s;
        s += 10;
        int symbolsH = s - bodyY;

        int leftH = Math.max(colourH, symbolsH);
        int rtop = one ? bodyY + leftH + 10 : bodyY + 5;
        prevY = rtop + CAP;
        int r = prevY + previews() * PREV_ROW + 10;
        expY = r + CAP;
        r = expY + 3 * OUT_ROW;

        bodyH = one ? r - bodyY : Math.max(leftH, r - bodyY);
        int contentH = bodyY + bodyH + 8;
        int natural = contentH + 1 + FOOT_H;
        h = Ui.fitH(screenH, natural);
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
        footY = h - FOOT_H;

        if (h < natural && !compact) {
            compact = true;
            layout();
            return;
        }
        pane.fit(HEAD_H + 1, footY, contentH);
        clampGrid();
        placeFields();
    }

    private void placeFields() {
        input.setX(x + PAD + 7);
        input.setY(absY(inputY) + (INPUT_H - 12) / 2 + 2);
        Ui.width(input, inner() - 14);
        hex.setX(leftX() + 6);
        hex.setY(absY(hexY) + (ROW - 12) / 2 + 2);
        Ui.width(hex, HEX_W - 12);
        search.setX(leftX() + 7);
        search.setY(absY(searchY) + (SEARCH_H - 12) / 2 + 2);
        Ui.width(search, lw - 14);
    }

    private int headY(int hgt) { return y + 1 + (HEAD_H - 1 - hgt) / 2; }

    private int headChipsX() {
        int cw = modeChips.width();
        int left = x + PAD + 9 + tr.getWidth(TITLE) + 14;
        int right = x + w - PAD - 22 - tr.getWidth("0000 симв.") - 14 - cw;
        return Math.max(left, Math.min(x + (w - cw) / 2, right));
    }

    private int absY(int rel) { return y + pane.at(rel); }
    private int leftX() { return x + PAD; }
    private int rightX() { return one ? x + PAD : x + PAD + lw + GUTTER; }
    private int accent() { return Values.color(Value.TEXT); }

    private void focus(TextFieldWidget f) {
        input.setFocused(f == input);
        hex.setFocused(f == hex);
        search.setFocused(f == search);
    }

    private int[] colourise(String s) {
        int[] out = new int[s.length()];
        int plainInk = Theme.TEXT, tagInk = 0xB08CFF, dimInk = 0x707A8C;
        Arrays.fill(out, plainInk);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int len = 0, ink = plainInk;

            if (McText.LEGACY.equals(parsing) && (c == '&' || c == '§') && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                String h6 = i + 8 <= s.length() && next == '#'
                        ? McText.normaliseHex(s.substring(i + 2, i + 8)) : null;
                if (h6 != null) {
                    len = 8;
                    ink = McText.hexRgb(h6);
                } else {
                    Formatting f = Formatting.byCode(next);
                    if (f != null) {
                        len = 2;
                        ink = f.getColorValue() != null ? f.getColorValue() : tagInk;
                    }
                }
            } else if (McText.MINI.equals(parsing) && c == '<') {
                int end = s.indexOf('>', i);
                if (end > i) {
                    len = end - i + 1;
                    String body = s.substring(i + 1, end);
                    boolean closing = body.startsWith("/");
                    String name = (closing ? body.substring(1) : body).toLowerCase();
                    ink = tagInk;
                    if (name.startsWith("#")) {
                        String h6 = McText.normaliseHex(name);
                        if (h6 != null) ink = McText.hexRgb(h6);
                    } else {
                        for (McText.Colour col : McText.COLOURS)
                            if (col.name().equals(name)) { ink = col.rgb(); break; }
                    }
                    if (closing) ink = Draw.shade(ink, -0.35f);
                }
            } else if (McText.JSON.equals(parsing)) {
                if (c == '"') {
                    int end = i + 1;
                    while (end < s.length() && (s.charAt(end) != '"' || s.charAt(end - 1) == '\\')) end++;
                    len = Math.min(s.length(), end + 1) - i;
                    ink = 0x9CDCFE;
                } else if ("{}[],:".indexOf(c) >= 0) {
                    len = 1;
                    ink = dimInk;
                }
            }

            if (len == 0) { i++; continue; }
            for (int k = i; k < i + len && k < out.length; k++) out[k] = ink;
            i += len;
        }
        return out;
    }

    private String textKey() { return parsing + SEP + input.getText(); }

    private OrderedText highlight(String visible, int offset) {
        String key = textKey();
        if (!key.equals(tintKey)) {
            tintKey = key;
            tint = colourise(input.getText());
        }
        List<OrderedText> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int run = Theme.TEXT;
        for (int i = 0; i < visible.length(); i++) {
            int idx = offset + i;
            int ink = idx >= 0 && idx < tint.length ? tint[idx] : Theme.TEXT;
            if (ink != run) {
                flush(out, buf, run);
                run = ink;
            }
            buf.append(visible.charAt(i));
        }
        flush(out, buf, run);
        return out.isEmpty() ? OrderedText.EMPTY : OrderedText.concat(out);
    }

    private static void flush(List<OrderedText> out, StringBuilder buf, int rgb) {
        if (buf.isEmpty()) return;
        out.add(OrderedText.styledForwardsVisitedString(buf.toString(),
                Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        buf.setLength(0);
    }

    private void syncPreview() {
        String key = textKey();
        if (key.equals(prevKey)) return;
        prevKey = key;
        String raw = input.getText();
        List<McText.Run> runs = McText.runs(raw, parsing);
        preview = McText.preview(raw, parsing);
        plainW = tr.getWidth(McText.writePlain(runs));
        List<String> rest = new ArrayList<>(MODES);
        rest.remove(parsing);
        otherModes = rest;
        otherText.clear();
        for (String m : rest) otherText.add(McText.convert(raw, parsing, m));
    }

    private void syncSymbols() {
        String q = search.getText().trim();
        String key = tab + SEP + cat + SEP + Symbols.favourites().size() + SEP
                + Symbols.onlyDrawable() + SEP + lw + SEP + q;
        if (key.equals(shownKey)) return;
        shownKey = key;
        shown = q.isEmpty() ? Symbols.group(cat) : Symbols.search(q);
        flow();
        clampGrid();
    }

    private void flow() {
        cells = new int[shown.size()][];
        int cx = 0, cy = 0;
        for (int i = 0; i < shown.size(); i++) {
            int cw = Math.min(lw, Math.max(CELL, tr.getWidth(shown.get(i).glyph()) + 7));
            if (cx > 0 && cx + cw > lw) {
                cx = 0;
                cy += CELL;
            }
            cells[i] = new int[]{cx, cy, cw};
            cx += cw;
        }
        gridTotal = shown.isEmpty() ? 0 : cy / CELL + 1;
    }

    private int gridMax() { return Math.max(0, gridTotal - gridRows) * CELL; }

    private void clampGrid() { gridScroll = Math.max(0, Math.min(gridMax(), gridScroll)); }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        syncPreview();
        syncSymbols();
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent());

        Draw.round(ctx, x + PAD, headY(10), 3, 10, 1, Draw.opaque(accent()));
        String len = input.getText().length() + " симв.";
        Draw.text(ctx, tr, TITLE, x + PAD + 9, headY(Ui.TEXT_H), Theme.TEXT, false);
        Draw.textRight(ctx, tr, len, x + w - PAD - 22, headY(Ui.TEXT_H), Theme.TEXT_FAINT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, headY(14), 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
        if (headMode)
            modeChips.render(ctx, tr, mouseX, mouseY, headChipsX(), headY(TAB_H),
                    MODES.indexOf(parsing), accent());

        ctx.enableScissor(x + 1, y + pane.top(), x + w - 1, y + footY);
        if (!headMode)
            modeChips.render(ctx, tr, mouseX, mouseY, leftX(), absY(modeY),
                    MODES.indexOf(parsing), accent());

        Ui.input(ctx, x + PAD, absY(inputY), inner(), INPUT_H, input.isFocused());
        input.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, input);

        drawTools(ctx, mouseX, mouseY);
        Ui.hairline(ctx, x + 1, absY(bodyY) - 1, w - 2);

        tabChips.render(ctx, tr, mouseX, mouseY, leftX(), absY(tabsY), tab, accent());
        if (tab == 0) drawColour(ctx, mouseX, mouseY, delta);
        else drawSymbols(ctx, mouseX, mouseY, delta);
        drawRight(ctx, mouseX, mouseY);
        if (!one) Ui.vline(ctx, x + PAD + lw + GUTTER / 2, absY(bodyY) + 4, bodyH - 8);
        ctx.disableScissor();
        pane.drawBar(ctx, bar, x + w - 4, y, mouseX, mouseY);

        Ui.hairline(ctx, x + 1, y + footY - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY);

        if (menu != null) {
            ctx.createNewRootLayer();
            menu.render(ctx, tr, mouseX, mouseY);
        }
    }

    private void drawTools(DrawContext ctx, int mouseX, int mouseY) {
        int lx = leftX(), ty = absY(toolY);
        boolean on = McText.formattable(parsing);
        for (int i = 0; i < McText.DECOS.size(); i++) {
            McText.Deco d = McText.DECOS.get(i);
            int cx = lx + decoAt + i * (DECO_W + 3);
            boolean hov = on && Ui.hit(mouseX, mouseY, cx, ty, DECO_W, TOOL_H);
            Draw.round(ctx, cx, ty, DECO_W, TOOL_H, Ui.R_SM,
                    Draw.opaque(hov ? Ui.BTN_HOVER : Ui.BTN));
            int ink = on ? (hov ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT;
            if (d.code() == 'm') {
                int gx = cx + (DECO_W - Draw.glyphW(Draw.STRIKE_TEXT)) / 2;
                int gy = ty + (TOOL_H - Draw.glyphH(Draw.STRIKE_TEXT)) / 2;
                Draw.glyph(ctx, Draw.STRIKE_TEXT, gx, gy, Draw.mix(ink, Ui.BTN, 0.45f));
                Draw.glyph(ctx, Draw.STRIKE_LINE, gx, gy, ink);
                continue;
            }
            if (d.code() == 'r') {
                Draw.glyph(ctx, Draw.RESET, cx + (DECO_W - Draw.glyphW(Draw.RESET)) / 2,
                        ty + (TOOL_H - Draw.glyphH(Draw.RESET)) / 2, ink);
                continue;
            }
            Text label = Text.literal(d.label()).styled(s -> switch (d.code()) {
                case 'l' -> s.withBold(true);
                case 'o' -> s.withItalic(true);
                case 'n' -> s.withUnderline(true);
                default -> s;
            });
            ctx.drawText(tr, label, cx + (DECO_W - tr.getWidth(d.label())) / 2,
                    ty + (TOOL_H - Ui.TEXT_H) / 2, Draw.opaque(ink), false);
        }

        boolean grad = McText.supportsGradient(parsing);
        int gx = lx + gradAt, gy = ty + gradRow * (TOOL_H + 4);
        Ui.swatch(ctx, gx, gy, 18, TOOL_H, gradA, grad,
                grad && Ui.hit(mouseX, mouseY, gx, gy, 18, TOOL_H));
        Draw.hgrad(ctx, gx + 19, gy + 4, 24, TOOL_H - 8,
                Draw.opaque(grad ? gradA : Draw.shade(gradA, -0.5f)),
                Draw.opaque(grad ? gradB : Draw.shade(gradB, -0.5f)));
        Ui.swatch(ctx, gx + 44, gy, 18, TOOL_H, gradB, grad,
                grad && Ui.hit(mouseX, mouseY, gx + 44, gy, 18, TOOL_H));
        Ui.button(ctx, tr, mouseX, mouseY, gx + 70, gy, gradBtnW, TOOL_H, "градиент",
                Ui.GHOST, grad);
        Ui.button(ctx, tr, mouseX, mouseY, gx + 74 + gradBtnW, gy, rainBtnW, TOOL_H, "радуга",
                Ui.GHOST, grad);

        String note = hint(mouseX, mouseY);
        int ink = !flash.isEmpty() && System.currentTimeMillis() - flashAt < 1800
                ? Theme.OK : Theme.TEXT_FAINT;
        Draw.textCenter(ctx, tr, note, lx, absY(hintY), inner(), inner() - 4, ink, false);
    }

    private String hint(int mx, int my) {
        if (!flash.isEmpty() && System.currentTimeMillis() - flashAt < 1800) return flash;
        String over = hovered(mx, my);
        if (!over.isEmpty()) return over;
        if (!McText.formattable(parsing))
            return "в режиме «" + Values.parsingName(parsing) + "» разметки нет";
        return "выделите текст — цвет и стиль обернут его";
    }

    private String hovered(int mx, int my) {
        int lx = leftX(), ty = absY(toolY);
        for (int i = 0; i < McText.DECOS.size(); i++)
            if (Ui.hit(mx, my, lx + decoAt + i * (DECO_W + 3), ty, DECO_W, TOOL_H)) {
                McText.Deco d = McText.DECOS.get(i);
                return d.code() == 'r' ? "сбросить формат · ПКМ — снять разметку" : d.title();
            }
        int gx = lx + gradAt, gy = ty + gradRow * (TOOL_H + 4);
        if (Ui.hit(mx, my, gx, gy, 18, TOOL_H)) return "начальный цвет · ПКМ — список";
        if (Ui.hit(mx, my, gx + 44, gy, 18, TOOL_H)) return "конечный цвет · ПКМ — список";
        if (!pane.inBody(my, y)) return "";
        if (tab == 0) {
            int swW = swatchW();
            for (int i = 0; i < McText.COLOURS.size(); i++)
                if (Ui.hit(mx, my, lx + i * (swW + 1), absY(swY), swW, SW_H))
                    return McText.COLOURS.get(i).name() + " · " + McText.colourTag(parsing,
                            McText.COLOURS.get(i));
            if (recents()) {
                for (int i = 0; i < RECENT.size(); i++)
                    if (Ui.hit(mx, my, lx + i * 14, absY(recentY), 13, 11))
                        return "#" + String.format("%06x", RECENT.get(i) & 0xFFFFFF);
            }
        } else {
            int at = symbolAt(mx, my);
            if (at >= 0) {
                Symbols.Sym s = shown.get(at);
                return s.glyph() + "  " + s.name() + " · " + Symbols.code(s.glyph())
                        + (s.drawable() ? "" : " · шрифт игры его не рисует");
            }
        }
        return "";
    }

    private int swatchW() { return (lw - 15) / 16; }

    private void drawColour(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        boolean on = McText.formattable(parsing);

        Ui.caption(ctx, tr, on ? "ЦВЕТ" : "ЦВЕТ · недоступен", lx, absY(swY) - CAP, lw);
        int swW = swatchW();
        for (int i = 0; i < McText.COLOURS.size(); i++) {
            int cx = lx + i * (swW + 1);
            Ui.swatch(ctx, cx, absY(swY), swW, SW_H, McText.COLOURS.get(i).rgb(), on,
                    on && Ui.hit(mouseX, mouseY, cx, absY(swY), swW, SW_H));
        }

        int sy = absY(pickY);
        Ui.svSquare(ctx, lx, sy, svW, svH(), pickH, pickS, pickV, 3);
        int slx = lx + svW + 8 + 11;
        int slw = lw - svW - 8 - 11 - 36;
        int slTop = sy + (svH() - (3 * SL_H + 2 * SL_GAP)) / 2;
        String[] names = {"H", "S", "V"};
        float[] vals = {pickH, pickS, pickV};
        String[] shows = {Math.round(pickH * 360) + "°",
                Math.round(pickS * 100) + "%", Math.round(pickV * 100) + "%"};
        for (int i = 0; i < 3; i++) {
            int ry = slTop + i * (SL_H + SL_GAP);
            Draw.text(ctx, tr, names[i], lx + svW + 8, ry + 1, Theme.TEXT_DIM, false);
            Ui.hsvSlider(ctx, slx, ry, slw, SL_H, i, pickH, pickS, pickV,
                    dragging == 2 + i || Ui.hit(mouseX, mouseY, slx, ry - 2, slw, SL_H + 4));
            Draw.textRight(ctx, tr, shows[i], lx + lw, ry + 1, Theme.TEXT_DIM, false);
        }

        Ui.input(ctx, lx, absY(hexY), HEX_W, ROW, hex.isFocused());
        hex.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, hex);
        Ui.swatch(ctx, lx + HEX_W + 4, absY(hexY), 24, ROW, pickRgb(), true, false);
        Ui.button(ctx, tr, mouseX, mouseY, lx + HEX_W + 32, absY(hexY), lw - HEX_W - 32, ROW,
                "применить цвет", Ui.GHOST, on);

        if (!recents()) return;
        Ui.caption(ctx, tr, "НЕДАВНИЕ", lx, absY(recentY) - CAP, lw);
        if (RECENT.isEmpty()) {
            Draw.textFit(ctx, tr, "пока пусто", lx, absY(recentY) + 2, lw, Theme.TEXT_FAINT, false);
            return;
        }
        for (int i = 0; i < RECENT.size(); i++)
            Ui.swatch(ctx, lx + i * 14, absY(recentY), 13, 11, RECENT.get(i), true,
                    Ui.hit(mouseX, mouseY, lx + i * 14, absY(recentY), 13, 11));
    }

    private void drawSymbols(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        Ui.input(ctx, lx, absY(searchY), lw, SEARCH_H, search.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, lx + lw - 14, absY(searchY) + 5,
                search.isFocused() ? Theme.TEXT_DIM : Theme.TEXT_FAINT);
        search.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, search);

        boolean searching = !search.getText().trim().isEmpty();
        catChips.render(ctx, tr, mouseX, mouseY, lx, absY(catsY),
                searching ? -1 : cat, accent());

        int gy = absY(gridY);
        ctx.enableScissor(lx, gy, lx + lw, gy + gridRows * CELL);
        for (int i = 0; i < shown.size(); i++) {
            int cx = lx + cells[i][0];
            int cy = gy + cells[i][1] - gridScroll;
            int cw = cells[i][2];
            if (cy + CELL < gy || cy > gy + gridRows * CELL) continue;
            boolean hov = Ui.hit(mouseX, mouseY, cx, cy, cw - 1, CELL - 1);
            if (hov) Draw.round(ctx, cx, cy, cw - 1, CELL - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            String g = shown.get(i).glyph();
            int ink = !shown.get(i).drawable() ? Theme.TEXT_FAINT
                    : hov ? Theme.TEXT : Theme.TEXT_DIM;
            ctx.drawText(tr, g, cx + (cw - 1 - tr.getWidth(g)) / 2, cy + 4,
                    Draw.opaque(ink), false);
            if (Symbols.favourite(g))
                Draw.rect(ctx, cx + 2, cy + CELL - 4, cw - 5, 1, Draw.opaque(accent()));
        }
        ctx.disableScissor();
        if (shown.isEmpty())
            Draw.textFit(ctx, tr, searching ? "ничего не нашлось"
                            : "ПКМ по символу добавит его сюда",
                    lx + 2, gy + 4, lw - 4, Theme.TEXT_FAINT, false);
        if (gridMax() > 0) {
            int trackH = gridRows * CELL;
            int thumb = Math.max(12, trackH * trackH / (trackH + gridMax()));
            int ty = gy + (trackH - thumb) * gridScroll / Math.max(1, gridMax());
            Draw.rect(ctx, lx + lw - 2, gy, 2, trackH, Draw.argb(0x30, 0x000000));
            Draw.round(ctx, lx + lw - 2, ty, 2, thumb, 1, Draw.opaque(0x5A6478));
        }
        int fy = absY(symHintY);
        boolean fhov = Ui.hit(mouseX, mouseY, lx, fy - 1, lw, 10);
        Draw.roundOutline(ctx, lx, fy - 1, 7, 7, 1,
                Draw.opaque(fhov ? Theme.TEXT_DIM : Theme.TEXT_FAINT));
        if (Symbols.onlyDrawable()) Draw.rect(ctx, lx + 2, fy + 1, 3, 3, Draw.opaque(accent()));
        Draw.textFit(ctx, tr, "только рисуемые · " + Symbols.drawable() + " из " + Symbols.total(),
                lx + 11, fy, lw - 11, fhov ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private int column(String[] labels, int lead, int trail) {
        int width = 0;
        for (String s : labels) width = Math.max(width, tr.getWidth(s));
        return lead + width + trail;
    }

    private void drawRight(DrawContext ctx, int mouseX, int mouseY) {
        int px = rightX();
        Ui.caption(ctx, tr, "ПРЕДПРОСМОТР", px, absY(prevY) - CAP, rw);
        int col = column(WHERE, 5, 8);
        for (int i = 0; i < previews(); i++) {
            int ry = absY(prevY) + i * PREV_ROW;
            Draw.rect(ctx, px, ry, rw, PREV_ROW - 1,
                    Draw.opaque(i % 2 == 0 ? Draw.mix(Ui.WELL, Ui.PANEL, 0.25f) : Ui.WELL));
            Draw.text(ctx, tr, WHERE[i], px + 5, ry + 5, Theme.TEXT_FAINT, false);
            int tx = px + col;
            int right = px + rw - (i == 1 ? 42 : 4);
            if (right > tx) {
                ctx.enableScissor(tx, ry, right, ry + PREV_ROW - 1);
                ctx.drawText(tr, preview, tx, ry + 5, Draw.opaque(Theme.TEXT), i != 1);
                ctx.disableScissor();
            }
            if (i != 1) continue;
            boolean over = plainW > SIGN_W;
            Draw.textRight(ctx, tr, plainW + "/" + SIGN_W, px + rw - 4, ry + 5,
                    over ? Theme.DANGER : Theme.OK, false);
        }

        Ui.caption(ctx, tr, "В ДРУГИХ РЕЖИМАХ · КЛИК КОПИРУЕТ", px, absY(expY) - CAP, rw);
        String[] modeNames = new String[MODES.size()];
        for (int i = 0; i < MODES.size(); i++) modeNames[i] = Values.parsingName(MODES.get(i));
        int mcol = column(modeNames, 5, 8);
        for (int i = 0; i < otherModes.size(); i++) {
            int ry = absY(expY) + i * OUT_ROW;
            boolean hov = Ui.hit(mouseX, mouseY, px, ry, rw, OUT_ROW - 1);
            if (hov) Draw.round(ctx, px, ry, rw, OUT_ROW - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            Draw.text(ctx, tr, Values.parsingName(otherModes.get(i)), px + 5, ry + 3,
                    hov ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, otherText.get(i), px + mcol, ry + 3,
                    rw - (hov ? 22 : 6) - mcol, hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (hov) Draw.glyph(ctx, Draw.COPY, px + rw - 14, ry + 2, Theme.TEXT_DIM);
        }
    }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY) {
        int fy = y + footY + (FOOT_H - ROW) / 2;
        Draw.textFit(ctx, tr, "Enter — сохранить · Esc — отменить · Ctrl+Z — вернуть",
                x + PAD, fy + 4, inner() - 126, Theme.TEXT_FAINT, false);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 58, fy, 58, ROW, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 118, fy, 56, ROW, "Отмена", Ui.GHOST);
    }

    private int pickRgb() { return McText.hsvRgb(pickH, pickS, pickV); }

    private void fromRgb(int rgb) {
        float[] hsv = McText.rgbHsv(rgb);
        if (hsv[1] > 0.001f) pickH = hsv[0];
        if (hsv[2] > 0.001f) pickS = hsv[1];
        pickV = hsv[2];
    }

    private void setHexFromPicker() {
        syncing = true;
        hex.setText("#" + String.format("%06x", pickRgb() & 0xFFFFFF));
        syncing = false;
    }

    private void remember(int rgb) {
        RECENT.remove(Integer.valueOf(rgb));
        RECENT.add(0, rgb);
        while (RECENT.size() > RECENT_MAX) RECENT.remove(RECENT.size() - 1);
    }

    private void toast(String s) {
        flash = s;
        flashAt = System.currentTimeMillis();
    }

    private void snapshot() {
        undo.push(new String[]{input.getText(), parsing});
        while (undo.size() > UNDO_MAX) undo.removeLast();
        redo.clear();
        lastTyped = System.currentTimeMillis();
    }

    private void typedSnapshot() {
        long now = System.currentTimeMillis();
        if (now - lastTyped > 700) {
            undo.push(new String[]{input.getText(), parsing});
            while (undo.size() > UNDO_MAX) undo.removeLast();
            redo.clear();
        }
        lastTyped = now;
    }

    private void step(Deque<String[]> from, Deque<String[]> to, String what) {
        if (from.isEmpty()) { toast("нечего " + what); return; }
        to.push(new String[]{input.getText(), parsing});
        String[] s = from.pop();
        parsing = s[1];
        focus(input);
        input.setText(s[0]);
        input.setCursorToEnd(false);
        lastTyped = 0;
    }

    private void wrap(String open, String close) {
        if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return; }
        snapshot();
        String sel = input.getSelectedText();
        focus(input);
        input.write(sel.isEmpty() ? open : open + sel + close);
    }

    private void stripFormatting() {
        snapshot();
        String sel = input.getSelectedText();
        focus(input);
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
        snapshot();
        focus(input);
        input.write(markup.apply(sel));
    }

    private void setMode(String mode) {
        if (mode.equals(parsing)) return;
        snapshot();
        String converted = McText.convert(input.getText(), parsing, mode);
        parsing = mode;
        focus(input);
        input.setText(converted);
        input.setCursorToEnd(false);
        toast("переведено в «" + Values.parsingName(mode) + "»");
    }

    private void insert(String glyph) {
        snapshot();
        focus(input);
        input.write(glyph);
    }

    private void copy(String s) {
        MinecraftClient.getInstance().keyboard.setClipboard(s);
        toast("скопировано");
    }

    private void openColourMenu(boolean first) {
        List<String> names = new ArrayList<>();
        for (McText.Colour c : McText.COLOURS) names.add(c.name());
        menu = Menu.options(screenW, screenH,
                leftX() + gradAt + (first ? 0 : 44),
                absY(toolY) + gradRow * (TOOL_H + 4) + TOOL_H + 2, tr,
                first ? "Начальный цвет" : "Конечный цвет", names, -1, i -> {
                    if (first) gradA = McText.COLOURS.get(i).rgb();
                    else gradB = McText.COLOURS.get(i).rgb();
                });
    }

    private int symbolAt(double mx, double my) {
        if (tab != 1) return -1;
        int lx = leftX(), gy = absY(gridY);
        if (!Ui.hit(mx, my, lx, gy, lw, gridRows * CELL)) return -1;
        for (int i = 0; i < cells.length && i < shown.size(); i++)
            if (Ui.hit(mx, my, lx + cells[i][0], gy + cells[i][1] - gridScroll,
                    cells[i][2] - 1, CELL - 1)) return i;
        return -1;
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        boolean right = click.button() == 1;
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (Ui.hit(mx, my, x + w - PAD - 14, headY(14), 14, 14)) { closed = true; return true; }
        if (headMode) {
            int mi = modeChips.indexAt(mx, my, headChipsX(), headY(TAB_H));
            if (mi >= 0) { setMode(MODES.get(mi)); return true; }
        }
        if (bar.grabbed(mx, my, 1, pane.max(), v -> { pane.scroll = v; placeFields(); }))
            return true;
        if (!pane.inBody(my, y)) return footClicked(mx, my);

        if (!headMode) {
            int mi = modeChips.indexAt(mx, my, leftX(), absY(modeY));
            if (mi >= 0) { setMode(MODES.get(mi)); return true; }
        }

        if (Ui.hit(mx, my, x + PAD, absY(inputY), inner(), INPUT_H)) {
            focus(input);
            if (!input.mouseClicked(click, doubled)) input.onClick(click, doubled);
            return true;
        }

        if (toolClicked(mx, my, right)) return true;

        int ti = tabChips.indexAt(mx, my, leftX(), absY(tabsY));
        if (ti >= 0) {
            tab = ti;
            focus(tab == 1 ? search : input);
            return true;
        }

        if (tab == 0 ? colourClicked(mx, my, click, doubled) : symbolsClicked(mx, my, click, doubled))
            return true;

        for (int i = 0; i < otherModes.size(); i++)
            if (Ui.hit(mx, my, rightX(), absY(expY) + i * OUT_ROW, rw, OUT_ROW - 1)) {
                copy(otherText.get(i));
                return true;
            }

        return footClicked(mx, my);
    }

    private boolean toolClicked(int mx, int my, boolean right) {
        int lx = leftX(), ty = absY(toolY);
        for (int i = 0; i < McText.DECOS.size(); i++)
            if (Ui.hit(mx, my, lx + decoAt + i * (DECO_W + 3), ty, DECO_W, TOOL_H)) {
                McText.Deco d = McText.DECOS.get(i);
                if (d.code() == 'r' && right) stripFormatting();
                else wrap(McText.decoTag(parsing, d), McText.decoClose(parsing, d));
                return true;
            }

        int gx = lx + gradAt, gy = ty + gradRow * (TOOL_H + 4);
        boolean grad = McText.supportsGradient(parsing);
        if (Ui.hit(mx, my, gx, gy, 18, TOOL_H)) {
            if (!grad) { toast("в этом режиме градиента нет"); return true; }
            if (right) openColourMenu(true);
            else { gradA = pickRgb(); toast("начальный цвет взят из пипетки"); }
            return true;
        }
        if (Ui.hit(mx, my, gx + 44, gy, 18, TOOL_H)) {
            if (!grad) { toast("в этом режиме градиента нет"); return true; }
            if (right) openColourMenu(false);
            else { gradB = pickRgb(); toast("конечный цвет взят из пипетки"); }
            return true;
        }
        if (Ui.hit(mx, my, gx + 70, gy, gradBtnW, TOOL_H)) {
            if (!grad) toast("в этом режиме градиента нет");
            else span(s -> McText.gradientMarkup(parsing, s, new int[]{gradA, gradB}));
            return true;
        }
        if (Ui.hit(mx, my, gx + 74 + gradBtnW, gy, rainBtnW, TOOL_H)) {
            if (!grad) toast("в этом режиме градиента нет");
            else span(s -> McText.rainbowMarkup(parsing, s));
            return true;
        }
        return false;
    }

    private boolean colourClicked(int mx, int my, Click click, boolean doubled) {
        int lx = leftX(), swW = swatchW();
        for (int i = 0; i < McText.COLOURS.size(); i++)
            if (Ui.hit(mx, my, lx + i * (swW + 1), absY(swY), swW, SW_H)) {
                McText.Colour c = McText.COLOURS.get(i);
                fromRgb(c.rgb());
                setHexFromPicker();
                wrap(McText.colourTag(parsing, c), McText.colourClose(parsing, c));
                return true;
            }

        int sy = absY(pickY);
        if (Ui.hit(mx, my, lx, sy, svW, svH())) { dragging = 1; dragPicker(mx, my); return true; }
        int slx = lx + svW + 8 + 11, slw = lw - svW - 8 - 11 - 36;
        int slTop = sy + (svH() - (3 * SL_H + 2 * SL_GAP)) / 2;
        for (int i = 0; i < 3; i++)
            if (Ui.hit(mx, my, slx, slTop + i * (SL_H + SL_GAP) - 2, slw, SL_H + 4)) {
                dragging = 2 + i;
                dragPicker(mx, my);
                return true;
            }

        if (Ui.hit(mx, my, lx, absY(hexY), HEX_W, ROW)) {
            focus(hex);
            if (!hex.mouseClicked(click, doubled)) hex.onClick(click, doubled);
            return true;
        }
        if (Ui.hit(mx, my, lx + HEX_W + 32, absY(hexY), lw - HEX_W - 32, ROW)) {
            applyPicked();
            return true;
        }
        if (recents())
            for (int i = 0; i < RECENT.size(); i++)
                if (Ui.hit(mx, my, lx + i * 14, absY(recentY), 13, 11)) {
                    fromRgb(RECENT.get(i));
                    setHexFromPicker();
                    return true;
                }
        return false;
    }

    private void applyPicked() {
        if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return; }
        remember(pickRgb());
        wrap(McText.hexTag(parsing, pickRgb()), McText.hexClose(parsing));
    }

    private boolean symbolsClicked(int mx, int my, Click click, boolean doubled) {
        int lx = leftX();
        if (Ui.hit(mx, my, lx, absY(searchY), lw, SEARCH_H)) {
            focus(search);
            if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
            return true;
        }
        int ci = catChips.indexAt(mx, my, lx, absY(catsY));
        if (ci >= 0) {
            cat = ci;
            gridScroll = 0;
            if (!search.getText().isEmpty()) {
                focus(search);
                search.setText("");
            }
            return true;
        }
        if (Ui.hit(mx, my, lx, absY(symHintY) - 1, lw, 10)) {
            Symbols.flipDrawable();
            gridScroll = 0;
            toast(Symbols.onlyDrawable() ? "показаны только те, что рисует шрифт игры"
                    : "показаны все — часть будет квадратиками без набора шрифтов");
            return true;
        }
        int at = symbolAt(mx, my);
        if (at < 0) return false;
        String glyph = shown.get(at).glyph();
        if (click.button() == 1) {
            Symbols.toggle(glyph);
            toast(Symbols.favourite(glyph) ? "в избранном" : "убрано из избранного");
            return true;
        }
        insert(glyph);
        return true;
    }

    private boolean footClicked(int mx, int my) {
        int fy = y + footY + (FOOT_H - ROW) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 58, fy, 58, ROW)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 118, fy, 56, ROW)) { closed = true; return true; }
        return true;
    }

    private void dragPicker(double mx, double my) {
        int lx = leftX(), sy = absY(pickY);
        int slx = lx + svW + 8 + 11, slw = lw - svW - 8 - 11 - 36;
        switch (dragging) {
            case 1 -> {
                pickS = clamp01((mx - lx) / (svW - 1.0));
                pickV = 1f - clamp01((my - sy) / (svH() - 1.0));
            }
            case 2 -> pickH = clamp01((mx - slx) / (slw - 1.0));
            case 3 -> pickS = clamp01((mx - slx) / (slw - 1.0));
            case 4 -> pickV = clamp01((mx - slx) / (slw - 1.0));
            default -> { return; }
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
        if (bar.dragged(click.y(), 1, pane.max(), v -> { pane.scroll = v; placeFields(); }))
            return true;
        if (dragging != 0) { dragPicker(click.x(), click.y()); return true; }
        if (hex.isFocused()) return hex.mouseDragged(click, dx, dy);
        if (search.isFocused()) return search.mouseDragged(click, dx, dy);
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
        if (tab == 1 && Ui.hit(mx, my, leftX(), absY(gridY), lw, gridRows * CELL)) {
            gridScroll -= (int) Math.round(amount * CELL);
            clampGrid();
            return true;
        }
        if (pane.max() > 0 && Ui.hit(mx, my, x, y, w, h)) {
            pane.wheel(amount);
            placeFields();
        }
        return true;
    }

    public boolean keyPressed(KeyInput in) {
        int key = in.key();
        boolean ctrl = (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (in.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (search.isFocused() && !search.getText().isEmpty()) {
                search.setText("");
                return true;
            }
            closed = true;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z && !shift) { step(undo, redo, "отменять"); return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_Y || (key == GLFW.GLFW_KEY_Z && shift))) {
            step(redo, undo, "возвращать");
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (hex.isFocused()) { applyPicked(); return true; }
            if (search.isFocused()) {
                if (!shown.isEmpty()) insert(shown.get(0).glyph());
                return true;
            }
            finish();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (input.isFocused()) focus(tab == 1 ? search : hex);
            else if (search.isFocused()) focus(hex);
            else focus(input);
            return true;
        }
        if (hex.isFocused()) return hex.keyPressed(in);
        if (search.isFocused()) return search.keyPressed(in);
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) typedSnapshot();
        return input.keyPressed(in);
    }

    public boolean charTyped(CharInput in) {
        if (menu != null) return true;
        if (hex.isFocused()) return hex.charTyped(in);
        if (search.isFocused()) return search.charTyped(in);
        typedSnapshot();
        return input.charTyped(in);
    }
}
