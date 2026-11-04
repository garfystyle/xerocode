package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Audio;
import com.xerocode.Blocks;
import com.xerocode.Catalog;
import com.xerocode.Clip;
import com.xerocode.Env;
import com.xerocode.Functions;
import com.xerocode.Localized;
import com.xerocode.Pickers;
import com.xerocode.Script;
import com.xerocode.Stacks;
import com.xerocode.Ticks;
import com.xerocode.Value;
import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class ValueEditor implements Overlay {
    private static final int PAD = 10;
    private static final int HEAD_H = 26;
    private static final int CAP = 11;
    private static final int GAP = 5;
    private static final int KIND_GAP = 3;
    private static final int WIDTH = 280;
    private static final int LIST_MAX = 5, LIST_MAX_COMPACT = 3;

    private static final List<String> PARAM_TYPES = Values.PARAM_TYPES;
    private static final String ANY_TYPE = PARAM_TYPES.get(0);
    private static final int FLAG_GAP = 3;

    private static final List<String> PARAM_KINDS = List.of(Value.SINGULAR, Value.PLURAL, Value.ENUM);
    private static final List<String> PARAM_KIND_NAMES = List.of("одиночное", "множество", "маркер");

    private static final String[][] COORD_CAPS = {
            {"X", "Y", "Z"}, {"X", "Y", "Z", "yaw", "pitch"}};
    private static final String[] XYZ = COORD_CAPS[0];
    private static final int STEP_GAP = 2, STEP_PX = 6, STEP_PX_COARSE = 10;
    private static final double[] LINEAR = {0.01, 0.1, 1}, ANGLE = {0.1, 1, 15};
    private static final double[] WHOLE = {1, 1, 10}, PLAIN = {0.1, 1, 10};
    private static final double[] NUDGE = {1, 0.1, 0.01}, TURN = {45, 90};
    private static final Set<String> WHOLE_CAPS =
            Set.of("СКОЛЬКО", "ДЛИТЕЛЬНОСТЬ В ТИКАХ", "УРОВЕНЬ", "КОЛИЧЕСТВО");
    private static final String UNIFORM = "равномерно";
    private static final String[][] ANGLE_TIPS = {
            {"Горизонтальный поворот", "от −360 до 360"}, {"Вертикальный наклон", "от −90 до 90"}};

    private static List<Values.Kind> kinds() { return Values.KINDS; }

    private String onlyKind;
    private String note;

    public void note(String text) { note = text; }

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
    private final Font tr;
    private int screenW, screenH;
    private final int anchorX, anchorY;
    private final boolean touch = Env.touch();
    private final int fieldH = touch ? 26 : 20, ctrlH = touch ? 22 : 17, rowH = touch ? 22 : 17;
    private final int addH = touch ? 20 : 15, btnH = touch ? 24 : 18, kindH = touch ? 28 : 20;
    private final int stepH = touch ? 24 : 14, footH = touch ? 34 : 26, footBtnH = touch ? 24 : 16;
    private final int okW = touch ? 84 : 56, noW = touch ? 76 : 52, kindW = touch ? 40 : 20;
    private final List<NamePick.Known> knownVars;
    private final List<String> knownParams;

    private final List<Value> values = new ArrayList<>();
    private int sel;

    private final List<EditBox> fields = new ArrayList<>();
    private Ui.Chips paramChips, targetChips, scopeChips, modeChips, sourceChips;
    private Ui.Chips kindChips;
    private int newElemAt = -1;
    private final Ui.Bar bodyBar = new Ui.Bar(), listBar = new Ui.Bar(), cellBar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();
    private int lastMx, lastMy;
    private int dragSlot = -1;
    private boolean slotMoved;
    private static final int CELL_NONE = 0, CELL_VALUE = 1, CELL_KEY = 2;
    private static final int CELL_MOVE_PX = 3;
    private static final long EDGE_SCROLL_MS = 90;
    private int cellDrag = -1, cellAction, cellGrabY;
    private double cellX0, cellY0;
    private boolean cellMoved;
    private long cellEdgeAt;
    private final Langs langs;
    private SoundPlayer player;
    private ParticleStage stage;

    private Menu menu;
    private final Complete complete = new Complete();
    private final NamePick names = new NamePick();
    private TextStudio studio;
    private TextLines lines;
    private ColorPick colors;
    private PickerPanel picker;
    private ItemPicker itemPicker;
    private ItemStudio itemStudio;
    private NumberStudio numbers;
    private final Sizer sizer = new Sizer("value", 220, 120, false, false);
    private ValueEditor nested;
    private int listScroll, cellScroll;
    private boolean rebuild;

    private String before;
    private int x, y, w, h;
    private int focus;
    private boolean closed, committed, changed, compact, placed;
    private String flash;
    private boolean flashBad;
    private long flashAt;
    private static final long FLASH_MS = 1800;
    private final Ui.Pane pane = new Ui.Pane();
    private int naturalH;
    private boolean pickInWorld, worldAllowed;
    private boolean cancelled;
    private boolean dragging;
    private int dragX, dragY;

    private int scrubField = -1;
    private double scrubFrom, scrubX0;
    private boolean scrubbing;
    private final Set<String> uniform = new HashSet<>();
    private final Map<String, Integer> stepCols = new HashMap<>();
    private final Map<Integer, Map<String, Value>> remembered = new java.util.HashMap<>();

    public interface Cell { void apply(Value value); }

    private final Cell cell;

    private ValueEditor(Script.Node node, int argIndex, Catalog.Arg arg, Cell cell,
                        Font tr, int anchorX, int anchorY, int screenW, int screenH,
                        List<NamePick.Known> knownVars, List<String> knownParams) {
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
        this.w = frameW(screenW);
        this.langs = new Langs(node, argIndex, arg != null && arg.list);
    }

    public ValueEditor(Script.Node node, int argIndex, Font tr,
                       int anchorX, int anchorY, int screenW, int screenH,
                       List<NamePick.Known> knownVars, List<String> knownParams) {
        this(node, argIndex, node.args().get(argIndex), null, tr, anchorX, anchorY,
                screenW, screenH, knownVars, knownParams);
        int other = node.dynArgs == null && localizedField() == null
                ? Catalog.pairOf(node.action, argIndex) : -1;
        if (other >= 0) {
            left = Math.min(argIndex, other);
            right = Math.max(argIndex, other);
            values.add(pairTable());
            before = pairJson();
            buildForm();
            return;
        }
        List<Value> existing = node.values.get(argIndex);
        if (existing != null) for (Value v : existing) values.add(v.copy());
        if (localizedField() != null) {
            langs.load(values);
            this.before = langs.json();
        } else {
            this.before = json(values);
        }
        if (values.isEmpty()) values.add(Value.of(Values.defaultKind(arg.type)));
        buildForm();
    }

    private int left = -1, right = -1;

    private boolean paired() { return left >= 0; }

    private Catalog.Arg leftArg() { return node.args().get(left); }

    private Catalog.Arg rightArg() { return node.args().get(right); }

    private record PairNames(String left, String right, String tail) {}

    private PairNames pairNames() {
        String[] a = leftArg().purpose.trim().split(" "), b = rightArg().purpose.trim().split(" ");
        int k = 0;
        while (k < a.length - 1 && k < b.length - 1
                && a[a.length - 1 - k].equalsIgnoreCase(b[b.length - 1 - k])) k++;
        String l = String.join(" ", java.util.Arrays.copyOf(a, a.length - k));
        String r = String.join(" ", java.util.Arrays.copyOf(b, b.length - k));
        String tail = String.join(" ", java.util.Arrays.copyOfRange(a, a.length - k, a.length));
        return new PairNames(beforeFor(l), beforeFor(r), tail);
    }

    private static String beforeFor(String s) {
        int cut = s.indexOf(" для ");
        return cut > 0 ? s.substring(0, cut) : s;
    }

    private static String lowerFirst(String s) {
        if (s.length() < 2 || Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String pairTitle(int room) {
        PairNames n = pairNames();
        String both = n.left() + " и " + lowerFirst(n.right());
        String full = n.tail().isEmpty() ? both : both + " " + n.tail();
        return tr.width(full) <= room ? full : both;
    }

    private Value pairTable() {
        Value t = Value.of(Value.MAP);
        List<Value> a = node.values.get(left), b = node.values.get(right);
        int n = Math.max(a == null ? 0 : a.size(), b == null ? 0 : b.size());
        for (int i = 0; i < n; i++) {
            t.keys.add(a != null && i < a.size() ? a.get(i).copy() : Value.of(Values.defaultKind(leftArg().type)));
            t.items.add(b != null && i < b.size() ? b.get(i).copy() : Value.of(Values.defaultKind(rightArg().type)));
        }
        return t;
    }

    private String pairJson() {
        StringBuilder sb = new StringBuilder();
        for (int side : new int[]{left, right}) {
            List<Value> list = node.values.get(side);
            if (list != null) for (Value v : list) sb.append(v.toJson()).append(',');
            sb.append('|');
        }
        return sb.toString();
    }

    private void storePairs() {
        Value t = values.get(0);
        List<Value> a = node.valuesOf(left), b = node.valuesOf(right);
        a.clear();
        b.clear();
        int cap = Math.min(leftArg().capacity, rightArg().capacity);
        for (int i = 0; i < Math.max(t.keys.size(), t.items.size()) && a.size() < cap; i++) {
            Value k = i < t.keys.size() ? t.keys.get(i) : Value.blank();
            Value v = i < t.items.size() ? t.items.get(i) : Value.blank();
            if (k.isBlank() && v.isBlank()) continue;
            a.add(k.isBlank() ? Value.blank() : k);
            b.add(v.isBlank() ? Value.blank() : v);
        }
    }

    private String localizedField() { return Langs.field(node, argIndex); }

    private void switchLang(String id) {
        if (id.equals(langs.current())) return;
        readForm();
        List<Value> now = langs.swap(id, values);
        values.clear();
        values.addAll(now);
        if (values.isEmpty()) values.add(Value.of(Values.defaultKind(arg.type)));
        sel = 0;
        listScroll = 0;
        buildForm();
    }

    private ValueEditor(Value value, Catalog.Arg arg, Font tr,
                        int anchorX, int anchorY, int screenW, int screenH,
                        List<NamePick.Known> knownVars, List<String> knownParams, Cell done) {
        this(null, -1, arg, done, tr, anchorX, anchorY, screenW, screenH, knownVars, knownParams);
        values.add(value.copy());
        this.before = json(values);
        buildForm();
    }

    public static ValueEditor forCell(Value value, String purpose, String onlyKind,
                                      Font tr, int anchorX, int anchorY,
                                      int screenW, int screenH, List<NamePick.Known> knownVars,
                                      List<String> knownParams, Cell done) {
        String type = onlyKind == null ? "" : Values.kindName(onlyKind);
        Catalog.Arg arg = Catalog.TYPE_COLORS.containsKey(type)
                ? Catalog.Arg.cell(purpose, type) : Catalog.Arg.cell(purpose);
        ValueEditor editor = new ValueEditor(value, arg, tr, anchorX, anchorY,
                screenW, screenH, knownVars, knownParams, done);
        editor.onlyKind = onlyKind;
        return editor;
    }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        screenW = sw;
        screenH = sh;
        complete.reset();
        w = frameW(screenW);
        compact = false;
        placed = false;
        if (nested != null) nested.resize(sw, sh);
        if (picker != null) picker.resize(sw, sh);
        if (itemPicker != null) itemPicker.resize(sw, sh);
        if (itemStudio != null) itemStudio.resize(sw, sh);
        if (studio != null) studio.resize(sw, sh);
        if (lines != null) lines.resize(sw, sh);
        if (numbers != null) numbers.resize(sw, sh);
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
        chosen(current());
        clampListScroll();
        buildForm();
    }

    static String json(List<Value> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Value v : list) if (!v.isBlank()) sb.append(v.toJson()).append(',');
        return sb.append(']').toString();
    }

    private Value current() { return values.get(Math.max(0, Math.min(values.size() - 1, sel))); }

    private static void chosen(Value v) {
        if (Value.ARRAY.equals(v.type) || Value.MAP.equals(v.type)) v.present = true;
    }

    private boolean list() { return arg.list && !paired(); }
    private boolean canAdd() { return list() && values.size() < arg.capacity; }
    private int inner() { return w - PAD * 2; }

    public boolean isClosed() { return closed; }
    public boolean cancelled() { return cancelled; }
    public boolean changed() { return changed; }
    public Script.Node node() { return node; }
    public int argIndex() { return argIndex; }
    public int selected() { return sel; }
    public boolean pickInWorld() { return pickInWorld; }

    public void allowWorld() { worldAllowed = true; }

    public Value edited() { return current(); }


    private final Form form = new Form(CAP, GAP);

    private void cap(String text)          { form.cap(text); }
    private void part(String id, int h)    { form.part(id, h); }
    private void gap()                     { form.gap(); }

    private void textInput(String id, String caption, String value, String hint) {
        form.row(id, fields.size(), new String[]{caption});
        fields.add(field(value, hint));
        part(id, 9 + fieldH);
    }

    private void inputs(String id, String[] caps, String... values) {
        form.row(id, fields.size(), caps);
        for (String value : values) fields.add(field(value, "0"));
        part(id, 9 + fieldH);
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

    private Form.Row rowOf(String id) { return form.row(id); }

    private int colW(Form.Row r) { return r.colW(inner()); }

    private int colX(Form.Row r, int i) { return r.colX(x + PAD, inner(), i); }

    private int py(String id) { return formY() + form.dy(id); }

    private int ph(String id) { return form.h(id); }

    private boolean has(String id) { return form.has(id); }

    private boolean rowHit(String id, double mx, double my) {
        return has(id) && Ui.hit(mx, my, x + PAD, py(id), inner(), ph(id));
    }

    private EditBox field(String text, String placeholder) {
        return Ui.field(tr, text, placeholder, 256);
    }

    private void buildForm() {
        Catalog.Slots grid = slots();
        if (grid != null) padSlots(grid);
        complete.reset();
        fields.clear();
        form.clear();
        paramChips = null;
        kindChips = null;
        newElemAt = -1;
        targetChips = null;
        scopeChips = null;
        modeChips = null;
        sourceChips = null;
        player = null;
        stage = null;
        names.reset();
        focus = 0;

        Value v = current();
        switch (v.type) {
            case Value.TEXT -> {
                EditBox f = Ui.field(tr, v.text, arg.purpose, Ui.TEXT_MAX);
                f.addFormatter(Tint.of(f, () -> current().parsing));
                fields.add(f);
                cap("ТЕКСТ");
                part("input", fieldH);
                gap();
                cap("РЕЖИМ РАЗМЕТКИ");
                List<String> modes = new ArrayList<>();
                for (Values.Parsing p : Values.PARSINGS) modes.add(p.name());
                modeChips = new Ui.Chips(tr, modes, inner(), ctrlH - 2, 3);
                part("mode", modeChips.height());
                gap();
                if (!compact) {
                    cap("ПРЕДПРОСМОТР");
                    part("preview", 24);
                    gap();
                }
                part("studio", btnH);
            }
            case Value.NUMBER -> {
                EditBox f = field(v.numberString(), "0");
                f.addFormatter(Tint.of(f, null));
                fields.add(f);
                cap("ЗНАЧЕНИЕ");
                part("input", fieldH);
                if (!compact) part("note", 12);
            }
            case Value.VARIABLE -> {
                nameField(v, "имя переменной", "ИМЯ ПЕРЕМЕННОЙ");
                cap("ТИП ПЕРЕМЕННОЙ");
                List<String> scopes = new ArrayList<>();
                for (Values.Scope s : Values.SCOPES) scopes.add(s.name());
                scopeChips = new Ui.Chips(tr, scopes, inner(), ctrlH - 2, 3);
                part("scope", scopeChips.height());
            }
            case Value.PARAMETER -> {
                nameField(v, "имя параметра", "ИМЯ ПАРАМЕТРА");
                textInput("pdesc", "ОПИСАНИЕ", Localized.text(v.paramDesc), "подсказка в сундуке вызова");
                gap();
                cap("ВИД ПАРАМЕТРА");
                kindChips = new Ui.Chips(tr, PARAM_KIND_NAMES, inner(), ctrlH - 2, 3);
                part("pkinds", kindChips.height());
                if (Value.ENUM.equals(v.typeKey)) {
                    gap();
                    cap("ВАРИАНТЫ");
                    part("elems", Math.max(1, v.elements.size()) * (rowH + 2) - 2);
                    gap();
                    newElemAt = fields.size();
                    fields.add(field("", "новый вариант"));
                    part("newelem", fieldH);
                } else {
                    gap();
                    cap("ЧТО ПРИНИМАЕТ");
                    paramChips = new Ui.Chips(tr, Values.paramTypeNames(), inner(), ctrlH - 2, 3);
                    part("ptypes", paramChips.height());
                    gap();
                    part("flags", Value.PLURAL.equals(v.typeKey) ? ctrlH * 2 + FLAG_GAP : ctrlH);
                    gap();
                    cap("ПО УМОЛЧАНИЮ");
                    part("pdefault", rowH);
                }
            }
            case Value.LOCATION, Value.VECTOR -> {
                boolean loc = Value.LOCATION.equals(v.type);
                cap(loc ? "КООРДИНАТЫ" : "НАПРАВЛЕНИЕ");
                if (loc) inputs("coords", COORD_CAPS[1], Value.num(v.x), Value.num(v.y),
                        Value.num(v.z), Value.num(v.yaw), Value.num(v.pitch));
                else inputs("coords", XYZ, Value.num(v.x), Value.num(v.y), Value.num(v.z));
                part("coords+", stepH + 4);
                if (loc && (node != null || worldAllowed) && !Env.browser()) {
                    gap();
                    part("world", btnH);
                }
            }
            case Value.SOUND -> {
                cap("ЗВУК");
                Pickers.Entry se = Pickers.sound(v.sound);
                part("card", cardH(se == null ? "" : se.name, se == null ? "" : se.description));
                player = new SoundPlayer(tr, v, Values.color(Value.SOUND));
                if (se != null) { gap(); part("player", SoundPlayer.H); }
                gap();
                part("choose", btnH);
                gap();
                inputs("nums", new String[]{"ГРОМКОСТЬ", "ТОН"},
                        Value.num(v.volume), Value.num(v.pitch2));
                gap();
                cap("КАНАЛ");
                List<String> sources = new ArrayList<>();
                for (String sid : Pickers.SOURCES) sources.add(Pickers.sourceName(sid));
                sourceChips = new Ui.Chips(tr, sources, inner(), ctrlH - 2, 3);
                part("source", sourceChips.height());
            }
            case Value.PARTICLE -> {
                cap("ЧАСТИЦА");
                Pickers.Entry ce = Pickers.particle(v.particle);
                part("card", cardH(ce == null ? "" : ce.name, ce == null ? "" : ce.description));
                stage = new ParticleStage(v);
                if (ce != null && !compact) { gap(); part("stage", ParticleStage.H); }
                gap();
                part("choose", btnH);
                gap();
                inputs("nums", new String[]{"СКОЛЬКО", "РАЗБРОС", "РАЗБРОС ПО Y"},
                        String.valueOf(v.count), Value.num(v.spread1), Value.num(v.spread2));
                Pickers.Entry pe = Pickers.particle(v.particle);
                if (pe != null && pe.has(Pickers.MOTION)) {
                    gap();
                    cap("ДВИЖЕНИЕ");
                    inputs("motion", XYZ, Value.num(v.mx), Value.num(v.my), Value.num(v.mz));
                    part("motion+", stepH + 4);
                }
                if (pe != null && pe.has(Pickers.SIZE)) {
                    gap();
                    inputs("size", new String[]{"РАЗМЕР"}, Value.num(v.size));
                }
                if (pe != null && pe.has(Pickers.MATERIAL)) {
                    gap();
                    cap("МАТЕРИАЛ");
                    fields.add(field(v.material, "minecraft:stone"));
                    part("material", fieldH);
                }
                if (pe != null && (pe.has(Pickers.COLOR) || pe.has(Pickers.TO_COLOR))) {
                    gap();
                    cap(pe.has(Pickers.TO_COLOR) ? "ЦВЕТ И ЦВЕТ ПЕРЕХОДА" : "ЦВЕТ");
                    part("color", ctrlH);
                }
            }
            case Value.POTION -> {
                cap("ЭФФЕКТ");
                Pickers.Entry oe = Pickers.potion(v.potion);
                part("card", cardH(oe == null ? "" : oe.name, oe == null ? "" : oe.description));
                gap();
                part("choose", btnH);
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
                cap(paired() ? "" : "ПАРЫ КЛЮЧ — ЗНАЧЕНИЕ");
                part("cells", cellsH(v));
            }
            case Value.ITEM -> {
                cap("ПРЕДМЕТ");
                part("card", cardH(v.itemId.isEmpty() ? "" : Stacks.plainName(v),
                        Stacks.summary(v)));
                gap();
                part("choose", btnH);
                gap();
                part("tools", btnH);
                gap();
                inputs("nums", new String[]{"КОЛИЧЕСТВО"}, String.valueOf(v.itemCount));
            }
            case Value.BLOCK -> {
                cap("БЛОК");
                Blocks.Entry be = Blocks.entry(v.block);
                part("card", cardH(be == null ? "" : be.name(), ""));
                gap();
                part("choose", btnH);
                if (hand()) {
                    gap();
                    part("tools", btnH);
                }
            }
            case Value.GAME_VALUE -> {
                cap("ЗНАЧЕНИЕ");
                Values.GameValue gv = Values.gameValue(v.gameValue);
                part("card", cardH(gv == null ? "" : gv.name, gv == null ? "" : gv.description));
                gap();
                part("choose", btnH);
                gap();
                cap("ЦЕЛЬ");
                if (compact) {
                    part("target", ctrlH);
                } else {
                    List<String> names = new ArrayList<>();
                    for (Values.Selector s : Values.SELECTORS) names.add(s.name());
                    targetChips = new Ui.Chips(tr, names, inner(), ctrlH - 2, 3);
                    part("target", targetChips.height());
                }
            }
            default -> part("unavailable", ctrlH);
        }

        clampListScroll();
        layout();
        if (h < naturalH && !compact) {
            compact = true;
            buildForm();
            return;
        }
        if (!fields.isEmpty()) {
            focus(0);
            selectAll(fields.get(0));
            syncComplete();
        }
        live();
    }

    private void live() {
        if (has("player") || has("stage")) readForm();
    }

    private void nameField(Value v, String hint, String caption) {
        EditBox f = field(v.name, hint);
        f.addFormatter(Tint.of(f, null));
        fields.add(f);
        cap(caption);
        part("input", fieldH);
        gap();
    }

    private int railH() {
        if (paired()) return -CAP;
        return localizedField() != null ? langsH() : grid().height(kinds().size());
    }

    private Ui.Grid grid() {
        int n = kinds().size();
        int inner = inner();
        int cols = n, cell = kindCell(inner, cols);
        for (int rows = 2; rows <= n && cell < kindW; rows++) {
            cols = (n + rows - 1) / rows;
            cell = kindCell(inner, cols);
        }
        int row = cols * cell + (cols - 1) * KIND_GAP;
        return new Ui.Grid(x + PAD + (inner - row) / 2, railY(), cols, cell, kindH, KIND_GAP);
    }

    private static int kindCell(int inner, int cols) {
        return Math.max(16, ((inner - (cols - 1) * KIND_GAP) / cols) & ~1);
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

    private int cellMax(Value v) {
        if (paired()) return Math.min(leftArg().capacity, rightArg().capacity);
        return Value.MAP.equals(v.type) ? Value.MAP_MAX : Value.ARRAY_MAX;
    }

    private boolean canAddCell(Value v) { return cellCount(v) < cellMax(v); }

    private int cellRows(Value v) {
        return Math.max(1, Math.min(cellCount(v), listMax()));
    }

    private int cellsH(Value v) {
        return cellRows(v) * (rowH + 2) + (canAddCell(v) ? addH + 2 : 0);
    }

    private int maxCellScroll(Value v) { return Math.max(0, cellCount(v) - listMax()); }

    private void openCell(Value holder, boolean key, int index) {
        readForm();
        List<Value> where = key ? holder.keys : holder.items;
        while (where.size() <= index) where.add(Value.blank());
        Value now = where.get(index);
        String purpose = paired()
                ? (key ? pairNames().left() : pairNames().right()) + ", строка " + (index + 1)
                : Value.MAP.equals(holder.type)
                ? (key ? "Ключ " + (index + 1) : "Значение " + (index + 1))
                : "Значение " + (index + 1) + " в списке";
        nested = new ValueEditor(now, Catalog.Arg.cell(purpose), tr, x + 12, y + 12,
                screenW, screenH, knownVars, knownParams, edited -> where.set(index, edited));
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

    private void drawDraggedSlot(GuiGraphicsExtractor ctx) {
        Catalog.Slots grid = slots();
        if (dragSlot < 0 || !slotMoved || grid == null || dragSlot >= values.size()) return;
        Value it = values.get(dragSlot);
        if (it.isBlank()) return;
        ctx.nextStratum();
        ctx.item(Stacks.preview(it), lastMx - 8, lastMy - 8);
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

    private Overlay child() {
        if (nested != null) return nested;
        if (picker != null) return picker;
        if (colors != null) return colors;
        if (studio != null) return studio;
        if (lines != null) return lines;
        if (itemPicker != null) return itemPicker;
        if (numbers != null) return numbers;
        return itemStudio;
    }

    private void settle(Overlay top) {
        if (!top.isClosed()) return;
        if (top == nested) { closeNested(); return; }
        if (top == picker) picker = null;
        else if (top == colors) { colors = null; rebuild = true; }
        else if (top == studio) studio = null;
        else if (top == lines) lines = null;
        else if (top == itemPicker) itemPicker = null;
        else if (top == itemStudio) itemStudio = null;
        else if (top == numbers) numbers = null;
        rebuildIfNeeded();
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
        return CAP + listRows() * (rowH + 2) + (canAdd() ? addH + 2 : 0) + GAP;
    }

    private int maxListScroll() { return Math.max(0, values.size() - listMax()); }

    private void clampListScroll() {
        if (sel < listScroll) listScroll = sel;
        else if (sel >= listScroll + listMax()) listScroll = sel - listMax() + 1;
        listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
    }

    private int frameW(int sw) {
        return touch ? Math.max(60, sw - Ui.margin(sw) * 2) : sizer.w(sw, Ui.fitW(sw, WIDTH));
    }

    private void reflow() {
        int was = focus;
        int at = fields.isEmpty() ? 0 : fields.get(focus).getCursorPosition();
        w = frameW(screenW);
        compact = false;
        readForm();
        buildForm();
        if (was <= 0 || was >= fields.size()) {
            if (!fields.isEmpty()) {
                EditBox f = fields.get(0);
                int p = Math.min(at, f.getValue().length());
                f.setCursorPosition(p);
                f.setHighlightPos(p);
            }
            return;
        }
        focus(was, false);
        EditBox f = fields.get(was);
        int p = Math.min(at, f.getValue().length());
        f.setCursorPosition(p);
        f.setHighlightPos(p);
    }

    private boolean numberToggle() { return !paired() && Value.NUMBER.equals(current().type); }

    private int toggleX() { return x + w - PAD - 14 - 6 - 14; }

    private void openNumbers() {
        readForm();
        EditBox f = fields.get(0);
        String title = arg.purpose.isEmpty() ? "Число" : arg.purpose;
        numbers = new NumberStudio(tr, screenW, screenH, title, f.getValue(), f.getCursorPosition(),
                Values.color(Value.NUMBER), timeUnit(), (text, at) -> {
                    focus(0, false);
                    EditBox g = fields.get(0);
                    g.setValue(text);
                    g.setCursorPosition(at);
                    g.setHighlightPos(at);
                    afterTyping();
                });
    }

    private void layout() {
        int contentH = HEAD_H + 4 + listH() + CAP + railH() + GAP + 4 + form.height() + 7;
        naturalH = contentH + footH;
        int auto = Ui.fitH(screenH, naturalH);
        h = touch ? Math.max(60, screenH - Ui.margin(screenH) * 2)
                  : Math.min(auto, sizer.h(screenH, auto));
        if (!placed && touch) {
            placed = true;
            x = Ui.margin(screenW);
            y = Ui.margin(screenH);
        } else if (!placed) {
            placed = true;
            int reserve = Math.min(screenH - 8, Math.max(h, 300));
            x = Ui.anchorX(screenW, anchorX, w);
            y = anchorY + reserve > screenH - 4 ? Math.max(4, screenH - reserve - 4)
                                                : Math.max(4, anchorY);
        } else if (y + h > screenH - 4) {
            y = Math.max(4, screenH - h - 4);
        }
        pane.fit(HEAD_H, h - footH, contentH);
        placeFields();
    }

    private int listY() { return y + pane.at(HEAD_H + 4); }
    private int railY() { return listY() + listH() + CAP; }
    private int formY() { return railY() + railH() + GAP + 4; }
    private int footY() { return y + h - footH; }

    private void placeFields() {
        form.place(fields, x + PAD, inner(), formY(), fieldH, Value.NUMBER.equals(current().type));
        placeNewElement();
    }

    private static final int[] AXIS_INK = {0xF0605E, 0x8FD94F, 0x5B8CF5, 0xFFD54A, 0xFFD54A};

    private static boolean scrubbable(Form.Row r) {
        return r != null && switch (r.id()) {
            case "coords", "nums", "motion", "size" -> true;
            default -> false;
        };
    }

    private void drawFieldRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, Form.Row r) {
        if (r == null) return;
        boolean axes = stepRow(r);
        boolean drag = scrubbable(r);
        int cw = colW(r), ry = py(r.id());
        for (int i = 0; i < r.count(); i++) {
            int cx = colX(r, i);
            int ink = axes && i < AXIS_INK.length ? AXIS_INK[i] : Theme.TEXT_FAINT;
            if (drag && Ui.hit(mouseX, mouseY, cx, ry, cw, 9))
                ctx.requestCursor(CursorTypes.RESIZE_EW);
            else if (Ui.hit(mouseX, mouseY, cx, ry + 9, cw, fieldH))
                ctx.requestCursor(CursorTypes.IBEAM);
            Draw.textFit(ctx, tr, r.caps()[i], cx + 2, ry, cw - 4, ink, false);
            Ui.input(ctx, cx, ry + 9, cw, fieldH, r.from() + i == focus);
            if (axes && i < AXIS_INK.length)
                Draw.roundRect(ctx, cx + 1, ry + 10, 3, fieldH - 2,
                        Ui.R_SM - 1, 0, 0, Ui.R_SM - 1, Draw.opaque(ink));
            fields.get(r.from() + i).extractRenderState(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, tr, fields.get(r.from() + i));
            if (angle(r, i) && Ui.hit(mouseX, mouseY, cx, ry, cw, 9 + fieldH)) {
                String[] tip = ANGLE_TIPS[i - 3];
                ctx.setComponentTooltipForNextFrame(tr, List.of(Component.literal(tip[0]),
                        Component.literal(tip[1]).withColor(Theme.TEXT_DIM)), mouseX, mouseY);
            }
        }
        if (stepRow(r)) {
            drawSteps(ctx, mouseX, mouseY, r);
            drawUniform(ctx, mouseX, mouseY, r);
        }
    }

    private static boolean stepRow(Form.Row r) {
        return r != null && ("coords".equals(r.id()) || "motion".equals(r.id()));
    }

    private static boolean angle(Form.Row r, int col) {
        return r != null && "coords".equals(r.id()) && col >= 3;
    }

    private Form.Row rowAt(int field) { return form.rowAt(field); }

    private int stepCol(Form.Row r) {
        if (focus >= r.from() && focus < r.from() + r.count()) return focus - r.from();
        return Math.min(r.count() - 1, stepCols.getOrDefault(r.id(), 0));
    }

    private int nudgeW(double d) { return Math.max(18, tr.width(Value.num(d)) + 10); }

    private int nudgeX(Form.Row r, int col, double[] steps) {
        int gw = -STEP_GAP;
        for (double d : steps) gw += nudgeW(d) + STEP_GAP;
        int at = colX(r, col) + colW(r) / 2 - gw / 2;
        return Math.max(x + PAD, Math.min(x + PAD + inner() - gw, at));
    }

    private int nudgeY(Form.Row r) { return py(r.id() + "+") + 3; }

    private void drawSteps(GuiGraphicsExtractor ctx, int mouseX, int mouseY, Form.Row r) {
        int col = stepCol(r);
        double[] steps = angle(r, col) ? TURN : NUDGE;
        if (touch) { drawTouchSteps(ctx, mouseX, mouseY, r, col, steps); return; }
        int bx = nudgeX(r, col, steps), by = nudgeY(r);
        for (double d : steps) {
            int bw = nudgeW(d);
            String label = Value.num(d);
            if (Ui.button(ctx, tr, mouseX, mouseY, bx, by, bw, stepH, label, Ui.GHOST))
                ctx.setComponentTooltipForNextFrame(tr, List.of(Component.literal(
                        Ui.click() + " +" + label + " · " + Ui.rmb() + " −" + label)), mouseX, mouseY);
            bx += bw + STEP_GAP;
        }
    }

    private static final int[] TOUCH_STEP = new int[2];

    private static int touchKind(double[] steps) { return steps == TURN ? 1 : 0; }

    private int signW() { return Math.max(stepH, 40); }

    private int touchSegX() { return x + PAD + signW() + STEP_GAP * 2; }

    private int touchSegW() { return inner() - signW() * 2 - STEP_GAP * 4; }

    private void drawTouchSteps(GuiGraphicsExtractor ctx, int mouseX, int mouseY, Form.Row r, int col,
                                double[] steps) {
        int by = nudgeY(r), sw = signW();
        int minusX = x + PAD, plusX = x + PAD + inner() - sw;
        signButton(ctx, mouseX, mouseY, minusX, by, sw, false);
        signButton(ctx, mouseX, mouseY, plusX, by, sw, true);
        List<String> labels = new ArrayList<>();
        for (double d : steps) labels.add(Value.num(d));
        int on = Math.min(TOUCH_STEP[touchKind(steps)], steps.length - 1);
        Ui.segmented(ctx, tr, mouseX, mouseY, touchSegX(), by, touchSegW(), stepH, labels, on,
                current().color());
    }

    private void signButton(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int bx, int by, int bw,
                            boolean plus) {
        boolean hov = Ui.button(ctx, tr, mouseX, mouseY, bx, by, bw, stepH, "", Ui.GHOST);
        int ink = Draw.opaque(hov ? Theme.TEXT : Theme.TEXT_DIM);
        int cx = bx + bw / 2, cy = by + stepH / 2;
        Draw.rect(ctx, cx - 5, cy - 1, 10, 2, ink);
        if (plus) Draw.rect(ctx, cx - 1, cy - 5, 2, 10, ink);
    }

    private boolean touchStepsClicked(int mx, int my, Form.Row r, int col, double[] steps) {
        int by = nudgeY(r), sw = signW();
        int seg = Ui.segmentAt(mx, my, touchSegX(), by, touchSegW(), stepH, steps.length);
        if (seg >= 0) {
            TOUCH_STEP[touchKind(steps)] = seg;
            return true;
        }
        int sign = Ui.hit(mx, my, x + PAD, by, sw, stepH) ? -1
                : Ui.hit(mx, my, x + PAD + inner() - sw, by, sw, stepH) ? 1 : 0;
        if (sign == 0) return false;
        double d = steps[Math.min(TOUCH_STEP[touchKind(steps)], steps.length - 1)];
        nudge(r.from() + col, sign * d);
        return true;
    }

    private boolean timed(int field) {
        String kind = current().type;
        return field == 0 && (Value.NUMBER.equals(kind) || Value.POTION.equals(kind));
    }

    private Ticks.Unit timeUnit() {
        if (Value.POTION.equals(current().type)) return Ticks.Unit.TICKS;
        return Ticks.unitFor(arg == null ? "" : arg.purpose, node);
    }

    private Double timeIn(int field) {
        if (!timed(field) || field >= fields.size()) return null;
        return Ticks.convert(fields.get(field).getValue(), timeUnit());
    }

    private double numberIn(int field) {
        Double time = timeIn(field);
        return time != null ? time : parse(fields.get(field).getValue());
    }

    private void nudge(int field, double delta) {
        if (focus != field) focus(field);
        double now = numberIn(field);
        setNumber(field, Math.round((now + delta) * 1e6) / 1e6);
    }

    private int uniformW() { return tr.width(UNIFORM) + 18; }

    private int uniformX() { return x + PAD + inner() - uniformW(); }

    private int uniformY(Form.Row r) { return py(r.id()) - CAP; }

    private void drawUniform(GuiGraphicsExtractor ctx, int mouseX, int mouseY, Form.Row r) {
        boolean on = uniform.contains(r.id());
        int ux = uniformX(), uy = uniformY(r), uw = uniformW();
        boolean hov = Ui.hit(mouseX, mouseY, ux - 2, uy - 2, uw + 2, CAP + 2);
        Draw.text(ctx, tr, UNIFORM, ux, uy, on || hov ? Theme.TEXT : Theme.TEXT_FAINT, false);
        int kw = 12, kh = 6, kx = ux + uw - kw, ky = uy + 1;
        Draw.pill(ctx, kx, ky, kw, kh, Draw.opaque(on ? current().color() : Theme.KNOB_OFF));
        Draw.round(ctx, on ? kx + kw - 5 : kx + 1, ky + 1, 4, kh - 2, 2,
                Draw.opaque(on ? 0xFFFFFF : 0x8A93A6));
        if (hov) ctx.setComponentTooltipForNextFrame(tr, List.of(Component.literal(
                "одно значение на X, Y и Z")), mouseX, mouseY);
    }

    private boolean stepsClicked(MouseButtonEvent click, int mx, int my) {
        for (Form.Row r : form.rows) {
            if (!stepRow(r)) continue;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && Ui.hit(mx, my, uniformX() - 2, uniformY(r) - 2, uniformW() + 2, CAP + 2)) {
                if (!uniform.remove(r.id())) {
                    uniform.add(r.id());
                    int col = stepCol(r);
                    spread(r.from() + (col < 3 ? col : 0));
                    live();
                }
                return true;
            }
            int col = stepCol(r);
            double[] steps = angle(r, col) ? TURN : NUDGE;
            if (touch) {
                if (touchStepsClicked(mx, my, r, col, steps)) return true;
                continue;
            }
            int bx = nudgeX(r, col, steps), by = nudgeY(r);
            for (double d : steps) {
                int bw = nudgeW(d);
                if (Ui.hit(mx, my, bx, by, bw, stepH)) {
                    double sign = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
                    nudge(r.from() + col, sign * d);
                    return true;
                }
                bx += bw + STEP_GAP;
            }
        }
        return false;
    }

    private void setNumber(int field, double value) {
        if (field < 0 || field >= fields.size()) return;
        EditBox f = fields.get(field);
        f.setValue(Value.num(clampAngle(field, value)));
        f.moveCursorToEnd(false);
        spread(field);
        live();
    }

    private double clampAngle(int field, double value) {
        Form.Row r = rowAt(field);
        if (r == null || !angle(r, field - r.from())) return value;
        double lim = field - r.from() == 3 ? 360 : 90;
        return Math.max(-lim, Math.min(lim, value));
    }

    private void spread(int field) {
        Form.Row r = rowAt(field);
        if (r == null || !uniform.contains(r.id()) || field - r.from() > 2) return;
        String text = fields.get(field).getValue();
        for (int k = 0; k < 3; k++) {
            EditBox o = fields.get(r.from() + k);
            if (r.from() + k == field || o.getValue().equals(text)) continue;
            o.setValue(text);
            o.moveCursorToEnd(false);
        }
    }

    private void settle(int field) {
        if (field < 0 || field >= fields.size()) return;
        Double time = timeIn(field);
        if (time != null) {
            fields.get(field).setValue(Value.num(time));
            return;
        }
        Form.Row r = rowAt(field);
        if (r == null || !angle(r, field - r.from())) return;
        String typed = fields.get(field).getValue().trim();
        if (!isNumber(typed)) return;
        double now = parse(typed), fit = clampAngle(field, now);
        if (fit != now) fields.get(field).setValue(Value.num(fit));
    }

    private static void selectAll(EditBox f) {
        f.moveCursorToEnd(false);
        f.setHighlightPos(0);
    }

    private static boolean ctrlHeld() {
        return held(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean shiftHeld() {
        return held(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private double[] stepsOf(int field) {
        Form.Row r = rowAt(field);
        if (r == null) return PLAIN;
        int col = field - r.from();
        if (angle(r, col)) return ANGLE;
        if (WHOLE_CAPS.contains(r.caps()[col])) return WHOLE;
        return LINEAR;
    }

    private double step(int field) {
        double[] t = stepsOf(field);
        return shiftHeld() ? t[0] : ctrlHeld() ? t[2] : t[1];
    }

    private static double snap(double from, int n, double step) {
        if (n == 0) return from;
        double base = n > 0 ? Math.floor(from / step + 1e-9) : Math.ceil(from / step - 1e-9);
        return Math.round((base + n) * step * 1e6) / 1e6;
    }

    private int numberCapY() { return py("input") - CAP; }

    private int wheelField(double mx, double my) {
        for (Form.Row r : form.rows) {
            if (!scrubbable(r)) continue;
            int cw = colW(r), ry = py(r.id());
            for (int i = 0; i < r.count(); i++)
                if (Ui.hit(mx, my, colX(r, i), ry, cw, 9 + fieldH)) return r.from() + i;
        }
        if (Value.NUMBER.equals(current().type) && has("input")
                && Ui.hit(mx, my, x + PAD, numberCapY(), inner(), CAP + fieldH)) return 0;
        return -1;
    }

    private void focus(int i) {
        focus(i, true);
    }

    private void focus(int i, boolean toStart) {
        if (fields.isEmpty()) return;
        complete.reset();
        int was = focus;
        focus = Math.max(0, Math.min(fields.size() - 1, i));
        if (was != focus) settle(was);
        for (int k = 0; k < fields.size(); k++) {
            EditBox f = fields.get(k);
            f.setFocused(k == focus);
            if (k != focus) f.setHighlightPos(f.getCursorPosition());
        }
        if (toStart) fields.get(focus).moveCursorToStart(false);
        Form.Row r = rowAt(focus);
        if (r != null) stepCols.put(r.id(), focus - r.from());
    }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        lastMx = mouseX;
        lastMy = mouseY;
        Overlay top = child();
        if (top != null && top != colors && top != lines) {
            top.render(ctx, mouseX, mouseY, delta);
            return;
        }

        Value v = current();
        int accent = v.color();
        if (!Value.SOUND.equals(v.type)) Audio.want("");

        Ui.panel(ctx, x, y, w, h);
        drawHeader(ctx, mouseX, mouseY);
        ctx.enableScissor(x + 1, y + pane.top(), x + w - 1, footY() - 1);
        if (list()) drawList(ctx, mouseX, mouseY);
        drawRail(ctx, mouseX, mouseY);
        if (!paired()) Ui.hairline(ctx, x + 1, formY() - 4, w - 2);
        drawCaptions(ctx);
        drawForm(ctx, mouseX, mouseY, delta);
        ctx.disableScissor();
        pane.drawBar(ctx, bodyBar, x + w - 4, y, mouseX, mouseY);
        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY, accent);
        if (!touch) sizer.draw(ctx, mouseX, mouseY, x, y, w, h);

        if (colors != null) {
            ctx.nextStratum();
            colors.render(ctx, mouseX, mouseY, delta);
        }
        if (menu != null) {
            ctx.nextStratum();
            menu.render(ctx, tr, mouseX, mouseY);
        }
        if (complete.active()) {
            ctx.nextStratum();
            complete.render(ctx, tr, mouseX, mouseY);
        } else if (names.active()) {
            ctx.nextStratum();
            names.render(ctx, tr, mouseX, mouseY);
        }
        drawDraggedSlot(ctx);
        if (lines != null) {
            ctx.nextStratum();
            Ui.dim(ctx, screenW, screenH);
            lines.render(ctx, mouseX, mouseY, delta);
        }
    }

    private void drawHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int tc = Catalog.TYPE_COLORS.getOrDefault(arg.type, 0xAAAAAA);
        int closeX0 = x + w - PAD - 14;
        boolean grab = dragging || (Ui.hit(mouseX, mouseY, x, y, w, HEAD_H)
                && !Ui.hit(mouseX, mouseY, closeX0, y + 6, 14, 14)
                && !(numberToggle() && Ui.hit(mouseX, mouseY, toggleX(), y + 6, 14, 14)));
        Ui.headerStrip(ctx, x, y, w, HEAD_H, grab ? Draw.shade(tc, 0.22f) : tc);
        if (grab) ctx.requestCursor(CursorTypes.RESIZE_ALL);
        Draw.round(ctx, x + PAD, y + 8, 3, 10, 1, Draw.opaque(tc));

        String type = paired() ? "" : Values.shortType(arg.type) + (list() ? " ×" + arg.capacity : "");
        int badgeW = type.isEmpty() ? -4 : Draw.badgeWidth(tr, type);
        int closeX = closeX0;
        int room = closeX - badgeW - 12 - (x + PAD + 9) - (numberToggle() ? 20 : 0);
        String title = paired() ? pairTitle(room)
                : arg.purpose.isEmpty() ? "Значение" : arg.purpose;
        if (!langs.current().isEmpty()) title += " · " + langs.current();
        Draw.textFit(ctx, tr, title, x + PAD + 9, y + 9, room, Theme.TEXT, false);
        int badgeX = closeX - badgeW - 8 - (numberToggle() ? 20 : 0);
        if (!type.isEmpty())
            Draw.badge(ctx, tr, type, badgeX, y + 8,
                    Draw.opaque(Draw.shade(tc, -0.68f)), Draw.shade(tc, 0.2f));
        if (numberToggle())
            Ui.iconButton(ctx, mouseX, mouseY, toggleX(), y + 6, 14, Draw.FIT, Ui.GHOST, true);
        Ui.closeButton(ctx, mouseX, mouseY, closeX, y + 6, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
    }

    private static final int LANG_ROW_H = 15, LANG_HINT_H = 11;

    private int langsH() {
        return Localized.LANGS.size() * (LANG_ROW_H + 2) + LANG_HINT_H;
    }

    private static String langLabel(Localized.Lang l) {
        return l.id().isEmpty() ? "по умолчанию" : l.id();
    }

    private void drawLangs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Ui.caption(ctx, tr, "ЯЗЫКИ", x + PAD, railY() - CAP, inner());
        int rw = inner();
        int labelW = 0;
        for (Localized.Lang l : Localized.LANGS)
            labelW = Math.max(labelW, tr.width(langLabel(l)));
        labelW += 10;
        for (int i = 0; i < Localized.LANGS.size(); i++) {
            Localized.Lang l = Localized.LANGS.get(i);
            int ry = railY() + i * (LANG_ROW_H + 2);
            boolean active = l.id().equals(langs.current());
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, LANG_ROW_H);
            Draw.round(ctx, x + PAD, ry, rw, LANG_ROW_H, Ui.R_SM,
                    Draw.opaque(active ? Ui.ROW_ON : (hov ? Ui.ROW_HOVER : Ui.WELL)));
            if (active) Draw.rect(ctx, x + PAD, ry + 2, 2, LANG_ROW_H - 4,
                    Draw.opaque(Theme.ACCENT));
            Draw.textFit(ctx, tr, langLabel(l), x + PAD + 7, ry + 4, labelW,
                    active ? Theme.TEXT : Theme.TEXT_DIM, false);
            String preview = active ? "" : langs.preview(l.id());
            boolean dim = preview.isEmpty();
            if (active) preview = "правится ниже";
            else if (dim) preview = l.id().isEmpty() ? "не задано" : "нет перевода";
            Draw.textFit(ctx, tr, preview, x + PAD + 7 + labelW, ry + 4,
                    rw - 14 - labelW, dim ? Theme.TEXT_FAINT : Theme.TEXT, false);
        }
        Draw.textFit(ctx, tr, langs.current().isEmpty()
                        ? "запасной: увидят все, у кого нет перевода"
                        : "увидят те, у кого клиент на " + langs.current(),
                x + PAD + 2, railY() + Localized.LANGS.size() * (LANG_ROW_H + 2) + 1,
                rw - 4, Theme.TEXT_FAINT, false);
    }

    private void drawList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Catalog.Slots grid = slots();
        if (grid != null) { drawSlots(ctx, grid, mouseX, mouseY); return; }
        Ui.caption(ctx, tr, "ЗНАЧЕНИЯ", x + PAD, listY(), inner(),
                values.size() + "/" + arg.capacity);
        for (int r = 0; r < listRows(); r++) {
            int i = listScroll + r;
            if (i >= values.size()) break;
            Value v = values.get(i);
            int ry = listY() + CAP + r * (rowH + 2);
            int rw = rowW();
            boolean active = i == sel;
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, rowH);
            Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM,
                    Draw.opaque(active ? Ui.ROW_ON : (hov ? Ui.ROW_HOVER : Ui.WELL)));
            if (active) Draw.rect(ctx, x + PAD, ry + 2, 2, rowH - 4, Draw.opaque(Theme.ACCENT));
            Draw.textRight(ctx, tr, String.valueOf(i + 1), x + PAD + 16, ry + 5,
                    active ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            Draw.dot(ctx, x + PAD + 21, ry + 6, Draw.opaque(v.color()));
            String note = v.note();
            int noteW = note.isEmpty() ? 0 : tr.width(note) + 8;
            Draw.textFit(ctx, tr, Layout.display(v), x + PAD + 29, ry + 5, rw - 35 - noteW,
                    v.isBlank() ? Theme.TEXT_FAINT : Theme.TEXT, false);
            if (!note.isEmpty())
                Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ry + 5, Theme.TEXT_FAINT, false);
            Ui.iconButton(ctx, mouseX, mouseY, x + PAD + inner() - rowH, ry, rowH,
                    Draw.CROSS, Ui.DANGER, true);
        }
        if (maxListScroll() > 0)
            listBar.draw(ctx, x + PAD + rowW() + 1, listY() + CAP, listRows() * (rowH + 2),
                    values.size() * (rowH + 2), listRows() * (rowH + 2),
                    listScroll * (rowH + 2), mouseX, mouseY);
        if (canAdd()) {
            int ay = listY() + CAP + listRows() * (rowH + 2);
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ay, inner(), addH,
                    Draw.PLUS, "добавить значение", Ui.GHOST, true);
        }
    }

    private void drawRail(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (paired()) return;
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
            int is = Math.max(8, Math.min(16, Math.min(cw, chh) - 4));
            Draw.item(ctx, icon, cx + (cw - is) / 2, cy + (chh - is) / 2, is);
            if (!ok) {
                Draw.round(ctx, cx + 1, cy + 1, cw - 2, chh - 2, Ui.R_SM - 1,
                        Draw.argb(0xB4, Ui.WELL));
                Draw.glyph(ctx, Draw.LOCK, cx + (cw - Draw.glyphW(Draw.LOCK)) / 2,
                        cy + (chh - Draw.glyphH(Draw.LOCK)) / 2, Theme.TEXT_FAINT);
            }
        }
    }

    private void drawCaptions(GuiGraphicsExtractor ctx) {
        for (int i = 0; i < form.parts.size(); i++) {
            Form.Part p = form.parts.get(i);
            if (p.caption() == null) continue;
            String next = i + 1 < form.parts.size() ? form.parts.get(i + 1).id() : null;
            boolean toggled = next != null && stepRow(rowOf(next));
            Ui.caption(ctx, tr, p.caption(), x + PAD, formY() + p.dy(),
                    toggled ? inner() - uniformW() - 6 : inner());
        }
    }

    private void drawForm(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Value v = current();
        int full = inner();
        switch (v.type) {
            case Value.TEXT -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                if (modeChips != null)
                    modeChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("mode"),
                            parsingIndex(v.parsing), Values.color(Value.TEXT));
                if (has("preview")) drawPreview(ctx, v);
                int half = (full - GAP) / 2;
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("studio"), half, btnH,
                        Draw.WINDOW, "Расширенный", Ui.GHOST, true);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD + half + GAP, py("studio"),
                        full - half - GAP, btnH, Draw.LINES, "Многострочный", Ui.GHOST, true);
            }
            case Value.NUMBER -> {
                if (Ui.hit(mouseX, mouseY, x + PAD, numberCapY(), full, CAP))
                    ctx.requestCursor(CursorTypes.RESIZE_EW);
                Ui.iconButton(ctx, mouseX, mouseY, x + PAD, py("input"), fieldH,
                        Draw.MINUS, Ui.GHOST, true);
                Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - fieldH, py("input"),
                        fieldH, Draw.PLUS, Ui.GHOST, true);
                Ui.input(ctx, x + PAD + fieldH + 4, py("input"), full - 2 * (fieldH + 4), fieldH,
                        fields.get(0).isFocused());
                fields.get(0).extractRenderState(ctx, mouseX, mouseY, delta);
                Ui.placeholder(ctx, tr, fields.get(0));
                if (has("note")) {
                    String text = fields.get(0).getValue().trim();
                    Double time = timeIn(0);
                    boolean bad = time == null && !text.isEmpty() && !formula(text) && !isNumber(text);
                    if (time != null) Draw.textFit(ctx, tr, "= " + Value.num(time),
                            x + PAD + 2, py("note") + 2, full - 4, Theme.OK, false);
                    else if (bad) Draw.textFit(ctx, tr, "не число — сохранится 0",
                            x + PAD + 2, py("note") + 2, full - 4, Theme.DANGER, false);
                }
            }
            case Value.VARIABLE -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                int cur = scopeIndex(v.scope);
                if (scopeChips != null)
                    scopeChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("scope"), cur,
                            Values.SCOPES.isEmpty() ? Theme.ACCENT
                                    : Values.SCOPES.get(Math.max(0, cur)).color());
            }
            case Value.PARAMETER -> {
                drawInput(ctx, mouseX, mouseY, delta, 0);
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("pdesc"));
                int accent = Values.color(Value.PARAMETER);
                if (kindChips != null)
                    kindChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("pkinds"),
                            Math.max(0, PARAM_KINDS.indexOf(v.typeKey)), accent);
                if (Value.ENUM.equals(v.typeKey)) {
                    drawElements(ctx, v, mouseX, mouseY, accent);
                    drawNewElement(ctx, v, mouseX, mouseY, delta);
                } else {
                    if (paramChips != null)
                        paramChips.render(ctx, tr, mouseX, mouseY, x + PAD, py("ptypes"),
                                Math.max(0, PARAM_TYPES.indexOf(v.valueType)), accent);
                    Ui.toggle(ctx, tr, mouseX, mouseY, x + PAD, py("flags"), full, ctrlH,
                            "обязательный", v.required);
                    if (Value.PLURAL.equals(v.typeKey))
                        Ui.toggle(ctx, tr, mouseX, mouseY, x + PAD, py("flags") + ctrlH + FLAG_GAP,
                                full, ctrlH, "игнорировать пустые значения", v.ignoreEmpty);
                    drawDefault(ctx, v, mouseX, mouseY);
                }
            }
            case Value.LOCATION, Value.VECTOR -> {
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("coords"));
                if (has("world")) {
                    boolean inGame = Minecraft.getInstance().player != null;
                    boolean set = v.x != 0 || v.y != 0 || v.z != 0 || v.yaw != 0 || v.pitch != 0;
                    Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("world"), full, btnH,
                            Draw.PIN, set ? "править в мире" : "выбрать в мире", Ui.PRIMARY, inGame);
                }
            }
            case Value.SOUND -> {
                Pickers.Entry e = Pickers.sound(v.sound);
                drawEntryCard(ctx, e == null ? null : e.item, e == null ? "звук не выбран" : e.name,
                        e == null ? "" : e.category, "", "");
                if (has("player")) {
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
                    stage.show(v.particle);
                    stage.render(ctx, x + PAD, py("stage"), full, ParticleStage.H, mouseX, mouseY);
                }
                drawChoose(ctx, mouseX, mouseY, e != null, "Выбрать частицу", "Другая частица");
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
                if (has("motion")) drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("motion"));
                if (has("size")) drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("size"));
                if (has("material")) {
                    Ui.input(ctx, x + PAD, py("material"), full, fieldH,
                            fields.get(fields.size() - 1).isFocused());
                    fields.get(fields.size() - 1).extractRenderState(ctx, mouseX, mouseY, delta);
                    Ui.placeholder(ctx, tr, fields.get(fields.size() - 1));
                }
                if (has("color")) {
                    int cy = py("color");
                    Ui.swatch(ctx, x + PAD, cy, 26, ctrlH, v.color & 0xFFFFFF, true,
                            Ui.hit(mouseX, mouseY, x + PAD, cy, 26, ctrlH));
                    Ui.button(ctx, tr, mouseX, mouseY, x + PAD + 30, cy, 84, ctrlH,
                            "выбрать цвет", Ui.GHOST, true);
                    if (e != null && e.has(Pickers.TO_COLOR)) {
                        Draw.glyph(ctx, Draw.ARROW_RIGHT, x + PAD + 118, cy + 6, Theme.TEXT_FAINT);
                        Ui.swatch(ctx, x + PAD + 128, cy, 26, ctrlH, v.toColor & 0xFFFFFF, true,
                                Ui.hit(mouseX, mouseY, x + PAD + 128, cy, 26, ctrlH));
                        Ui.button(ctx, tr, mouseX, mouseY, x + PAD + 158, cy,
                                Math.max(40, full - 158), ctrlH, "переход", Ui.GHOST, true);
                    }
                }
            }
            case Value.ARRAY, Value.MAP -> drawCells(ctx, v, mouseX, mouseY);
            case Value.ITEM -> {
                drawItemCard(ctx, v);
                drawChoose(ctx, mouseX, mouseY, !v.itemId.isEmpty(), "Выбрать предмет",
                        "Другой предмет");
                int ty = py("tools"), half = hand() ? (full - GAP) / 2 : -GAP;
                if (hand())
                    Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ty, half, btnH,
                            Draw.LOAD, "Взять из руки", Ui.GHOST, held() != null);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD + half + GAP, ty,
                        full - half - GAP, btnH, Draw.WINDOW, "Редактор предмета", Ui.GHOST,
                        !v.itemId.isEmpty());
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
            }
            case Value.BLOCK -> {
                Blocks.Entry be = Blocks.entry(v.block);
                if (be == null)
                    drawEntryCard(ctx, (ItemStack) null, v.block.isEmpty() ? "блок не выбран"
                            : "нет такого блока: " + v.block, "", "", "");
                else drawEntryCard(ctx, be.icon(), be.name(), be.id(), "", "");
                drawChoose(ctx, mouseX, mouseY, !v.block.isEmpty(),
                        "Редактор блока", "Редактор блока");
                if (hand())
                    Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("tools"), full, btnH,
                            Draw.LOAD, "Взять из руки", Ui.GHOST, Blocks.of(held()) != null);
            }
            case Value.POTION -> {
                Pickers.Entry e = Pickers.potion(v.potion);
                drawEntryCard(ctx, e == null ? null : Pickers.potionStack(e.id),
                        e == null ? "эффект не выбран" : e.name,
                        e == null ? "" : e.category, e == null ? "" : e.description, "");
                drawChoose(ctx, mouseX, mouseY, e != null, "Выбрать эффект", "Другой эффект");
                drawFieldRow(ctx, mouseX, mouseY, delta, rowOf("nums"));
                if (has("note")) {
                    Double time = timeIn(0);
                    Draw.textFit(ctx, tr, time != null ? "= " + Value.num(time) : "-1 — бесконечно",
                            x + PAD + 2, py("note") + 2, full - 4, time != null ? Theme.OK : Theme.TEXT_FAINT,
                            false);
                }
            }
            case Value.GAME_VALUE -> {
                Values.GameValue g = Values.gameValue(v.gameValue);
                drawEntryCard(ctx, g == null ? null : g.icon(),
                        g == null ? "значение не выбрано" : g.name,
                        g == null ? "" : g.category, g == null ? "" : g.description,
                        g == null ? "" : g.returns);
                Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("choose"), full, btnH,
                        Draw.SEARCH, Values.gameValue(v.gameValue) == null
                                ? "Выбрать значение" : "Другое значение", Ui.GHOST, true);
                if (compact) {
                    Ui.button(ctx, tr, mouseX, mouseY, x + PAD, py("target"), full, ctrlH,
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
                Draw.round(ctx, x + PAD, uy, full, ctrlH, Ui.R_SM, Draw.opaque(Ui.DANGER_WELL));
                Draw.glyph(ctx, Draw.WARN, x + PAD + 7, uy + 5, 0xFFE066);
                Draw.textFit(ctx, tr, "каталог «" + Values.kindName(v.type) + "» ещё не снят",
                        x + PAD + 17, uy + (ctrlH - Ui.TEXT_H) / 2, full - 24, Theme.TEXT_DIM, false);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor ctx, Value v, int mouseX, int mouseY) {
        boolean map = Value.MAP.equals(v.type);
        int top = py("cells"), full = inner();
        int count = cellCount(v), rows = cellRows(v);
        if (!paired())
            Draw.textRight(ctx, tr, count + "/" + cellMax(v), x + PAD + full, top - CAP,
                    Theme.TEXT_FAINT, false);

        int rw = cellRowW(v);
        if (paired()) {
            int halfW = keyW(rw);
            PairNames n = pairNames();
            Draw.textFit(ctx, tr, n.left().toUpperCase(), x + PAD + 18, top - CAP, halfW,
                    Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, n.right().toUpperCase(), x + PAD + 29 + halfW, top - CAP,
                    full - 29 - halfW, Theme.TEXT_FAINT, false);
        }
        if (count == 0) {
            Draw.round(ctx, x + PAD, top, full, rowH, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, paired() ? "пока пусто" : map ? "пар пока нет" : "значений пока нет",
                    x + PAD + 8, top + 5, full - 16, Theme.TEXT_FAINT, false);
        }
        boolean carrying = carrying(v);
        if (carrying) {
            followEdge(v, top, rows);
            mouseX = mouseY = Integer.MIN_VALUE;
        }
        int target = carrying ? cellTarget(v, top, rows) : -1;
        for (int r = 0; r < rows; r++) {
            int i = cellScroll + r;
            if (i >= count) break;
            int ry = top + r * (rowH + 2);
            if (i == target) {
                Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM, Draw.opaque(Draw.shade(Ui.WELL, -0.35f)));
                Draw.roundOutline(ctx, x + PAD, ry, rw, rowH, Ui.R_SM,
                        Draw.opaque(Draw.shade(Theme.ACCENT, -0.55f)));
                continue;
            }
            int src = carrying ? sourceAt(i, cellDrag, target) : i;
            drawCellRow(ctx, v, src, i + 1, ry, rw, mouseX, mouseY, false);
            Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - rowH, ry, rowH,
                    Draw.CROSS, Ui.DANGER, true);
            Ui.dragZone(x + PAD, ry, 16, rowH);
        }
        if (maxCellScroll(v) > 0)
            cellBar.draw(ctx, x + PAD + rw + 1, top, rows * (rowH + 2),
                    count * (rowH + 2), rows * (rowH + 2), cellScroll * (rowH + 2),
                    mouseX, mouseY);
        if (canAddCell(v))
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, top + rows * (rowH + 2), full, addH,
                    Draw.PLUS, paired() ? "добавить строку" : map ? "добавить пару" : "добавить значение",
                    Ui.GHOST, true);
        if (!carrying) return;
        ctx.nextStratum();
        int fy = carriedY(top, rows);
        Draw.shadow(ctx, x + PAD, fy, rw, rowH, Ui.R_SM);
        drawCellRow(ctx, v, cellDrag, target + 1, fy, rw, Integer.MIN_VALUE, Integer.MIN_VALUE, true);
        Draw.roundOutline(ctx, x + PAD, fy, rw, rowH, Ui.R_SM, Draw.opaque(Theme.ACCENT));
        ctx.requestCursor(CursorTypes.RESIZE_NS);
    }

    private void drawCellRow(GuiGraphicsExtractor ctx, Value v, int i, int shown, int ry, int rw,
                             int mouseX, int mouseY, boolean lifted) {
        boolean map = Value.MAP.equals(v.type);
        Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM, Draw.opaque(lifted ? Ui.ROW_HOVER : Ui.WELL));
        boolean half = map && paired() && v.keys.get(i).isBlank() != value(v, i).isBlank();
        Draw.textRight(ctx, tr, String.valueOf(shown), x + PAD + 15, ry + 5,
                half ? Theme.DANGER : Theme.TEXT_FAINT, false);
        if (map) {
            int halfW = keyW(rw);
            cellChip(ctx, v.keys.get(i), x + PAD + 18, ry, halfW, mouseX, mouseY);
            String[] link = paired() ? Draw.EQUAL : Draw.ARROW_RIGHT;
            Draw.glyph(ctx, link, x + PAD + 20 + halfW + (paired() ? 1 : 0),
                    ry + (rowH - Draw.glyphH(link)) / 2, Theme.TEXT_FAINT);
            cellChip(ctx, value(v, i), x + PAD + 29 + halfW, ry, rw - 31 - halfW,
                    mouseX, mouseY);
            return;
        }
        Value it = v.items.get(i);
        boolean hov = lifted || Ui.hit(mouseX, mouseY, x + PAD, ry, rw, rowH);
        if (hov && !lifted) Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM, Draw.opaque(Ui.ROW_HOVER));
        Draw.dot(ctx, x + PAD + 20, ry + 6, Draw.opaque(it.color()));
        String note = it.note();
        int noteW = note.isEmpty() ? 0 : tr.width(note) + 8;
        Draw.textFit(ctx, tr, it.isBlank() ? "пусто" : Layout.display(it),
                x + PAD + 28, ry + 5, rw - 34 - noteW,
                it.isBlank() ? Theme.TEXT_FAINT : (hov ? Theme.TEXT : Theme.TEXT_DIM), false);
        if (!note.isEmpty())
            Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ry + 5, Theme.TEXT_FAINT, false);
    }

    private boolean carrying(Value v) {
        return cellDrag >= 0 && cellMoved && cellDrag < cellCount(v);
    }

    private int carriedY(int top, int rows) {
        int fy = lastMy - cellGrabY;
        return Math.max(top, Math.min(top + (rows - 1) * (rowH + 2), fy));
    }

    private int cellTarget(Value v, int top, int rows) {
        int center = carriedY(top, rows) + rowH / 2;
        int r = Math.floorDiv(center - top + 1, rowH + 2);
        int visible = Math.min(rows, cellCount(v) - cellScroll);
        return cellScroll + Math.max(0, Math.min(visible - 1, r));
    }

    private static int sourceAt(int at, int from, int to) {
        int rest = at < to ? at : at - 1;
        return rest < from ? rest : rest + 1;
    }

    private void followEdge(Value v, int top, int rows) {
        long now = System.currentTimeMillis();
        if (now - cellEdgeAt < EDGE_SCROLL_MS) return;
        int step = lastMy < top + 4 ? -1 : lastMy > top + rows * (rowH + 2) - 6 ? 1 : 0;
        int next = Math.max(0, Math.min(maxCellScroll(v), cellScroll + step));
        if (next == cellScroll) return;
        cellScroll = next;
        cellEdgeAt = now;
    }

    private void cellDropped() {
        if (cellDrag < 0) return;
        int from = cellDrag, action = cellAction;
        boolean moved = cellMoved;
        cellDrag = -1;
        cellMoved = false;
        if (!has("cells")) return;
        Value v = current();
        if (from >= cellCount(v)) return;
        if (!moved) {
            if (action != CELL_NONE) openCell(v, action == CELL_KEY, from);
            return;
        }
        int to = cellTarget(v, py("cells"), cellRows(v));
        if (to == from) return;
        readForm();
        if (Value.MAP.equals(v.type)) {
            while (v.items.size() < v.keys.size()) v.items.add(Value.blank());
            v.keys.add(to, v.keys.remove(from));
        }
        v.items.add(to, v.items.remove(from));
        cellScroll = Math.max(0, Math.min(maxCellScroll(v), cellScroll));
        buildForm();
    }

    private void drawDefault(GuiGraphicsExtractor ctx, Value p, int mouseX, int mouseY) {
        int ry = py("pdefault"), full = inner();
        if (p.required) {
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ry, full, rowH, Draw.LOCK,
                    "только у необязательного", Ui.GHOST, false);
            return;
        }
        Value d = Functions.defaultOf(p);
        if (d == null) {
            Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, ry, full, rowH, Draw.PLUS,
                    "задать значение", Ui.GHOST, true);
            return;
        }
        int rw = full - rowH - 2;
        boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, rowH);
        Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM, Draw.opaque(hov ? Ui.ROW_HOVER : Ui.WELL));
        Draw.dot(ctx, x + PAD + 6, ry + 6, Draw.opaque(d.color()));
        String note = d.note();
        int noteW = note.isEmpty() ? 0 : tr.width(note) + 8;
        Draw.textFit(ctx, tr, Layout.display(d), x + PAD + 14, ry + 5, rw - 20 - noteW,
                hov ? Theme.TEXT : Theme.TEXT_DIM, false);
        if (!note.isEmpty())
            Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ry + 5, Theme.TEXT_FAINT, false);
        Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - rowH, ry, rowH, Draw.CROSS, Ui.DANGER, true);
    }

    private int elementRowY(int i) { return py("elems") + i * (rowH + 2); }

    private int elementAt(Value p, int mx, int my) {
        if (!has("elems")) return -1;
        for (int i = 0; i < p.elements.size(); i++)
            if (Ui.hit(mx, my, x + PAD, elementRowY(i), inner(), rowH)) return i;
        return -1;
    }

    private void drawElements(GuiGraphicsExtractor ctx, Value p, int mouseX, int mouseY, int accent) {
        int top = py("elems"), full = inner();
        Draw.textRight(ctx, tr, String.valueOf(p.elements.size()), x + PAD + full, top - CAP,
                Theme.TEXT_FAINT, false);
        if (p.elements.isEmpty()) {
            Draw.round(ctx, x + PAD, top, full, rowH, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, "вариантов пока нет", x + PAD + 8, top + (rowH - Ui.TEXT_H) / 2,
                    full - 16, Theme.TEXT_FAINT, false);
            return;
        }
        int rw = full - rowH - 2;
        for (int i = 0; i < p.elements.size(); i++) {
            Value.Elem e = p.elements.get(i);
            int ry = elementRowY(i);
            boolean main = e.name.equals(p.defaultElement);
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, rw, rowH);
            Draw.round(ctx, x + PAD, ry, rw, rowH, Ui.R_SM, Draw.opaque(hov ? Ui.ROW_HOVER : Ui.WELL));
            int rx = x + PAD + 6, rcy = ry + (rowH - 8) / 2;
            Draw.round(ctx, rx, rcy, 8, 8, 4, Draw.opaque(main ? accent : Theme.TEXT_FAINT));
            Draw.round(ctx, rx + 1, rcy + 1, 6, 6, 3, Draw.opaque(hov ? Ui.ROW_HOVER : Ui.WELL));
            if (main) Draw.round(ctx, rx + 2, rcy + 2, 4, 4, 2, Draw.opaque(accent));
            String note = main ? "по умолчанию" : hov ? "сделать по умолчанию" : "";
            int noteW = note.isEmpty() ? 0 : tr.width(note) + 8;
            int ty = ry + (rowH - Ui.TEXT_H) / 2;
            Draw.textFit(ctx, tr, e.name, rx + 13, ty, rw - 25 - noteW,
                    main || hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (!note.isEmpty())
                Draw.textRight(ctx, tr, note, x + PAD + rw - 6, ty, Theme.TEXT_FAINT, false);
            Ui.iconButton(ctx, mouseX, mouseY, x + PAD + full - rowH, ry, rowH, Draw.CROSS,
                    Ui.DANGER, true);
        }
    }

    private void drawNewElement(GuiGraphicsExtractor ctx, Value p, int mouseX, int mouseY, float delta) {
        if (newElemAt < 0 || newElemAt >= fields.size()) return;
        int ny = py("newelem"), iw = inner() - fieldH - 4;
        if (Ui.hit(mouseX, mouseY, x + PAD, ny, iw, fieldH)) ctx.requestCursor(CursorTypes.IBEAM);
        Ui.input(ctx, x + PAD, ny, iw, fieldH, focus == newElemAt);
        EditBox f = fields.get(newElemAt);
        f.extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, f);
        boolean ok = canAddElement(p);
        Ui.iconButton(ctx, mouseX, mouseY, x + PAD + iw + 4, ny, fieldH, Draw.PLUS,
                ok ? Ui.ACCENT : Ui.GHOST, ok);
    }

    private void placeNewElement() {
        if (newElemAt < 0 || newElemAt >= fields.size() || !has("newelem")) return;
        EditBox f = fields.get(newElemAt);
        f.setX(x + PAD + 6);
        f.setY(py("newelem") + (fieldH - 12) / 2 + 2);
        Ui.width(f, inner() - fieldH - 4 - 12);
    }

    private boolean elementsClicked(MouseButtonEvent click, boolean doubled, Value p, int mx, int my) {
        int ei = elementAt(p, mx, my);
        if (ei >= 0) {
            boolean remove = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    || mx >= x + PAD + inner() - rowH;
            readForm();
            if (remove) {
                String gone = p.elements.remove(ei).name;
                if (gone.equals(p.defaultElement))
                    p.defaultElement = p.elements.isEmpty() ? "" : p.elements.get(0).name;
                buildForm();
            } else {
                p.defaultElement = p.elements.get(ei).name;
            }
            return true;
        }
        if (newElemAt < 0 || !has("newelem")) return false;
        int ny = py("newelem"), iw = inner() - fieldH - 4;
        if (Ui.hit(mx, my, x + PAD + iw + 4, ny, fieldH, fieldH)) {
            addElement(p);
            return true;
        }
        if (Ui.hit(mx, my, x + PAD, ny, iw, fieldH)) return takeFocus(click, doubled, newElemAt);
        return false;
    }

    private boolean defaultClicked(Value p, int mx, int my) {
        if (!has("pdefault") || p.required || !rowHit("pdefault", mx, my)) return false;
        if (Functions.defaultOf(p) != null && mx >= x + PAD + inner() - rowH) {
            p.paramDefault = "";
            Functions.zeroDefault(p);
            return true;
        }
        readForm();
        boolean plural = Value.PLURAL.equals(p.typeKey);
        String only = plural ? Value.ARRAY : ANY_TYPE.equals(p.valueType) ? null : p.valueType;
        Value start = Functions.defaultOf(p);
        if (start == null) {
            start = Value.of(only == null ? Value.TEXT : only);
            chosen(start);
        }
        String who = p.name.isBlank() ? "параметра" : "«" + p.name + "»";
        nested = forCell(start, "По умолчанию у " + who, only, tr, x + 12, y + 12,
                screenW, screenH, knownVars, knownParams, edited -> Functions.setDefault(p, edited));
        return true;
    }

    private int cellRowW(Value v) { return inner() - rowH - 2 - (maxCellScroll(v) > 0 ? 5 : 0); }

    private static int keyW(int rowW) { return (rowW - 20 - 11) / 2; }

    private boolean onCellChip(double mx, double my, int cx, int ry, int cw) {
        return Ui.hit(mx, my, cx, ry + 1, cw, rowH - 2);
    }

    private void cellChip(GuiGraphicsExtractor ctx, Value v, int cx, int ry, int cw,
                          int mouseX, int mouseY) {
        boolean hov = onCellChip(mouseX, mouseY, cx, ry, cw);
        Draw.round(ctx, cx, ry + 1, cw, rowH - 2, Ui.R_SM - 1,
                Draw.opaque(hov ? Ui.BTN_HOVER : Ui.INPUT));
        Draw.dot(ctx, cx + 4, ry + 6, Draw.opaque(v.color()));
        Draw.textFit(ctx, tr, v.isBlank() ? "пусто" : Layout.display(v), cx + 12, ry + 5, cw - 16,
                v.isBlank() ? Theme.TEXT_FAINT : (hov ? Theme.TEXT : Theme.TEXT_DIM), false);
    }

    private static Value value(Value map, int i) {
        return i < map.items.size() ? map.items.get(i) : Value.blank();
    }

    private void drawSlots(GuiGraphicsExtractor ctx, Catalog.Slots s, int mouseX, int mouseY) {
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
                    Draw.opaque(i == sel ? Ui.ROW_ON : hov ? Ui.BTN_HOVER : Ui.WELL));
            boolean carried = i == dragSlot && slotMoved;
            if (!it.isBlank() && !carried) ctx.item(Stacks.preview(it), cx + 1, cy + 1);
            if (!it.isBlank()) Ui.dragZone(cx, cy, SLOT, SLOT);
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
            if (Ui.hit(mx, my, x + PAD, top + r * (rowH + 2), inner(), rowH)) return i;
        }
        return -1;
    }

    private boolean cellsClicked(Value v, double mx, double my) {
        boolean map = Value.MAP.equals(v.type);
        int top = py("cells"), full = inner();
        if (canAddCell(v) && Ui.hit(mx, my, x + PAD, top + cellRows(v) * (rowH + 2), full, addH)) {
            if (map) v.keys.add(paired() ? Value.of(Values.defaultKind(leftArg().type)) : Value.blank());
            v.items.add(paired() ? Value.of(Values.defaultKind(rightArg().type)) : Value.blank());
            cellScroll = Math.max(0, cellCount(v) - listMax());
            buildForm();
            return true;
        }
        int i = cellAt(v, mx, my);
        if (i < 0) return false;
        int ry = top + (i - cellScroll) * (rowH + 2);
        if (Ui.hit(mx, my, x + PAD + full - rowH, ry, rowH, rowH)) {
            if (map && i < v.keys.size()) v.keys.remove(i);
            if (i < v.items.size()) v.items.remove(i);
            cellScroll = Math.min(cellScroll, maxCellScroll(v));
            buildForm();
            return true;
        }
        int rw = cellRowW(v);
        if (!Ui.hit(mx, my, x + PAD, ry, rw, rowH)) return false;
        int action = CELL_VALUE;
        if (map) {
            int halfW = keyW(rw);
            action = onCellChip(mx, my, x + PAD + 18, ry, halfW) ? CELL_KEY
                    : onCellChip(mx, my, x + PAD + 29 + halfW, ry, rw - 31 - halfW) ? CELL_VALUE
                    : CELL_NONE;
        }
        cellDrag = i;
        cellAction = action;
        cellMoved = false;
        cellX0 = mx;
        cellY0 = my;
        cellGrabY = (int) my - ry;
        return true;
    }

    private void drawEntryCard(GuiGraphicsExtractor ctx, String icon, String name, String category,
                               String description, String badge) {
        drawEntryCard(ctx, icon == null ? null : Catalog.stackOf(icon), name, category,
                description, badge);
    }

    private void drawEntryCard(GuiGraphicsExtractor ctx, ItemStack picture, String name,
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
        ctx.item(picture, x + PAD + 6,
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

    private void drawItemCard(GuiGraphicsExtractor ctx, Value v) {
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
        ctx.item(st, x + PAD + 6, iconY);
        ctx.itemDecorations(tr, st, x + PAD + 6, iconY);
        ctx.text(tr, McText.fit(tr, McText.runsOf(st.getHoverName()), full - 35), tx, top,
                Draw.opaque(Theme.TEXT), false);
        Draw.textFit(ctx, tr, v.itemId, tx, top + 11, full - 35, Theme.TEXT_FAINT, false);
        if (!summary.isEmpty() && ch >= 40)
            Draw.textFit(ctx, tr, summary, x + PAD + 8, top + 11 + Ui.TEXT_H + 3, full - 16,
                    Theme.TEXT_DIM, false);
    }

    private static boolean hand() { return !Env.browser(); }

    private static ItemStack held() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack st = player.getMainHandItem();
        return st == null || st.isEmpty() ? null : st;
    }

    private void drawChoose(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean chosen, String pick,
                            String other) {
        Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, py("choose"), inner(), btnH,
                Draw.SEARCH, chosen ? other : pick, Ui.GHOST, true);
    }

    private void drawInput(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int i) {
        Ui.input(ctx, x + PAD, py("input"), inner(), fieldH, fields.get(i).isFocused());
        fields.get(i).extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, fields.get(i));
    }

    private void drawPreview(GuiGraphicsExtractor ctx, Value v) {
        int pyy = py("preview"), full = inner(), ph = ph("preview");
        Ui.well(ctx, x + PAD, pyy, full, ph);
        String raw = fields.get(0).getValue();
        if (raw.isEmpty()) {
            Draw.text(ctx, tr, "как это увидит игрок", x + PAD + 8, pyy + (ph - Ui.TEXT_H) / 2,
                    Theme.TEXT_FAINT, false);
            return;
        }
        ctx.enableScissor(x + PAD + 1, pyy + 1, x + PAD + full - 1, pyy + ph - 1);
        ctx.text(tr, McText.preview(raw, v.parsing), x + PAD + 8,
                pyy + (ph - Ui.TEXT_H) / 2, Draw.opaque(Theme.TEXT), false);
        ctx.disableScissor();
    }

    private int footBtnY() { return footY() + (footH - footBtnH) / 2; }

    private void drawFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int accent) {
        int fy = footBtnY();
        int room = inner() - (okW + Ui.FOOT_GAP + noW + 4);
        if (flash != null && System.currentTimeMillis() - flashAt < FLASH_MS) {
            Draw.textFit(ctx, tr, flash, x + PAD, fy + 4, room, flashBad ? Theme.DANGER : Theme.OK, false);
        } else if (note != null) {
            Draw.textFit(ctx, tr, note, x + PAD, fy + 4, room, Theme.TEXT_DIM, false);
        } else if (paired()) {
            Draw.textFit(ctx, tr, "заполнено " + cellCount(current()) + " из " + cellMax(current()),
                    x + PAD, fy + 4, room, Theme.TEXT_DIM, false);
        } else {
            ctx.item(Catalog.stackOf(Values.kindItem(current().type)), x + PAD - 2, fy);
            Draw.textFit(ctx, tr, Values.kindName(current().type), x + PAD + 16, fy + 4,
                    room - 16, Draw.shade(accent, 0.25f), false);
        }
        Ui.okCancel(ctx, tr, mouseX, mouseY, x + w - PAD, fy, okW, noW, footBtnH);
    }

    private boolean footClicked(double mx, double my) {
        int hit = Ui.okCancelAt(mx, my, x + w - PAD, footBtnY(), okW, noW, footBtnH);
        if (hit == Ui.FOOT_OK) { commit(); closed = true; return true; }
        if (hit == Ui.FOOT_CANCEL) {
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
        if (child() != null) return true;
        if (menu != null && menu.contains(mx, my)) return true;
        int out = touch ? 0 : 3;
        return mx >= x && mx < x + w + out && my >= y && my < y + h + out;
    }

    private void addAfterCurrent() {
        readForm();
        Value now = current();
        Value fresh = Value.of(now.type);
        if (Value.VARIABLE.equals(now.type)) fresh.scope = now.scope;
        if (Value.TEXT.equals(now.type)) fresh.parsing = now.parsing;
        int at = sel + 1;
        values.add(at, fresh);
        Map<Integer, Map<String, Value>> moved = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Value>> e : remembered.entrySet())
            moved.put(e.getKey() >= at ? e.getKey() + 1 : e.getKey(), e.getValue());
        remembered.clear();
        remembered.putAll(moved);
        sel = at;
        buildForm();
    }

    private void breakLine(EditBox f) {
        String text = f.getValue();
        int at = Math.max(0, Math.min(text.length(), f.getCursorPosition()));
        String picked = f.getHighlighted();
        int from = at, to = at;
        if (!picked.isEmpty()) {
            int start = text.indexOf(picked, Math.max(0, at - picked.length()));
            if (start >= 0) { from = start; to = start + picked.length(); }
        }
        openLines(current(), text.substring(0, from) + TextLines.BREAK + text.substring(to),
                from + TextLines.BREAK.length());
    }

    private static String describe(String data, String text) {
        if (text.equals(Localized.text(data))) return data;
        com.google.gson.JsonObject translations = Localized.translations(data);
        if (text.isEmpty() && translations.isEmpty()) return "";
        return Localized.data(text, Localized.parsing(data), translations);
    }

    private void openLines(Value v, String text, int cursor) {
        readForm();
        lines = new TextLines(tr, screenW, screenH, text, cursor, v.parsing, Values.color(Value.TEXT),
                (joined, at) -> {
                    v.text = joined;
                    EditBox f = fields.get(0);
                    f.setValue(joined);
                    f.setCursorPosition(at);
                    f.setHighlightPos(at);
                });
    }

    private void openStudio(Value v) {
        readForm();
        studio = new TextStudio(tr, screenW, screenH, v.text, v.parsing, (text, parsing) -> {
            v.text = text;
            v.parsing = parsing;
            fields.get(0).setValue(text);
            fields.get(0).moveCursorToStart(false);
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
                            g.description, g.returns, g.returnsNote, g.icon()));
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
            case Value.BLOCK -> picker = new BlockStudio(tr, screenW, screenH,
                    Values.color(Value.BLOCK), v.block, id -> {
                        v.block = id;
                        rebuild = true;
                    });
            default -> { }
        }
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

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        Overlay top = child();
        if (top != null) {
            top.mouseClicked(click, doubled);
            TextLines.Click passed = top == lines ? lines.outside() : null;
            settle(top);
            if (passed != null && child() == null && contains(mx, my))
                return mouseClicked(passed.event(), passed.doubled());
            return true;
        }
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (complete.mouseClicked(mx, my)) return true;
        if (!complete.active() && names.mouseClicked(mx, my, this::pickName)) return true;
        if (sizer.press(click, doubled, x, y, w, h)) {
            if (!sizer.active()) reflow();
            return true;
        }
        if (!contains(mx, my)) { commit(); closed = true; return true; }

        if (Ui.hit(mx, my, x + w - PAD - 14, y + 6, 14, 14)) { commit(); closed = true; return true; }
        if (numberToggle() && Ui.hit(mx, my, toggleX(), y + 6, 14, 14)) {
            openNumbers();
            return true;
        }

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
        if (listBar.grabbed(mx, my, rowH + 2, maxListScroll(), v -> listScroll = v)) return true;
        if (cellBar.grabbed(mx, my, rowH + 2, maxCellScroll(current()), v -> cellScroll = v))
            return true;

        if (list() && listClicked(mx, my, click.button())) return true;

        if (localizedField() != null) {
            for (int i = 0; i < Localized.LANGS.size(); i++)
                if (Ui.hit(mx, my, x + PAD, railY() + i * (LANG_ROW_H + 2), inner(), LANG_ROW_H)) {
                    switchLang(Localized.LANGS.get(i).id());
                    return true;
                }
        }

        int ki = localizedField() != null || paired() ? -1 : grid().indexAt(mx, my, kinds().size());
        if (ki >= 0) {
            String id = kinds().get(ki).id();
            if (usable(id) && !id.equals(current().type)) {
                readForm();
                Map<String, Value> kept = remembered.computeIfAbsent(sel, k -> new java.util.HashMap<>());
                kept.put(current().type, current());
                Value back = kept.get(id);
                values.set(sel, back != null ? back : Value.of(id));
                chosen(current());
                buildForm();
            } else if (usable(id)) {
                chosen(current());
            }
            return true;
        }

        if (formClicked(click, doubled, mx, my)) return true;

        footClicked(mx, my);
        return true;
    }

    private boolean listClicked(int mx, int my, int button) {
        Catalog.Slots grid = slots();
        if (grid != null) {
            int slot = slotAt(grid, mx, my);
            if (slot < 0) return false;
            if (button != 0) return clearSlot(mx, my);
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
            int ry = listY() + CAP + r * (rowH + 2);
            if (Ui.hit(mx, my, x + PAD + inner() - rowH, ry, rowH, rowH)) {
                readForm();
                remembered.clear();
                if (values.size() > 1) values.remove(i);
                else values.set(0, Value.of(Values.defaultKind(arg.type)));
                sel = Math.min(sel, values.size() - 1);
                buildForm();
                return true;
            }
            if (Ui.hit(mx, my, x + PAD, ry, rw, rowH)) {
                readForm();
                sel = i;
                buildForm();
                return true;
            }
        }
        if (canAdd() && Ui.hit(mx, my, x + PAD,
                listY() + CAP + listRows() * (rowH + 2), inner(), addH)) {
            readForm();
            values.add(Value.of(Values.defaultKind(arg.type)));
            sel = values.size() - 1;
            buildForm();
            return true;
        }
        return false;
    }

    private String newElementText() {
        return newElemAt < 0 || newElemAt >= fields.size() ? "" : fields.get(newElemAt).getValue().trim();
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
        if (newElemAt >= 0) focus(newElemAt);
    }

    private int chip(Ui.Chips chips, int mx, int my, String row) {
        return chips == null ? -1 : chips.indexAt(mx, my, x + PAD, py(row));
    }

    private boolean formClicked(MouseButtonEvent click, boolean doubled, int mx, int my) {
        Value v = current();
        int full = inner();
        boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (stepsClicked(click, mx, my)) return true;

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
                        fields.get(0).setValue(v.text);
                        fields.get(0).moveCursorToStart(false);
                    }
                    return true;
                }
                if (rowHit("studio", mx, my)) {
                    if (mx < x + PAD + (full - GAP) / 2 + GAP / 2) openStudio(v);
                    else {
                        readForm();
                        openLines(v, v.text, fields.get(0).getCursorPosition());
                    }
                    return true;
                }
            }
            case Value.NUMBER -> {
                if (Ui.hit(mx, my, x + PAD, numberCapY(), full, CAP)
                        && !formula(fields.get(0).getValue())) {
                    scrubField = 0;
                    scrubFrom = numberIn(0);
                    scrubX0 = click.x();
                    scrubbing = false;
                    focus(0);
                    return true;
                }
                int step = shift ? 10 : 1;
                if (Ui.hit(mx, my, x + PAD, py("input"), fieldH, fieldH)) { bump(-step); return true; }
                if (Ui.hit(mx, my, x + PAD + full - fieldH, py("input"), fieldH, fieldH)) {
                    bump(step);
                    return true;
                }
                if (Ui.hit(mx, my, x + PAD + fieldH + 4, py("input"), full - 2 * (fieldH + 4),
                        fieldH)) return clickField(click, doubled, mx, my, 0);
            }
            case Value.VARIABLE -> {
                if (clickField(click, doubled, mx, my, 0)) return true;
                int si = chip(scopeChips, mx, my, "scope");
                if (si >= 0) {
                    v.scope = Values.SCOPES.get(si).id();
                    focus(0);
                    fields.get(0).moveCursorToEnd(false);
                    syncComplete();
                    return true;
                }
            }
            case Value.PARAMETER -> {
                if (clickField(click, doubled, mx, my, 0)) return true;
                if (rowFieldClicked(click, doubled, mx, my)) return true;
                int ki = chip(kindChips, mx, my, "pkinds");
                if (ki >= 0) {
                    String picked = PARAM_KINDS.get(ki);
                    if (!picked.equals(v.typeKey)) {
                        readForm();
                        v.typeKey = picked;
                        v.paramDefault = "";
                        Functions.zeroDefault(v);
                        buildForm();
                    }
                    return true;
                }
                if (Value.ENUM.equals(v.typeKey)) return elementsClicked(click, doubled, v, mx, my);
                int pi = chip(paramChips, mx, my, "ptypes");
                if (pi >= 0) {
                    v.valueType = PARAM_TYPES.get(pi);
                    Functions.zeroDefault(v);
                    return true;
                }
                if (Ui.hit(mx, my, x + PAD, py("flags"), full, ctrlH)) {
                    v.required = !v.required;
                    Functions.zeroDefault(v);
                    return true;
                }
                if (Value.PLURAL.equals(v.typeKey)
                        && Ui.hit(mx, my, x + PAD, py("flags") + ctrlH + FLAG_GAP, full, ctrlH)) {
                    v.ignoreEmpty = !v.ignoreEmpty;
                    return true;
                }
                if (defaultClicked(v, mx, my)) return true;
            }
            case Value.LOCATION, Value.VECTOR -> {
                if (rowFieldClicked(click, doubled, mx, my)) return true;
                if (has("world") && Ui.hit(mx, my, x + PAD, py("world"), full, btnH)
                        && Minecraft.getInstance().player != null) {
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
                    if (Ui.hit(mx, my, x + PAD, cy, 26 + 4 + 84, ctrlH)) {
                        openColorPick(false);
                        return true;
                    }
                    if (Ui.hit(mx, my, x + PAD + 128, cy, full - 128, ctrlH)) {
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
                int ty = py("tools"), half = hand() ? (full - GAP) / 2 : -GAP;
                if (hand() && Ui.hit(mx, my, x + PAD, ty, half, btnH)) {
                    ItemStack st = held();
                    if (st != null) {
                        readForm();
                        Stacks.read(v, st.copy());
                        buildForm();
                    }
                    return true;
                }
                if (Ui.hit(mx, my, x + PAD + half + GAP, ty, full - half - GAP, btnH)) {
                    openItemStudio(v);
                    return true;
                }
                if (rowFieldClicked(click, doubled, mx, my)) return true;
            }
            case Value.BLOCK -> {
                if (rowHit("choose", mx, my) || rowHit("card", mx, my)) { openPicker(); return true; }
                if (hand() && rowHit("tools", mx, my)) {
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
                        menu = Menu.options(screenW, screenH, x + PAD, py("target") + ctrlH + 2, tr,
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

    private boolean rowFieldClicked(MouseButtonEvent click, boolean doubled, int mx, int my) {
        for (Form.Row r : form.rows) {
            int cw = colW(r), ry = py(r.id());
            for (int i = 0; i < r.count(); i++) {
                if (scrubbable(r) && Ui.hit(mx, my, colX(r, i), ry, cw, 9)) {
                    scrubField = r.from() + i;
                    scrubFrom = numberIn(scrubField);
                    scrubX0 = click.x();
                    scrubbing = false;
                    focus(r.from() + i);
                    return true;
                }
                if (Ui.hit(mx, my, colX(r, i), ry + 9, cw, fieldH))
                    return takeFocus(click, doubled, r.from() + i);
            }
        }
        return false;
    }

    private boolean takeFocus(MouseButtonEvent click, boolean doubled, int i) {
        if (i >= fields.size()) return false;
        focus(i, false);
        EditBox f = fields.get(i);
        if (!f.mouseClicked(click, doubled)) f.onClick(click, doubled);
        return true;
    }

    private boolean clickField(MouseButtonEvent click, boolean doubled, int mx, int my, int i) {
        if (i >= fields.size()) return false;
        String id = has("input") ? "input" : has("material") ? "material" : null;
        if (id == null) return false;
        int fx = x + PAD, fw = inner();
        if (Value.NUMBER.equals(current().type)) {
            fx += fieldH + 4;
            fw -= 2 * (fieldH + 4);
        }
        if (!Ui.hit(mx, my, fx, py(id), fw, fieldH)) return false;
        return takeFocus(click, doubled, i);
    }

    private void bump(double delta) {
        EditBox f = fields.get(0);
        if (formula(f.getValue())) return;
        double now = numberIn(0);
        f.setValue(Value.num(now + delta));
        f.moveCursorToEnd(false);
        focus(0);
    }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        Overlay top = child();
        if (top != null) return top.mouseDragged(click, dx, dy);
        if (sizer.drag(click.x(), click.y())) {
            reflow();
            return true;
        }
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (dragSlot >= 0) { slotMoved = true; return true; }
        if (cellDrag >= 0) {
            if (!cellMoved && Math.abs(click.x() - cellX0) + Math.abs(click.y() - cellY0) >= CELL_MOVE_PX)
                cellMoved = true;
            if (cellMoved) {
                lastMx = (int) click.x();
                lastMy = (int) click.y();
            }
            return true;
        }
        if (bodyBar.dragged(click.y(), 1, pane.max(), v -> pane.scroll = v)) return true;
        if (listBar.dragged(click.y(), rowH + 2, maxListScroll(), v -> listScroll = v)) return true;
        if (cellBar.dragged(click.y(), rowH + 2, maxCellScroll(current()), v -> cellScroll = v))
            return true;
        if (dragging) { moveTo((int) click.x() - dragX, (int) click.y() - dragY); return true; }
        if (scrub(click)) return true;
        if (has("player") && player.mouseDragged((int) click.x(), x + PAD, py("player"), inner(),
                SoundPlayer.H)) return true;
        if (fields.isEmpty()) return false;
        return fields.get(focus).mouseDragged(click, dx, dy);
    }

    private static boolean held(int left, int right) {
        var window = Minecraft.getInstance().getWindow();
        return window != null && (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, left)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, right));
    }

    private boolean scrub(MouseButtonEvent click) {
        if (scrubField < 0 || scrubField >= fields.size()) return false;
        double dx = click.x() - scrubX0;
        if (!scrubbing && Math.abs(dx) < 3) return true;
        if (!scrubbing) {
            scrubbing = true;
            fields.get(scrubField).moveCursorToEnd(false);
        }
        int n = (int) (dx / (ctrlHeld() ? STEP_PX_COARSE : STEP_PX));
        setNumber(scrubField, snap(scrubFrom, n, step(scrubField)));
        return true;
    }

    public void mouseReleased() {
        sizer.release();
        slotDropped();
        cellDropped();
        dragging = false;
        bodyBar.release();
        listBar.release();
        cellBar.release();
        if (menu != null) menu.mouseReleased();
        scrubField = -1;
        scrubbing = false;
        Overlay top = child();
        if (top != null) top.mouseReleased();
        if (nested != null) return;
        if (player != null) player.mouseReleased();
    }

    private void moveTo(int nx, int ny) {
        x = Math.max(4, Math.min(Math.max(4, screenW - w - 4), nx));
        y = Math.max(4, Math.min(Math.max(4, screenH - h - 4), ny));
        placeFields();
        if (!fields.isEmpty()) syncComplete();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        Overlay top = child();
        if (top != null) return top.mouseScrolled(mx, my, amount);
        if (menu != null) return menu.mouseScrolled(mx, my, amount);
        if (complete.mouseScrolled(mx, my, amount)) return true;
        if (!complete.active() && names.mouseScrolled(mx, my, amount)) return true;
        if (has("player") && player != null && player.mouseScrolled(mx, my, amount, x + PAD,
                py("player"), inner(), SoundPlayer.H)) return true;
        if (has("cells")) {
            Value v = current();
            if (maxCellScroll(v) > 0 && Ui.hit(mx, my, x + PAD, py("cells"), inner(),
                    cellRows(v) * (rowH + 2))) {
                cellScroll = Math.max(0, Math.min(maxCellScroll(v),
                        cellScroll - Ui.rows(amount, 1, rowH + 2)));
                return true;
            }
        }
        if (list() && maxListScroll() > 0 && Ui.hit(mx, my, x + PAD, listY() + CAP, inner(),
                listRows() * (rowH + 2))) {
            listScroll = Math.max(0, Math.min(maxListScroll(),
                    listScroll - Ui.rows(amount, 1, rowH + 2)));
            return true;
        }
        int wheel = Env.touch() || amount == 0 ? -1 : wheelField(mx, my);
        if (wheel >= 0) {
            if (!formula(fields.get(wheel).getValue()))
                setNumber(wheel, snap(numberIn(wheel), amount > 0 ? 1 : -1,
                        step(wheel)));
            return true;
        }
        if (pane.max() > 0 && contains(mx, my)) {
            pane.wheel(amount);
            placeFields();
            return true;
        }
        return contains(mx, my);
    }

    public boolean keyPressed(KeyEvent input) {
        Overlay top = child();
        if (top != null) {
            top.keyPressed(input);
            settle(top);
            return true;
        }
        int key = input.key();
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (complete.keyPressed(input)) return true;
        if (!complete.active() && names.keyPressed(input, this::pickName)) return true;
        if (key == GLFW.GLFW_KEY_SPACE && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && has("player")) {
            Audio.toggle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancelled = true; closed = true; return true; }
        if (input.isCopy() && copyValue()) return true;
        if (input.isCut() && cutSlot()) return true;
        if (input.isPaste() && pasteValue()) return true;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (enter && shift && list()) {
            if (canAdd()) addAfterCurrent();
            return true;
        }
        if (enter && shift && Value.TEXT.equals(current().type) && focus == 0 && !fields.isEmpty()) {
            breakLine(fields.get(0));
            return true;
        }
        if (enter && newElemAt >= 0 && focus == newElemAt && !newElementText().isEmpty()) {
            addElement(current());
            return true;
        }
        if (enter) {
            commit();
            closed = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && fields.size() > 1) {
            boolean back = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            focus(Math.floorMod(focus + (back ? -1 : 1), fields.size()));
            selectAll(fields.get(focus));
            return true;
        }
        if (fields.isEmpty()) return true;
        boolean used = fields.get(focus).keyPressed(input);
        afterTyping();
        return used;
    }

    public boolean charTyped(CharacterEvent input) {
        Overlay top = child();
        if (top != null) return top.charTyped(input);
        if (menu != null) return true;
        if (fields.isEmpty()) return true;
        boolean used = fields.get(focus).charTyped(input);
        afterTyping();
        return used;
    }

    private int hoveredSlot() {
        Catalog.Slots grid = slots();
        return grid == null || !contains(lastMx, lastMy) ? -1 : slotAt(grid, lastMx, lastMy);
    }

    private boolean copyValue() {
        if (paired()) return false;
        int slot = hoveredSlot();
        if (slot < 0 && !fields.isEmpty()) {
            EditBox f = fields.get(focus);
            String picked = f.getHighlighted();
            if (!picked.isEmpty() && !picked.equals(f.getValue())) return false;
        }
        readForm();
        Value v = slot >= 0 ? values.get(slot) : current();
        if (v.isBlank()) {
            flash("копировать нечего", true);
            return true;
        }
        Clip.copyValues(List.of(v));
        flash("скопировано «" + v.label() + "»", false);
        return true;
    }

    private boolean cutSlot() {
        int slot = hoveredSlot();
        if (slot < 0) return false;
        readForm();
        Value v = values.get(slot);
        if (v.isBlank()) {
            flash("вырезать нечего", true);
            return true;
        }
        Clip.copyValues(List.of(v));
        values.set(slot, Value.of(Value.ITEM));
        buildForm();
        flash("вырезано «" + v.label() + "»", false);
        return true;
    }

    private static boolean pristine(Value v) {
        return v.isBlank() || v.toJson().toString().equals(Value.of(v.type).toJson().toString());
    }

    private boolean pasteValue() {
        if (paired()) return false;
        if (slots() != null) return pasteSlots();
        List<Value> got = Clip.boardValues();
        if (got.isEmpty()) return false;
        List<Value> ok = new ArrayList<>(got.size());
        for (Value v : got) if (usable(v.type)) ok.add(v);
        if (ok.isEmpty()) {
            flash("такое значение сюда не кладётся", true);
            return true;
        }
        readForm();
        remembered.clear();
        int put = 0;
        if (!list()) {
            values.set(sel, ok.get(0).copy());
            put = 1;
        } else {
            int at = sel;
            boolean reuse = pristine(current());
            for (Value v : ok) {
                if (reuse) {
                    values.set(at, v.copy());
                    reuse = false;
                } else {
                    if (values.size() >= arg.capacity) break;
                    values.add(++at, v.copy());
                }
                put++;
            }
            sel = at;
        }
        chosen(current());
        clampListScroll();
        buildForm();
        int left = ok.size() - put;
        flash(left > 0 ? "вставлено " + put + ", не влезло " + left
                : put == 1 ? "вставлено «" + ok.get(0).label() + "»" : "вставлено " + put, left > 0);
        return true;
    }

    private boolean pasteSlots() {
        List<Clip.Placed> layout = Clip.boardSlots();
        List<Value> got = layout.isEmpty() ? Clip.boardValues() : List.of();
        if (layout.isEmpty() && got.isEmpty()) return false;
        List<Clip.Placed> placed = new ArrayList<>();
        int target = hoveredSlot();
        if (target < 0) target = sel;
        if (!layout.isEmpty()) {
            for (Clip.Placed p : layout) if (usable(p.value().type)) placed.add(p);
        } else {
            int at = target;
            for (Value v : got) {
                if (!usable(v.type)) continue;
                while (at < values.size() && at != target && !values.get(at).isBlank()) at++;
                placed.add(new Clip.Placed(at++, v));
            }
        }
        if (placed.isEmpty()) {
            flash("такое значение сюда не кладётся", true);
            return true;
        }
        readForm();
        remembered.clear();
        int put = 0, first = -1;
        for (Clip.Placed p : placed) {
            if (p.slot() >= values.size()) continue;
            values.set(p.slot(), p.value().copy());
            if (first < 0) first = p.slot();
            put++;
        }
        if (first >= 0) sel = layout.isEmpty() ? target : first;
        chosen(current());
        buildForm();
        int left = (layout.isEmpty() ? got.size() : layout.size()) - put;
        String said = put == 1 && left == 0 ? "вставлено «" + placed.get(0).value().label() + "»"
                : "вставлено " + put;
        if (!layout.isEmpty()) said += " · слоты те же";
        flash(left > 0 ? said + " · не влезло " + left : said, left > 0);
        return true;
    }

    private void flash(String text, boolean bad) {
        flash = text;
        flashBad = bad;
        flashAt = System.currentTimeMillis();
    }

    private void afterTyping() {
        spread(focus);
        syncComplete();
        live();
    }

    private void pickName(String name, String scope) {
        Value v = current();
        EditBox f = fields.get(0);
        f.setValue(name);
        f.moveCursorToEnd(false);
        v.name = name;
        if (Value.VARIABLE.equals(v.type) && !scope.isEmpty()) v.scope = scope;
        syncComplete();
    }

    private List<NamePick.Known> namePool() {
        if (Value.VARIABLE.equals(current().type)) return knownVars;
        List<NamePick.Known> out = new ArrayList<>();
        for (String p : knownParams) out.add(new NamePick.Known(p, "", false, 0));
        return out;
    }

    private boolean completable() {
        if (fields.isEmpty() || focus != 0) return false;
        String type = current().type;
        return Value.TEXT.equals(type) || Value.VARIABLE.equals(type)
                || Value.NUMBER.equals(type);
    }

    private void syncComplete() {
        Value v = current();
        complete.update(completable() ? fields.get(focus) : null, tr, screenW, screenH,
                Value.NUMBER.equals(v.type), Value.TEXT.equals(v.type) && McText.MINI.equals(v.parsing));
        boolean named = Value.VARIABLE.equals(v.type) || Value.PARAMETER.equals(v.type);
        names.update(named && !fields.isEmpty() && focus == 0 ? fields.get(0) : null, namePool(), v.scope,
                Value.VARIABLE.equals(v.type), tr, screenW, screenH, x, w);
    }

    private static boolean formula(String s) { return s.indexOf('%') >= 0; }

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
            case Value.TEXT -> v.text = fields.get(0).getValue();
            case Value.NUMBER -> {
                String typed = fields.get(0).getValue();
                if (formula(typed)) v.numberText = typed.trim();
                else { v.number = numberIn(0); v.numberText = ""; }
            }
            case Value.VARIABLE -> v.name = fields.get(0).getValue().trim();
            case Value.PARAMETER -> {
                v.name = fields.get(0).getValue().trim();
                Form.Row d = rowOf("pdesc");
                if (d != null) v.paramDesc = describe(v.paramDesc, fields.get(d.from()).getValue());
            }
            case Value.LOCATION -> {
                v.x = parse(fields.get(0).getValue()); v.y = parse(fields.get(1).getValue());
                v.z = parse(fields.get(2).getValue());
                v.yaw = Math.max(-360, Math.min(360, parse(fields.get(3).getValue())));
                v.pitch = Math.max(-90, Math.min(90, parse(fields.get(4).getValue())));
            }
            case Value.VECTOR -> {
                v.x = parse(fields.get(0).getValue()); v.y = parse(fields.get(1).getValue());
                v.z = parse(fields.get(2).getValue());
            }
            case Value.SOUND -> {
                v.volume = parse(fields.get(0).getValue());
                v.pitch2 = parse(fields.get(1).getValue());
            }
            case Value.PARTICLE -> {
                v.count = Math.max(0, (int) parse(fields.get(0).getValue()));
                v.spread1 = parse(fields.get(1).getValue());
                v.spread2 = parse(fields.get(2).getValue());
                Form.Row m = rowOf("motion");
                if (m != null) {
                    v.mx = parse(fields.get(m.from()).getValue());
                    v.my = parse(fields.get(m.from() + 1).getValue());
                    v.mz = parse(fields.get(m.from() + 2).getValue());
                }
                Form.Row sz = rowOf("size");
                if (sz != null) v.size = parse(fields.get(sz.from()).getValue());
                if (has("material")) v.material = Value.materialId(fields.get(fields.size() - 1).getValue());
            }
            case Value.POTION -> {
                v.duration = (int) numberIn(0);
                v.amplifier = Math.max(0, (int) parse(fields.get(1).getValue()) - 1);
            }
            case Value.ITEM -> v.itemCount = Math.max(1, Math.min(99,
                    (int) parse(fields.get(0).getValue())));
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
        if (paired()) {
            storePairs();
            changed = !pairJson().equals(before);
            return;
        }
        if (localizedField() != null) {
            langs.commit(values, this::store);
            changed = !langs.json().equals(before);
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
