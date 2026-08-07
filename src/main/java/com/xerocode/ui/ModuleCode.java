package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Functions;
import com.xerocode.Market;
import com.xerocode.Script;
import net.minecraft.client.font.TextRenderer;

public final class ModuleCode {
    private final String id;
    private final TextRenderer tr;

    private JsonObject payload;
    private Layout layout;
    private boolean asking, broken, parsed;
    private String trouble = "";

    public ModuleCode(String id, TextRenderer tr) {
        this.id = id;
        this.tr = tr;
    }

    public JsonObject payload() { return payload; }

    public boolean ready() { return payload != null; }

    public boolean asking() { return asking; }

    public boolean broken() { return broken; }

    public String trouble() { return trouble; }

    public void forget() {
        layout = null;
        parsed = false;
    }

    public void adopt(JsonObject got) {
        payload = got;
        forget();
        if (got == null) fetch();
    }

    public void again() {
        broken = false;
        trouble = "";
        payload = null;
        forget();
        fetch();
    }

    public void fetch() {
        if (asking || payload != null) return;
        asking = true;
        Market.payload(id, got -> {
            asking = false;
            payload = got;
            forget();
        }, (said, code) -> {
            asking = false;
            broken = true;
            trouble = said;
        });
    }

    public Layout layout() {
        if (parsed || payload == null) return layout;
        parsed = true;
        try {
            Script tmp = Script.fromJson(payload);
            Functions.rebuild(tmp);
            layout = Layout.of(tmp, tr);
        } catch (Throwable e) {
            layout = null;
            broken = true;
            trouble = "код модуля не разобрался";
        }
        return layout;
    }
}
