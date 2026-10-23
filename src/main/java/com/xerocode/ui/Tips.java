package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Env;
import com.xerocode.Functions;
import com.xerocode.Localized;
import com.xerocode.Mapping;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Value;
import com.xerocode.Values;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class Tips {
    private static final int TOOLTIP_W = 260;

    private final Font font;
    private final Script script;

    Tips(Font font, Script script) {
        this.font = font;
        this.script = script;
    }

    private static int room(int screenW) { return Math.max(120, Math.min(TOOLTIP_W, screenW - 40)); }

    void chip(GuiGraphicsExtractor ctx, Layout.Box box, Layout.Chip chip, int screenW,
              int mouseX, int mouseY) {
        int room = room(screenW);
        List<Component> lines = new ArrayList<>();
        if (chip.isPlus()) {
            lines.add(Component.literal("Добавить параметр"));
            lines.add(Component.literal("§8станет строчной переменной с этим именем"));
        } else if (chip.isCell()) {
            Value p = chip.value;
            lines.add(Component.literal(p == null || p.name.isBlank() ? "параметр без имени" : p.name));
            if (p != null) {
                for (String line : paramAbout(p, room)) lines.add(Component.literal("§7" + line));
                lines.add(Component.literal("§8принимает: §7" + p.paramNote()));
                if (Value.ENUM.equals(p.typeKey)) {
                    StringBuilder sb = new StringBuilder();
                    for (Value.Elem e : p.elements) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(e.name);
                    }
                    if (sb.length() > 0) lines.add(Component.literal("§8варианты: §7" + sb));
                }
            }
            lines.add(Component.literal(Env.touch() ? "§8тап — правка, 2 пальца — удалить" : "§8ЛКМ — правка, ПКМ — удалить"));
            if (!Env.touch() && p != null && !p.name.isBlank() && Carry.paramCell(box, chip))
                lines.add(Component.literal("§8Shift+ПКМ — взять как строчную переменную"));
            if (!Carry.values(box, chip).isEmpty())
                lines.add(Component.literal("§8" + copyHint()));
        } else if (chip.isCondition()) {
            Script.Node cond = box.node.cond;
            lines.add(Component.literal(cond == null ? "Условие не выбрано" : cond.action.name));
            if (cond == null) {
                lines.add(Component.literal("§8блок работает по вложенному условию"));
            } else {
                lines.add(Component.literal("§8" + (cond.action.category == null
                        ? "" : cond.action.category.name)
                        + (cond.inverted() ? " §8· §7НЕ" : "")));
                for (String line : describe(cond.action.description, room))
                    lines.add(Component.literal("§7" + line));
            }
            lines.add(Component.literal(Env.touch() ? "§8тап — выбрать условие, 2 пальца — НЕ" : "§8ЛКМ — выбрать условие, ПКМ — НЕ"));
        } else if (chip.isArg()) {
            Catalog.Arg arg = Layout.chipNode(box.node).args().get(chip.argIndex);
            lines.add(Component.literal(arg.purpose));
            lines.add(Component.literal("§8слот: §7" + arg.type
                    + (arg.list ? " §8· до " + arg.capacity + " значений" : "")));
            Value v = chip.value;
            if (v != null) {
                lines.add(Component.literal("§8значение: §7" + Values.kindName(v.type)
                        + (v.note().isEmpty() ? "" : " §8· " + v.note())));
                if (Value.GAME_VALUE.equals(v.type)) {
                    Values.GameValue g = Values.gameValue(v.gameValue);
                    if (g != null) {
                        for (String line : describe(g.description, room)) lines.add(Component.literal("§7" + line));
                        if (!g.returns.isEmpty())
                            lines.add(Component.literal("§8возвращает: §7" + g.returns
                                    + (g.returnsNote.isEmpty() ? "" : " §8— " + g.returnsNote)));
                    }
                }
            }
            lines.add(Component.literal(Env.touch() ? "§8тап — значение, 2 пальца — очистить" : "§8ЛКМ — значение, ПКМ — очистить"));
            if (!Carry.values(box, chip).isEmpty())
                lines.add(Component.literal("§8" + copyHint()));
        } else {
            Script.Node owner = Layout.chipNode(box.node);
            Catalog.Setting s = owner.settings().get(chip.settingIndex);
            lines.add(Component.literal(s.label));
            Value bound = owner.markerVar(chip.settingIndex);
            if (bound != null && !bound.name.isBlank()) {
                Values.Scope sc = Values.scope(bound.scope);
                lines.add(Component.literal("§8привязана к переменной: §7" + bound.name
                        + (sc == null ? "" : " §8· " + sc.name())));
                lines.add(Component.literal("§8по умолчанию: §7"
                        + owner.marker(chip.settingIndex)));
            } else {
                lines.add(Component.literal("§8" + s.options.size() + " вариантов"
                        + (Env.touch() ? "" : " · Ctrl+колесо — листать")));
            }
        }
        ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    void action(GuiGraphicsExtractor ctx, Catalog.Action a, Script.Node node, int screenW,
                int mouseX, int mouseY) {
        int room = room(screenW);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(a.name));
        if (a.category != null)
            lines.add(Component.literal("§8" + a.category.name
                    + (a.subcategory == null ? "" : " / " + a.subcategory)));
        for (String line : describe(a.description, room)) lines.add(Component.literal("§7" + line));
        if (a.unavailable) lines.add(Component.literal("§eНедоступно в этом мире"));
        if (!a.args.isEmpty()) {
            lines.add(Component.literal("§fАргументы:"));
            for (Catalog.Arg g : a.args)
                lines.add(Component.literal("§8• §7" + g.purpose + " §8— " + g.type
                        + (g.list ? " (до " + g.capacity + ")" : "")));
        }
        if (!a.settings.isEmpty()) {
            lines.add(Component.literal("§fНастройки:"));
            for (Catalog.Setting s : a.settings)
                lines.add(Component.literal("§8• §7" + s.label + " §8= " + s.def));
        }
        if (node != null && node.cond != null) {
            lines.add(Component.literal("§fУсловие:"));
            lines.add(Component.literal("§8• §7" + (node.cond.inverted() ? "НЕ " : "")
                    + node.cond.action.name));
            for (Catalog.Arg g : node.cond.action.args)
                lines.add(Component.literal("§8• §7" + g.purpose + " §8— " + g.type));
        } else if (node != null && Mapping.hasConditional(a)) {
            lines.add(Component.literal("§eУсловие не выбрано"));
        }
        if (node != null && (node.declares() || node.invokes())) function(lines, node);
        if (node != null) {
            int target = node.settingIndex(Catalog.TARGET);
            int invert = node.settingIndex(Catalog.INVERT);
            if (target >= 0)
                lines.add(Component.literal("§fЦель: §7" + node.marker(target)));
            if (invert >= 0 && Catalog.INVERT_ON.equals(node.marker(invert)))
                lines.add(Component.literal("§fУсловие: §cНЕ выполнено"));
            if (target >= 0 || invert >= 0)
                lines.add(Component.literal((Env.touch() ? "§8отпусти — " : "§8ПКМ — ")
                        + (target >= 0 ? "цель" : "") + (target >= 0 && invert >= 0 ? " и " : "")
                        + (invert >= 0 ? "«НЕ»" : "")));
        }
        ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private void function(List<Component> lines, Script.Node node) {
        boolean declares = node.declares();
        String name = declares ? Functions.nameOf(node) : Functions.targetOf(node);
        Script.Node decl = node;
        if (!declares) {
            Functions.Known known = Functions.of(script);
            Functions.Signature s = (node.isStart() ? known.processes() : known.functions()).get(name);
            if (s == null) {
                lines.add(Component.literal(name.isBlank()
                        ? "§eФункция не выбрана"
                        : "§eНа полотне нет " + (node.isStart() ? "процесса" : "функции") + " «"
                                + name + "»"));
                return;
            }
            decl = s.declaration();
            String shown = Layout.titleOf(s);
            lines.add(Component.literal("§f" + shown));
            if (!shown.equals(name)) lines.add(Component.literal("§8" + name));
        }
        if (declares) {
            Value display = Functions.displayOf(decl);
            if (display != null)
                lines.add(Component.literal("§8отображается как §7"
                        + McText.plain(display.text, display.parsing).trim()));
        }
        List<Value> params = Functions.parametersOf(decl);
        if (params.isEmpty()) {
            lines.add(Component.literal("§8без параметров"));
        } else {
            lines.add(Component.literal("§fПараметры:"));
            for (Value p : params)
                lines.add(Component.literal("§8• §7" + (p.name.isBlank() ? "без имени" : p.name)
                        + " §8— " + p.paramNote()));
        }
        List<String> desc = Layout.descAll(decl);
        if (!desc.isEmpty()) {
            lines.add(Component.literal("§fОписание:"));
            for (String d : desc) lines.add(Component.literal("§7" + d));
        }
    }

    private List<String> describe(String description, int room) {
        List<String> out = new ArrayList<>();
        if (description == null || description.isBlank()) return out;
        for (String part : description.split("(?=»)")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : piece.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(next) > room) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }

    private List<String> paramAbout(Value p, int room) {
        List<String> out = new ArrayList<>();
        String raw = Localized.text(p.paramDesc);
        if (raw.isBlank()) return out;
        String plain = McText.plain(raw, Localized.parsing(p.paramDesc));
        for (String part : plain.replace("\\n", "\n").split("\n")) {
            if (out.size() >= 6) break;
            out.addAll(describe(part, room));
        }
        return out.size() > 6 ? new ArrayList<>(out.subList(0, 6)) : out;
    }

    private static String copyHint() {
        return Env.touch() ? "тянуть — перенести" : Settings.get().label(Settings.Hot.DUPLICATE) + " — копировать, тянуть — перенести";
    }
}
