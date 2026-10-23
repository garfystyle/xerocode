package com.xerocode.ui;

import com.google.gson.JsonArray;
import com.xerocode.Functions;
import com.xerocode.Importer;
import com.xerocode.Modules;
import com.xerocode.Script;
import com.xerocode.XeroCode;
import net.minecraft.client.gui.Font;

public final class ModuleCode {
    private final Modules.Module module;
    private final Font tr;

    private JsonArray handlers;
    private Layout layout;
    private boolean asking, broken, parsed;
    private int blocks, lines;
    private String trouble = "";

    public ModuleCode(Modules.Module module, Font tr) {
        this.module = module;
        this.tr = tr;
    }

    public JsonArray handlers() { return handlers; }

    public boolean ready() { return handlers != null && !broken; }

    public boolean asking() { return asking; }

    public boolean broken() { return broken; }

    public String trouble() { return trouble; }

    public String size() {
        if (layout == null) return "";
        return Ui.plural(blocks, "блок", "блока", "блоков") + " · "
                + Ui.plural(lines, "строка", "строки", "строк");
    }

    public void forget() {
        layout = null;
        parsed = false;
    }

    public void adopt(JsonArray got) {
        handlers = got;
        forget();
        if (got == null) fetch();
    }

    public void again() {
        broken = false;
        trouble = "";
        handlers = null;
        forget();
        fetch();
    }

    public void fetch() {
        if (asking || handlers != null || !module.open) return;
        asking = true;
        Modules.code(module.id, got -> {
            asking = false;
            handlers = got;
            forget();
        }, said -> {
            asking = false;
            broken = true;
            trouble = said;
        });
    }

    public Layout layout() {
        if (parsed || handlers == null) return layout;
        parsed = true;
        try {
            Script tmp = new Script();
            Importer.importInto(tmp, handlers.deepCopy(), tr);
            Functions.rebuild(tmp);
            layout = Layout.of(tmp, tr);
            blocks = Script.blocksIn(tmp.roots);
            lines = tmp.roots.size();
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] код модуля {} не разобрался", module.id, e);
            layout = null;
            broken = true;
            trouble = "код модуля не разобрался";
        }
        return layout;
    }
}
