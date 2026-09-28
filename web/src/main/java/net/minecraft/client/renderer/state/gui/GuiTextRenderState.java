package net.minecraft.client.renderer.state.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;

public final class GuiTextRenderState implements ScreenArea {
    public final Font font;
    public final FormattedCharSequence text;
    public final Matrix3x2fc pose;
    public final int x, y, color;
    public final boolean dropShadow;
    public final ScreenRectangle scissor;
    private Font.PreparedText prepared;
    private ScreenRectangle bounds;

    public GuiTextRenderState(Font font, FormattedCharSequence text, Matrix3x2fc pose, int x, int y, int color,
                              boolean dropShadow, ScreenRectangle scissor) {
        this.font = font;
        this.text = text;
        this.pose = pose;
        this.x = x;
        this.y = y;
        this.color = color;
        this.dropShadow = dropShadow;
        this.scissor = scissor;
    }

    public Font.PreparedText ensurePrepared() {
        if (prepared == null) {
            prepared = font.prepareText(text, x, y, color, dropShadow, false, 0);
            ScreenRectangle b = prepared.bounds();
            if (b != null) {
                b = b.transformMaxBounds(pose);
                bounds = scissor != null ? scissor.intersection(b) : b;
            }
        }
        return prepared;
    }

    @Override
    public ScreenRectangle bounds() {
        ensurePrepared();
        return bounds;
    }
}
