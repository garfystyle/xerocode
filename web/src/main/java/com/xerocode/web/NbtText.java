package com.xerocode.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public final class NbtText {
    private NbtText() {}

    public static Component component(Nbt.Tag tag) {
        if (tag == null) return null;
        try {
            return ComponentSerialization.fromJson(json(tag));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static JsonElement json(Nbt.Tag tag) {
        if (tag == null) return JsonNull.INSTANCE;
        if (tag instanceof Nbt.Str s) return new JsonPrimitive(s.value);
        if (tag instanceof Nbt.Num n) {
            if (n.id() == Nbt.BYTE && (n.asInt() == 0 || n.asInt() == 1)) return new JsonPrimitive(n.asInt() == 1);
            return new JsonPrimitive(n.value);
        }
        if (tag instanceof Nbt.ListTag l) {
            JsonArray a = new JsonArray();
            for (Nbt.Tag t : l.items) a.add(json(t));
            return a;
        }
        if (tag instanceof Nbt.Compound c) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Nbt.Tag> e : c.map.entrySet()) o.add(e.getKey(), json(e.getValue()));
            return o;
        }
        if (tag instanceof Nbt.Arr arr) {
            JsonArray a = new JsonArray();
            for (long v : arr.values) a.add(v);
            return a;
        }
        return JsonNull.INSTANCE;
    }

    public static Nbt.Tag nbt(JsonElement el) {
        if (el == null || el.isJsonNull()) return new Nbt.Str("");
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return Nbt.ofByte(p.getAsBoolean() ? 1 : 0);
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE) return Nbt.ofInt((int) d);
                return new Nbt.Num(Nbt.DOUBLE, d);
            }
            return new Nbt.Str(p.getAsString());
        }
        if (el.isJsonArray()) {
            Nbt.ListTag l = new Nbt.ListTag();
            for (JsonElement e : el.getAsJsonArray()) l.items.add(nbt(e));
            return l;
        }
        Nbt.Compound c = new Nbt.Compound();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) c.put(e.getKey(), nbt(e.getValue()));
        return c;
    }

    public static Nbt.Tag of(Component c) {
        return nbt(ComponentSerialization.toJson(c));
    }
}
