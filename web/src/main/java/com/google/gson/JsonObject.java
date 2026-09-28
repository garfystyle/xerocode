package com.google.gson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JsonObject extends JsonElement {
    private final LinkedHashMap<String, JsonElement> members = new LinkedHashMap<>();

    @Override
    public JsonObject deepCopy() {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> e : members.entrySet()) result.add(e.getKey(), e.getValue().deepCopy());
        return result;
    }

    public void add(String property, JsonElement value) {
        members.put(Objects.requireNonNull(property), value == null ? JsonNull.INSTANCE : value);
    }

    public JsonElement remove(String property) { return members.remove(property); }

    public void addProperty(String property, String value) { add(property, value == null ? null : new JsonPrimitive(value)); }
    public void addProperty(String property, Number value) { add(property, value == null ? null : new JsonPrimitive(value)); }
    public void addProperty(String property, Boolean value) { add(property, value == null ? null : new JsonPrimitive(value)); }
    public void addProperty(String property, Character value) { add(property, value == null ? null : new JsonPrimitive(value)); }

    public Set<Map.Entry<String, JsonElement>> entrySet() { return members.entrySet(); }
    public Set<String> keySet() { return members.keySet(); }
    public int size() { return members.size(); }
    public boolean isEmpty() { return members.isEmpty(); }
    public boolean has(String memberName) { return members.containsKey(memberName); }
    public JsonElement get(String memberName) { return members.get(memberName); }
    public JsonPrimitive getAsJsonPrimitive(String memberName) { return (JsonPrimitive) members.get(memberName); }
    public JsonArray getAsJsonArray(String memberName) { return (JsonArray) members.get(memberName); }
    public JsonObject getAsJsonObject(String memberName) { return (JsonObject) members.get(memberName); }
    public Map<String, JsonElement> asMap() { return members; }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof JsonObject other && other.members.equals(members));
    }

    @Override
    public int hashCode() { return members.hashCode(); }
}
