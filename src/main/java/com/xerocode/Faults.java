package com.xerocode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;

public final class Faults {
    public enum Level { ERROR, FATAL }

    public record Fault(Level level, BlockPos at, String action, String reason, String plot, long time) {}

    public record Spot(Script script, Script.Node node) {}

    private static final Pattern TP = Pattern.compile("^/?editor tp (-?\\d+) (-?\\d+) (-?\\d+)$");
    private static final String[] ACTION_LABELS = {"Действие:", "Action:", "Дія:"};
    private static final String FRAME = "┌│┃└|";
    private static final int LINES = (Codespace.LAST_Z - Codespace.FIRST_Z) / Codespace.LINE_STEP + 1;

    private static volatile Fault last;
    private static volatile int version, shown = -1;

    private Faults() {}

    public static Fault last() { return last; }

    public static int version() { return version; }

    public static boolean fresh() { return shown != version; }

    public static void seen() { shown = version; }

    public static synchronized void clear() {
        if (last == null) return;
        last = null;
        version++;
    }

    public static synchronized boolean heard(String text, String click, String plot) {
        if (text == null || click == null) return false;
        Level level = text.contains("☠") ? Level.FATAL : text.contains("❌") ? Level.ERROR : null;
        if (level == null) return false;
        Matcher m = TP.matcher(click.strip());
        if (!m.matches()) return false;
        BlockPos at = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        String[] said = said(text);
        Fault next = new Fault(level, at, said[0], said[1], plot == null ? "" : plot, System.currentTimeMillis());
        Fault was = last;
        last = next;
        boolean same = was != null && was.at().equals(at) && was.reason().equals(next.reason()) && was.plot().equals(next.plot());
        if (!same) version++;
        return !same;
    }

    static String[] said(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = unframed(raw);
            if (!line.isEmpty()) lines.add(line);
        }
        for (int i = 0; i < lines.size(); i++)
            for (String label : ACTION_LABELS)
                if (lines.get(i).startsWith(label)) {
                    String action = lines.get(i).substring(label.length()).strip();
                    String reason = i + 1 < lines.size() ? String.join(" ", lines.subList(i + 1, lines.size())) : "";
                    return new String[]{action, reason};
                }
        String flat = String.join(" ", lines);
        int colon = flat.lastIndexOf(": ");
        return new String[]{"", colon < 0 ? flat : flat.substring(colon + 2).strip()};
    }

    private static String unframed(String line) {
        int from = 0;
        while (from < line.length() && (FRAME.indexOf(line.charAt(from)) >= 0 || Character.isWhitespace(line.charAt(from)))) from++;
        return line.substring(from).strip();
    }

    public static int position(Fault fault) {
        LineEdit.Where where = fault == null ? null : LineEdit.where(fault.at());
        return where == null ? -1 : (where.floor() - 1) * LINES + (where.event().getZ() - Codespace.FIRST_Z) / Codespace.LINE_STEP;
    }

    public static int column(Fault fault) {
        return fault == null ? -1 : (fault.at().getX() - Codespace.LINE_X) / 2 - 1;
    }

    public static Spot locate(Fault fault, Script active) {
        if (fault == null || active == null) return null;
        int position = position(fault), column = column(fault);
        if (position < 0 || column < 0) return null;
        Spot spot = find(Tabs.filled(active), position, column, fault.action());
        return spot != null ? spot : find(List.of(active), position, column, fault.action());
    }

    private static Spot find(List<Script> scripts, int position, int column, String action) {
        Exporter.Placed line = Exporter.lineAt(scripts, position);
        if (line == null) return null;
        List<Script.Node> chain = line.root().chain;
        Script.Node node = blockAt(chain.subList(1, chain.size()), column, new int[]{0});
        if (node != null && (action.isEmpty() || action.equals(node.action.name))) return new Spot(line.script(), node);
        if (action.isEmpty()) return null;
        Script.Node only = null;
        for (Script.Node n : flat(chain.subList(1, chain.size()), new ArrayList<>())) {
            if (!action.equals(n.action.name)) continue;
            if (only != null) return null;
            only = n;
        }
        return only == null ? null : new Spot(line.script(), only);
    }

    static Script.Node blockAt(List<Script.Node> ops, int column, int[] cursor) {
        for (Script.Node node : ops) {
            if (cursor[0] == column) return node;
            cursor[0]++;
            if (node.wraps() || !node.body.isEmpty()) {
                Script.Node inside = blockAt(node.body, column, cursor);
                if (inside != null) return inside;
                cursor[0]++;
            }
            if (cursor[0] > column) return null;
        }
        return null;
    }

    private static List<Script.Node> flat(List<Script.Node> ops, List<Script.Node> out) {
        for (Script.Node node : ops) {
            out.add(node);
            flat(node.body, out);
        }
        return out;
    }
}
