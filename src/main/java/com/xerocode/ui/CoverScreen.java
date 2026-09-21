package com.xerocode.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CoverScreen extends Screen {
    private final String label;
    private int frames;

    public CoverScreen(String label) {
        super(Component.literal(label));
        this.label = label;
    }

    private boolean escapable() { return frames > 60; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (escapable()) onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (escapable()) onClose();
        return true;
    }

    @Override
    public void onClose() {
        com.xerocode.XeroCode.coverDismissed();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        frames++;
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        int y = height / 2 - 4;
        Draw.textCenter(ctx, font, label, 0, y, width, width - 40, Theme.TEXT_DIM, false);
        if (escapable())
            Draw.textCenter(ctx, font, "любая клавиша — закрыть", 0, y + 14, width,
                    width - 40, Theme.TEXT_FAINT, false);
    }
}
