package com.xerocode.ui;

import com.xerocode.Script;
import com.xerocode.Value;
import com.xerocode.XeroCode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LocationPick {
    private static boolean active;
    private static Script.Node node;
    private static int argIndex, index;
    private static double px, py, pz, pyaw, ppitch;
    private static double[] before;

    private LocationPick() {}

    public static boolean active() { return active; }

    public static void start(Script.Node target, int arg, int valueIndex) {
        node = target;
        argIndex = arg;
        index = valueIndex;
        active = true;
        px = py = pz = pyaw = ppitch = 0;
        Value have = slotValue();
        if (have != null && Value.LOCATION.equals(have.type)) {
            px = have.x;
            py = have.y;
            pz = have.z;
            pyaw = have.yaw;
            ppitch = have.pitch;
        }
        before = values();
        XeroCode.canvasClosed();
        Minecraft.getInstance().gui.setScreen(new LocationForm());
    }

    private static Value slotValue() {
        if (node == null) return null;
        List<Value> slot = node.valuesOf(argIndex);
        return index >= 0 && index < slot.size() ? slot.get(index) : null;
    }

    public static void render(GuiGraphicsExtractor ctx) {}

    public static void startTick(Minecraft client) {}

    public static void tick(Minecraft client) {}

    public static double[] values() { return new double[]{px, py, pz, pyaw, ppitch}; }

    public static void setValues(double x, double y, double z, double yaw, double pitch) {
        px = x;
        py = y;
        pz = z;
        pyaw = wrap(yaw);
        ppitch = Math.max(-90, Math.min(90, pitch));
    }

    private static double wrap(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private static double r3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }

    public static void undoForm() {
        if (before != null) setValues(before[0], before[1], before[2], before[3], before[4]);
    }

    public static void doneFromForm() {
        write();
        finish(true);
    }

    public static void formClosed() {
        if (active) finish(false);
    }

    private static void write() {
        if (node == null) return;
        List<Value> slot = node.valuesOf(argIndex);
        Value v = index >= 0 && index < slot.size() ? slot.get(index) : null;
        if (v == null || !Value.LOCATION.equals(v.type)) {
            v = Value.of(Value.LOCATION);
            if (index >= 0 && index < slot.size()) slot.set(index, v);
            else {
                slot.add(v);
                index = slot.size() - 1;
            }
        }
        v.x = r3(px);
        v.y = r3(py);
        v.z = r3(pz);
        v.yaw = r1(wrap(pyaw));
        v.pitch = r1(Math.max(-90, Math.min(90, ppitch)));
    }

    private static void finish(boolean applied) {
        Script.Node target = node;
        int arg = argIndex, i = index;
        abandon();
        if (applied && target != null) EditorScreen.openPanelAfter(target, arg, i);
        XeroCode.openCanvas(Minecraft.getInstance());
    }

    public static void cancel() {
        if (active) finish(false);
    }

    public static void abandon() {
        active = false;
        node = null;
    }
}
