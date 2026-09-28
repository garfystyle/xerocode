package net.minecraft.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.xerocode.web.Textures;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public class GuiGraphicsExtractor {
    private final Minecraft minecraft;
    private final Matrix3x2fStack pose = new Matrix3x2fStack(16);
    public final ScissorStack scissorStack;
    public final GuiRenderState guiRenderState;
    private CursorType pendingCursor = CursorType.DEFAULT;
    private static final int FINGER = 26;
    private Runnable deferredTooltip;

    public GuiGraphicsExtractor(Minecraft minecraft, GuiRenderState state) {
        this.minecraft = minecraft;
        this.guiRenderState = state;
        this.scissorStack = new ScissorStack();
    }

    public CursorType pendingCursor() { return pendingCursor; }

    public void requestCursor(CursorType type) { pendingCursor = type; }

    public int guiWidth() { return minecraft.getWindow().getGuiScaledWidth(); }

    public int guiHeight() { return minecraft.getWindow().getGuiScaledHeight(); }

    public Matrix3x2fStack pose() { return pose; }

    public void nextStratum() { guiRenderState.nextStratum(); }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        scissorStack.push(new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(pose));
    }

    public void disableScissor() { scissorStack.pop(); }

    public boolean containsPointInScissor(int x, int y) { return scissorStack.containsPoint(x, y); }

    public void horizontalLine(int x0, int x1, int y, int col) {
        if (x1 < x0) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        fill(x0, y, x1 + 1, y + 1, col);
    }

    public void verticalLine(int x, int y0, int y1, int col) {
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        fill(x, y0 + 1, x + 1, y1, col);
    }

    public void fill(int x0, int y0, int x1, int y1, int col) {
        fill(RenderPipelines.GUI, x0, y0, x1, y1, col);
    }

    public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        if (x0 < x1) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        if (y0 < y1) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        innerFill(pipeline, x0, y0, x1, y1, col, col);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        innerFill(RenderPipelines.GUI, x0, y0, x1, y1, col1, col2);
    }

    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private void innerFill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int c1, int c2) {
        guiRenderState.addGuiElement(new ColoredRectangleRenderState(pipeline, TextureSetup.noTexture(),
                new Matrix3x2f(pose), x0, y0, x1, y1, c1, c2, scissorStack.peek()));
    }

    public void textHighlight(int x0, int y0, int x1, int y1, boolean invertText) {
        if (invertText) fill(RenderPipelines.GUI_INVERT, x0, y0, x1, y1, -1);
        fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, x0, y0, x1, y1, 0xFF0000FF);
    }

    public void text(Font font, String str, int x, int y, int color) { text(font, str, x, y, color, true); }

    public void text(Font font, String str, int x, int y, int color, boolean shadow) {
        if (str != null) text(font, Language.getInstance().getVisualOrder(FormattedText.of(str)), x, y, color, shadow);
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color) { text(font, str, x, y, color, true); }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean shadow) {
        if ((color >>> 24) != 0)
            guiRenderState.addText(new GuiTextRenderState(font, str, new Matrix3x2f(pose), x, y, color, shadow, scissorStack.peek()));
    }

    public void text(Font font, Component str, int x, int y, int color) { text(font, str, x, y, color, true); }

    public void text(Font font, Component str, int x, int y, int color, boolean shadow) {
        text(font, str.getVisualOrderText(), x, y, color, shadow);
    }

    public void centeredText(Font font, String str, int x, int y, int color) {
        text(font, str, x - font.width(str) / 2, y, color);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        FormattedCharSequence s = text.getVisualOrderText();
        text(font, s, x - font.width(s) / 2, y, color);
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        text(font, text, x - font.width(text) / 2, y, color);
    }

    public void textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col) {
        textWithWordWrap(font, string, x, y, width, col, true);
    }

    public void textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col, boolean shadow) {
        for (FormattedCharSequence line : font.split(string, width)) {
            text(font, line, x, y, col, shadow);
            y += 9;
        }
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                     int textureWidth, int textureHeight, int color) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                     int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                     int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, -1);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                     int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {
        innerBlit(pipeline, Textures.handle(texture), false, x, x + width, y, y + height,
                u / textureWidth, (u + srcWidth) / textureWidth, v / textureHeight, (v + srcHeight) / textureHeight, color);
    }

    public void blit(Identifier texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        innerBlit(RenderPipelines.GUI_TEXTURED, Textures.handle(texture), false, x0, x1, y0, y1, u0, u1, v0, v1, -1);
    }

    public void blitTexture(int texture, boolean linear, int x0, int y0, int x1, int y1, float u0, float u1, float v0,
                            float v1, int color) {
        innerBlit(RenderPipelines.GUI_TEXTURED, texture, linear, x0, x1, y0, y1, u0, u1, v0, v1, color);
    }

    private void innerBlit(RenderPipeline pipeline, int texture, boolean linear, int x0, int x1, int y0, int y1,
                           float u0, float u1, float v0, float v1, int color) {
        if (texture == 0) return;
        guiRenderState.addGuiElement(new BlitRenderState(pipeline, TextureSetup.of(texture, linear), new Matrix3x2f(pose),
                x0, y0, x1, y1, u0, u1, v0, v1, color, scissorStack.peek()));
    }

    public void blitSprite(RenderPipeline pipeline, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                           int x, int y, int width, int height, int color) {
        if (sprite == null) return;
        innerBlit(pipeline, sprite.texture(), false, x, x + width, y, y + height,
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), color);
    }

    public void blitSprite(RenderPipeline pipeline, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                           int x, int y, int width, int height) {
        blitSprite(pipeline, sprite, x, y, width, height, -1);
    }

    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        guiRenderState.addItem(new GuiItemRenderState(new Matrix3x2f(pose), stack, x, y, scissorStack.peek()));
    }

    public void fakeItem(ItemStack stack, int x, int y) { item(stack, x, y); }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        itemDecorations(font, stack, x, y, null);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String countText) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getCount() != 1 || countText != null) {
            String s = countText == null ? String.valueOf(stack.getCount()) : countText;
            pose.pushMatrix();
            text(font, s, x + 19 - 2 - font.width(s), y + 6 + 3, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        List<FormattedCharSequence> seq = new ArrayList<>();
        for (Component c : lines) seq.add(c.getVisualOrderText());
        setTooltipForNextFrame(font, seq, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        setTooltipForNextFrame(font, List.of(text.getVisualOrderText()), x, y);
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        if (lines.isEmpty()) return;
        deferredTooltip = () -> tooltip(font, lines, x, y);
    }

    public void renderDeferredElements() {
        if (deferredTooltip != null) {
            nextStratum();
            deferredTooltip.run();
            deferredTooltip = null;
        }
    }

    private void tooltip(Font font, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() == 1 ? 8 : 8 + 2 + (lines.size() - 1) * 10;
        int x = mouseX + 12, y = mouseY - 12;
        if (com.xerocode.Env.touch()) {
            x = Math.max(4, Math.min(guiWidth() - w - 4, mouseX - w / 2));
            y = mouseY - FINGER - h;
            if (y < 4) y = mouseY + FINGER;
        }
        if (x + w + 4 > guiWidth()) x = Math.max(4, mouseX - 16 - w);
        if (y + h + 3 > guiHeight()) y = guiHeight() - h - 3;
        if (y < 4) y = 4;
        pose.pushMatrix();
        pose.identity();
        int bg = 0xF0100010, b0 = 0x505000FF, b1 = 0x5028007F;
        fill(x - 3, y - 4, x + w + 3, y - 3, bg);
        fill(x - 3, y + h + 3, x + w + 3, y + h + 4, bg);
        fill(x - 3, y - 3, x + w + 3, y + h + 3, bg);
        fill(x - 4, y - 3, x - 3, y + h + 3, bg);
        fill(x + w + 3, y - 3, x + w + 4, y + h + 3, bg);
        fillGradient(x - 3, y - 2, x - 2, y + h + 2, b0, b1);
        fillGradient(x + w + 2, y - 2, x + w + 3, y + h + 2, b0, b1);
        fill(x - 3, y - 3, x + w + 3, y - 2, b0);
        fill(x - 3, y + h + 2, x + w + 3, y + h + 3, b1);
        int yy = y;
        for (int i = 0; i < lines.size(); i++) {
            text(font, lines.get(i), x, yy, 0xFFFFFFFF, true);
            yy += i == 0 ? 12 : 10;
        }
        pose.popMatrix();
    }

    public static final class ScissorStack {
        private final Deque<ScreenRectangle> stack = new ArrayDeque<>();

        public ScreenRectangle push(ScreenRectangle r) {
            ScreenRectangle last = stack.peekLast();
            if (last != null) {
                ScreenRectangle i = last.intersection(r);
                r = i != null ? i : ScreenRectangle.empty();
            }
            stack.addLast(r);
            return r;
        }

        public ScreenRectangle pop() {
            if (stack.isEmpty()) throw new IllegalStateException("Scissor stack underflow");
            stack.removeLast();
            return stack.peekLast();
        }

        public ScreenRectangle peek() { return stack.peekLast(); }

        public boolean containsPoint(int x, int y) {
            return stack.isEmpty() || stack.peekLast().containsPoint(x, y);
        }
    }
}
