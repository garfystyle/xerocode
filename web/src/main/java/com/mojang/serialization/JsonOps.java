package com.mojang.serialization;

import com.google.gson.JsonElement;

public final class JsonOps implements DynamicOps<JsonElement> {
    public static final JsonOps INSTANCE = new JsonOps();

    private JsonOps() {}
}
