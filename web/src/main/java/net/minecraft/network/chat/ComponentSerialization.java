package net.minecraft.network.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

public final class ComponentSerialization {
    public static final Codec<Component> CODEC = new Codec<>() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<Component> parse(DynamicOps<T> ops, T input) {
            if (ops != JsonOps.INSTANCE) return DataResult.error("только JSON");
            try {
                return DataResult.success(fromJson((JsonElement) input));
            } catch (RuntimeException e) {
                return DataResult.error(String.valueOf(e.getMessage()));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<T> encodeStart(DynamicOps<T> ops, Component input) {
            if (ops != JsonOps.INSTANCE) return DataResult.error("только JSON");
            return DataResult.success((T) toJson(input));
        }
    };

    private ComponentSerialization() {}

    public static Component fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) throw new IllegalArgumentException("пусто");
        if (el.isJsonPrimitive()) return Component.literal(el.getAsString());
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            if (a.isEmpty()) throw new IllegalArgumentException("пустой список");
            MutableComponent first = fromJson(a.get(0)).copy();
            for (int i = 1; i < a.size(); i++) first.append(fromJson(a.get(i)));
            return first;
        }
        JsonObject o = el.getAsJsonObject();
        String text;
        if (o.has("text")) text = o.get("text").getAsString();
        else if (o.has("translate")) text = o.has("fallback") ? o.get("fallback").getAsString() : o.get("translate").getAsString();
        else if (o.has("keybind")) text = o.get("keybind").getAsString();
        else if (o.has("selector")) text = o.get("selector").getAsString();
        else if (o.has("score")) text = "";
        else if (o.has("nbt")) text = o.get("nbt").getAsString();
        else if (o.has("extra")) text = "";
        else throw new IllegalArgumentException("не компонент");
        MutableComponent c = Component.literal(text);
        c.setStyle(style(o));
        if (o.has("extra") && o.get("extra").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("extra")) c.append(fromJson(e));
        return c;
    }

    private static Boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : null;
    }

    private static Style style(JsonObject o) {
        Style s = Style.EMPTY;
        if (o.has("color")) {
            TextColor c = TextColor.parseColor(o.get("color").getAsString());
            if (c == null) throw new IllegalArgumentException("цвет");
            s = s.withColor(c);
        }
        if (o.has("shadow_color")) {
            JsonElement sc = o.get("shadow_color");
            if (sc.isJsonPrimitive()) s = s.withShadowColor(sc.getAsInt());
        }
        s = s.withBold(flag(o, "bold")).withItalic(flag(o, "italic")).withUnderlined(flag(o, "underlined"))
                .withStrikethrough(flag(o, "strikethrough")).withObfuscated(flag(o, "obfuscated"));
        if (o.has("insertion")) s = s.withInsertion(o.get("insertion").getAsString());
        return s;
    }

    public static JsonElement toJson(Component c) {
        Style s = c.getStyle();
        if (s.isEmpty() && c.getSiblings().isEmpty()) return new JsonPrimitive(c.contents());
        JsonObject o = new JsonObject();
        o.addProperty("text", c.contents());
        if (!c.getSiblings().isEmpty()) {
            JsonArray extra = new JsonArray();
            for (Component sib : c.getSiblings()) extra.add(toJson(sib));
            o.add("extra", extra);
        }
        if (s.getColor() != null) o.addProperty("color", s.getColor().serialize());
        if (s.getShadowColor() != null) o.addProperty("shadow_color", s.getShadowColor());
        if (s.boldRaw() != null) o.addProperty("bold", s.boldRaw());
        if (s.italicRaw() != null) o.addProperty("italic", s.italicRaw());
        if (s.underlinedRaw() != null) o.addProperty("underlined", s.underlinedRaw());
        if (s.strikethroughRaw() != null) o.addProperty("strikethrough", s.strikethroughRaw());
        if (s.obfuscatedRaw() != null) o.addProperty("obfuscated", s.obfuscatedRaw());
        if (s.getInsertion() != null) o.addProperty("insertion", s.getInsertion());
        return o;
    }
}
