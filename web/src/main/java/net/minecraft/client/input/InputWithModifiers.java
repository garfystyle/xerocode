package net.minecraft.client.input;

public interface InputWithModifiers {
    int input();

    int modifiers();

    default boolean isSelection() { return input() == 257 || input() == 32 || input() == 335; }
    default boolean isConfirmation() { return input() == 257 || input() == 335; }
    default boolean isEscape() { return input() == 256; }
    default boolean isLeft() { return input() == 263; }
    default boolean isRight() { return input() == 262; }
    default boolean isUp() { return input() == 265; }
    default boolean isDown() { return input() == 264; }
    default boolean isCycleFocus() { return input() == 258; }

    default int getDigit() {
        int v = input() - 48;
        return v >= 0 && v <= 9 ? v : -1;
    }

    default boolean hasAltDown() { return (modifiers() & 4) != 0; }
    default boolean hasShiftDown() { return (modifiers() & 1) != 0; }
    default boolean hasControlDown() { return (modifiers() & 2) != 0; }
    default boolean hasControlDownWithQuirk() { return (modifiers() & 2) != 0; }
    default boolean isSelectAll() { return input() == 65 && hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown(); }
    default boolean isCopy() { return input() == 67 && hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown(); }
    default boolean isPaste() { return input() == 86 && hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown(); }
    default boolean isCut() { return input() == 88 && hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown(); }
}
