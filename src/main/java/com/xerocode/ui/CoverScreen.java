package com.xerocode.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class CoverScreen extends Screen {
    private final String label;
    private int frames;

    public CoverScreen(String label) {
        super(Text.literal(label));
        this.label = label;
    }

    private boolean escapable() { return frames > 60; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (escapable()) close();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (escapable()) close();
        return true;
    }

    @Override
    public void close() {
        com.xerocode.XeroCode.coverDismissed();
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        frames++;
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        int y = height / 2 - 4;
        Draw.textCenter(ctx, textRenderer, label, 0, y, width, width - 40, Theme.TEXT_DIM, false);
        if (escapable())
            Draw.textCenter(ctx, textRenderer, "любая клавиша — закрыть", 0, y + 14, width,
                    width - 40, Theme.TEXT_FAINT, false);
    }
}
