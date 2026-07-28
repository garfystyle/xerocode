package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class Value {
    public static final String TEXT = "text", NUMBER = "number", LOCATION = "location",
            VECTOR = "vector", SOUND = "sound", PARTICLE = "particle", POTION = "potion",
            GAME_VALUE = "game_value", VARIABLE = "variable", PARAMETER = "parameter",
            ARRAY = "array", MAP = "map", ITEM = "item";

    public static final String SINGULAR = "singular", PLURAL = "plural", ENUM = "enum";

    public static final class Elem {
        public String name;
        public String icon = "";
        public String display = "";
        public Elem(String name) { this.name = name; }
        public Elem(String name, String icon) { this.name = name; this.icon = icon; }
        public Elem copy() {
            Elem e = new Elem(name, icon);
            e.display = display;
            return e;
        }
    }

    public String type;

    public String text = "";
    public String parsing = "legacy";

    public double number;

    public double x, y, z, yaw, pitch;

    public String name = "";
    public String scope = "game";
    public String valueType = "any";
    public String typeKey = SINGULAR;
    public boolean required = true;
    public boolean ignoreEmpty = true;
    public final List<Elem> elements = new ArrayList<>();
    public String defaultElement = "";
    public String slotsRaw = "", descriptionSlotsRaw = "";
    public String paramDesc = "", paramDefault = "";

    public String gameValue = "";
    public String selection = "default";
    public String selectionRaw = "";

    public String sound = "";
    public String source = "MASTER";
    public double volume = 1, pitch2 = 1;
    public String variation = "";

    public String particle = "";
    public int count = 1;
    public double spread1, spread2;
    public double mx, my, mz;
    public int color = 0xFFA500, toColor = 0xFFFFFF;
    public double size = 1;
    public String material = "minecraft:stone";

    public String potion = "";
    public int duration = -1, amplifier = 0;

    public String itemId = "";
    public int itemCount = 1;
    public String itemName = "";
    public String itemParsing = "legacy";
    public final List<String> lore = new ArrayList<>();
    public final List<Ench> enchants = new ArrayList<>();
    public boolean unbreakable;
    public int itemDamage;
    public int modelData = -1;
    public int glint;
    public boolean hideTooltip;
    public final List<String> hidden = new ArrayList<>();
    public String components = "";
    public String itemRaw = "";
    public int itemRawHash;

    public JsonObject unknownRaw;

    public static final class Ench {
        public String id;
        public int level;
        public Ench(String id, int level) { this.id = id; this.level = level; }
    }

    public static final int ARRAY_MAX = 45, MAP_MAX = 20;

    public final List<Value> items = new ArrayList<>();
    public final List<Value> keys = new ArrayList<>();

    public Value(String type) { this.type = type; }

    public static Value blank() { return new Value(TEXT); }

    public static Value of(String type) { return new Value(type); }

    public static Value text(String s) {
        Value v = new Value(TEXT);
        v.text = s;
        return v;
    }

    public Value copy() {
        Value v = new Value(type);
        v.text = text; v.parsing = parsing; v.number = number;
        v.x = x; v.y = y; v.z = z; v.yaw = yaw; v.pitch = pitch;
        v.name = name; v.scope = scope; v.valueType = valueType; v.typeKey = typeKey;
        v.required = required; v.gameValue = gameValue; v.selection = selection;
        v.selectionRaw = selectionRaw;
        v.ignoreEmpty = ignoreEmpty; v.defaultElement = defaultElement;
        v.slotsRaw = slotsRaw; v.descriptionSlotsRaw = descriptionSlotsRaw;
        v.paramDesc = paramDesc; v.paramDefault = paramDefault;
        for (Elem e : elements) v.elements.add(e.copy());
        v.sound = sound; v.source = source; v.volume = volume; v.pitch2 = pitch2;
        v.variation = variation;
        v.particle = particle; v.count = count; v.spread1 = spread1; v.spread2 = spread2;
        v.mx = mx; v.my = my; v.mz = mz; v.color = color; v.toColor = toColor; v.size = size;
        v.material = material;
        v.potion = potion; v.duration = duration; v.amplifier = amplifier;
        v.itemId = itemId; v.itemCount = itemCount; v.itemName = itemName;
        v.itemParsing = itemParsing; v.unbreakable = unbreakable; v.itemDamage = itemDamage;
        v.modelData = modelData; v.glint = glint; v.hideTooltip = hideTooltip;
        v.components = components;
        v.itemRaw = itemRaw; v.itemRawHash = itemRawHash;
        if (unknownRaw != null) v.unknownRaw = unknownRaw.deepCopy();
        v.lore.addAll(lore);
        v.hidden.addAll(hidden);
        for (Ench e : enchants) v.enchants.add(new Ench(e.id, e.level));
        for (Value it : items) v.items.add(it.copy());
        for (Value k : keys) v.keys.add(k.copy());
        return v;
    }

    public int hash() {
        int h = type.hashCode();
        if (unknownRaw != null) h = h * 31 + unknownRaw.hashCode();
        h = h * 31 + text.hashCode();
        h = h * 31 + parsing.hashCode();
        h = h * 31 + Double.hashCode(number);
        h = h * 31 + Double.hashCode(x);
        h = h * 31 + Double.hashCode(y);
        h = h * 31 + Double.hashCode(z);
        h = h * 31 + Double.hashCode(yaw);
        h = h * 31 + Double.hashCode(pitch);
        h = h * 31 + name.hashCode();
        h = h * 31 + scope.hashCode();
        h = h * 31 + valueType.hashCode();
        h = h * 31 + typeKey.hashCode();
        h = h * 31 + (required ? 1 : 0) + (ignoreEmpty ? 2 : 0);
        h = h * 31 + defaultElement.hashCode();
        h = h * 31 + paramDesc.hashCode();
        h = h * 31 + paramDefault.hashCode();
        for (Elem e : elements) h = h * 31 + e.name.hashCode() * 31 + e.display.hashCode();
        h = h * 31 + gameValue.hashCode();
        h = h * 31 + selection.hashCode();
        h = h * 31 + selectionRaw.hashCode();
        h = h * 31 + sound.hashCode();
        h = h * 31 + source.hashCode();
        h = h * 31 + Double.hashCode(volume);
        h = h * 31 + Double.hashCode(pitch2);
        h = h * 31 + particle.hashCode();
        h = h * 31 + count;
        h = h * 31 + Double.hashCode(spread1);
        h = h * 31 + Double.hashCode(spread2);
        h = h * 31 + Double.hashCode(mx);
        h = h * 31 + Double.hashCode(my);
        h = h * 31 + Double.hashCode(mz);
        h = h * 31 + color;
        h = h * 31 + Double.hashCode(size);
        h = h * 31 + material.hashCode();
        h = h * 31 + potion.hashCode();
        h = h * 31 + duration;
        h = h * 31 + amplifier;
        h = h * 31 + itemId.hashCode();
        h = h * 31 + itemCount;
        h = h * 31 + itemName.hashCode();
        h = h * 31 + itemParsing.hashCode();
        h = h * 31 + lore.hashCode();
        for (Ench e : enchants) h = h * 31 + e.id.hashCode() * 31 + e.level;
        h = h * 31 + (unbreakable ? 1 : 0) + (hideTooltip ? 2 : 0) + glint * 4;
        h = h * 31 + itemDamage;
        h = h * 31 + modelData;
        h = h * 31 + hidden.hashCode();
        h = h * 31 + components.hashCode();
        for (Value it : items) h = h * 31 + it.hash();
        for (Value k : keys) h = h * 31 + k.hash();
        return h;
    }

    public boolean isBlank() {
        return switch (type) {
            case TEXT -> text.isEmpty();
            case VARIABLE, PARAMETER -> name.isBlank();
            case GAME_VALUE -> gameValue.isEmpty();
            case SOUND -> sound.isEmpty();
            case PARTICLE -> particle.isEmpty();
            case POTION -> potion.isEmpty();
            case ITEM -> itemId.isEmpty();
            case ARRAY -> filled() == 0;
            case MAP -> filled() == 0 && keys.stream().noneMatch(k -> !k.isBlank());
            default -> false;
        };
    }

    public int filled() {
        int n = 0;
        for (Value it : items) if (!it.isBlank()) n++;
        return n;
    }

    public static String num(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
            return String.valueOf((long) d);
        return String.valueOf(d);
    }

    public String label() {
        return switch (type) {
            case TEXT -> text.isEmpty() ? "пустой текст" : text;
            case NUMBER -> num(number);
            case LOCATION -> num(x) + " " + num(y) + " " + num(z)
                    + (yaw == 0 && pitch == 0 ? "" : " · " + num(yaw) + "/" + num(pitch));
            case VECTOR -> num(x) + " " + num(y) + " " + num(z);
            case VARIABLE -> name.isBlank() ? "без имени" : name;
            case PARAMETER -> name.isBlank() ? "без имени" : name;
            case GAME_VALUE -> Values.gameValueName(gameValue);
            case SOUND -> sound.isEmpty() ? "звук не выбран" : Pickers.soundName(sound);
            case PARTICLE -> particle.isEmpty() ? "частица не выбрана" : Pickers.particleName(particle);
            case POTION -> potion.isEmpty() ? "эффект не выбран" : Pickers.potionName(potion);
            case ITEM -> itemId.isEmpty() ? "предмет не выбран" : Stacks.plainName(this);
            case ARRAY -> "список";
            case MAP -> "словарь";
            default -> Values.kindName(type);
        };
    }

    public String note() {
        return switch (type) {
            case VARIABLE -> Values.scope(scope).name();
            case PARAMETER -> paramNote();
            case GAME_VALUE -> "default".equals(selection) ? "" : Values.selectorName(selection);
            case TEXT -> "legacy".equals(parsing) ? "" : Values.parsingName(parsing);
            case SOUND -> num(volume) + "/" + num(pitch2);
            case PARTICLE -> count == 1 ? "" : "×" + count;
            case POTION -> (amplifier > 0 ? "ур. " + (amplifier + 1) : "")
                    + (duration >= 0 ? (amplifier > 0 ? " · " : "") + duration + "т" : "");
            case ITEM -> itemCount > 1 ? "×" + itemCount : "";
            case ARRAY -> items.isEmpty() ? "" : "×" + filled();
            case MAP -> keys.isEmpty() ? "" : "×" + keys.size();
            default -> "";
        };
    }

    public int color() {
        if (VARIABLE.equals(type)) return Values.scope(scope).color();
        return Values.color(type);
    }

    public String paramNote() {
        if (ENUM.equals(typeKey)) return elements.isEmpty() ? "маркер" : "маркер ×" + elements.size();
        return Values.paramTypeName(valueType) + (PLURAL.equals(typeKey) ? "[]" : "")
                + (required ? "" : "*");
    }

    private static int one(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c) || (c == '-' && digits.isEmpty())) digits.append(c);
            else if (!digits.isEmpty()) break;
        }
        try { return Integer.parseInt(digits.toString()); } catch (RuntimeException e) { return fallback; }
    }

    private static String list(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.startsWith("[") ? raw : "[" + raw + "]";
    }

    private void readElements(String raw) {
        elements.clear();
        if (raw == null || raw.isBlank()) return;
        try {
            for (JsonElement e : JsonParser.parseString(raw).getAsJsonArray()) {
                JsonObject o = e.isJsonObject() ? e.getAsJsonObject()
                        : JsonParser.parseString(e.getAsString()).getAsJsonObject();
                Elem elem = new Elem(o.has("name") ? o.get("name").getAsString() : "",
                        o.has("icon") ? o.get("icon").getAsString() : "");
                if (o.has("display_name") && o.get("display_name").isJsonObject())
                    elem.display = o.get("display_name").toString();
                elements.add(elem);
            }
        } catch (RuntimeException ignored) {
            elements.clear();
        }
    }

    private String elementsJson() {
        JsonArray arr = new JsonArray();
        for (Elem e : elements) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            o.add("display_name", Localized.box(
                    e.display.isBlank() ? Localized.EMPTY : e.display));
            if (!e.icon.isEmpty()) o.addProperty("icon", e.icon);
            arr.add(o.toString());
        }
        return arr.toString();
    }

    public JsonObject toJson() {
        if (unknownRaw != null) return unknownRaw.deepCopy();
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        switch (type) {
            case TEXT -> { o.addProperty("text", text); o.addProperty("parsing", parsing); }
            case NUMBER -> o.addProperty("number", number);
            case LOCATION -> {
                o.addProperty("x", x); o.addProperty("y", y); o.addProperty("z", z);
                o.addProperty("yaw", yaw); o.addProperty("pitch", pitch);
            }
            case VECTOR -> { o.addProperty("x", x); o.addProperty("y", y); o.addProperty("z", z); }
            case VARIABLE -> { o.addProperty("variable", name); o.addProperty("scope", scope); }
            case GAME_VALUE -> {
                o.addProperty("game_value", gameValue);
                o.addProperty("selection", selectionRaw.isEmpty()
                        ? "{\"type\":\"" + selection + "\"}" : selectionRaw);
            }
            case PARAMETER -> {
                o.addProperty("type_key", typeKey);
                o.addProperty("description",
                        paramDesc.isBlank() ? Localized.EMPTY : paramDesc);
                o.addProperty("name", name);
                switch (typeKey) {
                    case PLURAL -> {
                        o.addProperty("value_type", valueType);
                        o.addProperty("is_required", String.valueOf(required));
                        o.addProperty("ignore_empty_values", String.valueOf(ignoreEmpty));
                        o.addProperty("default_value", paramDefault.isBlank()
                                ? "{\"type\":\"array\",\"values\":[]}" : paramDefault);
                        o.addProperty("slots", list(slotsRaw, "[0]"));
                        o.addProperty("description_slots", list(descriptionSlotsRaw, "[]"));
                    }
                    case ENUM -> {
                        o.addProperty("slot", one(slotsRaw, 0));
                        o.addProperty("elements", elementsJson());
                        o.addProperty("default_element", defaultElement);
                    }
                    default -> {
                        o.addProperty("value_type", valueType);
                        o.addProperty("is_required", String.valueOf(required));
                        o.addProperty("default_value",
                                paramDefault.isBlank() ? "{}" : paramDefault);
                        o.addProperty("slot", one(slotsRaw, 0));
                        o.addProperty("description_slot", one(descriptionSlotsRaw, -1));
                    }
                }
            }
            case SOUND -> {
                o.addProperty("sound", sound);
                o.addProperty("source", source);
                o.addProperty("volume", volume);
                o.addProperty("pitch", pitch2);
                o.addProperty("variation", variation);
            }
            case PARTICLE -> {
                o.addProperty("particle_type", particle);
                o.addProperty("count", count);
                o.addProperty("first_spread", spread1);
                o.addProperty("second_spread", spread2);
                Pickers.Entry e = Pickers.particle(particle);
                if (e != null && e.has(Pickers.MOTION)) {
                    o.addProperty("x_motion", mx);
                    o.addProperty("y_motion", my);
                    o.addProperty("z_motion", mz);
                }
                if (e != null && e.has(Pickers.COLOR))
                    o.addProperty("color", 0xFF000000 | (color & 0xFFFFFF));
                if (e != null && e.has(Pickers.TO_COLOR))
                    o.addProperty("to_color", 0xFF000000 | (toColor & 0xFFFFFF));
                if (e != null && e.has(Pickers.SIZE)) o.addProperty("size", size);
                if (e != null && e.has(Pickers.MATERIAL)) o.addProperty("material", material);
            }
            case POTION -> {
                o.addProperty("potion", potion);
                o.addProperty("duration", duration);
                o.addProperty("amplifier", amplifier);
            }
            case ITEM -> {
                o.addProperty("item", itemId);
                o.addProperty("item_count", itemCount);
                if (!itemName.isEmpty()) {
                    o.addProperty("item_name", itemName);
                    o.addProperty("item_parsing", itemParsing);
                }
                if (!lore.isEmpty()) {
                    JsonArray a = new JsonArray();
                    for (String line : lore) a.add(line);
                    o.add("lore", a);
                }
                if (!enchants.isEmpty()) {
                    JsonArray a = new JsonArray();
                    for (Ench e : enchants) {
                        JsonObject eo = new JsonObject();
                        eo.addProperty("id", e.id);
                        eo.addProperty("lvl", e.level);
                        a.add(eo);
                    }
                    o.add("enchantments", a);
                }
                if (unbreakable) o.addProperty("unbreakable", true);
                if (itemDamage > 0) o.addProperty("item_damage", itemDamage);
                if (modelData >= 0) o.addProperty("model_data", modelData);
                if (glint != 0) o.addProperty("glint", glint);
                if (hideTooltip) o.addProperty("hide_tooltip", true);
                if (!itemRaw.isEmpty()) {
                    o.addProperty("item_raw", itemRaw);
                    o.addProperty("item_raw_hash", itemRawHash);
                }
                if (!hidden.isEmpty()) {
                    JsonArray a = new JsonArray();
                    for (String id : hidden) a.add(id);
                    o.add("hidden", a);
                }
                if (!components.isEmpty()) o.addProperty("components", components);
            }
            case ARRAY -> {
                JsonArray arr = new JsonArray();
                int last = -1;
                for (int i = 0; i < items.size(); i++) if (!items.get(i).isBlank()) last = i;
                for (int i = 0; i <= last; i++) {
                    Value it = items.get(i);
                    arr.add(it.isBlank() ? new JsonObject() : it.toJson());
                }
                o.add("values", arr);
            }
            case MAP -> {
                JsonObject m = new JsonObject();
                for (int i = 0; i < keys.size(); i++) {
                    Value k = keys.get(i);
                    Value val = i < items.size() ? items.get(i) : blank();
                    m.add(i + "_" + (k.isBlank() ? "{}" : k.toJson().toString()),
                            val.isBlank() ? new JsonObject() : val.toJson());
                }
                o.add("values", m);
            }
            default -> { }
        }
        return o;
    }

    private static final List<String> KINDS = List.of(TEXT, NUMBER, LOCATION, VECTOR, SOUND,
            PARTICLE, POTION, GAME_VALUE, VARIABLE, PARAMETER, ARRAY, MAP, ITEM);

    public static Value fromJson(JsonObject o) {
        Value v = new Value(o.has("type") ? o.get("type").getAsString() : TEXT);
        if (!KINDS.contains(v.type)) {
            v.unknownRaw = o.deepCopy();
            return v;
        }
        if (o.has("text")) v.text = o.get("text").getAsString();
        if (o.has("parsing")) v.parsing = o.get("parsing").getAsString();
        if (o.has("number")) v.number = o.get("number").getAsDouble();
        if (o.has("x")) v.x = o.get("x").getAsDouble();
        if (o.has("y")) v.y = o.get("y").getAsDouble();
        if (o.has("z")) v.z = o.get("z").getAsDouble();
        if (o.has("yaw")) v.yaw = o.get("yaw").getAsDouble();
        if (o.has("variable")) v.name = o.get("variable").getAsString();
        if (o.has("name")) v.name = o.get("name").getAsString();
        if (o.has("scope")) v.scope = o.get("scope").getAsString();
        if (o.has("value_type")) v.valueType = o.get("value_type").getAsString();
        if (o.has("type_key")) v.typeKey = o.get("type_key").getAsString();
        if (o.has("is_required")) v.required = Boolean.parseBoolean(o.get("is_required").getAsString());
        if (o.has("ignore_empty_values"))
            v.ignoreEmpty = Boolean.parseBoolean(o.get("ignore_empty_values").getAsString());
        if (o.has("default_element")) v.defaultElement = o.get("default_element").getAsString();
        if (PARAMETER.equals(v.type)) {
            if (o.has("description")) v.paramDesc = o.get("description").getAsString();
            if (o.has("default_value")) v.paramDefault = o.get("default_value").getAsString();
        }
        if (o.has("slots")) v.slotsRaw = o.get("slots").getAsString();
        else if (o.has("slot")) v.slotsRaw = o.get("slot").getAsString();
        if (o.has("description_slots")) v.descriptionSlotsRaw = o.get("description_slots").getAsString();
        else if (o.has("description_slot")) v.descriptionSlotsRaw = o.get("description_slot").getAsString();
        if (o.has("elements")) v.readElements(o.get("elements").getAsString());
        if (o.has("game_value")) v.gameValue = o.get("game_value").getAsString();
        if (o.has("sound")) v.sound = o.get("sound").getAsString();
        if (o.has("source")) v.source = o.get("source").getAsString();
        if (o.has("volume")) v.volume = o.get("volume").getAsDouble();
        if (o.has("variation")) v.variation = o.get("variation").getAsString();
        if (o.has("particle_type")) v.particle = o.get("particle_type").getAsString();
        if (o.has("count")) v.count = o.get("count").getAsInt();
        if (o.has("first_spread")) v.spread1 = o.get("first_spread").getAsDouble();
        if (o.has("second_spread")) v.spread2 = o.get("second_spread").getAsDouble();
        if (o.has("x_motion")) v.mx = o.get("x_motion").getAsDouble();
        if (o.has("y_motion")) v.my = o.get("y_motion").getAsDouble();
        if (o.has("z_motion")) v.mz = o.get("z_motion").getAsDouble();
        if (o.has("color")) v.color = o.get("color").getAsInt() & 0xFFFFFF;
        if (o.has("to_color")) v.toColor = o.get("to_color").getAsInt() & 0xFFFFFF;
        if (o.has("size")) v.size = o.get("size").getAsDouble();
        if (o.has("material")) v.material = o.get("material").getAsString();
        if (o.has("potion")) v.potion = o.get("potion").getAsString();
        if (o.has("duration")) v.duration = o.get("duration").getAsInt();
        if (o.has("amplifier")) v.amplifier = o.get("amplifier").getAsInt();
        if (o.has("item")) v.itemId = o.get("item").getAsString();
        if (o.has("item_count")) v.itemCount = Math.max(1, o.get("item_count").getAsInt());
        if (o.has("item_name")) v.itemName = o.get("item_name").getAsString();
        if (o.has("item_parsing")) v.itemParsing = o.get("item_parsing").getAsString();
        if (o.has("lore") && o.get("lore").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("lore")) v.lore.add(e.getAsString());
        if (o.has("enchantments") && o.get("enchantments").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("enchantments")) {
                JsonObject eo = e.getAsJsonObject();
                v.enchants.add(new Ench(eo.get("id").getAsString(),
                        eo.has("lvl") ? eo.get("lvl").getAsInt() : 1));
            }
        if (o.has("unbreakable")) v.unbreakable = o.get("unbreakable").getAsBoolean();
        if (o.has("item_damage")) v.itemDamage = o.get("item_damage").getAsInt();
        if (o.has("model_data")) v.modelData = o.get("model_data").getAsInt();
        if (o.has("glint")) v.glint = o.get("glint").getAsInt();
        if (o.has("hide_tooltip")) v.hideTooltip = o.get("hide_tooltip").getAsBoolean();
        if (o.has("hidden") && o.get("hidden").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("hidden")) v.hidden.add(e.getAsString());
        if (o.has("components")) v.components = o.get("components").getAsString();
        if (o.has("item_raw")) v.itemRaw = o.get("item_raw").getAsString();
        if (o.has("item_raw_hash")) v.itemRawHash = o.get("item_raw_hash").getAsInt();
        if (o.has("pitch")) {
            if (SOUND.equals(v.type)) v.pitch2 = o.get("pitch").getAsDouble();
            else v.pitch = o.get("pitch").getAsDouble();
        }
        if (o.has("selection")) {
            String s = o.get("selection").getAsString();
            int i = s.indexOf("\"type\":\"");
            int j = i < 0 ? -1 : s.indexOf('"', i + 8);
            if (j > 0) v.selection = s.substring(i + 8, j);
            else v.selectionRaw = s;
        }
        if (o.has("values") && ARRAY.equals(v.type) && o.get("values").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("values")) v.items.add(cell(e));
        if (o.has("values") && MAP.equals(v.type) && o.get("values").isJsonObject()) {
            JsonObject m = o.getAsJsonObject("values");
            List<String> names = new ArrayList<>(m.keySet());
            names.sort((a, b) -> Integer.compare(indexOf(a), indexOf(b)));
            for (String field : names) {
                int cut = field.indexOf('_');
                String keyJson = cut < 0 ? field : field.substring(cut + 1);
                v.keys.add(cell(parse(keyJson)));
                v.items.add(cell(m.get(field)));
            }
        }
        return v;
    }

    private static int indexOf(String field) {
        int cut = field.indexOf('_');
        try {
            return cut <= 0 ? -1 : Integer.parseInt(field.substring(0, cut));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static JsonElement parse(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static Value cell(JsonElement e) {
        if (e == null || !e.isJsonObject()) return blank();
        JsonObject o = e.getAsJsonObject();
        return o.has("type") ? fromJson(o) : blank();
    }

    public static Value fromLegacy(String s, String argType) {
        if ("Число".equals(argType)) {
            try {
                Value v = new Value(NUMBER);
                v.number = Double.parseDouble(s.trim().replace(',', '.'));
                return v;
            } catch (NumberFormatException ignored) { }
        }
        return text(s);
    }
}
