package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Audio;
import com.xerocode.Blocks;
import com.xerocode.Catalog;
import com.xerocode.Importer;
import com.xerocode.Localized;
import com.xerocode.Pickers;
import com.xerocode.Script;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class ValueEditor {
    private static final int PAD = 10;
    private static final int HEAD_H = 26;
    private static final int FOOT_H = 26;
    private static final int CAP = 11;
    private static final int FIELD_H = 20;
    private static final int CTRL_H = 17;
    private static final int ROW_H = 17;
    private static final int ADD_H = 15;
    private static final int GAP = 5;
    private static final int BTN_H = 18;
    private static final int KIND_H = 20;
    private static final int WIDTH = 280;
    private static final int LIST_MAX = 5, LIST_MAX_COMPACT = 3;

    private static final List<String> PARAM_TYPES = Values.PARAM_TYPES;

    private static final List<String> PARAM_KINDS = List.of(Value.SINGULAR, Value.PLURAL, Value.ENUM);
    private static final List<String> PARAM_KIND_NAMES = List.of("одиночное", "множество", "маркер");

    private static final String[][] COORD_CAPS = {
            {"X", "Y", "Z"}, {"X", "Y", "Z", "yaw", "pitch"}};

    private static List<Values.Kind> kinds() { return Values.KINDS; }

    private String onlyKind;

    private boolean usable(String id) {
        if (onlyKind != null) return onlyKind.equals(id);
        if (declaresParameters()) return Value.PARAMETER.equals(id);
        if (declaresHead(Catalog.FN_NAME, Catalog.FN_DESC, Catalog.FN_DISPLAY))
            return Value.TEXT.equals(id);
        if (declaresHead(Catalog.FN_ICON)) return Value.ITEM.equals(id);
        return Values.EDITABLE.contains(id);
    }

    private boolean declaresParameters() { return arg != null && "Параметр".equals(arg.type); }

    private boolean declaresHead(int... args) {
        if (node == null || !node.declares()) return false;
        for (int a : args) if (argIndex == a) return true;
        return false;
    }

    private final Script.Node node;
    private final int argIndex;
    private final Catalog.Arg arg;
    private final TextRenderer tr;
    private int screenW, screenH;
    private final int anchorX, anchorY;
    private final List<String> knownVars, knownParams;

    private final List<Value> values = new ArrayList<>();
    private int sel;

    private final List<TextFieldWidget> fields = new ArrayList<>();
    private Ui.Chips paramChips, targetChips, scopeChips, modeChips, sourceChips;
    private Ui.Chips kindChips, elemChips;
    private final Ui.Bar bodyBar = new Ui.Bar(), listBar = new Ui.Bar(), cellBar = new Ui.Bar();
    private int lastMx, lastMy;
    private int dragSlot = -1;
    private boolean slotMoved;
    private String lang = "";
    private final Map<String, List<Value>> byLang = new LinkedHashMap<>();
    private List<String> suggestions = List.of();
    private SoundPlayer player;
    private ParticleStage stage;

    private Menu menu;
    private final Complete complete = new Complete();
    private TextStudio studio;
    private ColorPick colors;
    private CatalogPicker picker;
    private ItemPicker itemPicker;
    private ItemStudio itemStudio;
    private ValueEditor nested;
    private int listScroll, cellScroll;
    private boolean rebuild;

    private String before;
    private int x, y, w, h;
    private int focus;
    private boolean closed, committed, changed, compact, placed;
    private final Ui.Pane pane = new Ui.Pane();
    private int naturalH;
    private boolean pickInWorld;
    private boolean cancelled;
    private boolean dragging;
    private int dragX, dragY;

    private int scrubField = -1;
    private double scrubFrom, scrubX0;
    private boolean scrubbing;

    public interface Cell { void apply(Value value); }

    private final Cell cell;

    private ValueEditor(Script.Node node, int argIndex, Catalog.Arg arg, Cell cell,
                        TextRenderer tr, int anchorX, int anchorY, int screenW, int screenH,
                        List<String> knownVars, List<String> knownParams) {
        this.node = node;
        this.argIndex = argIndex;
        this.arg = arg;
        this.cell = cell;
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.knownVars = knownVars == null ? List.of() : knownVars;
        this.knownParams = knownParams == null ? List.of() : knownParams;
        this.w = Ui.fitW(screenW, WIDTH);
    }

    public ValueEditor(Script.Node node, int argIndex, TextRenderer tr,
                       int anchorX, int anchorY, int screenW, int screenH,
                       List<String> knownVars, List<String> knownParams) {
        this(node, argIndex, node.args().get(argIndex), null, tr, anchorX, anchorY,
                screenW, screenH, knownVars, knownParams);
        List<Value> existing = node.values.get(argIndex);
        if (existing != null) for (Value v : existing) values.add(v.copy());
        if (localizedField() != null) {
            byLang.put("", copies(values));
            for (Localized.Lang l : Localized.LANGS)
                if (!l.id().isEmpty()) byLang.put(l.id(), readTranslation(l.id()));
            this.before = allJson();
        } else {
            this.before = json(values);
        }
        if (values.isEmpty()) values.add(Value.of(Values.defaultKind(arg.type)));
        buildForm();
    }

    private String localizedField() {
        return node == null || !node.declares() ? null : Catalog.localizedField(argIndex);
    }

    private static List<Value> copies(List<Value> list) {
        List<Value> out = new ArrayList<>();
        for (Value v : list) out.add(v.copy());
        return out;
    }

    private static String kept(List<Value> list) {
        List<Value> out = new ArrayList<>();
        for (Value v : list) if (!v.isBlank()) out.add(v);
        return json(out);
    }

    private String allJson() {
        StringBuilder sb = new StringBuilder();
        for (Localized.Lang l : Localized.LANGS) {
            List<Value> list = byLang.get(l.id());
            sb.append(l.id()).append('=').append(list == null ? "" : kept(list)).append(';');
        }
        return sb.toString();
    }

    private JsonObject translations(boolean create) {
        if (localizedField() == null) return null;
        String key = Importer.translationsKey(argIndex);
        if (node.raw != null && node.raw.has(key) && node.raw.get(key).isJsonObject()) {
            JsonObject was = node.raw.getAsJsonObject(key);
            JsonObject now = Localized.normalize(was);
            if (now.equals(was)) return was;
            node.raw.add(key, now);
            return now;
        }
        if (!create) return null;
        if (node.raw == null) node.raw = new JsonObject();
        JsonObject made = new JsonObject();
        node.raw.add(key, made);
        return made;
    }

    private List<Value> readTranslation(String id) {
        List<Value> out = new ArrayList<>();
        JsonObject all = translations(false);
        if (all == null || !all.has(id)) return out;
        String text = Localized.entryText(all.get(id));
        String parsing = Localized.entryParsing(all.get(id));
        for (String part : arg != null && arg.list ? text.split("\n", -1) : new String[]{text}) {
            Value v = Value.of(Value.TEXT);
            v.text = part;
            v.parsing = parsing;
            out.add(v);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).text.isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private void writeTranslation(String id, List<Value> list) {
        Localized.Joined text = Localized.join(list);
        boolean any = !text.text().isEmpty();
        JsonObject all = translations(any);
        if (all == null) return;
        if (any) all.add(id, Localized.entry(text.text(), text.parsing()));
        else all.remove(id);
        if (all.isEmpty()) node.raw.remove(Importer.translationsKey(argIndex));
        if (node.raw.isEmpty()) node.raw = null;
    }

    private void switchLang(String id) {
        if (id.equals(lang)) return;
        readForm();
        byLang.put(lang, copies(values));
        lang = id;
        values.clear();
        values.addAll(copies(byLang.getOrDefault(id, List.of())));
        if (values.isEmpty()) values.add(Value.of(Values.defaultKind(arg.type)));
        sel = 0;
        listScroll = 0;
        buildForm();
    }

    private ValueEditor(Value value, String purpose, TextRenderer tr,
                        int anchorX, int anchorY, int screenW, int screenH,
                        List<String> knownVars, List<String> knownParams, Cell done) {
        this(null, -1, Catalog.Arg.cell(purpose), done, tr, anchorX, anchorY,
                screenW, screenH, knownVars, knownParams);
        values.add(value.copy());
        this.before = json(values);
        buildForm();
    }

    public static ValueEditor forCell(Value value, String purpose, String onlyKind,
                                      TextRenderer tr, int anchorX, int anchorY,
                                      int screenW, int screenH, List<String> knownVars,
                                      List<String> knownParams, Cell done) {
        ValueEditor editor = new ValueEditor(value, purpose, tr, anchorX, anchorY,
                screenW, screenH, knownVars, knownParams, done);
        editor.onlyKind = onlyKind;
        return editor;
    }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        screenW = sw;
        screenH = sh;
        complete.reset();
        w = Ui.fitW(screenW, WIDTH);
        compact = false;
        placed = false;
        if (nested != null) nested.resize(sw, sh);
        if (picker != null) picker.resize(sw, sh);
        if (itemPicker != null) itemPicker.resize(sw, sh);
        if (itemStudio != null) itemStudio.resize(sw, sh);
        if (studio != null) studio.resize(sw, sh);
        menu = null;
        colors = null;
        readForm();
        buildForm();
    }

    public void select(int index) {
        if (index < 0 || index >= values.size()) return;
        sel = index;
        clampListScroll();
        buildForm();
    }

    public void addCell() {
        if (values.size() == 1 && values.get(0).isBlank()) { sel = 0; buildForm(); return; }
        if (!canAdd()) return;
        values.add(Value.of(Values.defaultKind(arg.type)));
        sel = values.size() - 1;
        clampListScroll();
        buildForm();
    }

    private static String json(List<Value> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Value v : list) if (!v.isBlank()) sb.append(v.toJson()).append(',');
        return sb.append(']').toString();
    }

    private Value current() { return values.get(Math.max(0, Math.min(values.size() - 1, sel))); }

    private boolean list() { return arg.list; }
    private boolean canAdd() { return list() && values.size() < arg.capacity; }
    private int inner() { return w - PAD * 2; }

    public boolean isClosed() { return closed; }
    public boolean cancelled() { return cancelled; }
    public boolean changed() { return changed; }
    public Script.Node node() { return node; }
    public int argIndex() { return argIndex; }
    public int selected() { return sel; }
    public boolean pickInWorld() { return pickInWorld; }

    private record Part(String id, String caption, int dy, int h) {}

    private record FieldRow(String id, int from, String[] caps) {
        int count() { return caps.length; }
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<FieldRow> fieldRows = new ArrayList<>();
    private int formH;

    private void cap(String text)          { parts.add(new Part(null, text, formH, CAP)); formH += CAP; }
    private void part(String id, int h)    { parts.add(new Part(id, null, formH, h)); formH += h; }
    private void gap()                     { formH += GAP; }

    private void inputs(String id, String[] caps, String... values) {
        fieldRows.add(new FieldRow(id, fields.size(), caps));
        for (String value : values) fields.add(field(value, "0"));
        part(id, 9 + FIELD_H);
    }

    private static final int CARD_PAD = 6;

    private int cardH(String name, String description) {
        int titleLines = nameLines(name).size();
        int lines = compact || description == null || description.isEmpty() ? 0
                : Ui.wrap(tr, description, inner() - 16, 4).size();
        return CARD_PAD * 2 + cardInk(titleLines, lines);
    }

    private static int cardInk(int titleLines, int descLines) {
        return titleLines * 11 + Ui.TEXT_H + (descLines == 0 ? 0 : 3 + descLines * 10);
    }

    private List<String> nameLines(String name) {
        if (name == null || name.isEmpty()) return List.of("");
        return Ui.wrap(tr, name, inner() - 40, 2);
    }

    private FieldRow rowOf(String id) {
        for (FieldRow r : fieldRows) if (r.id().equals(id)) return r;
        return null;
    }

    private int colW(FieldRow r) { return (inner() - (r.count() - 1) * 4) / r.count(); }

    private int colX(FieldRow r, int i) { return x + PAD + i * (colW(r) + 4); }

    private int py(String id) {
        for (Part p : parts) if (id.equals(p.id())) return formY() + p.dy();
        return formY();
    }

    private int ph(String id) {
        for (Part p : parts) if (id.equals(p.id())) return p.h();
        return 0;
    }

    private boolean has(String id) {
        for (Part p : parts) if (id.equals(p.id())) return true;
        return false;
    }

    private boolean rowHit(String id, double mx, double my) {
        return has(id) && Ui.hit(mx, my, x + PAD, py(id), inner(), ph(id));
    }

    private TextFieldWidget field(String text, String placeholder) {
        return Ui.field(tr, text, placeholder, 256);
    }

    private void buildForm() {
        Catalog.Slots grid = slots();
        if (grid != null) padSlots(grid);
        complete.reset();
        fields.clear();
        parts.clear();
        fieldRows.clear();
        paramChips = null;
        kindChips = null;
        elemChips = null;
        targetChips = null;
        scopeChips = null;
        modeChips = null;
        sourceChips = null;
        player = null;
        stage = null;
        suggestions = List.of();
        formH = 0;
        focus = 0;

        Value v = current();
        switch (v.type) {
            case Value.TEXT -> {
                fields.add(Ui.field(tr, v.text, arg.purpose, Ui.TEXT_MAX));
                cap("ТЕКСТ");
                part("input", FIELD_H);
                gap();
                cap("РЕЖИМ РАЗМЕТКИ");
                List<String> modes = new ArrayList<>();
                for (Values.Parsing p : Values.PARSINGS) modes.add(p.name());
                modeChips = new Ui.Chips(tr, modes, inner(), CTRL_H - 2, 3);
                part("mode", modeChips.height());
                gap();
                if (!compact) {
                    cap("ПРЕДПРОСМОТР");
                    part("preview", 24);
                    gap();
                }
                part("studio", BTN_H);
            }
            case Value.NUMBER -> {
                fields.add(field(Value.num(v.number), "0"));
                cap("ЗНАЧЕНИЕ");
                part("input", FIELD_H);
                if (!compact) part("note", 12);
            }
            case Value.VARIABLE -> {
                nameField(v, "имя переменной", "ИМЯ ПЕРЕМЕННОЙ");
                cap("ТИП ПЕРЕМЕННОЙ");
                List<String> scopes = new ArrayList<>();
                for (Values.Scope s : Values.SCOPES) scopes.add(s.name());
                scopeChips = new Ui.Chips(tr, scopes, inner(), CTRL_H - 2, 3);
                part("scope", scopeChips.height());
            }
            case Value.PARAMETER -> {
                nameField(v, "имя параметра", "ИМЯ ПАРАМЕТРА");
                cap("ВИД ПАРАМЕТРА");
                kindChips = new Ui.Chips(tr, PARAM_KIND_NAMES, inner(), CTRL_H - 2, 3);
                part("pkinds", kindChips.height());
                if (Value.ENUM.equals(v.typeKey)) {
                    gap();
                    cap("ВАРИАНТЫ");
                    if (!v.elements.isEmpty()) {
                        elemChips = new Ui.Chips(tr, elementNames(v), inner(), CTRL_H - 2, 3);
                        part("elems", elemChips.height());
                        part("elemhint", 12);
                    }
                    inputs("newelem", new String[]{"ДОБАВИТЬ ВАРИАНТ"}, "");
                    part("addelem", CTRL_H);
                } else {
                    gap();
                    cap("ЧТО ПРИНИМАЕТ");
                    paramChips = new Ui.Chips(tr, Values.paramTypeNames(), inner(), CTRL_H - 2, 3);
                    part("ptypes", paramChips.height());
                    gap();
                    part("flags", CTRL_H);
                }
            }
            case Value.LOCATION, Value.VECTOR -> {
                boolean loc = Value.LOCATION.equals(v.type);
                cap(loc ? "КООРДИНАТЫ" : "НАПРАВЛЕНИЕ");
                if (loc) inputs("coords", COORD_CAPS[1], Value.num(v.x), Value.num(v.y),
                        Value.num(v.z), Value.num(v.yaw), Value.num(v.pitch));
                else inputs("coords", COORD_CAPS[0], Value.num(v.x), Value.num(v.y), Value.num(v.z));
                if (loc && node != null) {
                    gap();
                    part("world", BTN_H);
                }
            }
            case Value.SOUND -> {
                cap("ЗВУК");
                Pickers.Entry se = Pickers.sound(v.sound);
                part("card", cardH(se == null ? "" : se.name, se == null ? "" : se.description));
                player = new SoundPlayer(tr, v, Values.color(Value.SOUND));
                if (se != null) { gap(); part("player", SoundPlayer.H); }
                gap();
                part("choose", BTN_H);
                gap();
                inputs("nums", new String[]{"ГРОМКОСТЬ", "ТОН"},
                        Value.num(v.volume), Value.num(v.pitch2));
                gap();
                cap("КАНАЛ");
                List<String> sources = new ArrayList<>();
                for (String sid : Pickers.SOURCES) sources.add(Pickers.sourceName(sid));
                sourceChips = new Ui.Chips(tr, sources, inner(), CTRL_H - 2, 3);
                part("source", sourceChips.height());
            }
            case Value.PARTICLE -> {
                cap("ЧАСТИЦА");
                Pickers.Entry ce = Pickers.particle(v.particle);
                part("card", cardH(ce == null ? "" : ce.name, ce == null ? "" : ce.description));
                stage = new ParticleStage(v);
                if (ce != null && !compact) { gap(); part("stage", ParticleStage.H); }
                gap();
                part("choose", BTN_H);
                gap();
                inputs("nums", new String[]{"СКОЛЬКО", "РАЗЛЁТ", "РАЗЛЁТ ПО Y"},
                        String.valueOf(v.count), Value.num(v.spread1), Value.num(v.spread2));
                Pickers.Entry pe = Pickers.particle(v.particle);
                if (pe != null && pe.has(Pickers.MOTION)) {
                    gap();
                    inputs("motion", new String[]{"ДВИЖЕНИЕ X", "Y", "Z"},
                            Value.num(v.mx), Value.num(v.my), Value.num(v.mz));
                }
                if (pe != null && pe.has(Pickers.SIZE)) {
                    gap();
                    inputs("size", new String[]{"РАЗМЕР"}, Value.num(v.size));
                }
                if (pe != null && pe.has(Pickers.MATERIAL)) {
                    gap();
                    cap("МАТЕРИАЛ");
                    fields.add(field(v.material, "minecraft:stone"));
                    part("material", FIELD_H);
                }
                if (pe != null && (pe.has(Pickers.COLOR) || pe.has(Pickers.TO_COLOR))) {
                    gap();
                    cap(pe.has(Pickers.TO_COLOR) ? "ЦВЕТ И ЦВЕТ ПЕРЕХОДА" : "ЦВЕТ");
                    part("color", CTRL_H);
                }
            }
            case Value.POTION -> {
                cap("ЭФФЕКТ");
                Pickers.Entry oe = Pickers.potion(v.potion);
                part("card", cardH(oe == null ? "" : oe.name, oe == null ? "" : oe.description));
                gap();
                part("choose", BTN_H);
                gap();
                inputs("nums", new String[]{"ДЛИТЕЛЬНОСТЬ В ТИКАХ", "УРОВЕНЬ"},
                        String.valueOf(v.duration), String.valueOf(v.amplifier + 1));
                if (!compact) part("note", 12);
            }
            case Value.ARRAY -> {
                cap("СОДЕРЖИМОЕ");
                part("cells", cellsH(v));
            }
            case Value.MAP -> {
                cap("ПАРЫ КЛЮЧ — ЗНАЧЕНИЕ");
                part("cells", cellsH(v));
            }
            case Value.ITEM -> {
                cap("ПРЕДМЕТ");
                part("card", cardH(v.itemId.isEmpty() ? "" : Stacks.plainName(v),
                        Stacks.summary(v)));
                gap();
                part("choose", BTN_H);
                gap();
                part("tools", BTN_H);
                gap();
                inputs("nums", new String[]{"КОЛИЧЕСТВО"}, String.valueOf(v.itemCount));
            }
            case Value.BLOCK -> {
                cap("БЛОК");
                Blocks.Entry be = Blocks.entry(v.block);
                part("card", cardH(be == null ? "" : be.name(), ""));
                gap();
                part("choose", BTN_H);
                gap();
                part("tools", BTN_H);
            }
            case Value.GAME_VALUE -> {
                cap("ЗНАЧЕНИЕ");
                Values.GameValue gv = Values.gameValue(v.gameValue);
                part("card", cardH(gv == null ? "" : gv.name, gv == null ? "" : gv.description));
                gap();
                part("choose", BTN_H);
                gap();
                cap("ЦЕЛЬ");
                if (compact) {
                    part("target", CTRL_H);
                } else {
                    List<String> names = new ArrayList<>();
                    for (Values.Selector s : Values.SELECTORS) names.add(s.name());
                    targetChips = new Ui.Chips(tr, names, inner(), CTRL_H - 2, 3);
                    part("target", targetChips.height());
                }
            }
            default -> part("unavailable", CTRL_H);
        }

        clampListScroll();
        layout();
        if (h < naturalH && !compact) {
            compact = true;
            buildForm();
            return;
        }
        if (!fields.isEmpty()) focus(0);
    }

    private void nameField(Value v, String hint, String caption) {
        fields.add(field(v.name, hint));
        cap(caption);
        part("input", FIELD_H);
        refreshSuggestions();
        if (!suggestions.isEmpty()) { gap(); part("sugg", CTRL_H - 2); }
        gap();
    }

    private void refreshSuggestions() {
        Value v = current();
        List<String> pool = Value.PARAMETER.equals(v.type) ? knownParams : knownVars;
        String typed = (fields.isEmpty() ? v.name : fields.get(0).getText())
                .trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : new LinkedHashSet<>(pool)) {
            if (name.isBlank() || name.equalsIgnoreCase(typed)) continue;
            if (!typed.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(typed)) continue;
            out.add(name);
            if (out.size() == 6) break;
        }
        suggestions = out;
    }

    private int railH() {
        return localizedField() != null ? langsH() : grid().height(kinds().size());
    }

    private Ui.Grid grid() {
        int n = kinds().size();
        int inner = inner();
        int cell = Math.max(16, ((inner - (n - 1)) / n) & ~1);
        int gap = n > 1 ? Math.max(1, (inner - n * cell) / (n - 1)) : 0;
        int row = n * cell + (n - 1) * gap;
        return new Ui.Grid(x + PAD + (inner - row) / 2, railY(), n, cell, KIND_H, gap);
    }

    private int listMax() { return compact ? LIST_MAX_COMPACT : LIST_MAX; }

    private static final int SLOT = 18, SLOT_GAP = 4;

    private Catalog.Slots slots() {
        return node == null ? null : Catalog.slots(node.action, argIndex);
    }

    private void padSlots(Catalog.Slots s) {
        if (values.size() == 1 && Value.ARRAY.equals(values.get(0).type)) {
            List<Value> was = new ArrayList<>(values.get(0).items);
            values.clear();
            values.addAll(was);
        }
        while (values.size() < s.size()) values.add(Value.of(Value.ITEM));
        while (values.size() > s.size()) values.remove(values.size() - 1);
    }

    private int slotRows(Catalog.Slots s) {
        return (s.size() + s.cols() - 1) / s.cols();
    }

    private int slotsH(Catalog.Slots s) {
        return slotRows(s) * (SLOT + 2) + (s.hotbar() > 0 ? SLOT_GAP : 0);
    }

    private int slotLeft(Catalog.Slots s) {
        return x + PAD + Math.max(0, (inner() - s.cols() * (SLOT + 2)) / 2);
    }

    private boolean inHotbar(Catalog.Slots s, int i) {
        return s.hotbar() > 0 && i >= s.size() - s.hotbar();
    }

    private int slotX(Catalog.Slots s, int i) {
        return slotLeft(s) + (i % s.cols()) * (SLOT + 2);
    }

    private int slotY(Catalog.Slots s, int i, int top) {
        return top + (i / s.cols()) * (SLOT + 2) + (inHotbar(s, i) ? SLOT_GAP : 0);
    }

    private static int cellCount(Value v) {
        return Value.MAP.equals(v.type) ? v.keys.size() : v.items.size();
    }

    private static int cellMax(Value v) {
        return Value.MAP.equals(v.type) ? Value.MAP_MAX : Value.ARRAY_MAX;
    }

    private static boolean canAddCell(Value v) { return cellCount(v) < cellMax(v); }

    private int cellRows(Value v) {
        return Math.max(1, Math.min(cellCount(v), listMax()));
    }

    private int cellsH(Value v) {
        return cellRows(v) * (ROW_H + 2) + (canAddCell(v) ? ADD_H + 2 : 0);
    }

    private int maxCellScroll(Value v) { return Math.max(0, cellCount(v) - listMax()); }

    private void openCell(Value holder, boolean key, int index) {
        readForm();
        List<Value> where = key ? holder.keys : holder.items;
        while (where.size() <= index) where.add(Value.blank());
        Value now = where.get(index);
        String purpose = Value.MAP.equals(holder.type)
                ? (key ? "Ключ " + (index + 1) : "Значение " + (index + 1))
                : "Значение " + (index + 1) + " в списке";
        nested = new ValueEditor(now, purpose, tr, x + 12, y + 12, screenW, screenH,
                knownVars, knownParams, edited -> where.set(index, edited));
    }

    private void slotDropped() {
        if (dragSlot < 0) return;
        int from = dragSlot;
        dragSlot = -1;
        Catalog.Slots grid = slots();
        if (!slotMoved || grid == null) return;
        int to = slotAt(grid, lastMx, lastMy);
        if (to < 0 || to == from) return;
        readForm();
        Value moved = values.get(from);
        values.set(from, values.get(to));
        values.set(to, moved);
        sel = to;
        changed = true;
        buildForm();
    }

    private void drawDraggedSlot(DrawContext ctx) {
        Catalog.Slots grid = slots();
        if (dragSlot < 0 || !slotMoved || grid == null || dragSlot >= values.size()) return;
        Value it = values.get(dragSlot);
        if (it.isBlank()) return;
        ctx.createNewRootLayer();
        ctx.drawItem(Stacks.preview(it), lastMx - 8, lastMy - 8);
    }

    private boolean clearSlot(double mx, double my) {
        Catalog.Slots grid = slots();
        if (grid == null) return false;
        int slot = slotAt(grid, mx, my);
        if (slot < 0) return false;
        readForm();
        values.set(slot, Value.of(Value.ITEM));
        buildForm();
        return true;
    }

    private void closeNested() {
        if (!nested.cancelled()) nested.commit();
        if (nested.changed()) changed = true;
        nested = null;
        buildForm();
    }

    private int rowW() { return inner() - 19 - (maxListScroll() > 0 ? 5 : 0); }

    private int listRows() { return Math.min(values.size(), listMax()); }

    private int listH() {
        if (!list()) return 0;
        Catalog.Slots s = slots();
        if (s != null) return CAP + slotsH(s) + GAP;
        return CAP + listRows() * (ROW_H + 2) + (canAdd() ? ADD_H + 2 : 0) + GAP;
    }

    private int maxListScroll() { return Math.max(0, values.size() - listMax()); }

    private void clampListScroll() {
        if (sel < listScroll) listScroll = sel;
        else if (sel >= listScroll + listMax()) listScroll = sel - listMax() + 1;
        listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
    }

    private void layout() {
        int contentH = HEAD_H + 4 + listH() + CAP + railH() + GAP + 4 + formH + 7;
        naturalH = contentH + FOOT_H;
        h = Ui.fitH(screenH, naturalH);
        if (!placed) {
            placed = true;
            int reserve = Math.min(screenH - 8, Math.max(h, 300));
            x = Ui.anchorX(screenW, anchorX, w);
            y = anchorY + reserve > screenH - 4 ? Math.max(4, screenH - reserve - 4)
                                                : Math.max(4, anchorY);
        } else if (y + h > screenH - 4) {
            y = Math.max(4, screenH - h - 4);
        }
        pane.fit(HEAD_H, h - FOOT_H, contentH);
        placeFields();
    }

    private int listY() { return y + pane.at(HEAD_H + 4); }
    private int railY() { return listY() + listH() + CAP; }
    private int formY() { return railY() + railH() + GAP + 4; }
    private int footY() { return y + h - FOOT_H; }

    private void placeFields() {
        if (fields.isEmpty()) return;
        for (FieldRow r : fieldRows) {
            int cw = colW(r), ry = py(r.id()) + 9 + (FIELD_H - 12) / 2 + 2;
            for (int i = 0; i < r.count(); i++) {
                TextFieldWidget f = fields.get(r.from() + i);
                f.setX(colX(r, i) + 6);
                f.setY(ry);
                Ui.width(f, cw - 12);
            }
        }
        String single = has("input") ? "input" : has("material") ? "material" : null;
        if (single == null) return;
        TextFieldWidget f = null;
        for (int i = 0; i < fields.size() && f == null; i++) {
            boolean owned = false;
            for (FieldRow r : fieldRows)
                if (i >= r.from() && i < r.from() + r.count()) { owned = true; break; }
            if (!owned) f = fields.get(i);
        }
        if (f == null) return;
        boolean stepper = Value.NUMBER.equals(current().type);
        int left = x + PAD + (stepper ? FIELD_H + 4 : 0) + 6;
        int right = x + PAD + inner() - (stepper ? FIELD_H + 4 : 0) - 6;
        f.setX(left);
        f.setY(py(single) + (FIELD_H - 12) / 2 + 2);
        Ui.width(f, Math.max(20, right - left));
    }

    private static final int[] AXIS_INK = {0xF0605E, 0x8FD94F, 0x5B8CF5, 0xFFD54A, 0xFFD54A};

    private static boolean scrubbable(FieldRow r) {
        return r != null && switch (r.id()) {
            case "coords", "nums", "motion", "size" -> true;
            default -> false;
        };
    }

    private void drawFieldRow(DrawContext ctx, int mouseX, int mouseY, float delta, FieldRow r) {
        if (r == null) return;
        boolean axes = "coords".equals(r.id());
        boolean drag = scrubbable(r);
        int cw = colW(r), ry = py(r.id());
        for (int i = 0; i < r.count(); i++) {
            int cx = colX(r, i);
            int ink = axes && i < AXIS_INK.length ? AXIS_INK[i] : Theme.TEXT_FAINT;
            if (drag && Ui.hit(mouseX, mouseY, cx, ry, cw, 9))
                ctx.setCursor(StandardCursors.RESIZE_EW);
            else if (Ui.hit(mouseX, mouseY, cx, ry + 9, cw, FIELD_H))
                ctx.setCursor(StandardCursors.IBEAM);
            Draw.textFit(ctx, tr, r.caps()[i], cx + 2, ry, cw - 4, ink, false);
            Ui.input(ctx, cx, ry + 9, cw, FIELD_H, r.from() + i == focus);
            if (axes && i < AXIS_INK.length)
                Draw.roundRect(ctx, cx + 1, ry + 10, 3, FIELD_H - 2,
                        Ui.R_SM - 1, 0, 0, Ui.R_SM - 1, Draw.opaque(ink));
            fields.get(r.from() + i).render(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, tr, fields.get(r.from() + i));
        }
    }

    private void focus(int i) {
        if (fields.isEmpty()) return;
        complete.reset();
        focus = Math.max(0, Math.min(fields.size() - 1, i));
        for (int k = 0; k < fields.size(); k++) fields.get(k).setFocused(k == focus);
        fields.get(focus).setCursorToStart(false);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lastMx = mouseX;
        lastMy = mouseY;
        if (nested != null) { nested.render(ctx, mouseX, mouseY, delta); return; }
        if (picker != null) { picker.render(ctx, mouseX, mouseY, delta); return; }
        if (studio != null) { studio.render(ctx, mouseX, mouseY, delta); return; }
        if (itemPicker != null) { itemPicker.render(ctx, mouseX, mouseY, delta); return; }
        if (itemStudio != null) { itemStudio.render(ctx, mouseX, mouseY, delta); return; }

        Value v = current();
        int accent = v.color();
        if (!Value.SOUND.equals(v.type)) Audio.want("");

        Ui.panel(ctx, x, y, w, h);
        drawHeader(ctx, mouseX, mouseY);
        ctx.enableScissor(x + 1, y + pane.top(), x + w - 1, footY() - 1);
        if (list()) drawList(ctx, mouseX, mouseY);
        drawRail(ctx, mouseX, mouseY);
        Ui.hairline(ctx, x + 1, formY() - 4, w - 2);
        drawCaptions(ctx);
        drawForm(ctx, mouseX, mouseY, delta);
        ctx.disableScissor();
        pane.drawBar(ctx, bodyBar, x + w - 4, y, mouseX, mouseY);
        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY, accent);

        if (colors != null) {
            ctx.createNewRootLayer();
            colors.render(ctx, mouseX, mouseY, delta);
        }
        if (menu != null) {
            ctx.createNewRootLayer();
            menu.render(ctx, tr, mouseX, mouseY);
        }
        if (complete.active()) {
            ctx.createNewRootLayer();
            complete.render(ctx, tr, mouseX, mouseY);
        }
        drawDraggedSlot(ctx);
    }

    private void drawHeader(DrawContext ctx, int mouseX, int mouseY) {
        int tc = Catalog.TYPE_COLORS.getOrDefault(arg.type, 0xAAAAAA);
        int closeX0 = x + w - PAD - 14;
        boolean grab = dragging || (Ui.hit(mouseX, mouseY, x, y, w, HEAD_H)
                && !Ui.hit(mouseX, mouseY, closeX0, y + 6, 14, 14));
        Ui.headerStrip(ctx, x, y, w, HEAD_H, grab ? Draw.shade(tc, 0.22f) : tc);
        if (grab) ctx.setCursor(StandardCursors.RESIZE_ALL);
        Draw.round(ctx, x + PAD, y + 8, 3, 10, 1, Draw.opaque(tc));

        String type = arg.type + (list() ? " ×" + arg.capacity : "");
        int badgeW = Draw.badgeWidth(tr, type);
        int closeX = closeX0;
        String title = arg.purpose.isEmpty() ? "Значение" : arg.purpose;
        if (!lang.isEmpty()) title += " · " + lang;
        Draw.textFit(ctx, tr, title,
                x + PAD + 9, y + 9, closeX - badgeW - 12 - (x + PAD + 9), Theme.TEXT, false);
        Draw.badge(ctx, tr, type, closeX - badgeW - 8, y + 8,
                Draw.opaque(Draw.shade(tc, -0.68f)), Draw.shade(tc, 0.2f));
        Ui.closeButton(ctx, mouseX, mouseY, closeX, y + 6, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
    }

    private static final int LANG_ROW_H = 15, LANG_HINT_H = 11;

    private int langsH() {
        return Localized.LANGS.size() * (LANG_ROW_H + 2) + LANG_HINT_H;
    }

    private String langPreview(String id) {
        List<Value> list = byLang.get(id);
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Value v : list) {
            if (!Value.TEXT.equals(v.type) || v.text.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(McText.plain(v.text, v.parsing));
            if (sb.length() > 80) break;
        }
        return sb.toString().trim();
    }

    private static String langLabel(Localized.Lang l) {
        return l.id().isEmpty() ? "по умолчанию" : l.id();
    }

    private void drawLangs(DrawContext ctx, int mouseX, int mouseY) {
        Ui.caption(ctx, tr, "ЯЗЫКИ", x + PAD, railY() - CAP, inner());
        int rw = inner();
        int labelW = 0;
        for (Localized.Lang l : Localized.LANGS)
            labelW = Math.max(labelW, tr.getWidth(langLabel(l)));
        labelW += 10;
        for (int i = 0; i < Localized.LANGS.size(); i++) {
            Localized.Lang l = Localized.LANGS.get(i);
            int ry = railY() + i * (LANG_ROW_H + 2);
            boolean active = l.id().equals(lang);
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, LANG_ROW_H);
            Draw.round(ctx, x + PAD, ry, rw, LANG_ROW_H, Ui.R_SM,
                    Draw.opaque(active ? 0x22405F : (hov ? 0x1C222D : Ui.WELL)));
            if (active) Draw.rect(ctx, x + PAD, ry + 2, 2, LANG_ROW_H - 4,
                    Draw.opaque(Theme.ACCENT));
            Draw.textFit(ctx, tr, langLabel(l), x + PAD + 7, ry + 4, labelW,
                    active ? Theme.TEXT : Theme.TEXT_DIM, false);
            String preview = active ? "" : langPreview(l.id());
            boolean dim = preview.isEmpty();
            if (active) preview = "правится ниже";
            else if (dim) preview = l.id().isEmpty() ? "не задано" : "нет перевода";
            Draw.textFit(ctx, tr, preview, x + PAD + 7 + labelW, ry + 4,
                    rw - 14 - labelW, dim ? Theme.TEXT_FAINT : Theme.TEXT, false);
        }
        Draw.textFit(ctx, tr, lang.isEmpty()
                        ? "запасной: увидят все, у кого нет перевода"
                        : "увидят те, у кого клиент на " + lang,
                x + PAD + 2, railY() + Localized.LANGS.size() * (LANG_ROW_H + 2) + 1,
                rw - 4, Theme.TEXT_FAINT, false);
    }

    private void drawList(DrawContext ctx, int mouseX, int mouseY) {
        Catalog.Slots grid = slots();
        if (grid != null) { drawSlots(ctx, grid, mouseX, mouseY); return; }
        Ui.caption(ctx, tr, "ЗНАЧЕНИЯ", x + PAD, listY(), inner(),
                values.size() + "/" + arg.capacity);
        for (int r = 0; r < listRows(); r++) {
            int i = listScroll + r;
            if (i >= values.size()) break;
            Value v = values.get(i);
            int ry = listY() + CAP + r * (ROW_H + 2);
            int rw = rowW();
            boolean active = i == sel;
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, ROW_H);
            Draw.round(ctx, x + PAD, ry, rw, ROW_H, Ui.R_SM,
                    Draw.opaque(active ? 0x22405F : (hov ? 0x1C222D : Ui.WELL)));
            if (active) Draw.rect(ctx, x + PAD, ry + 2, 2, ROW_H - 4, Draw.opaque(Theme.ACCENT));
            Draw.textRight(ctx, tr, String.valueOf(i + 1), x + PAD + 16, ry + 5,
                    active ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            Draw.dot(ctx, x + PAD + 21, ry + 6, Draw.opaque(v.color()));
            String note = v.note();
            int noteW = note.isEmpty() ? 0 : tr.getWidth(note) + 8;
            Draw.textFit(ctx, tr, Layout.display(v), x + PAD + 29, ry + 5, rw - 35 - noteW,
                    v.isBlank() ? Theme.TEXT_FAINT : Theme.TEXT, false);
            if (!note.isEmpty())
                Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ry + 5, Theme.TEXT_FAINT, false);
            Ui.iconButton(ctx, mouseX, mouseY, x + PAD + inner() - ROW_H, ry, ROW_H,
                    Draw.CROSS, Ui.DANGER, true);
        }
        if (maxListScroll() > 0)
            listBar.draw(ctx, x + PAD + rowW() + 1, listY() + CAP, listRows() * (ROW_H + 2),
                    values.size() * (ROW_H + 2), listRows() * (ROW_H + 2),
                    listScroll * (ROW_H + 2), mouseX, mouseY);
        if (canAdd()) {
            int ay = listY() + CAP + listRows() * (ROW_H + 2);
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ay, inner(), ADD_H,
                    Draw.PLUS, "добавить значение", Ui.GHOST, true);
        }
    }

    private void drawRail(DrawContext ctx, int mouseX, int mouseY) {
        if (localizedField() != null) { drawLangs(ctx, mouseX, mouseY); return; }
        Ui.caption(ctx, tr, "ТИП ЗНАЧЕНИЯ", x + PAD, railY() - CAP, inner());
        Ui.Grid g = grid();
        int cw = g.cellW(), chh = g.cellH();
        String cur = current().type;
        for (int i = 0; i < kinds().size(); i++) {
            Values.Kind k = kinds().get(i);
            int cx = g.cellX(i), cy = g.cellY(i);
            boolean on = k.id().equals(cur);
            boolean ok = usable(k.id());
            boolean hov = Ui.hit(mouseX, mouseY, cx, cy, cw, chh);
            int color = Values.color(k.id());
            Draw.round(ctx, cx, cy, cw, chh, Ui.R_SM, Draw.opaque(on
                    ? Draw.shade(color, -0.56f) : (hov && ok ? Ui.BTN_HOVER : Ui.BTN)));
            if (on) Draw.roundOutline(ctx, cx, cy, cw, chh, Ui.R_SM, Draw.opaque(color));
            ItemStack icon = on && current().hasIcon()
                    ? Stacks.preview(current()) : ItemStack.EMPTY;
            if (icon.isEmpty()) icon = Catalog.stackOf(k.item());
            ctx.drawItem(icon, cx + (cw - 16) / 2, cy + (chh - 16) / 2);
            if (!ok) {
                Draw.round(ctx, cx + 1, cy + 1, cw - 2, chh - 2, Ui.R_SM - 1,
                        Draw.argb(0xB4, Ui.WELL));
                Draw.glyph(ctx, Draw.LOCK, cx + (cw - Draw.glyphW(Draw.LOCK)) / 2,
                        cy + (chh - Draw.glyphH(Draw.LOCK)) / 2, Theme.TEXT_FAINT);
            }
        }
    }

    private void drawCaptions(DrawContext ctx) {
        for (Part p : parts)
            if (p.caption() != null)
                Ui.caption(ctx, tr, p.caption(), x + PAD, formY() + p.dy(), inner());
    }

    private void drawForm(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Value v = current();
        int full = inner();
        switch (v.type) {
            case Value.TEXT -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                if (modeChips != null)
                    modeChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("mode"),
                            parsingIndex(v.parsing), Values.color(Value.TEXT));
                if (has("preview")) drawPreview(ctx, v);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("studio"), full, BTN_H,
                        Draw.WINDOW, "Расширенный редактор", Ui.GHOST, true);
            }
            case Value.NUMBER -> {
                Ui.iconButton(ctx, mouseX, mouseY, x + PAD, py("input"), FIELD_H,
                        Draw.MINUS, Ui.GHOST, true);
                Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - FIELD_H, py("input"),
                        FIELD_H, Draw.PLUS, Ui.GHOST, true);
                Ui.input(ctx, x + PAD + FIELD_H + 4, py("input"), full - 2 * (FIELD_H + 4), FIELD_H,
                        fields.get(0).isFocused());
                fields.get(0).render(ctx, mouseX, mouseY, delta);
                Ui.placeholder(ctx, tr, fields.get(0));
                if (has("note")) {
                    String text = fields.get(0).getText().trim();
                    boolean bad = !text.isEmpty() && !isNumber(text);
                    Draw.textFit(ctx, tr, bad ? "это не число — станет 0 при сохранении"
                                    : "дробная часть через точку или запятую",
                            x + PAD + 2, py("note") + 2, full - 4,
                            bad ? Theme.DANGER : Theme.TEXT_FAINT, false);
                }
            }
            case Value.VARIABLE -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                drawSuggestions(ctx, mouseX, mouseY);
                int cur = scopeIndex(v.scope);
                if (scopeChips != null)
                    scopeChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("scope"), cur,
                            Values.SCOPES.isEmpty() ? Theme.ACCENT
                                    : Values.SCOPES.get(Math.max(0, cur)).color());
            }
            case Value.PARAMETER -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                drawSuggestions(ctx, mouseX, mouseY);
                int accent = Values.color(Value.PARAMETER);
                if (kindChips != null)
                    kindChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("pkinds"),
                            Math.max(0, PARAM_KINDS.indexOf(v.typeKey)), accent);
                if (Value.ENUM.equals(v.typeKey)) {
                    if (elemChips != null)
                        elemChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("elems"),
                                defaultElementIndex(v), accent);
                    if (has("elemhint"))
                        Draw.textFit(ctx, tr, "клик — вариант по умолчанию, правый — удалить",
                                x + PAD + 2, py("elemhint") + 2, full - 4, Theme.TEXT_FAINT, false);
                    drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("newelem"));
                    Ui.button(ctx, tr, mouseX, mouseY, x + PAD, py("addelem"), full, CTRL_H,
                            "добавить вариант", Ui.GHOST, canAddElement(v));
                } else {
                    if (paramChips != null)
                        paramChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("ptypes"),
                                Math.max(0, PARAM_TYPES.indexOf(v.valueType)), accent);
                    boolean plural = Value.PLURAL.equals(v.typeKey);
                    int half = (full - GAP) / 2;
                    Ui.toggle(ctx, tr, mouseX, mouseY, x + PAD, py("flags"),
                            plural ? half : full, CTRL_H, "обязательный", v.required);
                    if (plural)
                        Ui.toggle(ctx, tr, mouseX, mouseY, x + PAD + half + GAP, py("flags"),
                                full - half - GAP, CTRL_H, "пропускать пустые", v.ignoreEmpty);
                }
            }
            case Value.LOCATION, Value.VECTOR -> {
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("coords"));
                if (has("world")) {
                    boolean inGame = MinecraftClient.getInstance().player != null;
                    boolean set = v.x != 0 || v.y != 0 || v.z != 0 || v.yaw != 0 || v.pitch != 0;
                    Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("world"), full, BTN_H,
                            Draw.PIN, set ? "править в мире" : "выбрать в мире", Ui.PRIMARY, inGame);
                }
            }
            case Value.SOUND -> {
                Pickers.Entry e = Pickers.sound(v.sound);
                drawEntryCard(ctx, e == null ? null : e.item, e == null ? "звук не выбран" : e.name,
                        e == null ? "" : e.category, "", "");
                if (has("player")) {
                    readForm();
                    player.select(v.sound);
                    player.render(ctx, x + PAD, py("player"), full, SoundPlayer.H, mouseX, mouseY);
                }
                drawChoose(ctx, mouseX, mouseY, e != null, "Выбрать звук", "Другой звук");
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
                if (sourceChips != null)
                    sourceChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("source"),
                            Math.max(0, Pickers.SOURCES.indexOf(v.source)),
                            Values.color(Value.SOUND));
            }
            case Value.PARTICLE -> {
                Pickers.Entry e = Pickers.particle(v.particle);
                drawEntryCard(ctx, e == null ? null : e.item,
                        e == null ? "частица не выбрана" : e.name,
                        e == null ? "" : e.category, e == null ? "" : e.description, "");
                if (has("stage")) {
                    readForm();
                    stage.show(v.particle);
                    stage.render(ctx, x + PAD, py("stage"), full, ParticleStage.H, mouseX, mouseY);
                }
                drawChoose(ctx, mouseX, mouseY, e != null, "Выбрать частицу", "Другая частица");
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
                if (has("motion")) drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("motion"));
                if (has("size")) drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("size"));
                if (has("material")) {
                    Ui.input(ctx, x + PAD, py("material"), full, FIELD_H,
                            fields.get(fields.size() - 1).isFocused());
                    fields.get(fields.size() - 1).render(ctx, mouseX, mouseY, delta);
                    Ui.placeholder(ctx, tr, fields.get(fields.size() - 1));
                }
                if (has("color")) {
                    int cy = py("color");
                    Ui.swatch(ctx, x + PAD, cy, 26, CTRL_H, v.color & 0xFFFFFF, true,
                            Ui.hit(mouseX, mouseY, x + PAD, cy, 26, CTRL_H));
                    Ui.button(ctx, tr, mouseX, mouseY, x + PAD + 30, cy, 84, CTRL_H,
                            "выбрать цвет", Ui.GHOST, true);
                    if (e != null && e.has(Pickers.TO_COLOR)) {
                        Draw.glyph(ctx, Draw.ARROW_RIGHT, x + PAD + 118, cy + 6, Theme.TEXT_FAINT);
                        Ui.swatch(ctx, x + PAD + 128, cy, 26, CTRL_H, v.toColor & 0xFFFFFF, true,
                                Ui.hit(mouseX, mouseY, x + PAD + 128, cy, 26, CTRL_H));
                        Ui.button(ctx, tr, mouseX, mouseY, x + PAD + 158, cy,
                                Math.max(40, full - 158), CTRL_H, "переход", Ui.GHOST, true);
                    }
                }
            }
            case Value.ARRAY, Value.MAP -> drawCells(ctx, v, mouseX, mouseY);
            case Value.ITEM -> {
                drawItemCard(ctx, v);
                drawChoose(ctx, mouseX, mouseY, !v.itemId.isEmpty(), "Выбрать предмет",
                        "Другой предмет");
                int ty = py("tools"), half = (full - GAP) / 2;
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ty, half, BTN_H,
                        Draw.LOAD, "Взять из руки", Ui.GHOST, held() != null);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD + half + GAP, ty,
                        full - half - GAP, BTN_H, Draw.WINDOW, "Редактор предмета", Ui.GHOST,
                        !v.itemId.isEmpty());
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
            }
            case Value.BLOCK -> {
                Blocks.Entry be = Blocks.entry(v.block);
                if (be == null)
                    drawEntryCard(ctx, (ItemStack) null, v.block.isEmpty() ? "блок не выбран"
                            : "нет такого блока: " + v.block, "", "", "");
                else drawEntryCard(ctx, be.icon(), be.name(), be.id(), "", "");
                drawChoose(ctx, mouseX, mouseY, !v.block.isEmpty(), "Выбрать блок", "Другой блок");
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("tools"), full, BTN_H,
                        Draw.LOAD, "Взять из руки", Ui.GHOST, Blocks.of(held()) != null);
            }
            case Value.POTION -> {
                Pickers.Entry e = Pickers.potion(v.potion);
                drawEntryCard(ctx, e == null ? null : Pickers.potionStack(e.id),
                        e == null ? "эффект не выбран" : e.name,
                        e == null ? "" : e.category, e == null ? "" : e.description, "");
                drawChoose(ctx, mouseX, mouseY, e != null, "Выбрать эффект", "Другой эффект");
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
                if (has("note"))
                    Draw.textFit(ctx, tr, "-1 — бесконечно; уровень 1 = amplifier 0",
                            x + PAD + 2, py("note") + 2, full - 4, Theme.TEXT_FAINT, false);
            }
            case Value.GAME_VALUE -> {
                Values.GameValue g = Values.gameValue(v.gameValue);
                drawEntryCard(ctx, g == null ? null : g.item,
                        g == null ? "значение не выбрано" : g.name,
                        g == null ? "" : g.category, g == null ? "" : g.description,
                        g == null ? "" : g.returns);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("choose"), full, BTN_H,
                        Draw.SEARCH, Values.gameValue(v.gameValue) == null
                                ? "Выбрать значение" : "Другое значение", Ui.GHOST, true);
                if (compact) {
                    Ui.button(ctx, tr, mouseX, mouseY, x + PAD, py("target"), full, CTRL_H,
                            Values.selectorName(v.selection), Ui.GHOST, true);
                    Draw.glyph(ctx, Draw.CARET_DOWN, x + PAD + full - 11, py("target") + 7,
                            Theme.TEXT_DIM);
                } else if (targetChips != null) {
                    targetChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("target"),
                            selectorIndex(v.selection), Values.color(Value.GAME_VALUE));
                }
            }
            default -> {
                int uy = py("unavailable");
                Draw.round(ctx, x + PAD, uy, full, CTRL_H, Ui.R_SM, Draw.opaque(0x2A2028));
                Draw.glyph(ctx, Draw.WARN, x + PAD + 7, uy + 5, 0xFFE066);
                Draw.textFit(ctx, tr, "каталог «" + Values.kindName(v.type) + "» ещё не снят",
                        x + PAD + 17, uy + (CTRL_H - Ui.TEXT_H) / 2, full - 24, Theme.TEXT_DIM, false);
            }
        }
    }

    private void drawCells(DrawContext ctx, Value v, int mouseX, int mouseY) {
        boolean map = Value.MAP.equals(v.type);
        int top = py("cells"), full = inner();
        int count = cellCount(v), rows = cellRows(v);
        Draw.textRight(ctx, tr, count + "/" + cellMax(v), x + PAD + full, top - CAP,
                Theme.TEXT_FAINT, false);

        int rw = full - ROW_H - 2 - (maxCellScroll(v) > 0 ? 5 : 0);
        if (count == 0) {
            Draw.round(ctx, x + PAD, top, full, ROW_H, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, map ? "пар пока нет" : "значений пока нет", x + PAD + 8,
                    top + 5, full - 16, Theme.TEXT_FAINT, false);
        }
        for (int r = 0; r < rows; r++) {
            int i = cellScroll + r;
            if (i >= count) break;
            int ry = top + r * (ROW_H + 2);
            Draw.round(ctx, x + PAD, ry, rw, ROW_H, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textRight(ctx, tr, String.valueOf(i + 1), x + PAD + 15, ry + 5,
                    Theme.TEXT_FAINT, false);
            if (map) {
                int halfW = (rw - 20 - 11) / 2;
                cellChip(ctx, v.keys.get(i), x + PAD + 18, ry, halfW, mouseX, mouseY);
                Draw.glyph(ctx, Draw.ARROW_RIGHT, x + PAD + 20 + halfW, ry + 6, Theme.TEXT_FAINT);
                cellChip(ctx, value(v, i), x + PAD + 29 + halfW, ry, rw - 31 - halfW,
                        mouseX, mouseY);
            } else {
                Value it = v.items.get(i);
                boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, ROW_H);
                if (hov) Draw.round(ctx, x + PAD, ry, rw, ROW_H, Ui.R_SM, Draw.opaque(0x1C222D));
                Draw.dot(ctx, x + PAD + 20, ry + 6, Draw.opaque(it.color()));
                String note = it.note();
                int noteW = note.isEmpty() ? 0 : tr.getWidth(note) + 8;
                Draw.textFit(ctx, tr, it.isBlank() ? "пусто" : Layout.display(it),
                        x + PAD + 28, ry + 5, rw - 34 - noteW,
                        it.isBlank() ? Theme.TEXT_FAINT : (hov ? Theme.TEXT : Theme.TEXT_DIM), false);
                if (!note.isEmpty())
                    Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ry + 5, Theme.TEXT_FAINT, false);
            }
            Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - ROW_H, ry, ROW_H,
                    Draw.CROSS, Ui.DANGER, true);
        }
        if (maxCellScroll(v) > 0)
            cellBar.draw(ctx, x + PAD + rw + 1, top, rows * (ROW_H + 2),
                    count * (ROW_H + 2), rows * (ROW_H + 2), cellScroll * (ROW_H + 2),
                    mouseX, mouseY);
        if (canAddCell(v))
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, top + rows * (ROW_H + 2), full, ADD_H,
                    Draw.PLUS, map ? "добавить пару" : "добавить значение", Ui.GHOST, true);
    }

    private void cellChip(DrawContext ctx, Value v, int cx, int ry, int cw,
                          int mouseX, int mouseY) {
        boolean hov = Ui.hit(mouseX, mouseY, cx, ry + 1, cw, ROW_H - 2);
        Draw.round(ctx, cx, ry + 1, cw, ROW_H - 2, Ui.R_SM - 1,
                Draw.opaque(hov ? Ui.BTN_HOVER : Ui.INPUT));
        Draw.dot(ctx, cx + 4, ry + 6, Draw.opaque(v.color()));
        Draw.textFit(ctx, tr, v.isBlank() ? "пусто" : Layout.display(v), cx + 12, ry + 5, cw - 16,
                v.isBlank() ? Theme.TEXT_FAINT : (hov ? Theme.TEXT : Theme.TEXT_DIM), false);
    }

    private static Value value(Value map, int i) {
        return i < map.items.size() ? map.items.get(i) : Value.blank();
    }

    private void drawSlots(DrawContext ctx, Catalog.Slots s, int mouseX, int mouseY) {
        int top = listY() + CAP;
        int filled = 0;
        for (Value it : values) if (!it.isBlank()) filled++;
        int at = slotAt(s, mouseX, mouseY);
        int show = at >= 0 ? at : sel;
        String note = (show >= 0 && show < s.size()
                ? "слот " + (show + 1) + (inHotbar(s, show) ? " · хотбар" : "") + "  ·  " : "")
                + filled + "/" + s.size();
        Ui.caption(ctx, tr, s.title(), x + PAD, listY(), inner(), note);
        for (int i = 0; i < s.size(); i++) {
            int cx = slotX(s, i), cy = slotY(s, i, top);
            boolean hov = i == at;
            Value it = values.get(i);
            Draw.round(ctx, cx, cy, SLOT, SLOT, Ui.R_SM,
                    Draw.opaque(i == sel ? 0x22405F : hov ? 0x2C3441 : Ui.WELL));
            boolean carried = i == dragSlot && slotMoved;
            if (!it.isBlank() && !carried) ctx.drawItem(Stacks.preview(it), cx + 1, cy + 1);
            if (i == sel || hov) Draw.roundOutline(ctx, cx, cy, SLOT, SLOT, Ui.R_SM,
                    Draw.opaque(i == sel ? Theme.ACCENT : Draw.shade(Theme.ACCENT, -0.35f)));
        }
    }

    private int slotAt(Catalog.Slots s, double mx, double my) {
        int top = listY() + CAP;
        for (int i = 0; i < s.size(); i++)
            if (Ui.hit(mx, my, slotX(s, i), slotY(s, i, top), SLOT, SLOT)) return i;
        return -1;
    }

    private int cellAt(Value v, double mx, double my) {
        int top = py("cells");
        for (int r = 0; r < cellRows(v); r++) {
            int i = cellScroll + r;
            if (i >= cellCount(v)) break;
            if (Ui.hit(mx, my, x + PAD, top + r * (ROW_H + 2), inner(), ROW_H)) return i;
        }
        return -1;
    }

    private boolean cellsClicked(Value v, double mx, double my) {
        boolean map = Value.MAP.equals(v.type);
        int top = py("cells"), full = inner();
        if (canAddCell(v) && Ui.hit(mx, my, x + PAD, top + cellRows(v) * (ROW_H + 2), full, ADD_H)) {
            if (map) v.keys.add(Value.blank());
            v.items.add(Value.blank());
            cellScroll = Math.max(0, cellCount(v) - listMax());
            buildForm();
            return true;
        }
        int i = cellAt(v, mx, my);
        if (i < 0) return false;
        int ry = top + (i - cellScroll) * (ROW_H + 2);
        if (Ui.hit(mx, my, x + PAD + full - ROW_H, ry, ROW_H, ROW_H)) {
            if (map && i < v.keys.size()) v.keys.remove(i);
            if (i < v.items.size()) v.items.remove(i);
            cellScroll = Math.min(cellScroll, maxCellScroll(v));
            buildForm();
            return true;
        }
        if (!map) { openCell(v, false, i); return true; }
        int rw = full - ROW_H - 2 - (maxCellScroll(v) > 0 ? 5 : 0);
        int halfW = (rw - 20 - 11) / 2;
        if (Ui.hit(mx, my, x + PAD + 18, ry, halfW, ROW_H)) { openCell(v, true, i); return true; }
        openCell(v, false, i);
        return true;
    }

    private void drawEntryCard(DrawContext ctx, String icon, String name, String category,
                               String description, String badge) {
        drawEntryCard(ctx, icon == null ? null : Catalog.stackOf(icon), name, category,
                description, badge);
    }

    private void drawEntryCard(DrawContext ctx, ItemStack picture, String name,
                               String category, String description, String badge) {
        int cy = py("card"), full = inner(), ch = ph("card");
        Ui.well(ctx, x + PAD, cy, full, ch);
        if (picture == null) {
            Draw.glyph(ctx, Draw.WARN, x + PAD + 8, cy + (ch - 6) / 2, 0xFFE066);
            Draw.textFit(ctx, tr, name, x + PAD + 20, cy + (ch - Ui.TEXT_H) / 2, full - 26,
                    Theme.TEXT_DIM, false);
            return;
        }
        List<String> title = nameLines(name);
        int top = cy + CARD_PAD;
        ctx.drawItem(picture, x + PAD + 6,
                description.isEmpty() ? cy + Math.max(4, (ch - 16) / 2) : top - 1);
        int tx = x + PAD + 27;
        int badgeW = badge.isEmpty() ? 0 : Draw.badgeWidth(tr, badge) + 8;
        for (int i = 0; i < title.size(); i++)
            Draw.textFit(ctx, tr, title.get(i), tx, top + i * 11,
                    full - 35 - (i == 0 ? badgeW : 0), Theme.TEXT, false);
        if (!badge.isEmpty()) {
            int bc = Catalog.TYPE_COLORS.getOrDefault(badge, 0x8A93A6);
            Draw.badge(ctx, tr, badge, x + PAD + full - 7 - Draw.badgeWidth(tr, badge), top - 1,
                    Draw.opaque(Draw.shade(bc, -0.66f)), Draw.shade(bc, 0.15f));
        }
        int at = top + title.size() * 11;
        Draw.textFit(ctx, tr, category, tx, at, full - 35, Theme.TEXT_FAINT, false);
        at += Ui.TEXT_H + 3;
        if (description.isEmpty()) return;
        int room = (cy + ch - CARD_PAD + 2 - at) / 10;
        if (room <= 0) return;
        for (String line : Ui.wrap(tr, description, full - 16, room)) {
            Draw.text(ctx, tr, line, x + PAD + 8, at, Theme.TEXT_DIM, false);
            at += 10;
        }
    }

    private void drawItemCard(DrawContext ctx, Value v) {
        int cy = py("card"), full = inner(), ch = ph("card");
        Ui.well(ctx, x + PAD, cy, full, ch);
        ItemStack st = Stacks.preview(v);
        if (st.isEmpty()) {
            Draw.glyph(ctx, Draw.WARN, x + PAD + 8, cy + (ch - 6) / 2, 0xFFE066);
            Draw.textFit(ctx, tr, v.itemId.isEmpty() ? "предмет не выбран"
                            : "нет такого предмета: " + v.itemId,
                    x + PAD + 20, cy + (ch - Ui.TEXT_H) / 2, full - 26, Theme.TEXT_DIM, false);
            return;
        }
        int top = cy + CARD_PAD, tx = x + PAD + 27;
        String summary = Stacks.summary(v);
        int iconY = summary.isEmpty() ? cy + Math.max(4, (ch - 16) / 2) : top - 1;
        ctx.drawItem(st, x + PAD + 6, iconY);
        ctx.drawStackOverlay(tr, st, x + PAD + 6, iconY);
        ctx.drawText(tr, McText.fit(tr, McText.runsOf(st.getName()), full - 35), tx, top,
                Draw.opaque(Theme.TEXT), false);
        Draw.textFit(ctx, tr, v.itemId, tx, top + 11, full - 35, Theme.TEXT_FAINT, false);
        if (!summary.isEmpty() && ch >= 40)
            Draw.textFit(ctx, tr, summary, x + PAD + 8, top + 11 + Ui.TEXT_H + 3, full - 16,
                    Theme.TEXT_DIM, false);
    }

    private static ItemStack held() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        ItemStack st = player.getMainHandStack();
        return st == null || st.isEmpty() ? null : st;
    }

    private void drawChoose(DrawContext ctx, int mouseX, int mouseY, boolean chosen, String pick,
                            String other) {
        Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("choose"), inner(), BTN_H,
                Draw.SEARCH, chosen ? other : pick, Ui.GHOST, true);
    }

    private void drawInput(DrawContext ctx, int mouseX, int mouseY, float delta, int i) {
        Ui.input(ctx, x + PAD, py("input"), inner(), FIELD_H, fields.get(i).isFocused());
        fields.get(i).render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, fields.get(i));
    }

    private static final String SUGG_LABEL = "уже есть:";

    private void drawSuggestions(DrawContext ctx, int mouseX, int mouseY) {
        if (!has("sugg")) return;
        int sy = py("sugg"), sh = ph("sugg"), at = x + PAD;
        int limit = x + PAD + inner();
        Draw.text(ctx, tr, SUGG_LABEL, at, sy + (sh - Ui.TEXT_H) / 2, Theme.TEXT_FAINT, false);
        at += tr.getWidth(SUGG_LABEL) + 5;
        for (String name : suggestions) {
            int cw = tr.getWidth(name) + 10;
            if (at + cw > limit) break;
            boolean hov = Ui.hit(mouseX, mouseY, at, sy, cw, sh);
            Draw.round(ctx, at, sy, cw, sh, 3, Draw.opaque(hov ? Ui.BTN_HOVER : Ui.BTN));
            Draw.text(ctx, tr, name, at + 5, sy + (sh - Ui.TEXT_H) / 2,
                    hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            at += cw + 3;
        }
    }

    private void drawPreview(DrawContext ctx, Value v) {
        int pyy = py("preview"), full = inner(), ph = ph("preview");
        Ui.well(ctx, x + PAD, pyy, full, ph);
        String raw = fields.get(0).getText();
        if (raw.isEmpty()) {
            Draw.text(ctx, tr, "как это увидит игрок", x + PAD + 8, pyy + (ph - Ui.TEXT_H) / 2,
                    Theme.TEXT_FAINT, false);
            return;
        }
        ctx.enableScissor(x + PAD + 1, pyy + 1, x + PAD + full - 1, pyy + ph - 1);
        ctx.drawText(tr, McText.preview(raw, v.parsing), x + PAD + 8,
                pyy + (ph - Ui.TEXT_H) / 2, Draw.opaque(Theme.TEXT), false);
        ctx.disableScissor();
    }

    private static final int OK_W = 56, NO_W = 52, BTN_GAP = 6, FOOT_BTN_H = 16;

    private int footBtnY() { return footY() + (FOOT_H - FOOT_BTN_H) / 2; }
    private int okX()      { return x + w - PAD - OK_W; }
    private int cancelX()  { return okX() - BTN_GAP - NO_W; }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY, int accent) {
        int fy = footBtnY();
        ctx.drawItem(Catalog.stackOf(Values.kindItem(current().type)), x + PAD - 2, fy);
        Draw.textFit(ctx, tr, Values.kindName(current().type), x + PAD + 16, fy + 4,
                inner() - 16 - (OK_W + BTN_GAP + NO_W + 4), Draw.shade(accent, 0.25f), false);
        Ui.button(ctx, tr, mouseX, mouseY, okX(), fy, OK_W, FOOT_BTN_H, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, cancelX(), fy, NO_W, FOOT_BTN_H, "Отмена", Ui.GHOST);
    }

    private boolean footClicked(double mx, double my) {
        int fy = footBtnY();
        if (Ui.hit(mx, my, okX(), fy, OK_W, FOOT_BTN_H)) { commit(); closed = true; return true; }
        if (Ui.hit(mx, my, cancelX(), fy, NO_W, FOOT_BTN_H)) {
            cancelled = true;
            closed = true;
            return true;
        }
        return false;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s.trim().replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parsingIndex(String id) {
        for (int i = 0; i < Values.PARSINGS.size(); i++)
            if (Values.PARSINGS.get(i).id().equals(id)) return i;
        return 0;
    }

    private static int scopeIndex(String id) {
        for (int i = 0; i < Values.SCOPES.size(); i++)
            if (Values.SCOPES.get(i).id().equals(id)) return i;
        return 0;
    }

    private static int selectorIndex(String id) {
        for (int i = 0; i < Values.SELECTORS.size(); i++)
            if (Values.SELECTORS.get(i).id().equals(id)) return i;
        return 0;
    }

    private static void selectTarget(Value v, int index) {
        v.selection = Values.SELECTORS.get(index).id();
        v.selectionRaw = "";
    }

    public boolean contains(double mx, double my) {
        if (nested != null || picker != null || studio != null || colors != null
                || itemPicker != null || itemStudio != null) return true;
        if (menu != null && menu.contains(mx, my)) return true;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void openStudio(Value v) {
        readForm();
        studio = new TextStudio(tr, screenW, screenH, v.text, v.parsing, (text, parsing) -> {
            v.text = text;
            v.parsing = parsing;
            fields.get(0).setText(text);
            fields.get(0).setCursorToStart(false);
        });
    }

    private void openPicker() {
        readForm();
        Value v = current();
        switch (v.type) {
            case Value.GAME_VALUE -> {
                List<CatalogPicker.Item> items = new ArrayList<>();
                for (Values.GameValue g : Values.GAME_VALUES)
                    items.add(new CatalogPicker.Item(g.id, g.name, g.category, g.item,
                            g.description, g.returns, g.returnsNote));
                picker = new CatalogPicker(tr, screenW, screenH, "Игровое значение",
                        Values.color(Value.GAME_VALUE), items, null, v.gameValue, null,
                        id -> v.gameValue = id);
            }
            case Value.SOUND -> {
                if (player == null) player = new SoundPlayer(tr, v, Values.color(Value.SOUND));
                player.select(v.sound);
                picker = new CatalogPicker(tr, screenW, screenH, "Звук",
                        Values.color(Value.SOUND), items(Pickers.SOUNDS), Pickers.SOUND_CATEGORIES,
                        v.sound, player, id -> {
                            v.sound = id;
                            rebuild = true;
                        });
            }
            case Value.PARTICLE -> {
                if (stage == null) stage = new ParticleStage(v);
                picker = new CatalogPicker(tr, screenW, screenH, "Эффект частиц",
                        Values.color(Value.PARTICLE), items(Pickers.PARTICLES),
                        Pickers.PARTICLE_CATEGORIES, v.particle, stage, id -> {
                            v.particle = id;
                            rebuild = true;
                        });
            }
            case Value.POTION -> picker = new CatalogPicker(tr, screenW, screenH, "Зелье",
                    Values.color(Value.POTION),
                    items(Pickers.POTIONS, e -> Pickers.potionStack(e.id)),
                    Pickers.POTION_CATEGORIES, v.potion, null, id -> v.potion = id);
            case Value.BLOCK -> {
                List<CatalogPicker.Item> items = new ArrayList<>();
                for (Blocks.Entry b : Blocks.all())
                    items.add(new CatalogPicker.Item(b.id(), b.name(), b.category(), "", "", "",
                            b.id(), b.icon()));
                picker = new CatalogPicker(tr, screenW, screenH, "Блок",
                        Values.color(Value.BLOCK), items, null, v.block, null, id -> {
                            v.block = id;
                            rebuild = true;
                        });
            }
            default -> { }
        }
    }

    private void closePicker() {
        picker = null;
        rebuildIfNeeded();
    }

    private void rebuildIfNeeded() {
        if (rebuild) { rebuild = false; buildForm(); }
    }

    private void openItemPicker() {
        readForm();
        Value v = current();
        itemPicker = new ItemPicker(tr, screenW, screenH, Values.color(Value.ITEM),
                Stacks.build(v), stack -> {
                    Stacks.read(v, stack);
                    rebuild = true;
                });
    }

    private void openItemStudio(Value v) {
        readForm();
        itemStudio = new ItemStudio(tr, screenW, screenH, v.copy(), edited -> {
            values.set(sel, edited);
            rebuild = true;
        });
    }

    private void closeItemStudio() {
        itemStudio = null;
        rebuildIfNeeded();
    }

    private static List<CatalogPicker.Item> items(List<Pickers.Entry> pool) {
        return items(pool, null);
    }

    private static List<CatalogPicker.Item> items(List<Pickers.Entry> pool,
                                                  Function<Pickers.Entry, ItemStack> picture) {
        List<CatalogPicker.Item> out = new ArrayList<>();
        for (Pickers.Entry e : pool) {
            String note = e.extras.isEmpty() ? ""
                    : "дополнительно: " + String.join(", ", e.extras).toLowerCase();
            out.add(new CatalogPicker.Item(e.id, e.name, e.category, e.item, e.description,
                    "", note, picture == null ? null : picture.apply(e)));
        }
        return out;
    }

    private void openColorPick(boolean transition) {
        readForm();
        Value v = current();
        int now = (transition ? v.toColor : v.color) & 0xFFFFFF;
        colors = new ColorPick(tr, transition ? "ЦВЕТ ПЕРЕХОДА" : "ЦВЕТ ЧАСТИЦЫ", now,
                x + w + 6, y + py("color") - 60, screenW, screenH, rgb -> {
                    if (transition) v.toColor = 0xFF000000 | rgb;
                    else v.color = 0xFF000000 | rgb;
                    changed = true;
                });
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (nested != null) {
            nested.mouseClicked(click, doubled);
            if (nested.isClosed()) closeNested();
            return true;
        }
        if (picker != null) {
            picker.mouseClicked(click, doubled);
            if (picker.isClosed()) closePicker();
            return true;
        }
        if (colors != null) {
            colors.mouseClicked(click, doubled);
            if (colors.isClosed()) { colors = null; rebuild = true; }
            return true;
        }
        if (studio != null) {
            studio.mouseClicked(click, doubled);
            if (studio.isClosed()) studio = null;
            return true;
        }
        if (itemPicker != null) {
            itemPicker.mouseClicked(click, doubled);
            if (itemPicker.isClosed()) { itemPicker = null; rebuildIfNeeded(); }
            return true;
        }
        if (itemStudio != null) {
            itemStudio.mouseClicked(click, doubled);
            if (itemStudio.isClosed()) closeItemStudio();
            return true;
        }
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (complete.mouseClicked(mx, my)) return true;
        if (!contains(mx, my)) { commit(); closed = true; return true; }

        if (Ui.hit(mx, my, x + w - PAD - 14, y + 6, 14, 14)) { commit(); closed = true; return true; }

        if (Ui.hit(mx, my, x, y, w, HEAD_H)) {
            dragging = true;
            dragX = mx - x;
            dragY = my - y;
            return true;
        }

        if (!pane.inBody(my, y)) {
            footClicked(mx, my);
            return true;
        }

        if (bodyBar.grabbed(mx, my, 1, pane.max(), v -> pane.scroll = v)) return true;
        if (listBar.grabbed(mx, my, ROW_H + 2, maxListScroll(), v -> listScroll = v)) return true;
        if (cellBar.grabbed(mx, my, ROW_H + 2, maxCellScroll(current()), v -> cellScroll = v))
            return true;

        if (list() && listClicked(mx, my)) return true;

        if (localizedField() != null) {
            for (int i = 0; i < Localized.LANGS.size(); i++)
                if (Ui.hit(mx, my, x + PAD, railY() + i * (LANG_ROW_H + 2), inner(), LANG_ROW_H)) {
                    switchLang(Localized.LANGS.get(i).id());
                    return true;
                }
        }

        int ki = localizedField() != null ? -1 : grid().indexAt(mx, my, kinds().size());
        if (ki >= 0) {
            String id = kinds().get(ki).id();
            if (usable(id) && !id.equals(current().type)) {
                readForm();
                values.set(sel, Value.of(id));
                buildForm();
            }
            return true;
        }

        if (click.button() == 1 && clearSlot(mx, my)) return true;
        if (formClicked(click, doubled, mx, my)) return true;

        footClicked(mx, my);
        return true;
    }

    private boolean listClicked(int mx, int my) {
        Catalog.Slots grid = slots();
        if (grid != null) {
            int slot = slotAt(grid, mx, my);
            if (slot < 0) return false;
            readForm();
            sel = slot;
            dragSlot = values.get(slot).isBlank() ? -1 : slot;
            slotMoved = false;
            buildForm();
            return true;
        }
        int rw = rowW();
        for (int r = 0; r < listRows(); r++) {
            int i = listScroll + r;
            if (i >= values.size()) break;
            int ry = listY() + CAP + r * (ROW_H + 2);
            if (Ui.hit(mx, my, x + PAD + inner() - ROW_H, ry, ROW_H, ROW_H)) {
                readForm();
                if (values.size() > 1) values.remove(i);
                else values.set(0, Value.of(Values.defaultKind(arg.type)));
                sel = Math.min(sel, values.size() - 1);
                buildForm();
                return true;
            }
            if (Ui.hit(mx, my, x + PAD, ry, rw, ROW_H)) {
                readForm();
                sel = i;
                buildForm();
                return true;
            }
        }
        if (canAdd() && Ui.hit(mx, my, x + PAD,
                listY() + CAP + listRows() * (ROW_H + 2), inner(), ADD_H)) {
            readForm();
            values.add(Value.of(Values.defaultKind(arg.type)));
            sel = values.size() - 1;
            buildForm();
            return true;
        }
        return false;
    }

    private static List<String> elementNames(Value v) {
        List<String> out = new ArrayList<>(v.elements.size());
        for (Value.Elem e : v.elements) out.add(e.name);
        return out;
    }

    private static int defaultElementIndex(Value v) {
        for (int i = 0; i < v.elements.size(); i++)
            if (v.elements.get(i).name.equals(v.defaultElement)) return i;
        return 0;
    }

    private String newElementText() {
        FieldRow r = rowOf("newelem");
        return r == null ? "" : fields.get(r.from()).getText().trim();
    }

    private boolean canAddElement(Value v) {
        String name = newElementText();
        if (name.isEmpty()) return false;
        for (Value.Elem e : v.elements) if (e.name.equals(name)) return false;
        return true;
    }

    private void addElement(Value v) {
        if (!canAddElement(v)) return;
        String name = newElementText();
        readForm();
        v.elements.add(new Value.Elem(name));
        if (v.defaultElement.isEmpty()) v.defaultElement = name;
        buildForm();
    }

    private int chip(Ui.Chips chips, int mx, int my, String row) {
        return chips == null ? -1 : chips.indexAt(mx, my, x + PAD, py(row));
    }

    private boolean formClicked(Click click, boolean doubled, int mx, int my) {
        Value v = current();
        int full = inner();
        boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        switch (v.type) {
            case Value.TEXT -> {
                if (clickField(click, doubled, mx, my, 0)) return true;
                int mi = chip(modeChips, mx, my, "mode");
                if (mi >= 0) {
                    Values.Parsing p = Values.PARSINGS.get(mi);
                    if (!p.id().equals(v.parsing)) {
                        readForm();
                        v.text = McText.convert(v.text, v.parsing, p.id());
                        v.parsing = p.id();
                        fields.get(0).setText(v.text);
                        fields.get(0).setCursorToStart(false);
                    }
                    return true;
                }
                if (rowHit("studio", mx, my)) { openStudio(v); return true; }
            }
            case Value.NUMBER -> {
                int step = shift ? 10 : 1;
                if (Ui.hit(mx, my, x + PAD, py("input"), FIELD_H, FIELD_H)) { bump(-step); return true; }
                if (Ui.hit(mx, my, x + PAD + full - FIELD_H, py("input"), FIELD_H, FIELD_H)) {
                    bump(step);
                    return true;
                }
                if (Ui.hit(mx, my, x + PAD + FIELD_H + 4, py("input"), full - 2 * (FIELD_H + 4),
                        FIELD_H)) return clickField(click, doubled, mx, my, 0);
            }
            case Value.VARIABLE -> {
                if (clickField(click, doubled, mx, my, 0)) return true;
                if (suggestionClicked(mx, my)) return true;
                int si = chip(scopeChips, mx, my, "scope");
                if (si >= 0) { v.scope = Values.SCOPES.get(si).id(); return true; }
            }
            case Value.PARAMETER -> {
                if (clickField(click, doubled, mx, my, 0)) return true;
                if (suggestionClicked(mx, my)) return true;
                int ki = chip(kindChips, mx, my, "pkinds");
                if (ki >= 0) {
                    String picked = PARAM_KINDS.get(ki);
                    if (!picked.equals(v.typeKey)) {
                        readForm();
                        v.typeKey = picked;
                        buildForm();
                    }
                    return true;
                }
                if (Value.ENUM.equals(v.typeKey)) {
                    int ei = chip(elemChips, mx, my, "elems");
                    if (ei >= 0 && ei < v.elements.size()) {
                        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            String gone = v.elements.remove(ei).name;
                            if (gone.equals(v.defaultElement)) v.defaultElement = "";
                            readForm();
                            buildForm();
                        } else {
                            v.defaultElement = v.elements.get(ei).name;
                        }
                        return true;
                    }
                    if (rowFieldClicked(click, doubled, mx, my)) return true;
                    if (Ui.hit(mx, my, x + PAD, py("addelem"), full, CTRL_H)) {
                        addElement(v);
                        return true;
                    }
                    return false;
                }
                int pi = chip(paramChips, mx, my, "ptypes");
                if (pi >= 0) { v.valueType = PARAM_TYPES.get(pi); return true; }
                boolean plural = Value.PLURAL.equals(v.typeKey);
                int half = (full - GAP) / 2;
                if (Ui.hit(mx, my, x + PAD, py("flags"), plural ? half : full, CTRL_H)) {
                    v.required = !v.required;
                    return true;
                }
                if (plural && Ui.hit(mx, my, x + PAD + half + GAP, py("flags"),
                        full - half - GAP, CTRL_H)) {
                    v.ignoreEmpty = !v.ignoreEmpty;
                    return true;
                }
            }
            case Value.LOCATION, Value.VECTOR -> {
                if (rowFieldClicked(click, doubled, mx, my)) return true;
                if (has("world") && Ui.hit(mx, my, x + PAD, py("world"), full, BTN_H)
                        && MinecraftClient.getInstance().player != null) {
                    commit();
                    pickInWorld = true;
                    closed = true;
                    return true;
                }
            }
            case Value.SOUND -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (has("player")) {
                    readForm();
                    if (player.mouseClicked(mx, my, x + PAD, py("player"), full, SoundPlayer.H))
                        return true;
                }
                if (rowFieldClicked(click, doubled, mx, my)) return true;
                int si2 = chip(sourceChips, mx, my, "source");
                if (si2 >= 0) { v.source = Pickers.SOURCES.get(si2); return true; }
            }
            case Value.PARTICLE -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (rowFieldClicked(click, doubled, mx, my)) return true;
                if (has("material") && clickField(click, doubled, mx, my, fields.size() - 1))
                    return true;
                if (has("color")) {
                    int cy = py("color");
                    if (Ui.hit(mx, my, x + PAD, cy, 26 + 4 + 84, CTRL_H)) {
                        openColorPick(false);
                        return true;
                    }
                    if (Ui.hit(mx, my, x + PAD + 128, cy, full - 128, CTRL_H)) {
                        openColorPick(true);
                        return true;
                    }
                }
            }
            case Value.POTION -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (rowFieldClicked(click, doubled, mx, my)) return true;
            }
            case Value.ARRAY, Value.MAP -> {
                if (cellsClicked(v, mx, my)) return true;
            }
            case Value.ITEM -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) {
                    openItemPicker();
                    return true;
                }
                int ty = py("tools"), half = (full - GAP) / 2;
                if (Ui.hit(mx, my, x + PAD, ty, half, BTN_H)) {
                    ItemStack st = held();
                    if (st != null) {
                        readForm();
                        Stacks.read(v, st.copy());
                        buildForm();
                    }
                    return true;
                }
                if (Ui.hit(mx, my, x + PAD + half + GAP, ty, full - half - GAP, BTN_H)) {
                    openItemStudio(v);
                    return true;
                }
                if (rowFieldClicked(click, doubled, mx, my)) return true;
            }
            case Value.BLOCK -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (rowHit("tools", mx, my)) {
                    String id = Blocks.of(held());
                    if (id != null) {
                        readForm();
                        v.block = id;
                        buildForm();
                    }
                    return true;
                }
            }
            case Value.GAME_VALUE -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (compact) {
                    if (rowHit("target", mx, my)) {
                        List<String> names = new ArrayList<>();
                        for (Values.Selector s : Values.SELECTORS) names.add(s.name());
                        menu = Menu.options(screenW, screenH, x + PAD, py("target") + CTRL_H + 2, tr,
                                "Цель", names, selectorIndex(v.selection),
                                i -> selectTarget(v, i));
                        return true;
                    }
                } else {
                    int ti = chip(targetChips, mx, my, "target");
                    if (ti >= 0) { selectTarget(v, ti); return true; }
                }
            }
            default -> { }
        }
        return false;
    }

    private boolean rowFieldClicked(Click click, boolean doubled, int mx, int my) {
        for (FieldRow r : fieldRows) {
            int cw = colW(r), ry = py(r.id());
            for (int i = 0; i < r.count(); i++) {
                if (scrubbable(r) && Ui.hit(mx, my, colX(r, i), ry, cw, 9)) {
                    scrubField = r.from() + i;
                    scrubFrom = parse(fields.get(scrubField).getText());
                    scrubX0 = click.x();
                    scrubbing = false;
                    focus(r.from() + i);
                    return true;
                }
                if (Ui.hit(mx, my, colX(r, i), ry + 9, cw, FIELD_H))
                    return takeFocus(click, doubled, r.from() + i);
            }
        }
        return false;
    }

    private boolean takeFocus(Click click, boolean doubled, int i) {
        if (i >= fields.size()) return false;
        focus(i);
        TextFieldWidget f = fields.get(i);
        if (!f.mouseClicked(click, doubled)) f.onClick(click, doubled);
        return true;
    }

    private boolean clickField(Click click, boolean doubled, int mx, int my, int i) {
        if (i >= fields.size()) return false;
        String id = has("input") ? "input" : has("material") ? "material" : null;
        if (id == null) return false;
        int fx = x + PAD, fw = inner();
        if (Value.NUMBER.equals(current().type)) {
            fx += FIELD_H + 4;
            fw -= 2 * (FIELD_H + 4);
        }
        if (!Ui.hit(mx, my, fx, py(id), fw, FIELD_H)) return false;
        return takeFocus(click, doubled, i);
    }

    private boolean suggestionClicked(int mx, int my) {
        if (!has("sugg")) return false;
        int sy = py("sugg"), sh = ph("sugg");
        int at = x + PAD + tr.getWidth(SUGG_LABEL) + 5;
        int limit = x + PAD + inner();
        for (String name : suggestions) {
            int cw = tr.getWidth(name) + 10;
            if (at + cw > limit) break;
            if (Ui.hit(mx, my, at, sy, cw, sh)) {
                fields.get(0).setText(name);
                fields.get(0).setCursorToEnd(false);
                current().name = name;
                afterTyping();
                return true;
            }
            at += cw + 3;
        }
        return false;
    }

    private void bump(double delta) {
        TextFieldWidget f = fields.get(0);
        double now = 0;
        try {
            String s = f.getText().trim().replace(',', '.');
            if (!s.isEmpty()) now = Double.parseDouble(s);
        } catch (NumberFormatException ignored) { }
        f.setText(Value.num(now + delta));
        f.setCursorToEnd(false);
        focus(0);
    }

    public boolean mouseDragged(Click click, double dx, double dy) {
        if (nested != null) return nested.mouseDragged(click, dx, dy);
        if (picker != null) return picker.mouseDragged(click, dx, dy);
        if (colors != null) return colors.mouseDragged(click, dx, dy);
        if (studio != null) return studio.mouseDragged(click, dx, dy);
        if (itemPicker != null) return itemPicker.mouseDragged(click, dx, dy);
        if (itemStudio != null) return itemStudio.mouseDragged(click, dx, dy);
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (dragSlot >= 0) { slotMoved = true; return true; }
        if (bodyBar.dragged(click.y(), 1, pane.max(), v -> pane.scroll = v)) return true;
        if (listBar.dragged(click.y(), ROW_H + 2, maxListScroll(), v -> listScroll = v)) return true;
        if (cellBar.dragged(click.y(), ROW_H + 2, maxCellScroll(current()), v -> cellScroll = v))
            return true;
        if (dragging) { moveTo((int) click.x() - dragX, (int) click.y() - dragY); return true; }
        if (scrub(click)) return true;
        if (has("player") && player.mouseDragged((int) click.x(), x + PAD, py("player"), inner(),
                SoundPlayer.H)) return true;
        if (fields.isEmpty()) return false;
        return fields.get(focus).mouseDragged(click, dx, dy);
    }

    private static boolean held(int left, int right) {
        var window = MinecraftClient.getInstance().getWindow();
        return window != null && (net.minecraft.client.util.InputUtil.isKeyPressed(window, left)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window, right));
    }

    private boolean scrub(Click click) {
        if (scrubField < 0 || scrubField >= fields.size()) return false;
        double dx = click.x() - scrubX0;
        if (!scrubbing && Math.abs(dx) < 3) return true;
        if (!scrubbing) {
            scrubbing = true;
            fields.get(scrubField).setCursorToEnd(false);
        }
        double step = held(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) ? 1.0
                : held(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 0.01 : 0.05;
        double v = scrubFrom + dx * step;
        v = Math.round(v * 1000.0) / 1000.0;
        TextFieldWidget f = fields.get(scrubField);
        f.setText(Value.num(v));
        f.setCursorToEnd(false);
        return true;
    }

    public void mouseReleased() {
        slotDropped();
        dragging = false;
        bodyBar.release();
        listBar.release();
        cellBar.release();
        if (menu != null) menu.mouseReleased();
        scrubField = -1;
        scrubbing = false;
        if (colors != null) colors.mouseReleased();
        if (studio != null) studio.mouseReleased();
        if (itemStudio != null) itemStudio.mouseReleased();
        if (itemPicker != null) itemPicker.mouseReleased();
        if (nested != null) { nested.mouseReleased(); return; }
        if (picker != null) picker.mouseReleased();
        if (player != null) player.mouseReleased();
    }

    private void moveTo(int nx, int ny) {
        x = Math.max(4, Math.min(Math.max(4, screenW - w - 4), nx));
        y = Math.max(4, Math.min(Math.max(4, screenH - h - 4), ny));
        placeFields();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (nested != null) return nested.mouseScrolled(mx, my, amount);
        if (picker != null) return picker.mouseScrolled(mx, my, amount);
        if (colors != null) return colors.mouseScrolled(mx, my, amount);
        if (studio != null) return studio.mouseScrolled(mx, my, amount);
        if (itemPicker != null) return itemPicker.mouseScrolled(mx, my, amount);
        if (itemStudio != null) return itemStudio.mouseScrolled(mx, my, amount);
        if (menu != null) return menu.mouseScrolled(mx, my, amount);
        if (complete.mouseScrolled(mx, my, amount)) return true;
        if (has("cells")) {
            Value v = current();
            if (maxCellScroll(v) > 0 && Ui.hit(mx, my, x + PAD, py("cells"), inner(),
                    cellRows(v) * (ROW_H + 2))) {
                cellScroll = Math.max(0, Math.min(maxCellScroll(v),
                        cellScroll - (int) Math.signum(amount)));
                return true;
            }
        }
        if (list() && maxListScroll() > 0 && Ui.hit(mx, my, x + PAD, listY() + CAP, inner(),
                listRows() * (ROW_H + 2))) {
            listScroll = Math.max(0, Math.min(maxListScroll(),
                    listScroll - (int) Math.signum(amount)));
            return true;
        }
        if (Value.NUMBER.equals(current().type)
                && Ui.hit(mx, my, x + PAD, py("input"), inner(), FIELD_H)) {
            bump(Math.signum(amount));
            return true;
        }
        if (pane.max() > 0 && contains(mx, my)) {
            pane.wheel(amount);
            placeFields();
            return true;
        }
        return contains(mx, my);
    }

    public boolean keyPressed(KeyInput input) {
        if (nested != null) {
            nested.keyPressed(input);
            if (nested.isClosed()) closeNested();
            return true;
        }
        if (picker != null) {
            picker.keyPressed(input);
            if (picker.isClosed()) closePicker();
            return true;
        }
        if (colors != null) {
            colors.keyPressed(input);
            if (colors.isClosed()) { colors = null; rebuild = true; }
            return true;
        }
        if (studio != null) {
            studio.keyPressed(input);
            if (studio.isClosed()) studio = null;
            return true;
        }
        if (itemPicker != null) {
            itemPicker.keyPressed(input);
            if (itemPicker.isClosed()) { itemPicker = null; rebuildIfNeeded(); }
            return true;
        }
        if (itemStudio != null) {
            itemStudio.keyPressed(input);
            if (itemStudio.isClosed()) closeItemStudio();
            return true;
        }
        int key = input.key();
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (complete.keyPressed(input)) return true;
        if (key == GLFW.GLFW_KEY_SPACE && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && has("player")) {
            Audio.toggle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancelled = true; closed = true; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (fields.size() > 1 && focus < fields.size() - 1) { focus(focus + 1); return true; }
            if (canAdd() && sel == values.size() - 1 && !current().isBlank()) {
                readForm();
                values.add(Value.of(Values.defaultKind(arg.type)));
                sel = values.size() - 1;
                buildForm();
                return true;
            }
            commit();
            closed = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && fields.size() > 1) {
            focus((focus + 1) % fields.size());
            return true;
        }
        if (fields.isEmpty()) return true;
        boolean used = fields.get(focus).keyPressed(input);
        afterTyping();
        return used;
    }

    public boolean charTyped(CharInput input) {
        if (nested != null) return nested.charTyped(input);
        if (picker != null) return picker.charTyped(input);
        if (colors != null) return colors.charTyped(input);
        if (studio != null) return studio.charTyped(input);
        if (itemPicker != null) return itemPicker.charTyped(input);
        if (itemStudio != null) return itemStudio.charTyped(input);
        if (menu != null) return true;
        if (fields.isEmpty()) return true;
        boolean used = fields.get(focus).charTyped(input);
        afterTyping();
        return used;
    }

    private void afterTyping() {
        Value v = current();
        if (Value.VARIABLE.equals(v.type) || Value.PARAMETER.equals(v.type)) {
            boolean had = has("sugg");
            refreshSuggestions();
            if (had != !suggestions.isEmpty()) {
                String text = fields.get(0).getText();
                int cursor = fields.get(0).getCursor();
                v.name = text.trim();
                buildForm();
                focus(0);
                fields.get(0).setText(text);
                fields.get(0).setCursor(cursor, false);
            }
        }
        syncComplete();
    }

    private boolean completable() {
        if (fields.isEmpty() || focus != 0) return false;
        String type = current().type;
        return Value.TEXT.equals(type) || Value.VARIABLE.equals(type);
    }

    private void syncComplete() {
        complete.update(completable() ? fields.get(focus) : null, tr, screenW, screenH);
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void readForm() {
        if (fields.isEmpty()) return;
        Value v = current();
        switch (v.type) {
            case Value.TEXT -> v.text = fields.get(0).getText();
            case Value.NUMBER -> v.number = parse(fields.get(0).getText());
            case Value.VARIABLE, Value.PARAMETER -> v.name = fields.get(0).getText().trim();
            case Value.LOCATION -> {
                v.x = parse(fields.get(0).getText()); v.y = parse(fields.get(1).getText());
                v.z = parse(fields.get(2).getText()); v.yaw = parse(fields.get(3).getText());
                v.pitch = parse(fields.get(4).getText());
            }
            case Value.VECTOR -> {
                v.x = parse(fields.get(0).getText()); v.y = parse(fields.get(1).getText());
                v.z = parse(fields.get(2).getText());
            }
            case Value.SOUND -> {
                v.volume = parse(fields.get(0).getText());
                v.pitch2 = parse(fields.get(1).getText());
            }
            case Value.PARTICLE -> {
                v.count = Math.max(0, (int) parse(fields.get(0).getText()));
                v.spread1 = parse(fields.get(1).getText());
                v.spread2 = parse(fields.get(2).getText());
                FieldRow m = rowOf("motion");
                if (m != null) {
                    v.mx = parse(fields.get(m.from()).getText());
                    v.my = parse(fields.get(m.from() + 1).getText());
                    v.mz = parse(fields.get(m.from() + 2).getText());
                }
                FieldRow sz = rowOf("size");
                if (sz != null) v.size = parse(fields.get(sz.from()).getText());
                if (has("material")) v.material = fields.get(fields.size() - 1).getText().trim();
            }
            case Value.POTION -> {
                v.duration = (int) parse(fields.get(0).getText());
                v.amplifier = Math.max(0, (int) parse(fields.get(1).getText()) - 1);
            }
            case Value.ITEM -> v.itemCount = Math.max(1, Math.min(99,
                    (int) parse(fields.get(0).getText())));
            default -> { }
        }
    }

    public void dispose() {
        Audio.release();
    }

    public void commit() {
        if (committed) return;
        committed = true;
        readForm();
        if (cell != null) {
            cell.apply(current());
            changed = !json(values).equals(before);
            return;
        }
        if (localizedField() != null) {
            byLang.put(lang, copies(values));
            for (Localized.Lang l : Localized.LANGS) {
                List<Value> list = byLang.get(l.id());
                if (list == null) continue;
                if (l.id().isEmpty()) store(list);
                else writeTranslation(l.id(), list);
            }
            changed = !allJson().equals(before);
            return;
        }
        store(values);
        changed = !json(node.values.get(argIndex)).equals(before);
    }

    private void store(List<Value> from) {
        List<Value> out = node.valuesOf(argIndex);
        out.clear();
        int cap = list() ? arg.capacity : 1;
        if (slots() != null) {
            int last = -1;
            for (int i = 0; i < from.size(); i++) if (!from.get(i).isBlank()) last = i;
            for (int i = 0; i <= last && i < cap; i++) out.add(from.get(i));
            return;
        }
        for (Value v : from) {
            if (v.isBlank() || out.size() >= cap) continue;
            out.add(v);
        }
    }
}
