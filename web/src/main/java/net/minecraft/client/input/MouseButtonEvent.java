package net.minecraft.client.input;

public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
    @Override
    public int input() { return button(); }

    public int button() { return buttonInfo.button(); }

    @Override
    public int modifiers() { return buttonInfo.modifiers(); }
}
