package com.google.gson;

public final class JsonNull extends JsonElement {
    public static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {}

    @Override
    public JsonNull deepCopy() { return INSTANCE; }

    @Override
    public int hashCode() { return 0x6e756c6c; }

    @Override
    public boolean equals(Object other) { return other instanceof JsonNull; }
}
