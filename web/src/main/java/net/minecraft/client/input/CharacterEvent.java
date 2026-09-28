package net.minecraft.client.input;

public record CharacterEvent(int codepoint) {
    public String codepointAsString() { return new String(Character.toChars(codepoint)); }

    public boolean isAllowedChatCharacter() {
        return codepoint != 167 && codepoint >= 32 && codepoint != 127;
    }
}
