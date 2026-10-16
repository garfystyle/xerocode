package com.xerocode;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Upgrade {
    private static final Map<String, Integer> DONE = new LinkedHashMap<>();

    public static List<String> done() {
        List<String> out = new ArrayList<>();
        DONE.forEach((what, n) -> out.add(n > 1 ? what + " ×" + n : what));
        return out;
    }

    public static void forget() { DONE.clear(); }

    public static Script.Node node(Script.Node n) {
        if (n == null || !Mapping.loaded()) return n;
        if (n.cond != null) n.cond = node(n.cond);
        String branchId = Mapping.elseCondId(n.action);
        String id = branchId != null ? branchId : Mapping.actionId(n.action);
        Mapping.Rule rule = Mapping.upgrade(id);
        if (rule == null) rule = moved(n, id, branchId);
        if (rule == null) return n;
        return replace(n, id, branchId, rule);
    }

    private static Mapping.Rule moved(Script.Node n, String id, String branchId) {
        if (id == null || !n.action.deprecated) return null;
        Catalog.Action live = branchId != null
                ? Mapping.elseFor(id) : Mapping.catalogAction(Mapping.action(id));
        return live != null && live != n.action && !live.deprecated
                ? new Mapping.Rule(id, false, Map.of(), Map.of()) : null;
    }

    private static Script.Node replace(Script.Node n, String id, String branchId,
                                       Mapping.Rule rule) {
        Mapping.Act from = Mapping.action(id), to = Mapping.action(rule.to());
        Catalog.Action target = branchId != null
                ? Mapping.elseFor(rule.to()) : Mapping.catalogAction(to);
        if (from == null || to == null || target == null) return n;

        Script.Node out = new Script.Node(target);
        boolean moved = rule.to().equals(id);
        n.values.forEach((index, list) -> {
            Integer at = moved ? byLabel(n.action, target, index)
                    : argAt(from.argNames.get(index), rule, to);
            if (at != null && at < target.args.size()) out.valuesOf(at).addAll(list);
        });
        n.markers.forEach((index, label) -> {
            int at = settingAt(n, out, index, moved, from, rule, to);
            String option = label;
            if (!moved) {
                Map<String, String> ids = from.optionIds.get(index);
                String enumId = ids == null ? null : ids.get(label);
                Mapping.Setting dst = at < 0 ? null : to.settings.get(to.settingNames.get(at));
                option = dst == null || enumId == null ? null : dst.option(enumId);
            }
            if (at >= 0 && option != null && target.settings.get(at).options.contains(option))
                out.markers.put(at, option);
        });
        n.markerVars.forEach((index, variable) -> {
            int at = settingAt(n, out, index, moved, from, rule, to);
            if (at >= 0) out.markerVars.put(at, variable);
        });
        rule.set().forEach((argId, enumId) -> {
            Mapping.Setting dst = to.settings.get(argId);
            String option = dst == null ? null : dst.option(enumId);
            if (option != null && dst.index < target.settings.size())
                out.markers.put(dst.index, option);
        });
        String aim = n.settingOf(Catalog.TARGET);
        if (aim != null) out.setSetting(Catalog.TARGET, aim);
        out.setSetting(Catalog.INVERT,
                n.inverted() != rule.invert() ? Catalog.INVERT_ON : Catalog.INVERT_OFF);

        out.body.addAll(n.body);
        out.cond = n.cond;
        out.folded = n.folded;
        if (n.raw != null) {
            JsonObject raw = n.raw.deepCopy();
            raw.remove(Importer.KEPT_ID);
            out.raw = raw.isEmpty() ? null : raw;
        }
        String said = title(n).equals(title(out))
                ? "«" + title(n) + "» → «" + out.action.category.name + "»"
                : "«" + title(n) + "» → «" + title(out) + "»";
        DONE.merge(said, 1, Integer::sum);
        return out;
    }

    private static Integer byLabel(Catalog.Action from, Catalog.Action to, int index) {
        if (index >= from.args.size()) return null;
        String label = from.args.get(index).purpose;
        for (int i = 0; i < to.args.size(); i++) if (to.args.get(i).purpose.equals(label)) return i;
        return null;
    }

    private static Integer argAt(String argId, Mapping.Rule rule, Mapping.Act to) {
        return argId == null ? null : to.args.get(rule.argId(argId));
    }

    private static int settingAt(Script.Node n, Script.Node out, int index, boolean moved,
                                 Mapping.Act from, Mapping.Rule rule, Mapping.Act to) {
        if (index >= n.action.settings.size()) return -1;
        int at;
        if (moved) {
            at = out.settingIndex(n.action.settings.get(index).label);
        } else {
            String argId = from.settingNames.get(index);
            Mapping.Setting dst = argId == null ? null : to.settings.get(rule.argId(argId));
            at = dst == null ? -1 : dst.index;
        }
        return at < out.action.settings.size() ? at : -1;
    }

    private static String title(Script.Node n) {
        return (n.inverted() ? Catalog.INVERT_ON + " " : "") + n.action.name;
    }

    private Upgrade() {}
}
