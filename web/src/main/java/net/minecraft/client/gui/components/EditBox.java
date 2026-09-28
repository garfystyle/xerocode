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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class EditBox extends AbstractWidget {
    private final Font font;
    private String value = "";
    private int maxLength = 32;
    private boolean bordered = true;
    private boolean canLoseFocus = true;
    private boolean isEditable = true;
    private boolean centered;
    private boolean textShadow = true;
    private int displayPos;
    private int cursorPos;
    private int highlightPos;
    private int textColor = 0xFFE0E0E0;
    private int textColorUneditable = 0xFF707070;
    private String suggestion;
    private Consumer<String> responder;
    private final List<TextFormatter> formatters = new ArrayList<>();
    private Component hint;
    private long focusedTime = System.currentTimeMillis();
    private int textX;
    private int textY;

    public EditBox(Font font, Component narration) {
        this(font, 0, 0, 150, 20, narration);
    }

    public EditBox(Font font, int width, int height, Component narration) {
        this(font, 0, 0, width, height, narration);
    }

    public EditBox(Font font, int x, int y, int width, int height, Component narration) {
        this(font, x, y, width, height, null, narration);
    }

    public EditBox(Font font, int x, int y, int width, int height, EditBox old, Component narration) {
        super(x, y, width, height, narration);
        this.font = font;
        if (old != null) setValue(old.getValue());
        updateTextPosition();
    }

    public void setResponder(Consumer<String> responder) { this.responder = responder; }

    public void addFormatter(TextFormatter formatter) { formatters.add(formatter); }

    public void setValue(String value) {
        this.value = value.length() > maxLength ? value.substring(0, maxLength) : value;
        moveCursorToEnd(false);
        setHighlightPos(cursorPos);
        onValueChange(value);
    }

    public String getValue() { return value; }

    public String getHighlighted() {
        return value.substring(Math.min(cursorPos, highlightPos), Math.max(cursorPos, highlightPos));
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        updateTextPosition();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        updateTextPosition();
    }

    private static String filter(String input) {
        StringBuilder sb = new StringBuilder();
        input.codePoints().forEach(cp -> {
            if (cp != 167 && cp >= 32 && cp != 127) sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    public void insertText(String input) {
        int start = Math.min(cursorPos, highlightPos), end = Math.max(cursorPos, highlightPos);
        int room = maxLength - value.length() - (start - end);
        if (room <= 0) return;
        String text = filter(input);
        int len = text.length();
        if (room < len) {
            if (Character.isHighSurrogate(text.charAt(room - 1))) room--;
            text = text.substring(0, room);
            len = room;
        }
        value = new StringBuilder(value).replace(start, end, text).toString();
        setCursorPosition(start + len);
        setHighlightPos(cursorPos);
        onValueChange(value);
    }

    private void onValueChange(String v) {
        if (responder != null) responder.accept(v);
        updateTextPosition();
    }

    private void deleteText(int dir, boolean wholeWord) {
        if (wholeWord) deleteWords(dir);
        else deleteChars(dir);
    }

    public void deleteWords(int dir) {
        if (value.isEmpty()) return;
        if (highlightPos != cursorPos) insertText("");
        else deleteCharsToPos(getWordPosition(dir));
    }

    public void deleteChars(int dir) { deleteCharsToPos(getCursorPos(dir)); }

    public void deleteCharsToPos(int pos) {
        if (value.isEmpty()) return;
        if (highlightPos != cursorPos) {
            insertText("");
            return;
        }
        int start = Math.min(pos, cursorPos), end = Math.max(pos, cursorPos);
        if (start == end) return;
        value = new StringBuilder(value).delete(start, end).toString();
        setCursorPosition(start);
        onValueChange(value);
        moveCursorTo(start, false);
    }

    public int getWordPosition(int dir) { return getWordPosition(dir, getCursorPosition()); }

    private int getWordPosition(int dir, int from) {
        int result = from;
        boolean reverse = dir < 0;
        for (int i = 0; i < Math.abs(dir); i++) {
            if (!reverse) {
                int length = value.length();
                result = value.indexOf(' ', result);
                if (result == -1) result = length;
                else while (result < length && value.charAt(result) == ' ') result++;
            } else {
                while (result > 0 && value.charAt(result - 1) == ' ') result--;
                while (result > 0 && value.charAt(result - 1) != ' ') result--;
            }
        }
        return result;
    }

    public void moveCursor(int dir, boolean shift) { moveCursorTo(getCursorPos(dir), shift); }

    private int getCursorPos(int dir) {
        int pos = cursorPos;
        try {
            return value.offsetByCodePoints(pos, dir);
        } catch (IndexOutOfBoundsException e) {
            return dir < 0 ? 0 : value.length();
        }
    }

    public void moveCursorTo(int pos, boolean extend) {
        setCursorPosition(pos);
        if (!extend) setHighlightPos(cursorPos);
        updateTextPosition();
    }

    public void setCursorPosition(int pos) {
        cursorPos = Mth.clamp(pos, 0, value.length());
        scrollTo(cursorPos);
    }

    public void moveCursorToStart(boolean shift) { moveCursorTo(0, shift); }

    public void moveCursorToEnd(boolean shift) { moveCursorTo(value.length(), shift); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isActive() || !isFocused()) return false;
        switch (event.key()) {
            case 259 -> {
                if (isEditable) deleteText(-1, event.hasControlDownWithQuirk());
                return true;
            }
            case 261 -> {
                if (isEditable) deleteText(1, event.hasControlDownWithQuirk());
                return true;
            }
            case 262 -> {
                if (event.hasControlDownWithQuirk()) moveCursorTo(getWordPosition(1), event.hasShiftDown());
                else moveCursor(1, event.hasShiftDown());
                return true;
            }
            case 263 -> {
                if (event.hasControlDownWithQuirk()) moveCursorTo(getWordPosition(-1), event.hasShiftDown());
                else moveCursor(-1, event.hasShiftDown());
                return true;
            }
            case 268 -> {
                moveCursorToStart(event.hasShiftDown());
                return true;
            }
            case 269 -> {
                moveCursorToEnd(event.hasShiftDown());
                return true;
            }
            default -> {
                if (event.isSelectAll()) {
                    moveCursorToEnd(false);
                    setHighlightPos(0);
                    return true;
                }
                if (event.isCopy()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(getHighlighted());
                    return true;
                }
                if (event.isPaste()) {
                    if (isEditable) insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
                    return true;
                }
                if (event.isCut()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(getHighlighted());
                    if (isEditable) insertText("");
                    return true;
                }
                return false;
            }
        }
    }

    public boolean canConsumeInput() { return isActive() && isFocused() && isEditable; }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!canConsumeInput()) return false;
        if (event.isAllowedChatCharacter()) {
            if (isEditable) insertText(event.codepointAsString());
            return true;
        }
        return false;
    }

    private int clickedPosition(MouseButtonEvent event) {
        int at = Math.min(Mth.floor(event.x()) - textX, getInnerWidth());
        String shown = value.substring(displayPos);
        return displayPos + font.plainSubstrByWidth(shown, at).length();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) {
            int p = clickedPosition(event);
            moveCursorTo(getWordPosition(-1, p), false);
            moveCursorTo(getWordPosition(1, p), true);
        } else {
            moveCursorTo(clickedPosition(event), event.hasShiftDown());
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        moveCursorTo(clickedPosition(event), true);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!isVisible()) return;
        if (bordered) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF000000);
        }
        int color = isEditable ? textColor : textColorUneditable;
        int relCursor = cursorPos - displayPos;
        String shown = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        boolean cursorOnScreen = relCursor >= 0 && relCursor <= shown.length();
        boolean showCursor = isFocused() && (System.currentTimeMillis() - focusedTime) / 300L % 2L == 0L && cursorOnScreen;
        int drawX = textX;
        int relHighlight = Mth.clamp(highlightPos - displayPos, 0, shown.length());
        if (!shown.isEmpty()) {
            String half = cursorOnScreen ? shown.substring(0, relCursor) : shown;
            FormattedCharSequence seq = applyFormat(half, displayPos);
            g.text(font, seq, drawX, textY, color, textShadow);
            drawX += font.width(seq) + 1;
        }
        boolean insert = cursorPos < value.length() || value.length() >= maxLength;
        int cursorX = drawX;
        if (!cursorOnScreen) {
            cursorX = relCursor > 0 ? textX + width : textX;
        } else if (insert) {
            cursorX--;
            drawX--;
        }
        if (!shown.isEmpty() && cursorOnScreen && relCursor < shown.length())
            g.text(font, applyFormat(shown.substring(relCursor), cursorPos), drawX, textY, color, textShadow);
        if (hint != null && shown.isEmpty() && !isFocused()) g.text(font, hint, drawX, textY, color);
        if (!insert && suggestion != null) g.text(font, suggestion, cursorX - 1, textY, 0xFF808080, textShadow);
        if (relHighlight != relCursor) {
            int hx = textX + font.width(shown.substring(0, relHighlight));
            g.textHighlight(Math.min(cursorX, getX() + width), textY - 1, Math.min(hx - 1, getX() + width), textY + 1 + 9, true);
        }
        if (showCursor) {
            if (insert) g.fill(cursorX, textY - 1, cursorX + 1, textY + 10, color);
            else g.text(font, "_", cursorX, textY, color, textShadow);
        }
        if (isHovered()) g.requestCursor(isEditable ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
        if (isFocused() && isEditable) {
            Input.drawn(this);
            Input.caretAt(cursorX, textY);
            Input.selection(getHighlighted(), value);
        }
        if (isEditable) Input.textRect(getX(), getY(), width, height);
    }

    private FormattedCharSequence applyFormat(String text, int offset) {
        for (TextFormatter f : formatters) {
            FormattedCharSequence s = f.format(text, offset);
            if (s != null) return s;
        }
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private void updateTextPosition() {
        if (font == null) return;
        String shown = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        textX = getX() + (centered ? (getWidth() - font.width(shown)) / 2 : (bordered ? 4 : 0));
        textY = bordered ? getY() + (height - 8) / 2 : getY();
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
            onValueChange(value);
        }
    }

    public int getCursorPosition() { return cursorPos; }
    public boolean isBordered() { return bordered; }

    public void setBordered(boolean bordered) {
        this.bordered = bordered;
        updateTextPosition();
    }

    public void setTextColor(int c) { textColor = c; }
    public void setTextColorUneditable(int c) { textColorUneditable = c; }

    @Override
    public void setFocused(boolean focused) {
        if (canLoseFocus || focused) {
            super.setFocused(focused);
            if (focused) focusedTime = System.currentTimeMillis();
            Input.textFocus(this, focused && isEditable);
        }
    }

    public boolean isEditable() { return isEditable; }
    public void setEditable(boolean e) { isEditable = e; }

    public void setCentered(boolean c) {
        centered = c;
        updateTextPosition();
    }

    public void setTextShadow(boolean s) { textShadow = s; }
    public void setInvertHighlightedTextColor(boolean v) {}
    public int getInnerWidth() { return bordered ? width - 8 : width; }

    public void setHighlightPos(int pos) {
        highlightPos = Mth.clamp(pos, 0, value.length());
        scrollTo(highlightPos);
    }

    private void scrollTo(int pos) {
        if (font == null) return;
        displayPos = Math.min(displayPos, value.length());
        int inner = getInnerWidth();
        String shown = font.plainSubstrByWidth(value.substring(displayPos), inner);
        int last = shown.length() + displayPos;
        if (pos == displayPos) displayPos -= font.plainSubstrByWidth(value, inner, true).length();
        if (pos > last) displayPos += pos - last;
        else if (pos <= displayPos) displayPos -= displayPos - pos;
        displayPos = Mth.clamp(displayPos, 0, value.length());
    }

    public void setCanLoseFocus(boolean v) { canLoseFocus = v; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; }
    public void setSuggestion(String s) { suggestion = s; }

    public int getScreenX(int index) {
        return index > value.length() ? getX() : getX() + font.width(value.substring(0, index));
    }

    public void setHint(Component hint) {
        this.hint = hint.getStyle().equals(Style.EMPTY)
                ? hint.copy().withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)) : hint;
    }

    @FunctionalInterface
    public interface TextFormatter {
        FormattedCharSequence format(String text, int offset);
    }
}
