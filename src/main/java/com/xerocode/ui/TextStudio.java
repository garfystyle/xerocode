package com.xerocode.ui;

import com.xerocode.Settings;
import com.xerocode.Symbols;
import com.xerocode.Value;
import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class TextStudio implements Overlay {
    private static final int PAD = 12, HEAD_H = 26, FOOT_H = 28, GUTTER = 16;
    private static final int CAP = 12, SEG_H = 18, TOOL_H = 20, TOOL_W = 22, GROUP_GAP = 17;
    private static final int SW_GAP = 3, SL_H = 8, CELL = 18, OUT_ROW = 16, SIGN_W = 90;
    private static final int HEX_W = 66, SEARCH_H = 18, CAT_H = 15, RECENT_W = 16;
    private static final int RECENT_MAX = 14, UNDO_MAX = 64, SPR = 20, CHAT_W = 320, CHAT_LINE = 9;
    private static final int BOARD_W = 96, BOARD_H = 48, SIGN_LINE = 10, SIGN_SHADE = 0xFFCCCCCC;
    private static final int NAME_H = 17, SWATCH = 16, GRAD_BAR = 24;

    private static final int T_DECO = 0, T_RESET = 1, T_COLOUR = 2, T_GRAD_A = 3, T_GRAD_B = 4,
            T_GRAD = 5, T_RAIN = 6, T_UNDO = 7, T_REDO = 8;

    private static final String SEP = "\0";
    private static final String TITLE = "Редактор текста";
    private static final Identifier SIGN_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/oak_sign.png");
    private static final String COUNT_ROOM = "00000 символов";
    private static final String COLOUR_LABEL = "Цвет", GRAD_LABEL = "Градиент", RAIN_LABEL = "Радуга";
    private static final List<String> MODES = List.of(
            McText.PLAIN, McText.LEGACY, McText.MINI, McText.JSON);
    private static final List<String> TABS = List.of("Цвет", "Символы");
    private static final List<String> TABS_SPRITES = List.of("Цвет", "Символы", "Спрайты");
    private static final List<Integer> RECENT = new ArrayList<>();
    private static final Map<String, String> RU = Map.ofEntries(
            Map.entry("black", "чёрный"), Map.entry("dark_blue", "тёмно-синий"),
            Map.entry("dark_green", "тёмно-зелёный"), Map.entry("dark_aqua", "тёмно-бирюзовый"),
            Map.entry("dark_red", "тёмно-красный"), Map.entry("dark_purple", "фиолетовый"),
            Map.entry("gold", "золотой"), Map.entry("gray", "серый"),
            Map.entry("dark_gray", "тёмно-серый"), Map.entry("blue", "синий"),
            Map.entry("green", "зелёный"), Map.entry("aqua", "бирюзовый"),
            Map.entry("red", "красный"), Map.entry("light_purple", "розовый"),
            Map.entry("yellow", "жёлтый"), Map.entry("white", "белый"));
    private static final String[] NOISE = {
            "# # # ",
            " # # #",
            "# # # ",
            " # # #",
            "# # # ",
            " # # #",
            "# # # "};
    private static final String[] HOTKEYS = {"Ctrl+B", "Ctrl+I", "Ctrl+U", "", ""};

    public interface Done { void apply(String text, String parsing); }

    private final Font tr;
    private final Done done;
    private int screenW, screenH;

    private String parsing;
    private final WrapField.Box input;
    private final WrapField area;
    private final EditBox hex, search;
    private Ui.Chips catChips, sprChips;

    private final Hsv hsv = new Hsv(0f, 0.85f, 1f);
    private int gradA = 0xFF5555, gradB = 0x5555FF;
    private int tab, cat, gridScroll, dragging;
    private Menu menu;
    private final Complete complete = new Complete();
    private String flash = "";
    private long flashAt, lastTyped;

    private final Deque<String[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    private int x, y, w, h, lw, rw;
    private final Ui.Pane pane = new Ui.Pane();
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();
    private boolean one, compact, closed, headMode;
    private int srcLines, modeW, toolRows;
    private final int[] toolX = new int[9], toolRow = new int[9], toolW = new int[9];
    private int modeY, toolY, srcY, srcH, hintY, bodyY, colTop, bodyH, tabsY;
    private int palY, palCols, swH, pickY, svW, svH, slPitch, recentY;
    private int searchY, catsY, gridY, gridRows, symHintY;
    private int sprGroup, sprScroll, sprY, sprRows, sprCols;
    private int chatY, chatRows, signY, nameY, expY, footY;
    private int[][] cells = new int[0][];
    private int gridTotal;

    private String prevKey = SEP, shownKey = SEP;
    private Component preview = Component.empty();
    private List<FormattedCharSequence> chat = List.of();
    private FormattedCharSequence sign = FormattedCharSequence.EMPTY;
    private int signW, nameW;
    private List<String> otherModes = List.of();
    private final List<String> otherText = new ArrayList<>();
    private List<Symbols.Sym> shown = List.of();
    private List<String> sprites = List.of();
    private String spritesKey = SEP;

    public TextStudio(Font tr, int screenW, int screenH,
                      String text, String parsing, Done done) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.parsing = parsing;
        this.done = done;

        input = new WrapField.Box(tr, text, "текст сообщения");
        area = new WrapField(tr, input, () -> this.parsing);
        hex = Ui.field(tr, "", "#RRGGBB", 7);
        search = Ui.field(tr, "", "поиск символа", 32);
        hsv.bind(hex, s -> {
            Integer rgb = Hsv.parse(s);
            if (rgb != null) hsv.keep(rgb);
        });
        setHexFromPicker();
        focus(input);

        layout();
    }

    public boolean isClosed() { return closed; }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        screenW = sw;
        screenH = sh;
        menu = null;
        complete.reset();
        layout();
    }

    private int inner() { return w - PAD * 2; }

    private boolean recents() { return !compact; }

    private void layout() {
        w = Ui.fitW(screenW, 780);
        one = inner() < 460;
        lw = one ? inner() : (inner() - GUTTER) / 2;
        rw = one ? inner() : inner() - GUTTER - lw;

        catChips = new Ui.Chips(tr, Symbols.categories(), lw, CAT_H, 3);
        sprChips = new Ui.Chips(tr, SpriteText.GROUPS, lw, CAT_H, 3);

        int segW = 0;
        for (String m : MODES) segW = Math.max(segW, tr.width(Values.parsingName(m)) + 18);
        modeW = segW * MODES.size() + 2;
        int headRoom = inner() - tr.width(TITLE) - 9 - 16 - tr.width(COUNT_ROOM) - 16 - 22;
        headMode = modeW <= headRoom;
        if (!headMode) modeW = Math.min(modeW, inner());
        placeTools();

        int fullH = Ui.fitH(screenH, 10000);
        compact = false;
        srcLines = 4;
        int natural = measure();
        if (natural > fullH) {
            compact = true;
            srcLines = 2;
            natural = measure();
        } else if (natural + WrapField.LINE <= fullH) {
            srcLines++;
            natural = measure();
        }
        h = Ui.fitH(screenH, natural);
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
        footY = h - FOOT_H;
        pane.fit(HEAD_H + 1, footY, natural - FOOT_H - 1);
        clampGrid();
        placeFields();
    }

    private void placeTools() {
        int decoW = TOOL_W * McText.DECOS.size() - TOOL_W;
        int colourW = SWATCH - 4 + 5 + tr.width(COLOUR_LABEL) + 14;
        int gradW = Ui.buttonW(tr, GRAD_LABEL), rainW = Ui.buttonW(tr, RAIN_LABEL);
        int[][] groups = {
                {T_DECO, 0, decoW, T_RESET, decoW + 4, TOOL_W},
                {T_COLOUR, 0, colourW},
                {T_GRAD_A, 0, SWATCH, T_GRAD_B, SWATCH + GRAD_BAR + 4, SWATCH,
                        T_GRAD, SWATCH * 2 + GRAD_BAR + 8, gradW,
                        T_RAIN, SWATCH * 2 + GRAD_BAR + 12 + gradW, rainW}};
        int limit = inner() - TOOL_H * 2 - 4 - 12;
        int row = 0, at = 0;
        for (int[] g : groups) {
            int gw = g[g.length - 2] + g[g.length - 1];
            int gap = at == 0 ? 0 : GROUP_GAP;
            if (at > 0 && at + gap + gw > (row == 0 ? limit : inner())) {
                row++;
                at = 0;
                gap = 0;
            }
            for (int k = 0; k < g.length; k += 3) {
                toolX[g[k]] = at + gap + g[k + 1];
                toolW[g[k]] = g[k + 2];
                toolRow[g[k]] = row;
            }
            at += gap + gw;
        }
        toolRows = row + 1;
        toolX[T_UNDO] = inner() - TOOL_H * 2 - 4;
        toolX[T_REDO] = inner() - TOOL_H;
        toolW[T_UNDO] = toolW[T_REDO] = TOOL_H;
        toolRow[T_UNDO] = toolRow[T_REDO] = 0;
    }

    private int measure() {
        int at = HEAD_H + 1 + 9;
        modeY = -1;
        if (!headMode) {
            modeY = at;
            at += SEG_H + 8;
        }
        toolY = at;
        at += toolRows * TOOL_H + (toolRows - 1) * 4 + 7;
        srcY = at;
        srcH = WrapField.height(srcLines);
        at += srcH;
        hintY = at + 5;
        at += 5 + Ui.TEXT_H + 7;
        bodyY = at;
        colTop = bodyY + 10;

        tabsY = colTop;
        int c = tabsY + SEG_H + 10;
        palCols = compact ? 16 : 8;
        swH = compact ? 12 : 14;
        palY = c + CAP;
        c = palY + (16 / palCols) * (swH + SW_GAP) - SW_GAP + 10;
        svW = Math.min(124, lw * 36 / 100);
        svH = compact ? 48 : 62;
        slPitch = compact ? 10 : 13;
        pickY = c + CAP;
        c = pickY + svH + 10;
        recentY = c + CAP;
        if (recents()) c = recentY + 12 + 2;
        int colourH = c - colTop;

        searchY = tabsY + SEG_H + 10;
        catsY = searchY + SEARCH_H + 6;
        gridY = catsY + catChips.height() + 6;
        sprY = catsY + sprChips.height() + 6;
        sprCols = Math.max(1, lw / SPR);

        chatRows = compact ? 2 : 3;
        int rtop = colTop;
        int leftH = Math.max(colourH, gridY - colTop + CELL * 3 + 16);
        if (one) rtop = colTop + leftH + 12;
        int gap = compact ? 6 : 10;
        chatY = rtop + CAP;
        int r = chatY + chatRows * CHAT_LINE + 8 + gap;
        signY = r;
        r = signY + BOARD_H + gap;
        nameY = r + CAP;
        if (!compact) r = nameY + NAME_H + gap;
        expY = r + CAP;
        r = expY + 3 * OUT_ROW;
        int rightH = r - rtop;

        int span = one ? leftH : Math.max(leftH, rightH);
        gridRows = Math.max(3, (colTop + span - 16 - gridY) / CELL);
        symHintY = gridY + gridRows * CELL + 7;
        sprRows = Math.max(2, (gridY + gridRows * CELL - sprY) / SPR);

        int end = one ? r : colTop + span;
        bodyH = end - bodyY;
        return end + 10 + 1 + FOOT_H;
    }

    private void placeFields() {
        area.place(x + PAD, absY(srcY), inner(), srcH);
        hex.setX(hexX() + 6);
        hex.setY(absY(hexY()) + (TOOL_H - 4 - 12) / 2 + 2);
        Ui.width(hex, HEX_W - 12);
        search.setX(leftX() + 7);
        search.setY(absY(searchY) + (SEARCH_H - 12) / 2 + 2);
        Ui.width(search, lw - 24);
    }

    private int headY(int hgt) { return y + 1 + (HEAD_H - 1 - hgt) / 2; }

    private int modeX() {
        int left = x + PAD + 9 + tr.width(TITLE) + 16;
        int right = x + w - PAD - 22 - tr.width(COUNT_ROOM) - 16 - modeW;
        return Math.max(left, Math.min(x + (w - modeW) / 2, right));
    }

    private int modeTop() { return headMode ? headY(SEG_H) : absY(modeY); }

    private int modeLeft() { return headMode ? modeX() : x + (w - modeW) / 2; }

    private int absY(int rel) { return y + pane.at(rel); }
    private int leftX() { return x + PAD; }
    private int rightX() { return one ? x + PAD : x + PAD + lw + GUTTER; }
    private int accent() { return Values.color(Value.TEXT); }

    private int toolLeft(int t) { return leftX() + toolX[t]; }
    private int toolTop(int t) { return absY(toolY) + toolRow[t] * (TOOL_H + 4); }
    private boolean overTool(double mx, double my, int t) {
        return Ui.hit(mx, my, toolLeft(t), toolTop(t), toolW[t], TOOL_H);
    }

    private int decoAt(double mx, double my) {
        int n = McText.DECOS.size() - 1;
        for (int i = 0; i < n; i++)
            if (Ui.hit(mx, my, toolLeft(T_DECO) + i * TOOL_W, toolTop(T_DECO), TOOL_W, TOOL_H))
                return i;
        return -1;
    }

    private void focus(EditBox f) {
        input.setFocused(f == input);
        hex.setFocused(f == hex);
        search.setFocused(f == search);
    }

    private int chatWrap() { return Math.min(CHAT_W, rw - 8); }

    private void syncPreview() {
        String key = parsing + SEP + input.getValue() + SEP + chatWrap();
        if (key.equals(prevKey)) return;
        prevKey = key;
        String raw = input.getValue();
        List<McText.Run> runs = McText.runs(raw, parsing);
        preview = McText.preview(raw, parsing);
        Component onBoard = firstLine(runs);
        signW = tr.width(onBoard);
        nameW = tr.width(preview);
        chat = chatLines(runs, chatWrap());
        List<FormattedCharSequence> onSign = tr.split(onBoard, SIGN_W);
        sign = onSign.isEmpty() ? FormattedCharSequence.EMPTY : onSign.get(0);
        List<String> rest = new ArrayList<>(MODES);
        rest.remove(parsing);
        otherModes = rest;
        otherText.clear();
        for (String m : rest) otherText.add(McText.convert(raw, parsing, m));
    }

    private List<FormattedCharSequence> chatLines(List<McText.Run> runs, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        MutableComponent line = Component.empty();
        for (McText.Run r : runs) {
            String t = r.text();
            int from = 0;
            for (int at = t.indexOf(TextLines.BREAK); at >= 0; at = t.indexOf(TextLines.BREAK, from)) {
                if (at > from) line.append(Component.literal(t.substring(from, at)).setStyle(r.style()));
                wrapInto(out, line, width);
                line = Component.empty();
                from = at + TextLines.BREAK.length();
            }
            if (from < t.length()) line.append(Component.literal(t.substring(from)).setStyle(r.style()));
        }
        wrapInto(out, line, width);
        return out;
    }

    private static Component firstLine(List<McText.Run> runs) {
        MutableComponent line = Component.empty();
        for (McText.Run r : runs) {
            int cut = r.text().indexOf(TextLines.BREAK);
            String part = cut < 0 ? r.text() : r.text().substring(0, cut);
            if (!part.isEmpty()) line.append(Component.literal(part).setStyle(r.style()));
            if (cut >= 0) break;
        }
        return line;
    }

    private void wrapInto(List<FormattedCharSequence> out, Component line, int width) {
        List<FormattedCharSequence> split = tr.split(line, Math.max(20, width));
        if (split.isEmpty()) out.add(FormattedCharSequence.EMPTY);
        else out.addAll(split);
    }

    private void syncSymbols() {
        String q = search.getValue().trim();
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
        int cx = 0, cy = 0, room = lw - 9;
        for (int i = 0; i < shown.size(); i++) {
            int cw = Math.min(room, Math.max(CELL, tr.width(shown.get(i).glyph()) + 8));
            if (cx > 0 && cx + cw > room) {
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

    private boolean spritesOn() { return McText.MINI.equals(parsing) && SpriteText.available(); }

    private List<String> tabNames() { return spritesOn() ? TABS_SPRITES : TABS; }

    private void syncSprites() {
        if (tab != 2) return;
        String q = search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        String key = sprGroup + SEP + q;
        if (key.equals(spritesKey)) return;
        spritesKey = key;
        List<String> all = SpriteText.ids(sprGroup);
        if (q.isEmpty()) sprites = all;
        else {
            List<String> found = new ArrayList<>();
            for (String id : all) if (id.contains(q)) found.add(id);
            sprites = found;
        }
        clampSprites();
    }

    private int sprMax() {
        return Math.max(0, (sprites.size() + sprCols - 1) / sprCols - sprRows) * SPR;
    }

    private void clampSprites() { sprScroll = Math.max(0, Math.min(sprMax(), sprScroll)); }

    private void showTab(int t) {
        tab = t;
        if (t == 1 && cat == 0 && Symbols.favourites().isEmpty() && Symbols.categories().size() > 1)
            cat = 1;
        search.setValue("");
        search.setMessage(Component.literal(tab == 2 ? "поиск текстуры" : "поиск символа"));
        gridScroll = 0;
        sprScroll = 0;
        focus(tab >= 1 ? search : input);
    }

    private void modeChanged() {
        if (tab >= tabNames().size()) showTab(0);
    }

    private static String ru(McText.Colour c) { return RU.getOrDefault(c.name(), c.name()); }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        syncPreview();
        syncSymbols();
        syncSprites();
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent());

        Draw.round(ctx, x + PAD, headY(10), 3, 10, 1, Draw.opaque(accent()));
        Draw.text(ctx, tr, TITLE, x + PAD + 9, headY(Ui.TEXT_H), Theme.TEXT, false);
        String len = Ui.plural(input.getValue().length(), "символ", "символа", "символов");
        Draw.textRight(ctx, tr, len, x + w - PAD - 22, headY(Ui.TEXT_H), Theme.TEXT_FAINT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, headY(14), 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
        if (headMode) drawModes(ctx, mouseX, mouseY);

        ctx.enableScissor(x + 1, y + pane.top(), x + w - 1, y + footY);
        if (!headMode) drawModes(ctx, mouseX, mouseY);
        drawTools(ctx, mouseX, mouseY);
        area.render(ctx, mouseX, mouseY, delta);
        drawHint(ctx, mouseX, mouseY);
        Ui.hairline(ctx, x + 1, absY(bodyY), w - 2);

        Ui.segmented(ctx, tr, mouseX, mouseY, leftX(), absY(tabsY), lw, SEG_H, tabNames(), tab,
                accent());
        if (tab == 0) drawColour(ctx, mouseX, mouseY, delta);
        else if (tab == 1) drawSymbols(ctx, mouseX, mouseY, delta);
        else drawSprites(ctx, mouseX, mouseY, delta);
        drawPreviews(ctx, mouseX, mouseY);
        if (!one) Ui.vline(ctx, x + PAD + lw + GUTTER / 2, absY(colTop), bodyH - 10);
        ctx.disableScissor();
        pane.drawBar(ctx, bar, x + w - 4, y, mouseX, mouseY);

        Ui.hairline(ctx, x + 1, y + footY - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY);

        if (menu != null) {
            ctx.nextStratum();
            menu.render(ctx, tr, mouseX, mouseY);
        }
        if (complete.active()) {
            ctx.nextStratum();
            complete.render(ctx, tr, mouseX, mouseY);
        }
    }

    private void drawModes(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<String> names = new ArrayList<>();
        for (String m : MODES) names.add(Values.parsingName(m));
        Ui.segmented(ctx, tr, mouseX, mouseY, modeLeft(), modeTop(), modeW, SEG_H, names,
                MODES.indexOf(parsing), accent());
    }

    private void group(GuiGraphicsExtractor ctx, int gx, int gy, int n, int hot, boolean on) {
        int r = Settings.radius(TOOL_H);
        int gw = n * TOOL_W;
        if (Settings.outlined()) {
            Draw.round(ctx, gx, gy, gw, TOOL_H, r, Draw.argb(0x28, Ui.BTN));
            Draw.roundOutline(ctx, gx, gy, gw, TOOL_H, r, Draw.opaque(Draw.shade(Ui.BTN, 0.42f)));
        } else {
            Draw.round(ctx, gx, gy, gw, TOOL_H, r, Draw.opaque(Ui.BTN));
        }
        for (int i = 1; i < n; i++)
            if (i != hot && i != hot + 1)
                Draw.rect(ctx, gx + i * TOOL_W, gy + 5, 1, TOOL_H - 10, Draw.opaque(Ui.LINE_IN));
        if (on && hot >= 0)
            Draw.round(ctx, gx + hot * TOOL_W, gy, TOOL_W, TOOL_H, r, Draw.opaque(Ui.BTN_HOVER));
    }

    private void drawTools(GuiGraphicsExtractor ctx, int mx, int my) {
        boolean on = McText.formattable(parsing);
        boolean grad = McText.supportsGradient(parsing);
        int dx = toolLeft(T_DECO), dy = toolTop(T_DECO);
        int hot = decoAt(mx, my);
        group(ctx, dx, dy, McText.DECOS.size() - 1, hot, on);
        for (int i = 0; i < McText.DECOS.size() - 1; i++) {
            McText.Deco d = McText.DECOS.get(i);
            int cx = dx + i * TOOL_W;
            int ink = on ? (i == hot ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT;
            if (d.code() == 'm') {
                int gx = cx + (TOOL_W - Draw.glyphW(Draw.STRIKE_TEXT)) / 2;
                int gy = dy + (TOOL_H - Draw.glyphH(Draw.STRIKE_TEXT)) / 2;
                Draw.glyph(ctx, Draw.STRIKE_TEXT, gx, gy, Draw.mix(ink, Ui.BTN, 0.45f));
                Draw.glyph(ctx, Draw.STRIKE_LINE, gx, gy, ink);
                continue;
            }
            if (d.code() == 'k' && i != hot) {
                Draw.glyph(ctx, NOISE, cx + (TOOL_W - Draw.glyphW(NOISE)) / 2,
                        dy + (TOOL_H - Draw.glyphH(NOISE)) / 2, ink);
                continue;
            }
            Component label = Component.literal(d.label()).withStyle(s -> switch (d.code()) {
                case 'l' -> s.withBold(true);
                case 'o' -> s.withItalic(true);
                case 'n' -> s.withUnderlined(true);
                case 'k' -> s.withObfuscated(true);
                default -> s;
            });
            ctx.text(tr, label, cx + (TOOL_W - tr.width(label)) / 2,
                    dy + (TOOL_H - Ui.TEXT_H) / 2, Draw.opaque(ink), false);
        }

        int rx = toolLeft(T_RESET), ry = toolTop(T_RESET);
        boolean rh = Ui.button(ctx, tr, mx, my, rx, ry, TOOL_W, TOOL_H, "", Ui.GHOST, on);
        Draw.glyph(ctx, Draw.RESET, rx + (TOOL_W - Draw.glyphW(Draw.RESET)) / 2,
                ry + (TOOL_H - Draw.glyphH(Draw.RESET)) / 2,
                !on ? Theme.TEXT_FAINT : rh ? Theme.TEXT : Theme.TEXT_DIM);

        separator(ctx, T_COLOUR);
        int cx = toolLeft(T_COLOUR), cy = toolTop(T_COLOUR);
        boolean ch = Ui.button(ctx, tr, mx, my, cx, cy, toolW[T_COLOUR], TOOL_H, "", Ui.GHOST, on);
        Ui.swatch(ctx, cx + 6, cy + 4, SWATCH - 4, TOOL_H - 8, hsv.rgb(), on, false);
        Draw.text(ctx, tr, COLOUR_LABEL, cx + SWATCH + 7, cy + (TOOL_H - Ui.TEXT_H) / 2,
                !on ? Theme.TEXT_FAINT : ch ? Theme.TEXT : Theme.TEXT_DIM, false);

        separator(ctx, T_GRAD_A);
        int ax = toolLeft(T_GRAD_A), ay = toolTop(T_GRAD_A);
        int bx = toolLeft(T_GRAD_B);
        Ui.swatch(ctx, ax, ay, SWATCH, TOOL_H, gradA, grad, grad && overTool(mx, my, T_GRAD_A));
        Draw.hgrad(ctx, ax + SWATCH + 2, ay + 7, GRAD_BAR, TOOL_H - 14,
                Draw.opaque(grad ? gradA : Draw.shade(gradA, -0.5f)),
                Draw.opaque(grad ? gradB : Draw.shade(gradB, -0.5f)));
        Ui.swatch(ctx, bx, ay, SWATCH, TOOL_H, gradB, grad, grad && overTool(mx, my, T_GRAD_B));
        colourButton(ctx, mx, my, T_GRAD, GRAD_LABEL, grad, false);
        colourButton(ctx, mx, my, T_RAIN, RAIN_LABEL, grad, true);

        int ux = toolLeft(T_UNDO), uy = toolTop(T_UNDO);
        Ui.iconButton(ctx, mx, my, ux, uy, TOOL_H, Draw.UNDO, Ui.GHOST, !undo.isEmpty());
        Ui.iconButton(ctx, mx, my, toolLeft(T_REDO), uy, TOOL_H, Draw.REDO, Ui.GHOST,
                !redo.isEmpty());
    }

    private void separator(GuiGraphicsExtractor ctx, int t) {
        if (toolX[t] == 0) return;
        Ui.vline(ctx, toolLeft(t) - (GROUP_GAP + 1) / 2, toolTop(t) + 3, TOOL_H - 6);
    }

    private void colourButton(GuiGraphicsExtractor ctx, int mx, int my, int t, String label,
                              boolean on, boolean rainbow) {
        int bx = toolLeft(t), by = toolTop(t), bw = toolW[t];
        boolean hov = Ui.button(ctx, tr, mx, my, bx, by, bw, TOOL_H, "", Ui.GHOST, on);
        MutableComponent text = Component.empty();
        int n = label.length();
        double phase = rainbow && hov ? (System.currentTimeMillis() % 4000) / 4000.0 : 0;
        for (int i = 0; i < n; i++) {
            double k = n == 1 ? 0 : i / (double) (n - 1);
            int rgb = rainbow ? McText.rainbowAt(i / (double) n, phase)
                    : McText.gradientAt(new int[]{gradA, gradB}, k);
            rgb = on ? legible(rgb) : Theme.TEXT_FAINT;
            text.append(Component.literal(String.valueOf(label.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        }
        ctx.text(tr, text, bx + (bw - tr.width(label)) / 2, by + (TOOL_H - Ui.TEXT_H) / 2,
                0xFFFFFFFF, false);
    }

    private static int legible(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int luma = (r * 299 + g * 587 + b * 114) / 1000;
        return luma >= 110 ? rgb : Draw.mix(rgb, 0xFFFFFF, (110 - luma) / 160f);
    }

    private void drawHint(GuiGraphicsExtractor ctx, int mx, int my) {
        boolean fresh = !flash.isEmpty() && System.currentTimeMillis() - flashAt < 1800;
        String note = fresh ? flash : hovered(mx, my);
        if (note.isEmpty()) note = idleHint();
        Draw.textFit(ctx, tr, note, x + PAD + 2, absY(hintY), inner() - 4,
                fresh ? Theme.OK : Theme.TEXT_FAINT, false);
    }

    private String idleHint() {
        if (!McText.formattable(parsing))
            return "в режиме «" + Values.parsingName(parsing) + "» разметки нет — "
                    + "цвет и стиль доступны в «Цветном» и «Стилизуемом»";
        int sel = input.getHighlighted().length();
        if (sel > 0)
            return "выделено " + Ui.plural(sel, "символ", "символа", "символов")
                    + " — стиль, цвет и градиент применятся к ним";
        return "выделите текст — цвет и стиль обернут его";
    }

    private String hovered(int mx, int my) {
        int d = decoAt(mx, my);
        if (d >= 0) {
            McText.Deco deco = McText.DECOS.get(d);
            return deco.title() + (HOTKEYS[d].isEmpty() ? "" : " · " + HOTKEYS[d]);
        }
        if (overTool(mx, my, T_RESET))
            return "сбросить формат · " + Ui.rmb() + " — снять разметку";
        if (overTool(mx, my, T_COLOUR))
            return "покрасить выделение цветом пипетки · " + Ui.rmb() + " — цвета Minecraft";
        if (overTool(mx, my, T_GRAD_A))
            return "начальный цвет — " + Ui.click() + " берёт из пипетки · " + Ui.rmb() + " — список";
        if (overTool(mx, my, T_GRAD_B))
            return "конечный цвет — " + Ui.click() + " берёт из пипетки · " + Ui.rmb() + " — список";
        if (overTool(mx, my, T_GRAD)) return "плавный переход цвета по выделению";
        if (overTool(mx, my, T_RAIN)) return "все цвета радуги по выделению";
        if (overTool(mx, my, T_UNDO)) return "отменить · Ctrl+Z";
        if (overTool(mx, my, T_REDO)) return "вернуть · Ctrl+Y";
        if (!pane.inBody(my, y)) return "";
        if (tab == 0) {
            int sw = swatchAt(mx, my);
            if (sw >= 0) {
                McText.Colour c = McText.COLOURS.get(sw);
                return ru(c) + " · " + McText.colourTag(parsing, c);
            }
            int rc = recentAt(mx, my);
            if (rc >= 0) return "#" + String.format("%06x", RECENT.get(rc) & 0xFFFFFF)
                    + " · " + Ui.click() + " — в пипетку";
        } else if (tab == 1) {
            int at = symbolAt(mx, my);
            if (at >= 0) {
                Symbols.Sym s = shown.get(at);
                return s.glyph() + "  " + s.name() + " · " + Symbols.code(s.glyph())
                        + (s.drawable() ? "" : " · шрифт игры его не рисует");
            }
        } else {
            int at = spriteAt(mx, my);
            if (at >= 0) return SpriteText.tag(sprGroup, sprites.get(at))
                    + " · " + Ui.rmb() + " — скопировать";
        }
        return "";
    }

    private int swatchW() { return (lw - (palCols - 1) * SW_GAP) / palCols; }
    private int swatchX(int i) { return leftX() + (i % palCols) * (swatchW() + SW_GAP); }
    private int swatchY(int i) { return absY(palY) + (i / palCols) * (swH + SW_GAP); }

    private int swatchAt(double mx, double my) {
        for (int i = 0; i < McText.COLOURS.size(); i++)
            if (Ui.hit(mx, my, swatchX(i), swatchY(i), swatchW(), swH)) return i;
        return -1;
    }

    private int recentAt(double mx, double my) {
        if (!recents()) return -1;
        for (int i = 0; i < RECENT.size(); i++)
            if (Ui.hit(mx, my, leftX() + i * (RECENT_W + SW_GAP), absY(recentY), RECENT_W, 12))
                return i;
        return -1;
    }

    private int sliderX() { return leftX() + svW + 22; }
    private int sliderW() { return lw - svW - 22 - 34; }
    private int sliderY(int i) { return absY(pickY) + 1 + i * slPitch; }
    private int hexX() { return leftX() + svW + 12; }
    private int hexY() { return pickY + svH - (TOOL_H - 4); }

    private void drawColour(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        boolean on = McText.formattable(parsing);

        Ui.caption(ctx, tr, on ? "ЦВЕТА MINECRAFT" : "ЦВЕТА MINECRAFT · в этом режиме недоступны",
                lx, absY(palY) - CAP, lw);
        int hotSw = on ? swatchAt(mouseX, mouseY) : -1;
        for (int i = 0; i < McText.COLOURS.size(); i++)
            Ui.swatch(ctx, swatchX(i), swatchY(i), swatchW(), swH, McText.COLOURS.get(i).rgb(), on,
                    i == hotSw);

        Ui.caption(ctx, tr, "СВОЙ ЦВЕТ", lx, absY(pickY) - CAP, lw);
        int sy = absY(pickY);
        Ui.svSquare(ctx, lx, sy, svW, svH, hsv.h, hsv.s, hsv.v, 3);
        String[] names = {"H", "S", "V"};
        String[] shows = {Math.round(hsv.h * 360) + "°",
                Math.round(hsv.s * 100) + "%", Math.round(hsv.v * 100) + "%"};
        for (int i = 0; i < 3; i++) {
            int ry = sliderY(i);
            Draw.text(ctx, tr, names[i], hexX(), ry, Theme.TEXT_FAINT, false);
            Ui.hsvSlider(ctx, sliderX(), ry, sliderW(), SL_H, i, hsv.h, hsv.s, hsv.v,
                    dragging == 2 + i || Ui.hit(mouseX, mouseY, sliderX(), ry - 2, sliderW(), SL_H + 4));
            Draw.textRight(ctx, tr, shows[i], lx + lw, ry, Theme.TEXT_DIM, false);
        }

        int hy = absY(hexY()), rowH = TOOL_H - 4;
        Ui.input(ctx, hexX(), hy, HEX_W, rowH, hex.isFocused());
        hex.extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, hex);
        Ui.swatch(ctx, hexX() + HEX_W + 4, hy, 22, rowH, hsv.rgb(), true, false);
        int ax = applyX();
        Ui.button(ctx, tr, mouseX, mouseY, ax, hy, lx + lw - ax, rowH, "Применить", Ui.ACCENT, on);

        if (!recents()) return;
        Ui.caption(ctx, tr, "НЕДАВНИЕ", lx, absY(recentY) - CAP, lw);
        if (RECENT.isEmpty()) {
            Draw.textFit(ctx, tr, "здесь появятся применённые цвета", lx, absY(recentY) + 2, lw,
                    Theme.TEXT_FAINT, false);
            return;
        }
        int hotRc = recentAt(mouseX, mouseY);
        for (int i = 0; i < RECENT.size(); i++)
            Ui.swatch(ctx, lx + i * (RECENT_W + SW_GAP), absY(recentY), RECENT_W, 12, RECENT.get(i),
                    true, i == hotRc);
    }

    private int applyX() { return hexX() + HEX_W + 30; }

    private void drawSearch(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        Ui.input(ctx, lx, absY(searchY), lw, SEARCH_H, search.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, lx + lw - 14, absY(searchY) + 5,
                search.isFocused() ? Theme.TEXT_DIM : Theme.TEXT_FAINT);
        search.extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, search);
    }

    private void drawScroll(GuiGraphicsExtractor ctx, int lx, int gy, int trackH, int max, int at) {
        if (max <= 0) return;
        int thumb = Math.max(12, trackH * trackH / (trackH + max));
        int ty = gy + (trackH - thumb) * at / Math.max(1, max);
        Draw.rect(ctx, lx + lw - 2, gy, 2, trackH, Draw.argb(0x30, 0x000000));
        Draw.round(ctx, lx + lw - 2, ty, 2, thumb, 1, Draw.opaque(0x5A6478));
    }

    private void drawSprites(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        drawSearch(ctx, mouseX, mouseY, delta);
        sprChips.render(ctx, tr, mouseX, mouseY, lx, absY(catsY), sprGroup, accent());

        int gy = absY(sprY), gh = sprRows * SPR;
        Ui.well(ctx, lx, gy - 3, lw, gh + 6);
        int hot = spriteAt(mouseX, mouseY);
        ctx.enableScissor(lx + 1, gy, lx + lw - 1, gy + gh);
        for (int i = sprScroll / SPR * sprCols; i < sprites.size(); i++) {
            int cx = sprLeft() + (i % sprCols) * SPR;
            int cy = gy + (i / sprCols) * SPR - sprScroll;
            if (cy > gy + gh) break;
            if (i == hot) Draw.round(ctx, cx, cy, SPR - 1, SPR - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            SpriteText.draw(ctx, tr, sprGroup, sprites.get(i), cx + 2, cy + 2, SPR - 4);
        }
        ctx.disableScissor();
        boolean searching = !search.getValue().trim().isEmpty();
        if (sprites.isEmpty())
            Draw.textFit(ctx, tr, searching ? "ничего не нашлось" : "текстуры не найдены",
                    lx + 6, gy + 4, lw - 12, Theme.TEXT_FAINT, false);
        drawScroll(ctx, lx - 2, gy, gh, sprMax(), sprScroll);
        Draw.textFit(ctx, tr, sprites.size() + " · " + Ui.click()
                        + " — вставить тег, виден в чате и в названии",
                lx, absY(symHintY), lw, Theme.TEXT_FAINT, false);
    }

    private int sprLeft() { return leftX() + (lw - sprCols * SPR) / 2; }

    private void drawSymbols(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int lx = leftX();
        drawSearch(ctx, mouseX, mouseY, delta);

        boolean searching = !search.getValue().trim().isEmpty();
        catChips.render(ctx, tr, mouseX, mouseY, lx, absY(catsY),
                searching ? -1 : cat, accent());

        int gy = absY(gridY), gh = gridRows * CELL;
        Ui.well(ctx, lx, gy - 3, lw, gh + 6);
        ctx.enableScissor(lx + 1, gy, lx + lw - 1, gy + gh);
        int hot = symbolAt(mouseX, mouseY);
        for (int i = 0; i < shown.size(); i++) {
            int cx = lx + 2 + cells[i][0];
            int cy = gy + cells[i][1] - gridScroll;
            int cw = cells[i][2];
            if (cy + CELL < gy || cy > gy + gh) continue;
            boolean hov = i == hot;
            if (hov) Draw.round(ctx, cx, cy, cw - 1, CELL - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            String g = shown.get(i).glyph();
            int ink = !shown.get(i).drawable() ? Theme.TEXT_FAINT
                    : hov ? Theme.TEXT : Theme.TEXT_DIM;
            ctx.text(tr, g, cx + (cw - 1 - tr.width(g)) / 2, cy + 5, Draw.opaque(ink), false);
            if (Symbols.favourite(g))
                Draw.rect(ctx, cx + 3, cy + CELL - 3, cw - 7, 1, Draw.opaque(accent()));
        }
        ctx.disableScissor();
        if (shown.isEmpty())
            Draw.textFit(ctx, tr, searching ? "ничего не нашлось"
                            : Ui.rmb() + " по символу добавит его сюда",
                    lx + 6, gy + 4, lw - 12, Theme.TEXT_FAINT, false);
        drawScroll(ctx, lx - 2, gy, gh, gridMax(), gridScroll);
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
        for (String s : labels) width = Math.max(width, tr.width(s));
        return lead + width + trail;
    }

    private void drawPreviews(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int px = rightX();

        int cw = chatWrap() + 8;
        int extra = chat.size() - chatRows;
        Ui.caption(ctx, tr, "В ЧАТЕ", px, absY(chatY) - CAP, cw,
                chat.size() > 1 ? Ui.plural(chat.size(), "строка", "строки", "строк") : "");
        int cy = absY(chatY), ch = chatRows * CHAT_LINE + 8;
        Draw.rect(ctx, px, cy, cw, ch, Draw.argb(0xE6, 0x0B0C10));
        ctx.enableScissor(px, cy, px + cw, cy + ch);
        for (int i = 0; i < Math.min(chatRows, chat.size()); i++) {
            boolean cut = extra > 0 && i == chatRows - 1;
            ctx.text(tr, cut ? Draw.ordered("…") : chat.get(i), px + 4, cy + 4 + i * CHAT_LINE,
                    cut ? 0xFFAAAAAA : 0xFFFFFFFF, true);
        }
        ctx.disableScissor();
        if (extra > 0)
            Draw.textRight(ctx, tr, "ещё " + (extra + 1), px + cw - 4,
                    cy + 4 + (chatRows - 1) * CHAT_LINE, Theme.TEXT_FAINT, false);

        int sy = absY(signY), nx = px + BOARD_W + 10, nw = rw - BOARD_W - 10;
        boolean over = signW > SIGN_W;
        drawSign(ctx, px, sy);
        Ui.caption(ctx, tr, "НА ТАБЛИЧКЕ", nx, sy + 4, nw);
        Draw.textFit(ctx, tr, signW + " / " + SIGN_W + " px", nx, sy + 16, nw,
                over ? Theme.DANGER : Theme.OK, false);
        List<String> lines = Ui.wrap(tr, over ? "не влезает — видно до переноса" : "влезает в строку",
                nw, 2);
        for (int i = 0; i < lines.size(); i++)
            Draw.text(ctx, tr, lines.get(i), nx, sy + 28 + i * 10, Theme.TEXT_FAINT, false);

        if (!compact) {
            Ui.caption(ctx, tr, "В НАЗВАНИИ ПРЕДМЕТА", px, absY(nameY) - CAP, rw);
            drawTooltip(ctx, px, absY(nameY));
        }

        Ui.caption(ctx, tr, "В ДРУГИХ РЕЖИМАХ · " + Ui.click().toUpperCase() + " КОПИРУЕТ", px,
                absY(expY) - CAP, rw);
        String[] modeNames = new String[MODES.size()];
        for (int i = 0; i < MODES.size(); i++) modeNames[i] = Values.parsingName(MODES.get(i));
        int mcol = column(modeNames, 6, 8);
        for (int i = 0; i < otherModes.size(); i++) {
            int ry = absY(expY) + i * OUT_ROW;
            boolean hov = Ui.hit(mouseX, mouseY, px, ry, rw, OUT_ROW - 1);
            Draw.round(ctx, px, ry, rw, OUT_ROW - 2, 3,
                    Draw.opaque(hov ? Ui.BTN_HOVER : Draw.mix(Ui.WELL, Ui.PANEL, 0.4f)));
            Draw.text(ctx, tr, Values.parsingName(otherModes.get(i)), px + 6, ry + 3,
                    hov ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, otherText.get(i), px + mcol, ry + 3,
                    rw - (hov ? 22 : 8) - mcol, hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (hov) Draw.glyph(ctx, Draw.COPY, px + rw - 14, ry + 3, Theme.TEXT_DIM);
        }
    }

    private void drawSign(GuiGraphicsExtractor ctx, int sx, int sy) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, SIGN_TEXTURE, sx, sy, 0f, 2f, BOARD_W, BOARD_H,
                24, 12, 32, 32, SIGN_SHADE);
        int top = sy + BOARD_H / 2 - 2 * SIGN_LINE;
        ctx.text(tr, sign, sx + BOARD_W / 2 - tr.width(sign) / 2, top, 0xFF000000, false);
    }

    private void drawTooltip(GuiGraphicsExtractor ctx, int tx, int ty) {
        int tw = Math.min(rw, Math.max(40, nameW + 10));
        int bg = 0xF0100010, top = 0x505000FF, bottom = 0x5028007F;
        Draw.rect(ctx, tx + 1, ty, tw - 2, NAME_H, bg);
        Draw.rect(ctx, tx, ty + 1, 1, NAME_H - 2, bg);
        Draw.rect(ctx, tx + tw - 1, ty + 1, 1, NAME_H - 2, bg);
        Draw.rect(ctx, tx + 2, ty + 1, tw - 4, 1, top);
        Draw.rect(ctx, tx + 2, ty + NAME_H - 2, tw - 4, 1, bottom);
        Draw.roundRectGrad(ctx, tx + 1, ty + 1, 1, NAME_H - 2, 0, 0, 0, 0, top, bottom);
        Draw.roundRectGrad(ctx, tx + tw - 2, ty + 1, 1, NAME_H - 2, 0, 0, 0, 0, top, bottom);
        ctx.enableScissor(tx + 3, ty, tx + tw - 3, ty + NAME_H);
        ctx.text(tr, preview, tx + 5, ty + (NAME_H - Ui.TEXT_H) / 2, 0xFFFFFFFF, true);
        ctx.disableScissor();
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int fy = y + footY + (FOOT_H - 18) / 2;
        int doneW = Ui.buttonW(tr, "Готово"), cancelW = Ui.buttonW(tr, "Отмена");
        Draw.textFit(ctx, tr, "Enter — готово · Shift+Enter — новая строка · Esc — отмена",
                x + PAD, fy + 5, inner() - doneW - cancelW - 16, Theme.TEXT_FAINT, false);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - doneW, fy, doneW, 18, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - doneW - 4 - cancelW, fy, cancelW, 18,
                "Отмена", Ui.GHOST);
    }

    private void setHexFromPicker() { hsv.write(Hsv.hex(hsv.rgb())); }

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
        undo.push(new String[]{input.getValue(), parsing});
        while (undo.size() > UNDO_MAX) undo.removeLast();
        redo.clear();
        lastTyped = System.currentTimeMillis();
    }

    private void typedSnapshot() {
        long now = System.currentTimeMillis();
        if (now - lastTyped > 700) {
            undo.push(new String[]{input.getValue(), parsing});
            while (undo.size() > UNDO_MAX) undo.removeLast();
            redo.clear();
        }
        lastTyped = now;
    }

    private void step(Deque<String[]> from, Deque<String[]> to, String what) {
        if (from.isEmpty()) { toast("нечего " + what); return; }
        to.push(new String[]{input.getValue(), parsing});
        String[] s = from.pop();
        parsing = s[1];
        modeChanged();
        focus(input);
        input.setValue(s[0]);
        input.moveCursorToEnd(false);
        lastTyped = 0;
    }

    private void wrap(String open, String close) { wrap(open, close, false); }

    private void wrap(String open, String close, boolean colour) {
        if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return; }
        snapshot();
        String sel = input.getHighlighted();
        if (colour) sel = uncoloured(sel);
        focus(input);
        if (sel.isEmpty()) {
            input.insertText(open);
            return;
        }
        int start = Math.min(input.getCursorPosition(), input.anchor());
        String lead = McText.LEGACY.equals(parsing) && open.length() == 2 && isDeco(open.charAt(1))
                ? leadingColours(sel) : "";
        String made = lead + open + sel.substring(lead.length()) + close;
        input.insertText(made);
        input.select(start, start + made.length());
    }

    private String uncoloured(String sel) {
        if (McText.LEGACY.equals(parsing)) {
            String lead = leadingColours(sel);
            if (lead.isEmpty()) return sel;
            String rest = sel.substring(lead.length());
            return rest.endsWith("&r") ? rest.substring(0, rest.length() - 2) : rest;
        }
        if (!McText.MINI.equals(parsing)) return sel;
        boolean last = Math.max(input.getCursorPosition(), input.anchor()) >= input.getValue().length();
        while (sel.startsWith("<")) {
            int end = sel.indexOf('>');
            if (end < 0) break;
            String tag = sel.substring(1, end).toLowerCase(java.util.Locale.ROOT);
            boolean hexTag = tag.startsWith("#");
            boolean known = hexTag && McText.normaliseHex(tag) != null;
            for (McText.Colour c : McText.COLOURS) if (c.name().equals(tag)) known = true;
            if (!known) break;
            String close = hexTag ? "</color>" : "</" + tag + ">";
            if (sel.length() >= end + 1 + close.length()
                    && sel.toLowerCase(java.util.Locale.ROOT).endsWith(close))
                sel = sel.substring(end + 1, sel.length() - close.length());
            else if (last) sel = sel.substring(end + 1);
            else break;
        }
        return sel;
    }

    private static boolean isDeco(char code) { return "lonmk".indexOf(code) >= 0; }

    private static String leadingColours(String s) {
        int i = 0;
        while (i + 1 < s.length() && (s.charAt(i) == '&' || s.charAt(i) == '§')) {
            char c = Character.toLowerCase(s.charAt(i + 1));
            if (c == '#' && i + 8 <= s.length() && McText.normaliseHex(s.substring(i + 2, i + 8)) != null)
                i += 8;
            else if ("0123456789abcdef".indexOf(c) >= 0) i += 2;
            else break;
        }
        return s.substring(0, i);
    }

    private void stripFormatting() {
        snapshot();
        String sel = input.getHighlighted();
        focus(input);
        if (sel.isEmpty()) {
            input.setValue(McText.plain(input.getValue(), parsing));
            input.moveCursorToEnd(false);
            toast("разметка убрана");
            return;
        }
        input.insertText(McText.plain(sel, parsing));
        toast("разметка убрана из выделения");
    }

    private void span(java.util.function.Function<String, String> markup) {
        String sel = input.getHighlighted();
        if (sel.isEmpty()) { toast("сначала выделите текст"); return; }
        snapshot();
        focus(input);
        int start = Math.min(input.getCursorPosition(), input.anchor());
        String made = markup.apply(sel);
        input.insertText(made);
        input.select(start, start + made.length());
    }

    private void setMode(String mode) {
        if (mode.equals(parsing)) return;
        snapshot();
        String converted = McText.convert(input.getValue(), parsing, mode);
        parsing = mode;
        modeChanged();
        focus(input);
        input.setValue(converted);
        input.moveCursorToEnd(false);
        toast("переведено в «" + Values.parsingName(mode) + "»");
    }

    private void insert(String glyph) {
        snapshot();
        focus(input);
        input.insertText(glyph);
    }

    private void copy(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        toast("скопировано");
    }

    private void openColourMenu(int t, java.util.function.IntConsumer pick, String title) {
        List<String> names = new ArrayList<>();
        for (McText.Colour c : McText.COLOURS) names.add(ru(c));
        menu = Menu.options(screenW, screenH, toolLeft(t), toolTop(t) + TOOL_H + 2, tr,
                title, names, -1, pick);
    }

    private void applyColour(McText.Colour c) {
        hsv.keep(c.rgb());
        setHexFromPicker();
        wrap(McText.colourTag(parsing, c), McText.colourClose(parsing, c), true);
    }

    private int spriteAt(double mx, double my) {
        if (tab != 2) return -1;
        int lx = sprLeft(), gy = absY(sprY);
        if (!Ui.hit(mx, my, lx, gy, sprCols * SPR, sprRows * SPR)) return -1;
        int col = (int) (mx - lx) / SPR, row = (int) (my - gy + sprScroll) / SPR;
        int i = row * sprCols + col;
        return i < sprites.size() ? i : -1;
    }

    private int symbolAt(double mx, double my) {
        if (tab != 1) return -1;
        int lx = leftX() + 2, gy = absY(gridY);
        if (!Ui.hit(mx, my, leftX(), gy, lw, gridRows * CELL)) return -1;
        for (int i = 0; i < cells.length && i < shown.size(); i++)
            if (Ui.hit(mx, my, lx + cells[i][0], gy + cells[i][1] - gridScroll,
                    cells[i][2] - 1, CELL - 1)) return i;
        return -1;
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        boolean right = click.button() == 1;
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (complete.mouseClicked(mx, my)) { syncPreview(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 14, headY(14), 14, 14)) { closed = true; return true; }
        if (headMode && modeClicked(mx, my)) return true;
        if (bar.grabbed(mx, my, 1, pane.max(), v -> { pane.scroll = v; placeFields(); }))
            return true;
        if (!pane.inBody(my, y)) return footClicked(mx, my);
        if (!headMode && modeClicked(mx, my)) return true;

        if (area.press(click, doubled)) {
            focus(input);
            syncComplete();
            return true;
        }

        if (toolClicked(mx, my, right)) return true;

        int ti = Ui.segmentAt(mx, my, leftX(), absY(tabsY), lw, SEG_H, tabNames().size());
        if (ti >= 0) {
            if (ti != tab) showTab(ti);
            return true;
        }

        boolean used = switch (tab) {
            case 0 -> colourClicked(mx, my, click, doubled);
            case 1 -> symbolsClicked(mx, my, click, doubled);
            default -> spritesClicked(mx, my, click, doubled);
        };
        if (used) return true;

        for (int i = 0; i < otherModes.size(); i++)
            if (Ui.hit(mx, my, rightX(), absY(expY) + i * OUT_ROW, rw, OUT_ROW - 1)) {
                copy(otherText.get(i));
                return true;
            }

        return footClicked(mx, my);
    }

    private boolean modeClicked(int mx, int my) {
        int mi = Ui.segmentAt(mx, my, modeLeft(), modeTop(), modeW, SEG_H, MODES.size());
        if (mi < 0) return false;
        setMode(MODES.get(mi));
        return true;
    }

    private boolean toolClicked(int mx, int my, boolean right) {
        int d = decoAt(mx, my);
        if (d >= 0) {
            McText.Deco deco = McText.DECOS.get(d);
            wrap(McText.decoTag(parsing, deco), McText.decoClose(parsing, deco));
            return true;
        }
        if (overTool(mx, my, T_RESET)) {
            McText.Deco reset = McText.DECOS.get(McText.DECOS.size() - 1);
            if (right) stripFormatting();
            else wrap(McText.decoTag(parsing, reset), McText.decoClose(parsing, reset));
            return true;
        }
        if (overTool(mx, my, T_COLOUR)) {
            if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return true; }
            if (right) openColourMenu(T_COLOUR, i -> applyColour(McText.COLOURS.get(i)),
                    "Цвет выделения");
            else applyPicked();
            return true;
        }
        boolean grad = McText.supportsGradient(parsing);
        for (int t : new int[]{T_GRAD_A, T_GRAD_B}) {
            if (!overTool(mx, my, t)) continue;
            boolean first = t == T_GRAD_A;
            if (!grad) toast("в этом режиме градиента нет");
            else if (right) openColourMenu(t, i -> {
                if (first) gradA = McText.COLOURS.get(i).rgb();
                else gradB = McText.COLOURS.get(i).rgb();
            }, first ? "Начальный цвет" : "Конечный цвет");
            else {
                if (first) gradA = hsv.rgb();
                else gradB = hsv.rgb();
                toast((first ? "начальный" : "конечный") + " цвет взят из пипетки");
            }
            return true;
        }
        if (overTool(mx, my, T_GRAD)) {
            if (!grad) toast("в этом режиме градиента нет");
            else span(s -> McText.gradientMarkup(parsing, s, new int[]{gradA, gradB}));
            return true;
        }
        if (overTool(mx, my, T_RAIN)) {
            if (!grad) toast("в этом режиме градиента нет");
            else span(s -> McText.rainbowMarkup(parsing, s));
            return true;
        }
        if (overTool(mx, my, T_UNDO)) { step(undo, redo, "отменять"); return true; }
        if (overTool(mx, my, T_REDO)) { step(redo, undo, "возвращать"); return true; }
        return false;
    }

    private boolean colourClicked(int mx, int my, MouseButtonEvent click, boolean doubled) {
        int sw = swatchAt(mx, my);
        if (sw >= 0) {
            applyColour(McText.COLOURS.get(sw));
            return true;
        }

        int sy = absY(pickY);
        if (Ui.hit(mx, my, leftX(), sy, svW, svH)) { dragging = 1; dragPicker(mx, my); return true; }
        for (int i = 0; i < 3; i++)
            if (Ui.hit(mx, my, sliderX(), sliderY(i) - 2, sliderW(), SL_H + 4)) {
                dragging = 2 + i;
                dragPicker(mx, my);
                return true;
            }

        int hy = absY(hexY()), rowH = TOOL_H - 4;
        if (Ui.hit(mx, my, hexX(), hy, HEX_W, rowH)) {
            focus(hex);
            if (!hex.mouseClicked(click, doubled)) hex.onClick(click, doubled);
            grab.take(hex);
            return true;
        }
        if (Ui.hit(mx, my, applyX(), hy, leftX() + lw - applyX(), rowH)) {
            applyPicked();
            return true;
        }
        int rc = recentAt(mx, my);
        if (rc >= 0) {
            hsv.keep(RECENT.get(rc));
            setHexFromPicker();
            return true;
        }
        return false;
    }

    private void applyPicked() {
        if (!McText.formattable(parsing)) { toast("в этом режиме разметки нет"); return; }
        remember(hsv.rgb());
        wrap(McText.hexTag(parsing, hsv.rgb()), McText.hexClose(parsing), true);
    }

    private boolean searchClicked(int mx, int my, MouseButtonEvent click, boolean doubled) {
        if (!Ui.hit(mx, my, leftX(), absY(searchY), lw, SEARCH_H)) return false;
        focus(search);
        if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
        grab.take(search);
        return true;
    }

    private boolean spritesClicked(int mx, int my, MouseButtonEvent click, boolean doubled) {
        if (searchClicked(mx, my, click, doubled)) return true;
        int gi = sprChips.indexAt(mx, my, leftX(), absY(catsY));
        if (gi >= 0) {
            sprGroup = gi;
            sprScroll = 0;
            return true;
        }
        int at = spriteAt(mx, my);
        if (at < 0) return false;
        String tag = SpriteText.tag(sprGroup, sprites.get(at));
        if (click.button() == 1) copy(tag);
        else insert(tag);
        return true;
    }

    private boolean symbolsClicked(int mx, int my, MouseButtonEvent click, boolean doubled) {
        int lx = leftX();
        if (searchClicked(mx, my, click, doubled)) return true;
        int ci = catChips.indexAt(mx, my, lx, absY(catsY));
        if (ci >= 0) {
            cat = ci;
            gridScroll = 0;
            if (!search.getValue().isEmpty()) {
                focus(search);
                search.setValue("");
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
        int fy = y + footY + (FOOT_H - 18) / 2;
        int doneW = Ui.buttonW(tr, "Готово"), cancelW = Ui.buttonW(tr, "Отмена");
        if (Ui.hit(mx, my, x + w - PAD - doneW, fy, doneW, 18)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - doneW - 4 - cancelW, fy, cancelW, 18)) {
            closed = true;
            return true;
        }
        return true;
    }

    private void dragPicker(double mx, double my) {
        int lx = leftX(), sy = absY(pickY);
        switch (dragging) {
            case 1 -> hsv.square((mx - lx) / (svW - 1.0), (my - sy) / (svH - 1.0));
            case 2 -> hsv.hue((mx - sliderX()) / (sliderW() - 1.0));
            case 3 -> hsv.sat((mx - sliderX()) / (sliderW() - 1.0));
            case 4 -> hsv.val((mx - sliderX()) / (sliderW() - 1.0));
            default -> { return; }
        }
        setHexFromPicker();
    }

    private void finish() {
        done.apply(input.getValue(), parsing);
        closed = true;
    }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (bar.dragged(click.y(), 1, pane.max(), v -> { pane.scroll = v; placeFields(); }))
            return true;
        if (dragging != 0) { dragPicker(click.x(), click.y()); return true; }
        if (area.drag(click.x(), click.y())) return true;
        return grab.drag(click, dx, dy);
    }

    public void mouseReleased() {
        dragging = 0;
        bar.release();
        grab.release();
        area.release();
        if (menu != null) menu.mouseReleased();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (menu != null) return menu.mouseScrolled(mx, my, amount);
        if (complete.active() && complete.mouseScrolled(mx, my, amount)) return true;
        if (area.wheel(mx, my, amount)) return true;
        if (tab == 1 && Ui.hit(mx, my, leftX(), absY(gridY), lw, gridRows * CELL)) {
            gridScroll -= (int) Math.round(amount * CELL);
            clampGrid();
            return true;
        }
        if (tab == 2 && Ui.hit(mx, my, leftX(), absY(sprY), lw, sprRows * SPR)) {
            sprScroll -= (int) Math.round(amount * SPR);
            clampSprites();
            return true;
        }
        if (pane.max() > 0 && Ui.hit(mx, my, x, y, w, h)) {
            pane.wheel(amount);
            placeFields();
        }
        return true;
    }

    public boolean keyPressed(KeyEvent in) {
        int key = in.key();
        boolean ctrl = (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (in.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (complete.keyPressed(in)) { syncPreview(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (search.isFocused() && !search.getValue().isEmpty()) {
                search.setValue("");
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
                if (tab == 2) {
                    if (!sprites.isEmpty()) insert(SpriteText.tag(sprGroup, sprites.get(0)));
                } else if (!shown.isEmpty()) insert(shown.get(0).glyph());
                return true;
            }
            if (shift) {
                insert(TextLines.BREAK);
                syncComplete();
                return true;
            }
            finish();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (shift) {
                if (input.isFocused()) focus(hex);
                else if (hex.isFocused()) focus(tab >= 1 ? search : input);
                else focus(input);
            } else if (input.isFocused()) focus(tab >= 1 ? search : hex);
            else if (search.isFocused()) focus(hex);
            else focus(input);
            return true;
        }
        if (hex.isFocused()) return hex.keyPressed(in);
        if (search.isFocused()) return search.keyPressed(in);
        if (ctrl && !shift && input.isFocused()) {
            int deco = key == GLFW.GLFW_KEY_B ? 0 : key == GLFW.GLFW_KEY_I ? 1
                    : key == GLFW.GLFW_KEY_U ? 2 : -1;
            if (deco >= 0) {
                McText.Deco d = McText.DECOS.get(deco);
                wrap(McText.decoTag(parsing, d), McText.decoClose(parsing, d));
                return true;
            }
        }
        if (input.isFocused() && area.key(in)) {
            syncComplete();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) typedSnapshot();
        if (ctrl && (key == GLFW.GLFW_KEY_V || key == GLFW.GLFW_KEY_X)) snapshot();
        boolean used = input.keyPressed(in);
        syncComplete();
        return used;
    }

    public boolean charTyped(CharacterEvent in) {
        if (menu != null) return true;
        if (hex.isFocused()) return hex.charTyped(in);
        if (search.isFocused()) return search.charTyped(in);
        typedSnapshot();
        boolean used = input.charTyped(in);
        syncComplete();
        return used;
    }

    private void syncComplete() {
        area.follow();
        complete.update(input.isFocused() ? input : null, tr, screenW, screenH);
    }
}
