package net.minecraft.client.input;

public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers {
    @Override
    public int input() { return button; }
}
