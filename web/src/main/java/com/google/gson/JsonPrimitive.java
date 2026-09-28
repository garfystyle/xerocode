package com.google.gson;

import com.google.gson.internal.LazilyParsedNumber;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public final class JsonPrimitive extends JsonElement {
    private final Object value;

    public JsonPrimitive(Boolean bool) { value = Objects.requireNonNull(bool); }
    public JsonPrimitive(Number number) { value = Objects.requireNonNull(number); }
    public JsonPrimitive(String string) { value = Objects.requireNonNull(string); }
    public JsonPrimitive(Character c) { value = Objects.requireNonNull(c).toString(); }

    @Override
    public JsonPrimitive deepCopy() { return this; }

    public boolean isBoolean() { return value instanceof Boolean; }
    public boolean isNumber() { return value instanceof Number; }
    public boolean isString() { return value instanceof String; }

    Object raw() { return value; }

    @Override
    public boolean getAsBoolean() {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(getAsString());
    }

    @Override
    public Number getAsNumber() {
        if (value instanceof Number n) return n;
        if (value instanceof String s) return new LazilyParsedNumber(s);
        throw new UnsupportedOperationException("Primitive is neither a number nor a string");
    }

    @Override
    public String getAsString() {
        if (value instanceof String s) return s;
        if (value instanceof Number n) return n.toString();
        return value.toString();
    }

    @Override public double getAsDouble() { return isNumber() ? getAsNumber().doubleValue() : Double.parseDouble(getAsString()); }
    @Override public float getAsFloat() { return isNumber() ? getAsNumber().floatValue() : Float.parseFloat(getAsString()); }
    @Override public long getAsLong() { return isNumber() ? getAsNumber().longValue() : Long.parseLong(getAsString()); }
    @Override public int getAsInt() { return isNumber() ? getAsNumber().intValue() : Integer.parseInt(getAsString()); }
    @Override public short getAsShort() { return isNumber() ? getAsNumber().shortValue() : Short.parseShort(getAsString()); }
    @Override public byte getAsByte() { return isNumber() ? getAsNumber().byteValue() : Byte.parseByte(getAsString()); }

    @Override
    public BigDecimal getAsBigDecimal() {
        return value instanceof BigDecimal b ? b : new BigDecimal(getAsString());
    }

    @Override
    public BigInteger getAsBigInteger() {
        return value instanceof BigInteger b ? b : new BigInteger(getAsString());
    }

    private boolean integral() {
        return value instanceof BigInteger || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte;
    }

    @Override
    public int hashCode() {
        if (value instanceof Number n) {
            long v = integral() ? n.longValue() : Double.doubleToLongBits(n.doubleValue());
            return (int) (v ^ (v >>> 32));
        }
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonPrimitive other)) return false;
        if (value instanceof Number a && other.value instanceof Number b) {
            if (integral() && other.integral()) return a.longValue() == b.longValue();
            double x = a.doubleValue(), y = b.doubleValue();
            return x == y || (Double.isNaN(x) && Double.isNaN(y));
        }
        return value.equals(other.value);
    }
}
