package com.google.gson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JsonArray extends JsonElement implements Iterable<JsonElement> {
    private final ArrayList<JsonElement> elements;

    public JsonArray() { elements = new ArrayList<>(); }
    public JsonArray(int capacity) { elements = new ArrayList<>(capacity); }

    @Override
    public JsonArray deepCopy() {
        JsonArray result = new JsonArray(elements.size());
        for (JsonElement e : elements) result.add(e.deepCopy());
        return result;
    }

    public void add(Boolean bool) { add(bool == null ? null : new JsonPrimitive(bool)); }
    public void add(Character c) { add(c == null ? null : new JsonPrimitive(c)); }
    public void add(Number number) { add(number == null ? null : new JsonPrimitive(number)); }
    public void add(String string) { add(string == null ? null : new JsonPrimitive(string)); }
    public void add(JsonElement element) { elements.add(element == null ? JsonNull.INSTANCE : element); }
    public void addAll(JsonArray array) { elements.addAll(array.elements); }

    public JsonElement set(int index, JsonElement element) {
        return elements.set(index, element == null ? JsonNull.INSTANCE : element);
    }

    public boolean remove(JsonElement element) { return elements.remove(element); }
    public JsonElement remove(int index) { return elements.remove(index); }
    public boolean contains(JsonElement element) { return elements.contains(element); }
    public int size() { return elements.size(); }
    public boolean isEmpty() { return elements.isEmpty(); }

    @Override
    public Iterator<JsonElement> iterator() { return elements.iterator(); }

    public JsonElement get(int i) { return elements.get(i); }
    public List<JsonElement> asList() { return elements; }

    private JsonElement single() {
        if (elements.size() == 1) return elements.get(0);
        throw new IllegalStateException("Array must have size 1, but has size " + elements.size());
    }

    @Override public Number getAsNumber() { return single().getAsNumber(); }
    @Override public String getAsString() { return single().getAsString(); }
    @Override public double getAsDouble() { return single().getAsDouble(); }
    @Override public BigDecimal getAsBigDecimal() { return single().getAsBigDecimal(); }
    @Override public BigInteger getAsBigInteger() { return single().getAsBigInteger(); }
    @Override public float getAsFloat() { return single().getAsFloat(); }
    @Override public long getAsLong() { return single().getAsLong(); }
    @Override public int getAsInt() { return single().getAsInt(); }
    @Override public byte getAsByte() { return single().getAsByte(); }
    @Override public short getAsShort() { return single().getAsShort(); }
    @Override public boolean getAsBoolean() { return single().getAsBoolean(); }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof JsonArray other && other.elements.equals(elements));
    }

    @Override
    public int hashCode() { return elements.hashCode(); }
}
