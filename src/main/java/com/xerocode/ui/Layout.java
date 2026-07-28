package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Functions;
import com.xerocode.Mapping;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class Layout {
    public static final int PAD = 7;
    public static final int TITLE_H = 24;
    public static final int CHIP_H = 15;
    public static final int CHIP_GAP = 4;
    public static final int ROW_GAP = 3;
    public static final int NO_CHIP_PAD = 4;
    public static final int CHIP_BOTTOM_PAD = 6;
    public static final int HAT_H = 10;
    public static final int INDENT = 16;
    public static final int MOUTH_LIFT = 1;
    public static final int SEAM_LIFT = 1;
    public static final int ARM_H = 14;
    public static final int EMPTY_BODY_H = 24;
    public static final int MIN_W = 118;
    public static final int MAX_W = 300;
    public static final int CHIP_INK_X = 11;
    public static final int CHIP_ITEM_INK_X = 15;
    public static final int CHIP_MIN_W = 40;
    public static final int CHIP_MAX_W = 230;

    public static final int CARD_ID_H = 28, CARD_ICON = 20, NAME_SCALE = 2;
    public static final int DESC_H = 11, SEP_H = 7;
    public static final int NAME_MAX = 250;
    public static final int ID_MAX = 90, ID_GAP = 6;
    public static final int MARKER_PARAM_COLOR = 0x9AA6BD;

    public static final class Chip {
        public final int argIndex, settingIndex;
        public final int cell;
        public final boolean plus;
        public Value value;
        public int x, y, w;
        int row;

        public OrderedText count;
        public OrderedText note;
        public int countW, noteW;
        public OrderedText fitted;
        public ItemStack icon = ItemStack.EMPTY;

        public boolean filled;
        public int border, top, bottom, dot, dim, ink;

        Chip(int argIndex, int settingIndex, int w) {
            this(argIndex, settingIndex, -1, false, w);
        }

        Chip(int argIndex, int settingIndex, int cell, boolean plus, int w) {
            this.argIndex = argIndex; this.settingIndex = settingIndex;
            this.cell = cell; this.plus = plus; this.w = w;
        }

        public boolean condition;

        public boolean isArg() { return argIndex >= 0 && cell < 0 && !plus && !condition; }
        public boolean isCell() { return cell >= 0 && !plus; }
        public boolean isPlus() { return plus; }
        public boolean isMarker() { return settingIndex >= 0 && !plus && !condition; }
        public boolean isCondition() { return condition; }
        public int h() { return CHIP_H; }
        public boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        }
    }

    public static final class Card {
        public OrderedText name;
        public int nameX, nameY, nameW;
        public int scale = 1;
        public boolean named;
        public int idH;
        public OrderedText kind;
        public int kindX, kindY, kindW;
        public OrderedText verb;
        public int verbX, verbW;
        public OrderedText id;
        public int idX, idY, idW;
        public boolean missing;
        public final List<OrderedText> desc = new ArrayList<>();
        public int descX, descY, descW;
        public ItemStack icon = ItemStack.EMPTY;
        public int iconX, iconY, iconSize = 16;
        public int sepY;
        public int headH;

        String nameRaw, kindRaw, verbRaw, idRaw;
        final List<String> descRaws = new ArrayList<>();

        public boolean hitName(double mx, double my) {
            return name != null && mx >= (verb == null ? nameX : verbX) - 3
                    && mx < nameX + nameW + 6
                    && my >= nameY - 4 && my < nameY + 8 * scale + 4;
        }

        public boolean hitId(double mx, double my) {
            return id != null && mx >= idX - 3 && mx < idX + idW + 3
                    && my >= nameY - 4 && my < nameY + 8 * scale + 4;
        }

        public boolean hitIcon(double mx, double my) {
            return mx >= iconX - 2 && mx < iconX + iconSize + 2
                    && my >= iconY - 2 && my < iconY + iconSize + 2;
        }

        public boolean hitDesc(double mx, double my) {
            return !desc.isEmpty() && mx >= descX - 3 && mx < descX + descW + 6
                    && my >= descY - 2 && my < descY + desc.size() * DESC_H;
        }
    }

    public static final class Box {
        public final Script.Node node;
        public final List<Script.Node> owner;
        public final int index;
        public final Script.Root root;
        public final boolean nested;
        public final int x, y, hatH;
        public int w, headerH, totalH;
        public OrderedText title;
        public OrderedText target;
        public int targetX, targetW;
        public int targetSetting = -1;
        public Card card;
        public int top, bottom, border, ink;
        public boolean lightHead;
        public int coverFrom, coverTo;
        public int mouthFrom, mouthTo;
        public final List<Chip> chips = new ArrayList<>();

        Box(Script.Node node, List<Script.Node> owner, int index, Script.Root root,
            boolean nested, int x, int y) {
            this.node = node; this.owner = owner; this.index = index; this.root = root;
            this.nested = nested;
            this.x = x; this.y = y;
            this.hatH = node.isHat() ? HAT_H : 0;
        }

        public int bottom()  { return y + totalH; }
        public int armY()    { return y + totalH - ARM_H; }
        public int bodyTop() { return y + headerH - MOUTH_LIFT; }

        public boolean hitGrab(double mx, double my) {
            if (mx < x || mx >= x + w) return false;
            if (my >= y && my < y + headerH) return true;
            if (!node.wraps()) return false;
            int arm = armY();
            if (my >= arm && my < arm + ARM_H) return true;
            return mx < x + INDENT && my >= y + headerH && my < arm;
        }

        public boolean hitTarget(double mx, double my) {
            if (target == null) return false;
            int top = y + hatH + 5;
            return mx >= targetX - 3 && mx < targetX + targetW - TARGET_GAP + 3
                    && my >= top && my < top + 15;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < bottom();
        }

        public Chip chipAt(double mx, double my) {
            if (!contains(mx, my)) return null;
            for (Chip c : chips) if (c.hit(mx, my)) return c;
            return null;
        }
    }

    public final List<Box> boxes = new ArrayList<>();

    public static final class Chunk {
        public final int from, to;
        public final int x0, y0, x1, y1;
        Chunk(int from, int to, int x0, int y0, int x1, int y1) {
            this.from = from; this.to = to;
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }
        public boolean visible(double vx0, double vy0, double vx1, double vy1) {
            return x1 >= vx0 && x0 <= vx1 && y1 >= vy0 && y0 <= vy1;
        }
    }

    public final List<Chunk> chunks = new ArrayList<>();

    public static Layout of(Script script, TextRenderer tr) {
        Layout l = new Layout();
        for (Script.Root r : script.roots) {
            int from = l.boxes.size();
            l.chain(r.chain, r, false, (int) r.x, (int) r.y, tr);
            l.seal(from);
        }
        return l;
    }

    private void seal(int from) {
        if (boxes.size() == from) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = from; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            x0 = Math.min(x0, b.x);
            y0 = Math.min(y0, b.y);
            x1 = Math.max(x1, b.x + b.w);
            y1 = Math.max(y1, b.bottom());
        }
        chunks.add(new Chunk(from, boxes.size(), x0, y0, x1, y1));
    }

    public static Layout ofChain(List<Script.Node> chain, int x, int y, TextRenderer tr) {
        Layout l = new Layout();
        l.chain(chain, null, false, x, y, tr);
        return l;
    }

    public static int chainHeight(List<Script.Node> chain, TextRenderer tr) {
        if (chain.isEmpty()) return 0;
        return new Layout().chain(chain, null, false, 0, 0, tr);
    }

    private int chain(List<Script.Node> chain, Script.Root root, boolean nested,
                      int x, int y, TextRenderer tr) {
        List<Box> mine = new ArrayList<>();
        int cy = y;
        for (int i = 0; i < chain.size(); i++) {
            Script.Node n = chain.get(i);
            Box box = new Box(n, chain, i, root, nested, x, cy);
            measure(box, tr);
            boxes.add(box);
            mine.add(box);
            if (n.wraps()) {
                int bodyTop = box.bodyTop();
                int bodyEnd;
                if (n.body.isEmpty()) {
                    bodyEnd = bodyTop + EMPTY_BODY_H;
                } else {
                    int firstBody = boxes.size();
                    bodyEnd = chain(n.body, root, true, x + INDENT, bodyTop, tr);
                    Box first = boxes.get(firstBody);
                    box.mouthFrom = first.x + 1;
                    box.mouthTo = Math.min(first.x + first.w, x + box.w) - 1;
                }
                box.totalH = (bodyEnd + ARM_H) - cy;
            } else {
                box.totalH = box.headerH;
            }
            cy += box.totalH - SEAM_LIFT;
        }
        for (int i = 0; i + 1 < mine.size(); i++) {
            Box a = mine.get(i), b = mine.get(i + 1);
            a.coverFrom = a.x + 1;
            a.coverTo = a.x + Math.min(a.w, b.w) - 1;
        }
        return cy;
    }

    public static Script.Node chipNode(Script.Node n) { return n.cond == null ? n : n.cond; }

    public static String conditionText(Script.Node n) {
        if (n.cond == null) return "выбрать условие";
        return (n.cond.inverted() ? INVERT_PREFIX : "") + n.cond.action.name;
    }

    private static void measure(Box box, TextRenderer tr) {
        Script.Node outer = box.node;
        Script.Node n = chipNode(outer);
        Catalog.Action a = outer.action;

        String name = null, target = null;
        int titleW;
        if (outer.declares() || outer.invokes()) {
            box.card = new Card();
            titleW = measureCard(box, tr);
        } else {
            name = outer.inverted() ? INVERT_PREFIX + a.name : a.name;
            box.targetSetting = outer.settingIndex(Catalog.TARGET);
            target = box.targetSetting < 0 ? null : outer.marker(box.targetSetting);
            if (target != null && Catalog.TARGET_DEFAULT.equals(target)) target = null;
            box.targetW = target == null ? 0 : tr.getWidth(target) + TARGET_GAP;
            titleW = 20 + tr.getWidth(name) + (a.unavailable ? 10 : 0) + box.targetW;
        }

        List<Chip> chips = new ArrayList<>();
        if (Mapping.hasConditional(a)) {
            String text = conditionText(outer);
            Chip c = new Chip(-1, -1, 15 + tr.getWidth(text) + 7);
            c.condition = true;
            c.fitted = Draw.ordered(Draw.fit(tr, text, c.w - 15 - 7));
            chips.add(c);
        }
        if (outer.declares()) {
            paramChips(outer, chips, tr);
        } else {
            for (int i = 0; i < n.args().size(); i++) {
                if (box.card != null && i == Catalog.CALL_NAME) continue;
                Value cv = chipValue(n, i);
                boolean withItem = cv != null && Value.ITEM.equals(cv.type) && !cv.itemId.isEmpty();
                Chip c = new Chip(i, -1, argChipWidth(n, i, tr, withItem));
                c.value = cv;
                label(c, n, tr);
                chips.add(c);
            }
        }
        List<Catalog.Setting> settings = n.settings();
        for (int i = 0; i < settings.size(); i++) {
            if (settings.get(i).quiet) continue;
            Chip c = new Chip(-1, i, markerChipWidth(n, i, tr));
            c.fitted = Draw.ordered(Draw.fit(tr, markerText(n, i),
                    c.w - (markerBound(n, i) ? 13 : 8) - 13));
            chips.add(c);
        }
        if (box.card != null) plusChip(box, chips, tr);

        int sum = 0;
        for (Chip c : chips) sum += c.w + CHIP_GAP;
        if (!chips.isEmpty()) sum -= CHIP_GAP;

        int contentMax = MAX_W - PAD * 2;
        int flowW = Math.max(titleW, Math.min(contentMax, sum));

        int cx = 0, row = 0, used = 0;
        for (Chip c : chips) {
            if (cx > 0 && cx + c.w > flowW) { row++; cx = 0; }
            c.x = cx;
            c.row = row;
            cx += c.w + CHIP_GAP;
            used = Math.max(used, cx - CHIP_GAP);
        }
        int rows = chips.isEmpty() ? 0 : row + 1;

        box.w = Math.max(MIN_W, Math.max(titleW, used) + PAD * 2);
        if (box.card != null) {
            placeCard(box, tr);
        } else {
            box.title = Draw.ordered(Draw.fit(tr, name,
                    box.w - PAD * 2 - 20 - (a.unavailable ? 9 : 0) - box.targetW));
            if (target != null) {
                int room = box.w - PAD * 2 - 20 - (a.unavailable ? 9 : 0);
                String fitted = Draw.fit(tr, target, room);
                box.targetW = tr.getWidth(fitted) + TARGET_GAP;
                box.target = Draw.ordered(fitted);
                box.targetX = box.x + box.w - PAD - (a.unavailable ? 10 : 0)
                        - (box.targetW - TARGET_GAP);
            }
        }
        int base = a.category == null ? 0x7A7A7A : a.category.color;
        if (a.unavailable) base = Draw.mix(base, 0x8A8A8A, 0.4f);
        boolean grad = Settings.gradient();
        int head = Draw.shade(base, grad ? 0.15f : 0.04f);
        box.top = Draw.opaque(head);
        box.bottom = grad ? Draw.opaque(Draw.shade(base, -0.12f)) : box.top;
        box.border = Draw.opaque(Draw.shade(base, -0.46f));
        box.lightHead = Draw.isLight(head);
        box.ink = box.lightHead ? 0x141821 : 0xFFFFFF;
        int headH = box.card == null ? TITLE_H : box.card.headH;
        box.headerH = box.hatH + headH
                + (rows == 0 ? NO_CHIP_PAD : rows * (CHIP_H + ROW_GAP) - ROW_GAP + CHIP_BOTTOM_PAD);

        int chipTop = box.y + box.hatH + headH;
        for (Chip c : chips) {
            c.x += box.x + PAD;
            c.y = chipTop + c.row * (CHIP_H + ROW_GAP);
            box.chips.add(c);
        }
    }

    private static int measureCard(Box box, TextRenderer tr) {
        Script.Node n = box.node;
        Card c = box.card;
        boolean declares = n.declares();
        c.scale = declares ? NAME_SCALE : 1;
        c.iconSize = declares ? CARD_ICON : 16;
        c.idH = declares ? CARD_ID_H : TITLE_H;

        String id = declares ? Functions.nameOf(n) : Functions.targetOf(n);
        Value display = declares ? Functions.displayOf(n) : n.dynDisplay;
        String shown = display == null ? "" : McText.plain(display.text, display.parsing).trim();
        c.named = !id.isBlank();
        c.nameRaw = !shown.isEmpty() ? shown : c.named ? id : declares
                ? (n.isProcess() ? "имя процесса" : "имя функции")
                : (n.isStart() ? "процесс" : "функцию");
        c.idRaw = !shown.isEmpty() && c.named ? id : null;

        int need = c.iconSize + (declares ? 4 : 3)
                + Math.min(NAME_MAX, tr.getWidth(c.nameRaw) * c.scale);
        if (c.idRaw != null) need += ID_GAP + Math.min(ID_MAX, tr.getWidth(c.idRaw));
        if (declares) {
            c.kindRaw = n.isProcess() ? "ПРОЦЕСС" : "ФУНКЦИЯ";
            need += TARGET_GAP + tr.getWidth(c.kindRaw);
        } else {
            c.verbRaw = n.isStart() ? "Запустить" : "Вызвать";
            need += tr.getWidth(c.verbRaw) + 5;
            c.missing = c.named && n.dynArgs == null;
            if (c.missing) need += 9;
        }

        Value icon = declares ? Functions.iconOf(n) : n.dynIcon;
        c.icon = icon != null ? Stacks.preview(icon) : n.action.icon();

        if (declares) {
            c.descRaws.addAll(descAll(n));
            for (String s : c.descRaws)
                need = Math.max(need, Math.min(MAX_W - PAD * 2, tr.getWidth(s)));
        }
        c.headH = c.idH + c.descRaws.size() * DESC_H + (declares ? SEP_H : 0);
        return need;
    }

    public static String titleOf(Functions.Signature signature) {
        Value display = signature.display();
        String shown = display == null ? "" : McText.plain(display.text, display.parsing).trim();
        return shown.isEmpty() ? signature.name() : shown;
    }

    public static int descLines(Script.Node declaration) {
        return descAll(declaration).size();
    }

    public static List<String> descAll(Script.Node declaration) {
        List<Value> lines = declaration.values.get(Catalog.FN_DESC);
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (Value v : lines) {
            if (!Value.TEXT.equals(v.type)) continue;
            for (String part : McText.plain(v.text, v.parsing).split("\n"))
                if (!part.isBlank()) out.add(part);
        }
        return out;
    }

    private static void placeCard(Box box, TextRenderer tr) {
        Card c = box.card;
        int left = box.x + PAD;
        int top = box.y + box.hatH;
        boolean big = c.scale > 1;

        c.iconX = left - 1;
        c.iconY = big ? top + (c.idH - c.iconSize) / 2 : top + 5;
        int inkY = big ? top + (c.idH - 8) / 2 : top + 9;
        int textX = left + c.iconSize + (big ? 4 : 3);
        if (c.verbRaw != null) {
            c.verb = Draw.ordered(c.verbRaw);
            c.verbW = tr.getWidth(c.verbRaw);
            c.verbX = textX;
            textX += c.verbW + 5;
        }
        int right = box.x + box.w - PAD;
        if (c.kindRaw != null) {
            c.kindW = tr.getWidth(c.kindRaw);
            c.kindX = right - c.kindW;
            c.kindY = inkY;
            right = c.kindX - TARGET_GAP;
        }
        if (c.missing) right -= 9;
        c.id = null;
        c.idW = 0;
        int idRoom = 0;
        if (c.idRaw != null) {
            int want = Math.min(ID_MAX, tr.getWidth(c.idRaw));
            if (tr.getWidth(c.nameRaw) * c.scale + ID_GAP + want <= right - textX) idRoom = want;
        }
        String name = Draw.fit(tr, c.nameRaw,
                Math.max(8, (right - textX - (idRoom == 0 ? 0 : idRoom + ID_GAP)) / c.scale));
        c.name = Draw.ordered(name);
        c.nameW = tr.getWidth(name) * c.scale;
        c.nameX = textX;
        c.nameY = big ? top + (c.idH - 8 * c.scale) / 2 : inkY;
        if (idRoom > 0) {
            String id = Draw.fit(tr, c.idRaw, idRoom);
            c.id = Draw.ordered(id);
            c.idW = tr.getWidth(id);
            c.idX = c.nameX + c.nameW + ID_GAP;
            c.idY = c.nameY + (8 * c.scale - 8);
        }

        int cy = top + c.idH;
        c.descX = left;
        c.descY = cy;
        c.desc.clear();
        if (!c.descRaws.isEmpty()) {
            int room = Math.max(24, box.w - PAD * 2);
            for (String raw : c.descRaws)
                c.desc.addAll(tr.wrapLines(Text.literal(raw), room));
            for (OrderedText l : c.desc) c.descW = Math.max(c.descW, tr.getWidth(l));
            cy += c.desc.size() * DESC_H;
        }
        c.headH = c.idH + c.desc.size() * DESC_H + (c.scale > 1 ? SEP_H : 0);
        c.sepY = big ? cy + 2 : 0;
    }

    private static void paramChips(Script.Node n, List<Chip> out, TextRenderer tr) {
        List<Value> params = n.values.get(Catalog.FN_PARAMS);
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            Value p = params.get(i);
            if (!Value.PARAMETER.equals(p.type)) continue;
            Chip c = new Chip(Catalog.FN_PARAMS, -1, i, false, 0);
            c.value = p;
            paramLabel(c, p, tr);
            out.add(c);
        }
    }

    private static void paramLabel(Chip c, Value p, TextRenderer tr) {
        boolean marker = Value.ENUM.equals(p.typeKey);
        String label = (p.name.isBlank() ? "без имени" : p.name)
                + (Value.PLURAL.equals(p.typeKey) ? "[]" : "")
                + (marker || p.required ? "" : "*");
        String note = marker
                ? (p.elements.isEmpty() ? "маркер" : "маркер ×" + p.elements.size())
                : ("any".equals(p.valueType) ? "" : Values.paramTypeName(p.valueType));
        int tc = marker ? MARKER_PARAM_COLOR
                : Catalog.TYPE_COLORS.getOrDefault(Catalog.typeOfParam(p), 0xD8D8D8);

        c.noteW = note.isEmpty() ? 0 : tr.getWidth(note);
        c.note = note.isEmpty() ? null : Draw.ordered(note);
        c.w = Math.max(CHIP_MIN_W, Math.min(CHIP_MAX_W,
                CHIP_INK_X + tr.getWidth(label) + 7 + (note.isEmpty() ? 0 : c.noteW + 6)));
        c.fitted = Draw.ordered(Draw.fit(tr, label,
                c.w - 6 - CHIP_INK_X - (note.isEmpty() ? 0 : c.noteW + 4)));

        c.filled = true;
        boolean grad = Settings.gradient();
        c.border = Draw.opaque(Draw.shade(tc, -0.5f));
        c.top = Draw.opaque(Draw.shade(tc, grad ? 0.12f : 0.02f));
        c.bottom = grad ? Draw.opaque(Draw.shade(tc, -0.10f)) : c.top;
        c.dot = Draw.opaque(Draw.shade(tc, -0.55f));
        c.dim = Draw.shade(tc, -0.42f);
        c.ink = Draw.isLight(tc) ? 0x141821 : 0xFFFFFF;
    }

    private static void plusChip(Box box, List<Chip> out, TextRenderer tr) {
        Script.Node n = box.node;
        if (!n.declares()) return;
        List<Value> params = n.values.get(Catalog.FN_PARAMS);
        if (params != null && params.size() >= Catalog.MAX_PARAMS) return;
        String label = "параметр";
        Chip c = new Chip(Catalog.FN_PARAMS, -1, -1, true, 16 + tr.getWidth(label) + 7);
        c.fitted = Draw.ordered(label);
        boolean grad = Settings.gradient();
        c.border = Draw.opaque(Draw.shade(0xB8C2D4, -0.62f));
        c.top = Theme.LIGHT ? Draw.argb(0xC4, 0xF4F6FA) : Draw.argb(0xB4, 0x11151D);
        c.bottom = grad
                ? (Theme.LIGHT ? Draw.argb(0xC4, 0xE4E8F0) : Draw.argb(0xB4, 0x090C12))
                : c.top;
        c.ink = Theme.TEXT_FAINT;
        c.dim = Draw.shade(Theme.TEXT_FAINT, -0.20f);
        out.add(c);
    }

    public static Value chipValue(Script.Node n, int argIndex) {
        List<Value> v = n.values.get(argIndex);
        if (v == null || v.isEmpty()) return null;
        return n.args().get(argIndex).list && v.size() > 1 ? null : v.get(0);
    }

    public static String display(Value v) {
        if (!Value.TEXT.equals(v.type)) return v.label();
        String plain = McText.plain(v.text, v.parsing);
        return plain.isEmpty() ? v.label() : plain;
    }

    public static String argText(Script.Node n, int argIndex) {
        List<Value> v = n.values.get(argIndex);
        if (v == null || v.isEmpty()) return n.args().get(argIndex).purpose;
        if (v.size() == 1) return display(v.get(0));
        StringBuilder sb = new StringBuilder();
        for (Value x : v) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(display(x));
        }
        return sb.toString();
    }

    public static String argNote(Script.Node n, int argIndex) {
        if (n.invokes() && argIndex > 0 && argFilled(n, argIndex))
            return n.args().get(argIndex).purpose;
        Value v = chipValue(n, argIndex);
        return v == null ? "" : v.note();
    }

    public static boolean argFilled(Script.Node n, int argIndex) {
        List<Value> v = n.values.get(argIndex);
        return v != null && !v.isEmpty();
    }

    public static String argCount(Script.Node n, int argIndex) {
        Catalog.Arg a = n.args().get(argIndex);
        if (!a.list) return null;
        List<Value> v = n.values.get(argIndex);
        if (v == null) return "0/" + a.capacity;
        if (Catalog.slots(n.action, argIndex) == null) return v.size() + "/" + a.capacity;
        int filled = 0;
        for (Value it : v) if (!it.isBlank()) filled++;
        return filled + "/" + a.capacity;
    }

    private static void label(Chip c, Script.Node n, TextRenderer tr) {
        String count = argCount(n, c.argIndex);
        String note = argNote(n, c.argIndex);
        c.countW = count == null ? 0 : tr.getWidth(count);
        c.noteW = note.isEmpty() ? 0 : tr.getWidth(note);
        c.count = count == null ? null : Draw.ordered(count);
        c.note = note.isEmpty() ? null : Draw.ordered(note);
        int right = c.w - 6;
        if (count != null) right -= c.countW + 5;
        if (!note.isEmpty()) right -= c.noteW + 4;
        Value v = c.value;
        boolean withItem = v != null && Value.ITEM.equals(v.type) && !v.itemId.isEmpty();
        int room = right - (withItem ? CHIP_ITEM_INK_X : CHIP_INK_X);

        if (v != null && Value.TEXT.equals(v.type))
            c.fitted = McText.fit(tr, McText.runs(v.text, v.parsing), room).asOrderedText();
        else
            c.fitted = Draw.ordered(Draw.fit(tr, argText(n, c.argIndex), room));
        if (v != null && Value.ITEM.equals(v.type) && !v.itemId.isEmpty())
            c.icon = Stacks.preview(v);

        int tc = v != null ? v.color()
                : Catalog.TYPE_COLORS.getOrDefault(n.args().get(c.argIndex).type, 0xAAAAAA);
        c.filled = argFilled(n, c.argIndex);
        boolean grad = Settings.gradient();
        if (c.filled) {
            c.border = Draw.opaque(Draw.shade(tc, -0.5f));
            c.top = Draw.opaque(Draw.shade(tc, grad ? 0.12f : 0.02f));
            c.bottom = grad ? Draw.opaque(Draw.shade(tc, -0.10f)) : c.top;
        } else {
            c.border = Draw.opaque(Draw.shade(tc, -0.58f));
            c.top = Theme.LIGHT ? Draw.argb(0xC4, 0xF4F6FA) : Draw.argb(0xB4, 0x11151D);
            c.bottom = grad
                    ? (Theme.LIGHT ? Draw.argb(0xC4, 0xE4E8F0) : Draw.argb(0xB4, 0x090C12))
                    : c.top;
        }
        c.dot = Draw.opaque(c.filled ? Draw.shade(tc, -0.55f) : tc);
        c.dim = c.filled ? Draw.shade(tc, -0.42f) : Draw.shade(Theme.TEXT_FAINT, -0.30f);
        c.ink = c.filled ? (Draw.isLight(tc) ? 0x141821 : 0xFFFFFF)
                : Draw.shade(Theme.TEXT_FAINT, -0.15f);
    }

    private static int argChipWidth(Script.Node n, int i, TextRenderer tr, boolean withItem) {
        int w = (withItem ? CHIP_ITEM_INK_X : CHIP_INK_X) + tr.getWidth(argText(n, i)) + 7;
        String note = argNote(n, i);
        if (!note.isEmpty()) w += tr.getWidth(note) + 6;
        String count = argCount(n, i);
        if (count != null) w += tr.getWidth(count) + 8;
        return Math.max(CHIP_MIN_W, Math.min(CHIP_MAX_W, w));
    }

    public static String markerText(Script.Node n, int settingIndex) {
        Value bound = n.markerVar(settingIndex);
        if (bound != null && !bound.name.isBlank())
            return n.settings().get(settingIndex).label + ": " + bound.name;
        String option = n.marker(settingIndex);
        if (!n.invokes()) return option;
        return n.settings().get(settingIndex).label + ": " + option;
    }

    public static boolean markerBound(Script.Node n, int settingIndex) {
        Value bound = n.markerVar(settingIndex);
        return bound != null && !bound.name.isBlank();
    }

    public static final int TARGET_GAP = 8;
    public static final String INVERT_PREFIX = "НЕ ";

    private static int markerChipWidth(Script.Node n, int i, TextRenderer tr) {
        int w = (markerBound(n, i) ? 13 : 8) + tr.getWidth(markerText(n, i)) + 6 + 5 + 6;
        return Math.max(CHIP_MIN_W, Math.min(CHIP_MAX_W, w));
    }

    private Layout() {}
}
