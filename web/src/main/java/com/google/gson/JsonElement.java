package com.google.gson;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class JsonElement {
    public abstract JsonElement deepCopy();

    public boolean isJsonArray() { return this instanceof JsonArray; }
    public boolean isJsonObject() { return this instanceof JsonObject; }
    public boolean isJsonPrimitive() { return this instanceof JsonPrimitive; }
    public boolean isJsonNull() { return this instanceof JsonNull; }

    public JsonObject getAsJsonObject() {
        if (isJsonObject()) return (JsonObject) this;
        throw new IllegalStateException("Not a JSON Object: " + this);
    }

    public JsonArray getAsJsonArray() {
        if (isJsonArray()) return (JsonArray) this;
        throw new IllegalStateException("Not a JSON Array: " + this);
    }

    public JsonPrimitive getAsJsonPrimitive() {
        if (isJsonPrimitive()) return (JsonPrimitive) this;
        throw new IllegalStateException("Not a JSON Primitive: " + this);
    }

    public JsonNull getAsJsonNull() {
        if (isJsonNull()) return (JsonNull) this;
        throw new IllegalStateException("Not a JSON Null: " + this);
    }

    public boolean getAsBoolean() { throw unsupported(); }
    public Number getAsNumber() { throw unsupported(); }
    public String getAsString() { throw unsupported(); }
    public double getAsDouble() { throw unsupported(); }
    public float getAsFloat() { throw unsupported(); }
    public long getAsLong() { throw unsupported(); }
    public int getAsInt() { throw unsupported(); }
    public byte getAsByte() { throw unsupported(); }
    public short getAsShort() { throw unsupported(); }
    public BigDecimal getAsBigDecimal() { throw unsupported(); }
    public BigInteger getAsBigInteger() { throw unsupported(); }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(getClass().getSimpleName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        JsonWriter.write(this, sb);
        return sb.toString();
    }
}
