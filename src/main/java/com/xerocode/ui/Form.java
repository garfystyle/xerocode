package com.xerocode.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;

final class Form {
    record Part(String id, String caption, int dy, int h) {}

    record Row(String id, int from, String[] caps) {
        int count() { return caps.length; }

        int colW(int width) { return (width - (count() - 1) * 4) / count(); }

        int colX(int left, int width, int i) { return left + i * (colW(width) + 4); }

        boolean owns(int field) { return field >= from && field < from + count(); }
    }

    final List<Part> parts = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    private final int capH, gapH;
    private int height;

    Form(int capH, int gapH) {
        this.capH = capH;
        this.gapH = gapH;
    }

    int height() { return height; }

    void clear() {
        parts.clear();
        rows.clear();
        height = 0;
    }

    void cap(String text) { parts.add(new Part(null, text, height, capH)); height += capH; }

    void part(String id, int h) { parts.add(new Part(id, null, height, h)); height += h; }

    void gap() { height += gapH; }

    void row(String id, int from, String[] caps) { rows.add(new Row(id, from, caps)); }

    Row row(String id) {
        for (Row r : rows) if (r.id().equals(id)) return r;
        return null;
    }

    Row rowAt(int field) {
        for (Row r : rows) if (r.owns(field)) return r;
        return null;
    }

    int dy(String id) {
        for (Part p : parts) if (id.equals(p.id())) return p.dy();
        return 0;
    }

    int h(String id) {
        for (Part p : parts) if (id.equals(p.id())) return p.h();
        return 0;
    }

    boolean has(String id) {
        for (Part p : parts) if (id.equals(p.id())) return true;
        return false;
    }

    void place(List<EditBox> fields, int left, int width, int top, int fieldH, boolean stepper) {
        if (fields.isEmpty()) return;
        for (Row r : rows) {
            int cw = r.colW(width), ry = top + dy(r.id()) + 9 + (fieldH - 12) / 2 + 2;
            for (int i = 0; i < r.count(); i++) {
                EditBox f = fields.get(r.from() + i);
                f.setX(r.colX(left, width, i) + 6);
                f.setY(ry);
                Ui.width(f, cw - 12);
            }
        }
        String single = has("input") ? "input" : has("material") ? "material" : null;
        if (single == null) return;
        EditBox f = null;
        for (int i = 0; i < fields.size() && f == null; i++)
            if (rowAt(i) == null) f = fields.get(i);
        if (f == null) return;
        int from = left + (stepper ? fieldH + 4 : 0) + 6;
        int to = left + width - (stepper ? fieldH + 4 : 0) - 6;
        f.setX(from);
        f.setY(top + dy(single) + (fieldH - 12) / 2 + 2);
        Ui.width(f, Math.max(20, to - from));
    }
}
