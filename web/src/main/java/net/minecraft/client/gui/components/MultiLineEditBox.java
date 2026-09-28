package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.web.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public class MultiLineEditBox extends AbstractWidget {
    private static final int PAD = 4;
    private static final int SCROLLBAR = 6;

    private record View(int begin, int end) {}

    private final Font font;
    private final Component placeholder;
    private final int textColor;
    private final boolean textShadow;
    private final int cursorColor;
    private final boolean showBackground;
    private final boolean showDecorations;
    private final int fieldWidth;
    private final List<View> lines = new ArrayList<>();
    private String value = "";
    private int cursor;
    private int selectCursor;
    private boolean selecting;
    private int characterLimit = Integer.MAX_VALUE;
    private int lineLimit = Integer.MAX_VALUE;
    private Consumer<String> valueListener = s -> {};
    private double scroll;
    private boolean scrolling;
    private long focusedTime = System.currentTimeMillis();

    private MultiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component narration,
                             int textColor, boolean textShadow, int cursorColor, boolean showBackground,
                             boolean showDecorations) {
        super(x, y, width, height, narration);
        this.font = font;
        this.placeholder = placeholder;
        this.textColor = textColor;
        this.textShadow = textShadow;
        this.cursorColor = cursorColor;
        this.showBackground = showBackground;
        this.showDecorations = showDecorations;
        this.fieldWidth = width - PAD * 2;
        setValue("");
    }

    public static Builder builder() { return new Builder(); }

    public void setCharacterLimit(int limit) { characterLimit = limit; }

    public void setLineLimit(int limit) { lineLimit = limit; }

    public void setValueListener(Consumer<String> listener) { valueListener = listener; }

    public void setValue(String v) { setValue(v, false); }

    public void setValue(String v, boolean allowOverflow) {
        String nv = truncate(v, characterLimit);
        if (allowOverflow || !overflows(nv)) {
            value = nv;
            cursor = value.length();
            selectCursor = cursor;
            changed();
        }
    }

    public String getValue() { return value; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(0, max));
    }

    private boolean overflows(String nv) {
        return lineLimit != Integer.MAX_VALUE
                && font.getSplitter().splitLines(nv, fieldWidth, Style.EMPTY).size() + (nv.endsWith("\n") ? 1 : 0) > lineLimit;
    }

    private static String filter(String input) {
        StringBuilder sb = new StringBuilder();
        input.codePoints().forEach(cp -> {
            if (cp == '\n' || (cp != 167 && cp >= 32 && cp != 127)) sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    private void insertText(String input) {
        if (input.isEmpty() && !hasSelection()) return;
        String text = filter(input);
        if (characterLimit != Integer.MAX_VALUE) text = truncate(text, characterLimit - value.length());
        View sel = selected();
        String nv = new StringBuilder(value).replace(sel.begin, sel.end, text).toString();
        if (overflows(nv)) return;
        value = nv;
        cursor = sel.begin + text.length();
        selectCursor = cursor;
        changed();
    }

    private void deleteText(int dir) {
        if (!hasSelection()) selectCursor = Mth.clamp(cursor + dir, 0, value.length());
        insertText("");
    }

    private View selected() { return new View(Math.min(selectCursor, cursor), Math.max(selectCursor, cursor)); }

    private boolean hasSelection() { return selectCursor != cursor; }

    private int lineAtCursor() {
        for (int i = 0; i < lines.size(); i++) {
            View v = lines.get(i);
            if (cursor >= v.begin && cursor <= v.end) return i;
        }
        return -1;
    }

    private View line(int i) { return lines.get(Mth.clamp(i, 0, lines.size() - 1)); }

    private View cursorLine(int offset) {
        int i = lineAtCursor();
        return i < 0 ? lines.get(lines.size() - 1) : line(i + offset);
    }

    private void seek(int to) {
        cursor = Mth.clamp(to, 0, value.length());
        scrollToCursor();
        if (!selecting) selectCursor = cursor;
    }

    private void seekLine(int offset) {
        if (offset == 0) return;
        int left = font.width(value.substring(cursorLine(0).begin, cursor)) + 2;
        View v = cursorLine(offset);
        seek(v.begin + font.plainSubstrByWidth(value.substring(v.begin, v.end), left).length());
    }

    private void seekPoint(double x, double y) {
        View v = line(Mth.floor(y / 9.0));
        seek(v.begin + font.plainSubstrByWidth(value.substring(v.begin, v.end), Mth.floor(x)).length());
    }

    private View previousWord() {
        if (value.isEmpty()) return new View(0, 0);
        int p = Mth.clamp(cursor, 0, value.length() - 1);
        while (p > 0 && Character.isWhitespace(value.charAt(p - 1))) p--;
        while (p > 0 && !Character.isWhitespace(value.charAt(p - 1))) p--;
        return new View(p, wordEnd(p));
    }

    private View nextWord() {
        if (value.isEmpty()) return new View(0, 0);
        int p = Mth.clamp(cursor, 0, value.length() - 1);
        while (p < value.length() && !Character.isWhitespace(value.charAt(p))) p++;
        while (p < value.length() && Character.isWhitespace(value.charAt(p))) p++;
        return new View(p, wordEnd(p));
    }

    private int wordEnd(int from) {
        int e = from;
        while (e < value.length() && !Character.isWhitespace(value.charAt(e))) e++;
        return e;
    }

    private void changed() {
        lines.clear();
        if (value.isEmpty()) {
            lines.add(new View(0, 0));
        } else {
            font.getSplitter().splitLines(value, fieldWidth, Style.EMPTY, false, (st, s, e) -> lines.add(new View(s, e)));
            if (value.charAt(value.length() - 1) == '\n') lines.add(new View(value.length(), value.length()));
        }
        valueListener.accept(value);
        scrollToCursor();
    }

    private int contentHeight() { return 9 * lines.size() + PAD * 2; }

    private int maxScroll() { return Math.max(0, contentHeight() - height); }

    private void setScroll(double v) { scroll = Mth.clamp(v, 0.0, maxScroll()); }

    private void scrollToCursor() {
        if (lines.isEmpty()) return;
        double amount = scroll;
        View first = line((int) (amount / 9.0));
        if (cursor <= first.begin) {
            amount = lineAtCursor() * 9;
        } else {
            View last = line((int) ((amount + height) / 9.0) - 1);
            if (cursor > last.end) amount = lineAtCursor() * 9 - height + 9 + PAD * 2;
        }
        setScroll(amount);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!visible) return false;
        setScroll(scroll - sy * 4);
        return true;
    }

    private boolean overScrollbar(double x, double y) {
        return x >= getRight() && x <= getRight() + SCROLLBAR && y >= getY() && y < getBottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        scrolling = maxScroll() > 0 && event.button() == 0 && overScrollbar(event.x(), event.y());
        return super.mouseClicked(event, doubleClick) || scrolling;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return active && visible && mx >= getX() && my >= getY() && mx < getRight() + SCROLLBAR && my < getBottom();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) {
            View w = previousWord();
            seek(w.begin);
            selecting = true;
            seek(w.end);
        } else {
            selecting = event.hasShiftDown();
            seekPoint(event.x() - getX() - PAD, event.y() - getY() - PAD + scroll);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrolling) {
            if (event.y() < getY()) setScroll(0);
            else if (event.y() > getBottom()) setScroll(maxScroll());
            else {
                double max = Math.max(1, maxScroll());
                int bar = Mth.clamp((int) ((float) (height * height) / contentHeight()), 32, height - 8);
                setScroll(scroll + dy * Math.max(1.0, max / (height - bar)));
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        selecting = true;
        seekPoint(event.x() - getX() - PAD, event.y() - getY() - PAD + scroll);
        selecting = event.hasShiftDown();
    }

    @Override
    public void onRelease(MouseButtonEvent event) { scrolling = false; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isUp() || event.isDown()) {
            double before = scroll;
            setScroll(scroll + (event.isUp() ? -1 : 1) * 4);
            if (before != scroll) return true;
        }
        selecting = event.hasShiftDown();
        if (event.isSelectAll()) {
            cursor = value.length();
            selectCursor = 0;
            return true;
        }
        if (event.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            return true;
        }
        if (event.isPaste()) {
            insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            insertText("");
            return true;
        }
        boolean ctrl = event.hasControlDownWithQuirk();
        switch (event.key()) {
            case 257, 335 -> insertText("\n");
            case 259 -> {
                if (ctrl) deleteText(previousWord().begin - cursor);
                else deleteText(-1);
            }
            case 261 -> {
                if (ctrl) deleteText(nextWord().begin - cursor);
                else deleteText(1);
            }
            case 262 -> seek(ctrl ? nextWord().begin : cursor + 1);
            case 263 -> seek(ctrl ? previousWord().begin : cursor - 1);
            case 264 -> {
                if (!ctrl) seekLine(1);
            }
            case 265 -> {
                if (!ctrl) seekLine(-1);
            }
            case 266 -> seek(0);
            case 267 -> seek(value.length());
            case 268 -> seek(ctrl ? 0 : cursorLine(0).begin);
            case 269 -> seek(ctrl ? value.length() : cursorLine(0).end);
            default -> {
                return false;
            }
        }
        return true;
    }

    private String selectedText() {
        View s = selected();
        return value.substring(s.begin, s.end);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (visible && isFocused() && event.isAllowedChatCharacter()) {
            insertText(event.codepointAsString());
            return true;
        }
        return false;
    }

    private boolean within(int top, int bottom) {
        return bottom - scroll >= getY() && top - scroll <= getY() + height;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!visible) return;
        if (showBackground) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF000000);
        }
        g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, (float) -scroll);
        contents(g);
        g.pose().popMatrix();
        g.disableScissor();
        if (maxScroll() > 0) {
            int bar = Mth.clamp((int) ((float) (height * height) / contentHeight()), 32, height - 8);
            int by = Math.max(getY(), (int) scroll * (height - bar) / maxScroll() + getY());
            g.fill(getRight(), getY(), getRight() + SCROLLBAR, getBottom(), 0xFF000000);
            g.fill(getRight(), by, getRight() + SCROLLBAR, by + bar, 0xFF808080);
            g.fill(getRight(), by, getRight() + SCROLLBAR - 1, by + bar - 1, 0xFFC0C0C0);
        }
        if (showDecorations && characterLimit != Integer.MAX_VALUE) {
            String count = value.length() + "/" + characterLimit;
            g.text(font, count, getX() + width - font.width(count), getY() + height + 4, 0xFFA0A0A0);
        }
    }

    private void contents(GuiGraphicsExtractor g) {
        int left = getX() + PAD;
        int top = getY() + PAD;
        if (value.isEmpty() && !isFocused()) {
            g.textWithWordWrap(font, placeholder, left, top, width - PAD * 2, 0xCCE0E0E0);
            return;
        }
        boolean showCursor = isFocused() && (System.currentTimeMillis() - focusedTime) / 300L % 2L == 0L;
        boolean insert = cursor < value.length();
        int cx = 0, cy = 0;
        int y = top;
        boolean drawn = false;
        for (View v : lines) {
            boolean vis = within(y, y + 9);
            if (!drawn && showCursor && insert && cursor >= v.begin && cursor <= v.end) {
                if (vis) {
                    String before = value.substring(v.begin, cursor);
                    int right = left + font.width(before);
                    g.text(font, before, left, y, textColor, textShadow);
                    g.text(font, value.substring(cursor, v.end), right, y, textColor, textShadow);
                    cx = right;
                    cy = y;
                    g.fill(cx, cy - 1, cx + 1, cy + 10, cursorColor);
                    drawn = true;
                }
            } else if (vis) {
                String s = value.substring(v.begin, v.end);
                g.text(font, s, left, y, textColor, textShadow);
                if (!insert) {
                    cx = left + font.width(s);
                    cy = y;
                }
            }
            if (cursor >= v.begin && cursor <= v.end && !drawn) {
                cx = left + font.width(value.substring(v.begin, Math.min(cursor, v.end)));
                cy = y;
            }
            y += 9;
        }
        if (showCursor && !insert && within(cy, cy + 9)) g.text(font, "_", cx, cy, cursorColor, textShadow);
        if (hasSelection()) {
            View sel = selected();
            y = top;
            for (View v : lines) {
                if (sel.begin > v.end) {
                    y += 9;
                    continue;
                }
                if (v.begin > sel.end) break;
                if (within(y, y + 9)) {
                    int b = font.width(value.substring(v.begin, Math.max(sel.begin, v.begin)));
                    int e = sel.end > v.end ? width - PAD : font.width(value.substring(v.begin, sel.end));
                    g.textHighlight(left + b, y, left + e, y + 9, true);
                }
                y += 9;
            }
        }
        if (isHovered()) g.requestCursor(CursorTypes.IBEAM);
        if (isFocused()) {
            Input.drawn(this);
            Input.caretAt(cx, (int) (cy - scroll));
            Input.selection(selectedText(), value);
        }
        Input.textRect(getX(), getY(), width, height);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) focusedTime = System.currentTimeMillis();
        Input.textFocus(this, focused);
    }

    public static final class Builder {
        private int x;
        private int y;
        private Component placeholder = CommonComponents.EMPTY;
        private int textColor = 0xFFE0E0E0;
        private boolean textShadow = true;
        private int cursorColor = 0xFFD0D0D0;
        private boolean showBackground = true;
        private boolean showDecorations = true;

        public Builder setX(int x) { this.x = x; return this; }
        public Builder setY(int y) { this.y = y; return this; }
        public Builder setPlaceholder(Component p) { this.placeholder = p; return this; }
        public Builder setTextColor(int c) { this.textColor = c; return this; }
        public Builder setTextShadow(boolean s) { this.textShadow = s; return this; }
        public Builder setCursorColor(int c) { this.cursorColor = c; return this; }
        public Builder setShowBackground(boolean b) { this.showBackground = b; return this; }
        public Builder setShowDecorations(boolean b) { this.showDecorations = b; return this; }

        public MultiLineEditBox build(Font font, int width, int height, Component narration) {
            return new MultiLineEditBox(font, x, y, width, height, placeholder, narration, textColor, textShadow,
                    cursorColor, showBackground, showDecorations);
        }
    }
}
