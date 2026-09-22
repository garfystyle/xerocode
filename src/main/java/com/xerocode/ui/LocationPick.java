package com.xerocode.ui;

import com.xerocode.XeroCode;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xerocode.Script;
import com.xerocode.Value;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class LocationPick {
    public enum Snap {
        FREE("нет", 0), Q("0.25", 0.25), HALF("0.5", 0.5), ONE("1", 1),
        CENTER("центр", 0), TOP("верх", 0);
        public final String label;
        public final double step;
        Snap(String label, double step) { this.label = label; this.step = step; }
    }

    private enum Op { NONE, MOVE, LOOK }

    private static final int REACH = 96;
    private static final double SPEED = 0.32, FAST = 1.05;
    private static final double HANDLE_PX = 90;
    private static final double GRAB_R = 0.35;
    private static final double FRAME_DIST = 5.5;
    private static final double GHOST_D = 0.4;
    private static final double SKIN = 0.002;

    private static final int PAD = 8, MIN_W = 240, PANEL_W = 420;

    private static final int AX_X = 0xF0605E, AX_Y = 0x8FD94F, AX_Z = 0x5B8CF5;
    private static final int ROT = 0xFFD54A, POINT = 0xFFFFFF, BOX = 0xFF59A6FF, GHOST = 0x8FBFD4EA;

    private static Script.Node node;
    private static int argIndex, index;
    private static boolean active;
    private static Snap snap = Snap.TOP;

    private static double px, py, pz;
    private static float pyaw, ppitch;

    private static Entity cam;
    private static double cx, cy, cz;
    private static float camYaw, camPitch;
    private static boolean fromSlot;
    private static boolean snapCam;

    private static Vec3 frozen = Vec3.ZERO;
    private static float lastYaw, lastPitch;
    private static boolean hudWas;

    private static Op op = Op.NONE;
    private static int axis = -1;
    private static boolean planeMode;
    private static boolean byHandle;
    private static double sx, sy, sz;
    private static float syaw, spitch;
    private static double bx, by, bz;
    private static float byaw, bpitch;
    private static double mdx, mdy;
    private static String typed = "";
    private static int typeAxis;
    private static final double[] typedVal = new double[3];
    private static final boolean[] typedSet = new boolean[3];

    private static int hover = -1;

    private static double curX, curY;
    private static boolean curKnown;

    private static final Deque<double[]> undo = new ArrayDeque<>();
    private static final Deque<double[]> redo = new ArrayDeque<>();

    private static final int[] WATCH = {
            GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_N,
            GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_Z,
            GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
            GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL,
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT,
            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
            GLFW.GLFW_KEY_KP_0, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3,
            GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6, GLFW.GLFW_KEY_KP_7,
            GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9,
    };
    private static final int MOUSE_L = WATCH.length, MOUSE_R = WATCH.length + 1;
    private static final boolean[] keyNow = new boolean[WATCH.length + 2];
    private static final boolean[] keyWas = new boolean[WATCH.length + 2];
    private static boolean freshKeys;

    public static boolean active() { return active; }

    public static net.minecraft.world.InteractionResult interceptHands(net.minecraft.world.level.Level world) {
        if (!active || !world.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
        return net.minecraft.world.InteractionResult.FAIL;
    }

    public static void start(Script.Node target, int arg, int valueIndex) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        node = target;
        argIndex = arg;
        index = valueIndex;
        active = true;
        op = Op.NONE;
        axis = -1;
        planeMode = false;
        byHandle = false;
        hover = -1;
        typed = "";
        curKnown = false;
        undo.clear();
        redo.clear();
        java.util.Arrays.fill(keyNow, false);
        java.util.Arrays.fill(keyWas, false);
        freshKeys = true;

        Value have = slotValue();
        boolean has = have != null && Value.LOCATION.equals(have.type)
                && !(have.x == 0 && have.y == 0 && have.z == 0 && have.yaw == 0 && have.pitch == 0);
        if (has) {
            px = have.x; py = have.y; pz = have.z;
            pyaw = (float) have.yaw; ppitch = (float) have.pitch;
        }
        fromSlot = has;

        XeroCode.canvasClosed();
        XeroCode.cover("Выбор местоположения…", null);
        hudWas = client.gui.hud.isHidden();
        if (!hudWas) client.gui.hud.toggle();
        client.gameRenderer.setRenderBlockOutline(false);
        attach(client);

        if (!client.isLocalServer() && client.getConnection() != null)
            client.getConnection().sendCommand("build");
    }

    private static Value slotValue() {
        if (node == null) return null;
        List<Value> slot = node.valuesOf(argIndex);
        return index >= 0 && index < slot.size() ? slot.get(index) : null;
    }

    private static void attach(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        frozen = player.position();
        Vec3 eye = player.getEyePosition();
        cx = eye.x;
        cy = eye.y;
        cz = eye.z;
        camYaw = player.getYRot();
        camPitch = player.getXRot();
        lastYaw = camYaw;
        lastPitch = camPitch;
        if (fromSlot) frame();
        else placeUnderAim(client, false);
        try {
            if (cam != null) client.setCameraEntity(player);
            cam = new Marker(EntityTypes.MARKER, client.level);
            place(true);
            client.setCameraEntity(cam);
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] фрикам не поднялся, выбор точки идёт от игрока", e);
            cam = null;
        }
        snapCam = true;
    }

    private static void place(boolean snapTo) {
        if (cam == null) return;
        if (snapTo) {
            cam.xo = cx;
            cam.yo = cy;
            cam.zo = cz;
        } else {
            cam.xo = cam.getX();
            cam.yo = cam.getY();
            cam.zo = cam.getZ();
        }
        cam.xOld = cam.xo;
        cam.yOld = cam.yo;
        cam.zOld = cam.zo;
        cam.setPos(cx, cy, cz);
        cam.setYRot(camYaw);
        cam.setXRot(camPitch);
        cam.yRotO = camYaw;
        cam.xRotO = camPitch;
    }

    private static Vec3 eye(Minecraft client) {
        if (cam != null) return new Vec3(cx, cy, cz);
        return client.player == null ? Vec3.ZERO : client.player.getEyePosition();
    }

    private static Vec3 look(float yaw, float pitch) {
        float p = pitch * 0.017453292F, y = -yaw * 0.017453292F;
        float cosY = Mth.cos(y), sinY = Mth.sin(y);
        float cosP = Mth.cos(p), sinP = Mth.sin(p);
        return new Vec3(sinY * cosP, -sinP, cosY * cosP);
    }

    private static Vec3 side(Vec3 dir) {
        Vec3 s = new Vec3(-dir.z, 0, dir.x);
        return s.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : s.normalize();
    }

    private static Vec3 forward() { return look(camYaw, camPitch); }
    private static Vec3 right()   { return side(forward()); }
    private static Vec3 up()      { return right().cross(forward()).normalize(); }

    private static Vec3 point() { return new Vec3(px, py, pz); }

    private static Vec3 axisVec(int i) {
        return i == 0 ? new Vec3(1, 0, 0) : i == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
    }

    private static int axisColor(int i) { return i == 0 ? AX_X : i == 1 ? AX_Y : AX_Z; }

    private static BlockHitResult aim(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        Vec3 from = eye(client);
        Vec3 to = from.add(forward().scale(REACH));
        BlockHitResult hit = client.level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static double ppu(Minecraft client, double depth) {
        double fov = client.options.fov().get();
        double h = Math.max(1, client.getWindow().getScreenHeight());
        return (h / 2.0) / Math.tan(Math.toRadians(Math.max(1, fov) / 2.0)) / Math.max(0.05, depth);
    }

    private static double depth() {
        return point().subtract(eye(Minecraft.getInstance())).dot(forward());
    }

    private static double handleLen(Minecraft client) {
        double d = Math.max(0.5, depth());
        return Mth.clamp(HANDLE_PX / ppu(client, d), 0.25, 24);
    }

    private static double snapTo(double v, double step) {
        return step <= 0 ? v : Math.round(v / step) * step;
    }

    private static Vec3 snapPoint(Vec3 p) {
        return switch (snap) {
            case FREE -> new Vec3(snapTo(p.x, 1), snapTo(p.y, 1), snapTo(p.z, 1));
            case Q, HALF, ONE -> new Vec3(snapTo(p.x, snap.step), snapTo(p.y, snap.step),
                    snapTo(p.z, snap.step));
            case CENTER -> new Vec3(Math.floor(p.x) + 0.5, Math.floor(p.y) + 0.5,
                    Math.floor(p.z) + 0.5);
            case TOP -> new Vec3(Math.floor(p.x) + 0.5, Math.round(p.y), Math.floor(p.z) + 0.5);
        };
    }

    private static Vec3 preview(Minecraft client) {
        BlockHitResult hit = aim(client);
        if (hit == null) return null;
        BlockPos at = hit.getBlockPos();
        Vec3 exact = hit.getLocation();
        return switch (snap) {
            case FREE -> exact;
            case Q, HALF, ONE -> new Vec3(snapTo(exact.x, snap.step), snapTo(exact.y, snap.step),
                    snapTo(exact.z, snap.step));
            case CENTER -> new Vec3(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
            case TOP -> new Vec3(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        };
    }

    private static double[] snapshot() { return new double[]{px, py, pz, pyaw, ppitch}; }

    private static void restore(double[] s) {
        px = s[0]; py = s[1]; pz = s[2]; pyaw = (float) s[3]; ppitch = (float) s[4];
    }

    private static void push() {
        undo.push(snapshot());
        if (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private static void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    private static void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!active) return;
        try {
            renderFrame(ctx);
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] выбор местоположения упал, выходим из режима", e);
            try { abandon(); } catch (Throwable ignored) { }
        }
    }

    private static void renderFrame(GuiGraphicsExtractor ctx) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        readKeys(client);
        double[] d = cursorDelta(client);
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (client.gui.screen() != null) { d[0] = 0; d[1] = 0; }

        if (op == Op.MOVE) {
            mdx += d[0];
            mdy += d[1];
            applyOp(client);
        } else {
            if (op == Op.LOOK) {
                typedLook(player);
                yaw = player.getYRot();
                pitch = player.getXRot();
            }
            camYaw = yaw;
            camPitch = pitch;
            if (cam != null) {
                cam.setYRot(yaw);
                cam.setXRot(pitch);
                cam.yRotO = yaw;
                cam.xRotO = pitch;
            }
            if (op == Op.LOOK) {
                pyaw = Mth.wrapDegrees(yaw);
                ppitch = Mth.clamp(pitch, -90f, 90f);
            } else {
                hover = pickHandle(client);
            }
        }
        lastYaw = yaw;
        lastPitch = pitch;

        keyActions(client);

        if (client.gui.screen() instanceof LocationForm) return;
        SmoothText.clip(null);
        Draw.batch(null);
        hud(ctx, client);
    }

    public static void startTick(Minecraft client) {
        if (!active) return;
        Options o = client.options;
        drain(o.keyChat);
        drain(o.keyCommand);
        drain(o.keyInventory);
        drain(o.keyDrop);
        drain(o.keySwapOffhand);
        drain(o.keySocialInteractions);
        drain(o.keyAdvancements);
        drain(o.keyTogglePerspective);
        drain(o.keyToggleGui);
        drain(o.keyQuickActions);
        drain(o.keyPickItem);
        drain(o.keyAttack);
    }

    private static void drain(KeyMapping key) {
        if (key == null) return;
 while (key.consumeClick()) { }
    }

    private static double[] cursorDelta(Minecraft client) {
        if (client.getWindow() == null) return new double[]{0, 0};
        double[] xs = new double[1], ys = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().handle(), xs, ys);
        double dx = curKnown ? xs[0] - curX : 0;
        double dy = curKnown ? ys[0] - curY : 0;
        curX = xs[0];
        curY = ys[0];
        curKnown = true;
        return new double[]{dx, dy};
    }

    private static void begin(Op what, boolean handle) {
        op = what;
        byHandle = handle;
        planeMode = false;
        mdx = mdy = 0;
        typed = "";
        typeAxis = 0;
        typedSet[0] = typedSet[1] = typedSet[2] = false;
        sx = px; sy = py; sz = pz;
        syaw = pyaw; spitch = ppitch;
        push();
        if (what != Op.LOOK) return;
        hover = -1;
        bx = cx; by = cy; bz = cz;
        byaw = camYaw; bpitch = camPitch;
        cx = px; cy = py; cz = pz;
        camYaw = pyaw;
        camPitch = ppitch;
        aimPlayer(camYaw, camPitch);
        snapCam = true;
        place(true);
    }

    private static void aimPlayer(float yaw, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(yaw);
        player.setXRot(pitch);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private static void applyOp(Minecraft client) {
        boolean ctrl = down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (op == Op.MOVE) {
            Vec3 start = new Vec3(sx, sy, sz);
            Vec3 moved;
            if (axis >= 0 && !planeMode) {
                moved = start.add(axisVec(axis).scale(along(client, axisVec(axis), start)));
            } else if (axis >= 0) {
                moved = start;
                for (int i = 0; i < 3; i++)
                    if (i != axis) moved = moved.add(axisVec(i).scale(along(client, axisVec(i), start)));
            } else {
                double k = ppu(client, Math.max(0.5, start.subtract(eye(client)).dot(forward())));
                moved = start.add(right().scale(mdx / k)).add(up().scale(-mdy / k));
            }
            if (ctrl) moved = snapPoint(moved);
            px = moved.x; py = moved.y; pz = moved.z;
            for (int i = 0; i < 3; i++) {
                if (!typedSet[i]) continue;
                if (i == 0) px = typedVal[0];
                if (i == 1) py = typedVal[1];
                if (i == 2) pz = typedVal[2];
            }
        }
    }

    private static double along(Minecraft client, Vec3 ax, Vec3 start) {
        Vec3 rel = start.subtract(eye(client));
        double depth = Math.max(0.5, rel.dot(forward()));
        double k = ppu(client, depth);
        double sxp = ax.dot(right()) * k;
        double syp = -ax.dot(up()) * k;
        double len2 = sxp * sxp + syp * syp;
        if (len2 < 1.0E-4) return 0;
        return (mdx * sxp + mdy * syp) / len2;
    }

    private static void commitOp() {
        endOp();
    }

    private static void cancelOp() {
        if (op == Op.NONE) return;
        px = sx; py = sy; pz = sz;
        pyaw = syaw; ppitch = spitch;
        if (!undo.isEmpty()) undo.pop();
        endOp();
    }

    private static void endOp() {
        if (op == Op.LOOK) {
            cx = bx; cy = by; cz = bz;
            camYaw = byaw;
            camPitch = bpitch;
            snapCam = true;
            place(true);
        }
        aimPlayer(camYaw, camPitch);
        op = Op.NONE;
        axis = -1;
        planeMode = false;
        byHandle = false;
        typed = "";
    }

    private static double[] formBefore;

    private static void openForm(Minecraft client) {
        formBefore = snapshot();
        push();
        client.gui.setScreen(new LocationForm());
    }

    public static double[] values() { return snapshot(); }

    public static void setValues(double x, double y, double z, double yaw, double pitch) {
        px = x;
        py = y;
        pz = z;
        pyaw = Mth.wrapDegrees((float) yaw);
        ppitch = Mth.clamp((float) pitch, -90f, 90f);
    }

    public static void undoForm() {
        if (formBefore != null) restore(formBefore);
        if (!undo.isEmpty()) undo.pop();
        formBefore = null;
    }

    public static void doneFromForm() {
        write();
        finish(Minecraft.getInstance(), true);
    }

    public static void formClosed() {
        curKnown = false;
        freshKeys = true;
        formBefore = null;
    }

    private static int pickHandle(Minecraft client) {
        if (depth() <= 0.2) return -1;
        double len = handleLen(client);
        Vec3 o = eye(client), dir = forward(), p = point();
        int best = -1;
        double bestDist = GRAB_R * len;
        for (int i = 0; i < 3; i++) {
            Vec3 a = p, b = p.add(axisVec(i).scale(len));
            double dist = rayToSegment(o, dir, a, b);
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    private static double rayToSegment(Vec3 o, Vec3 dir, Vec3 a, Vec3 b) {
        Vec3 u = dir, v = b.subtract(a), w = o.subtract(a);
        double aa = u.dot(u), bb = u.dot(v), cc = v.dot(v);
        double dd = u.dot(w), ee = v.dot(w);
        double den = aa * cc - bb * bb;
        double s, t;
        if (Math.abs(den) < 1.0E-8) { s = 0; t = cc < 1.0E-8 ? 0 : ee / cc; }
        else {
            s = (bb * ee - cc * dd) / den;
            t = (aa * ee - bb * dd) / den;
        }
        s = Math.max(0, s);
        t = Mth.clamp(t, 0, 1);
        Vec3 pa = o.add(u.scale(s)), pb = a.add(v.scale(t));
        return pa.distanceTo(pb);
    }

    private static void readKeys(Minecraft client) {
        System.arraycopy(keyNow, 0, keyWas, 0, keyNow.length);
        var window = client.getWindow();
        if (window == null) {
            java.util.Arrays.fill(keyNow, false);
            return;
        }
        for (int i = 0; i < WATCH.length; i++) keyNow[i] = InputConstants.isKeyDown(window, WATCH[i]);
        long handle = window.handle();
        keyNow[MOUSE_L] =
                GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        keyNow[MOUSE_R] =
                GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (freshKeys) {
            System.arraycopy(keyNow, 0, keyWas, 0, keyNow.length);
            freshKeys = false;
        }
    }

    private static int slotOf(int code) {
        for (int i = 0; i < WATCH.length; i++) if (WATCH[i] == code) return i;
        return -1;
    }

    private static boolean down(int code) {
        int i = slotOf(code);
        return i >= 0 && keyNow[i];
    }

    private static boolean hit(int code) {
        int i = slotOf(code);
        return i >= 0 && keyNow[i] && !keyWas[i];
    }

    private static boolean pressed(int slot)  { return keyNow[slot] && !keyWas[slot]; }
    private static boolean released(int slot) { return !keyNow[slot] && keyWas[slot]; }

    private static void keyActions(Minecraft client) {
        if (client.gui.screen() != null) return;
        boolean ctrl = down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift = down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean alt = down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT);
        boolean enter = hit(GLFW.GLFW_KEY_ENTER) || hit(GLFW.GLFW_KEY_KP_ENTER);

        if (op != Op.NONE) {
            typeKeys();
            if (op == Op.MOVE) axisKeys(ctrl, shift);
            if (byHandle) {
                if (enter || released(MOUSE_R) || pressed(MOUSE_L)) commitOp();
                return;
            }
            if (enter || pressed(MOUSE_L)) { commitOp(); return; }
            if (pressed(MOUSE_R)) { cancelOp(); return; }
            return;
        }

        if (ctrl && hit(GLFW.GLFW_KEY_Z)) { undo(); return; }
        if (ctrl && hit(GLFW.GLFW_KEY_Y)) { redo(); return; }
        if (alt && hit(GLFW.GLFW_KEY_R)) { push(); pyaw = 0; ppitch = 0; return; }
        if (!ctrl && hit(GLFW.GLFW_KEY_G)) { begin(Op.MOVE, false); return; }
        if (!ctrl && !alt && hit(GLFW.GLFW_KEY_R)) { begin(Op.LOOK, false); return; }
        if (!ctrl && hit(GLFW.GLFW_KEY_F)) { frame(); return; }
        if (!ctrl && hit(GLFW.GLFW_KEY_N)) { openForm(client); return; }
        if (hit(GLFW.GLFW_KEY_TAB)) {
            snap = Snap.values()[(snap.ordinal() + 1) % Snap.values().length];
            return;
        }
        if (pressed(MOUSE_R)) {
            if (hover >= 0) { axis = hover; begin(Op.MOVE, true); }
            else placeUnderAim(client, true);
            return;
        }
        if (enter || pressed(MOUSE_L)) { write(); finish(client, true); }
    }

    private static void axisKeys(boolean ctrl, boolean shift) {
        if (ctrl) return;
        for (int key = 0; key < 3; key++) {
            int code = key == 0 ? GLFW.GLFW_KEY_X : key == 1 ? GLFW.GLFW_KEY_Y : GLFW.GLFW_KEY_Z;
            if (!hit(code)) continue;
            boolean same = axis == key && planeMode == shift;
            axis = same ? -1 : key;
            planeMode = !same && shift;
            typed = "";
        }
    }

    private static void typedLook(LocalPlayer player) {
        if (typed.isEmpty()) return;
        double v;
        try { v = Double.parseDouble(typed); } catch (NumberFormatException e) { return; }
        if (typeAxis == 0) player.setYRot(Mth.wrapDegrees((float) v));
        else player.setXRot(Mth.clamp((float) v, -90f, 90f));
    }

    private static void typeKeys() {
        int cap = op == Op.LOOK ? 2 : 3;
        if (hit(GLFW.GLFW_KEY_TAB)) {
            commitTyped();
            typeAxis = (typeAxis + 1) % cap;
            typed = "";
            return;
        }
        for (int i = 0; i <= 9; i++) {
            if (hit(GLFW.GLFW_KEY_0 + i) || hit(GLFW.GLFW_KEY_KP_0 + i))
                typed += (char) ('0' + i);
        }
        if (hit(GLFW.GLFW_KEY_PERIOD) || hit(GLFW.GLFW_KEY_KP_DECIMAL)) {
            if (typed.indexOf('.') < 0) typed += typed.isEmpty() ? "0." : ".";
        }
        if (hit(GLFW.GLFW_KEY_MINUS) || hit(GLFW.GLFW_KEY_KP_SUBTRACT))
            typed = typed.startsWith("-") ? typed.substring(1) : "-" + typed;
        if (hit(GLFW.GLFW_KEY_BACKSPACE)) {
            if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
            else typedSet[typeAxis] = false;
        }
        if (!typed.isEmpty()) {
            if (op == Op.MOVE && axis != typeAxis) { axis = typeAxis; planeMode = false; }
            commitTyped();
        }
    }

    private static void commitTyped() {
        if (typed.isEmpty() || typed.equals("-") || typed.equals("0.")) return;
        try {
            typedVal[typeAxis] = Double.parseDouble(typed);
            typedSet[typeAxis] = true;
        } catch (NumberFormatException ignored) { }
    }

    private static void placeUnderAim(Minecraft client, boolean undoable) {
        Vec3 at = preview(client);
        if (at == null) {
            if (!undoable) at = eye(client).add(forward().scale(4));
            else return;
        }
        if (undoable) push();
        px = at.x;
        py = at.y;
        pz = at.z;
        if (!undoable) { pyaw = Mth.wrapDegrees(camYaw); ppitch = camPitch; }
    }

    private static void frame() {
        Vec3 back = forward().scale(FRAME_DIST);
        cx = px - back.x;
        cy = py - back.y;
        cz = pz - back.z;
        snapCam = true;
        place(true);
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.level == null && client.getConnection() == null) { abandon(); return; }
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        if (cam == null || cam.level() != client.level) { attach(client); return; }

        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            client.gui.setScreen(null);
            if (op != Op.NONE) { cancelOp(); return; }
            finish(client, false);
            return;
        }
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)
            client.gui.setScreen(null);

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(frozen.x, frozen.y, frozen.z);
        player.xo = player.xOld = frozen.x;
        player.yo = player.yOld = frozen.y;
        player.zo = player.zOld = frozen.z;
        player.fallDistance = 0;
        if (client.getCameraEntity() != cam) {
            client.setCameraEntity(cam);
        }

        if (client.gameMode != null) client.gameMode.stopDestroyBlock();

        boolean typing = client.gui.screen() != null;
        Options o = client.options;
        boolean fast = !typing && o.keySprint.isDown();
        if (!typing) {
            if (op == Op.NONE) fly(client, fast);
            else if (op == Op.MOVE && axis < 0) depthKeys(client, fast);
        }
        if (op == Op.LOOK) { cx = px; cy = py; cz = pz; }
        place(snapCam);
        snapCam = false;
    }

    private static void fly(Minecraft client, boolean fast) {
        Vec3 move = walk(client);
        if (move == null) return;
        move = move.normalize().scale(fast ? FAST : SPEED);
        cx += move.x;
        cy += move.y;
        cz += move.z;
    }

    private static void depthKeys(Minecraft client, boolean fast) {
        Options o = client.options;
        double step = (fast ? 0.5 : 0.12);
        double d = 0;
        if (o.keyUp.isDown()) d += step;
        if (o.keyDown.isDown()) d -= step;
        if (d == 0) return;
        Vec3 f = forward().scale(d);
        sx += f.x; sy += f.y; sz += f.z;
        px += f.x; py += f.y; pz += f.z;
    }

    private static Vec3 walk(Minecraft client) {
        Options o = client.options;
        if (client.gui.screen() != null) return null;
        Vec3 dir = forward();
        Vec3 s = side(dir);
        Vec3 move = Vec3.ZERO;
        if (o.keyUp.isDown()) move = move.add(dir);
        if (o.keyDown.isDown()) move = move.subtract(dir);
        if (o.keyRight.isDown()) move = move.add(s);
        if (o.keyLeft.isDown()) move = move.subtract(s);
        if (o.keyJump.isDown()) move = move.add(0, 1, 0);
        if (o.keyShift.isDown()) move = move.add(0, -1, 0);
        return move.lengthSqr() < 1.0E-6 ? null : move;
    }

    private static double r3(double d) { return Math.round(d * 1000.0) / 1000.0; }
    private static double r1(double d) { return Math.round(d * 10.0) / 10.0; }

    private static void write() {
        if (node == null) return;
        List<Value> slot = node.valuesOf(argIndex);
        Value v = index >= 0 && index < slot.size() ? slot.get(index) : null;
        if (v == null || !Value.LOCATION.equals(v.type)) {
            v = Value.of(Value.LOCATION);
            if (index >= 0 && index < slot.size()) slot.set(index, v);
            else { slot.add(v); index = slot.size() - 1; }
        }
        v.x = r3(px);
        v.y = r3(py);
        v.z = r3(pz);
        v.yaw = r1(Mth.wrapDegrees(pyaw));
        v.pitch = r1(Mth.clamp(ppitch, -90f, 90f));
    }

    private static void finish(Minecraft client, boolean applied) {
        Script.Node target = node;
        int arg = argIndex, i = index;
        abandon();
        if (applied && target != null) EditorScreen.openPanelAfter(target, arg, i);
        XeroCode.openCanvas(client);
    }

    public static void cancel() {
        if (!active) return;
        if (op != Op.NONE) { cancelOp(); return; }
        finish(Minecraft.getInstance(), false);
    }

    public static void abandon() {
        Minecraft client = Minecraft.getInstance();
        if (cam != null) {
            try { client.setCameraEntity(client.player); } catch (Throwable ignored) { }
            cam = null;
        }
        if (client.player != null) {
            client.player.setYRot(camYaw);
            client.player.setXRot(camPitch);
        }
        if (client.gui.hud.isHidden() != hudWas) client.gui.hud.toggle();
        client.gameRenderer.setRenderBlockOutline(true);
        active = false;
        op = Op.NONE;
        node = null;
    }

    private static Vec3 origin = Vec3.ZERO;
    private static double ghostScale = 1, baseScale = 1;
    private record Seg(float ax, float ay, float az, float bx, float by, float bz, Vector3f dir, int argb, float width) {}

    private static final java.util.ArrayList<Seg> SEGMENTS = new java.util.ArrayList<>();
    private static float hair = 1, thick = 2;

    public static void renderWorld(LevelRenderContext ctx) {
        if (!active) return;
        try {
            worldFrame(ctx);
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] линии выбора местоположения упали, выходим из режима", e);
            try { abandon(); } catch (Throwable ignored) { }
        }
    }

    private static void worldFrame(LevelRenderContext ctx) {
        Minecraft client = Minecraft.getInstance();
        PoseStack matrices = ctx.poseStack();
        if (matrices == null || client.level == null || client.player == null) {
            return;
        }
        origin = client.gameRenderer.mainCamera().position();
        SEGMENTS.clear();
        try {
            collect(client);
        } finally {
            ghostScale = 1;
        }
        if (SEGMENTS.isEmpty()) return;
        Seg[] lines = SEGMENTS.toArray(new Seg[0]);
        SEGMENTS.clear();
        ctx.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.LINES, (pose, vc) -> {
            for (Seg l : lines) {
                vc.addVertex(pose, l.ax, l.ay, l.az).setColor(l.argb).setNormal(pose, l.dir).setLineWidth(l.width);
                vc.addVertex(pose, l.bx, l.by, l.bz).setColor(l.argb).setNormal(pose, l.dir).setLineWidth(l.width);
            }
        });
    }

    private static void collect(Minecraft client) {
        thick = Math.max(2f, client.getWindow().getAppropriateLineWidth() * 2f);
        hair = Math.max(1f, client.getWindow().getAppropriateLineWidth());

        if (op == Op.LOOK) {
            ghostScale = 1;
            cell(point(), Draw.argb(0x60, 0x9BB4CC));
            return;
        }

        if (op == Op.NONE && hover < 0) {
            BlockHitResult hit = aim(client);
            if (hit != null) {
                BlockPos at = hit.getBlockPos();
                if (!at.equals(BlockPos.containing(px, py, pz))) {
                    VoxelShape shape = client.level.getBlockState(at).getShape(client.level, at);
                    if (shape.isEmpty()) shape = Shapes.block();
                    xray(Vec3.atCenterOf(at));
                    outline(shape.bounds().move(at).inflate(SKIN), BOX, thick);
                }
                Vec3 ghostAt = preview(client);
                if (ghostAt != null && ghostAt.distanceToSqr(point()) > 0.01) {
                    xray(ghostAt);
                    marker(ghostAt, GHOST, handleLen(client) * 0.16);
                }
            }
        }

        double d = point().subtract(origin).length();
        baseScale = d > GHOST_D ? GHOST_D / d : 1;
        gizmo(client);
        ghostScale = 1;
    }

    private static void layer(double k) { ghostScale = baseScale * k; }

    private static void xray(Vec3 p) {
        double d = p.subtract(origin).length();
        ghostScale = d > GHOST_D ? GHOST_D / d : 1;
    }

    private static void outline(net.minecraft.world.phys.AABB b, int argb, float width) {
        double[][] xz = {{b.minX, b.minZ}, {b.maxX, b.minZ}, {b.maxX, b.maxZ}, {b.minX, b.maxZ}};
        for (int i = 0; i < 4; i++) {
            double[] a = xz[i], c = xz[(i + 1) % 4];
            line(new Vec3(a[0], b.minY, a[1]), new Vec3(c[0], b.minY, c[1]), argb, width);
            line(new Vec3(a[0], b.maxY, a[1]), new Vec3(c[0], b.maxY, c[1]), argb, width);
            line(new Vec3(a[0], b.minY, a[1]), new Vec3(a[0], b.maxY, a[1]), argb, width);
        }
    }

    private static void gizmo(Minecraft client) {
        double len = handleLen(client);
        Vec3 p = point();

        layer(1.06);
        cell(p, Draw.argb(0x60, 0x9BB4CC));
        drop(client, p, Draw.argb(0x50, 0x9BB4CC));
        if (op != Op.NONE && axis >= 0 && !planeMode) guide(p, axisVec(axis), len, axis);
        if (op != Op.NONE) {
            Vec3 s = new Vec3(sx, sy, sz);
            if (s.distanceToSqr(p) > 1.0E-4) {
                marker(s, Draw.argb(0x70, POINT), len * 0.10);
                dashed(s, p, Draw.argb(0x80, 0xFFFFFF), 0.25);
            }
        }

        layer(1.0);
        for (int i = 0; i < 3; i++) {
            boolean lit = op != Op.NONE ? axis == i && !planeMode : hover == i;
            boolean muted = op != Op.NONE && axis >= 0 && (planeMode ? axis == i : axis != i);
            int c = axisColor(i);
            if (lit) c = Draw.mix(c, 0xFFFFFF, 0.45f);
            int a = muted ? 0x50 : lit ? 0xFF : 0xC8;
            arrow(p, axisVec(i), len, Draw.argb(a, c), lit ? thick : hair);
        }

        layer(0.94);
        marker(p, Draw.argb(0xFF, POINT), len * 0.16);
        rotArrow(p, len * 1.5, Draw.argb(0xD0, ROT));
    }

    private static void guide(Vec3 p, Vec3 ax, double len, int i) {
        int argb = Draw.argb(0x55, axisColor(i));
        double dash = Math.max(0.25, len * 0.5);
        dashed(p.subtract(ax.scale(48)), p, argb, dash);
        dashed(p.add(ax.scale(len)), p.add(ax.scale(48)), argb, dash);
    }

    private static void marker(Vec3 o, int argb, double r) {
        Vec3[] ring = {o.add(r, 0, 0), o.add(0, 0, r), o.add(-r, 0, 0), o.add(0, 0, -r)};
        Vec3 top = o.add(0, r, 0), bottom = o.add(0, -r, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 4];
            line(a, b, argb, hair);
            line(a, top, argb, hair);
            line(a, bottom, argb, hair);
        }
    }

    private static void arrow(Vec3 from, Vec3 dir, double len, int argb, float width) {
        Vec3 tip = from.add(dir.scale(len));
        line(from, tip, argb, width);
        Vec3 any = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a1 = dir.cross(any).normalize();
        Vec3 a2 = dir.cross(a1).normalize();
        Vec3 back = tip.subtract(dir.scale(len * 0.22));
        double s = len * 0.09;
        line(tip, back.add(a1.scale(s)), argb, width);
        line(tip, back.subtract(a1.scale(s)), argb, width);
        line(tip, back.add(a2.scale(s)), argb, width);
        line(tip, back.subtract(a2.scale(s)), argb, width);
    }

    private static void rotArrow(Vec3 o, double len, int argb) {
        Vec3 dir = look(pyaw, ppitch);
        arrow(o, dir, len, argb, hair);
    }

    private static void cell(Vec3 p, int argb) {
        double bx = Math.floor(p.x) + SKIN, by = Math.floor(p.y) + SKIN, bz = Math.floor(p.z) + SKIN;
        double s = 1 - SKIN * 2;
        double[][] corners = {{0, 0}, {s, 0}, {s, s}, {0, s}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i], b = corners[(i + 1) % 4];
            for (int lvl = 0; lvl <= 1; lvl++)
                line(new Vec3(bx + a[0], by + lvl * s, bz + a[1]),
                        new Vec3(bx + b[0], by + lvl * s, bz + b[1]), argb, hair);
            line(new Vec3(bx + a[0], by, bz + a[1]),
                    new Vec3(bx + a[0], by + s, bz + a[1]), argb, hair);
        }
    }

    private static void drop(Minecraft client, Vec3 p, int argb) {
        if (client.level == null || client.player == null) return;
        Vec3 to = p.subtract(0, 40, 0);
        BlockHitResult hit = client.level.clip(new ClipContext(p, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        Vec3 end = hit != null && hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
        dashed(p, end, argb, 0.3);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            double r = 0.15;
            line(end.add(-r, 0.01, 0), end.add(r, 0.01, 0), argb, hair);
            line(end.add(0, 0.01, -r), end.add(0, 0.01, r), argb, hair);
        }
    }

    private static void dashed(Vec3 a, Vec3 b, int argb, double dash) {
        double len = a.distanceTo(b);
        if (len < 1.0E-4) return;
        Vec3 dir = b.subtract(a).scale(1 / len);
        for (double t = 0; t < len; t += dash * 2) {
            Vec3 s = a.add(dir.scale(t));
            Vec3 e = a.add(dir.scale(Math.min(len, t + dash)));
            line(s, e, argb, hair);
        }
    }

    private static void line(Vec3 a, Vec3 b, int argb, float width) {
        double ax = (a.x - origin.x) * ghostScale, ay = (a.y - origin.y) * ghostScale,
                az = (a.z - origin.z) * ghostScale;
        double bx = (b.x - origin.x) * ghostScale, by = (b.y - origin.y) * ghostScale,
                bz = (b.z - origin.z) * ghostScale;
        Vector3f dir = new Vector3f((float) (bx - ax), (float) (by - ay), (float) (bz - az));
        if (dir.lengthSquared() < 1.0E-10f) return;
        dir.normalize();
        SEGMENTS.add(new Seg((float) ax, (float) ay, (float) az, (float) bx, (float) by, (float) bz,
                dir, argb, width));
    }

    private static String f3(double d) { return String.format("%.3f", d); }
    private static String f1(double d) { return String.format("%.1f", d); }
    private static String sign(double d) { return (d >= 0 ? "+" : "") + f3(d); }

    private static void hud(GuiGraphicsExtractor ctx, Minecraft client) {
        var tr = client.font;
        int sw = ctx.guiWidth(), sh = ctx.guiHeight();
        crosshair(ctx, sw, sh);

        int w = Math.max(Math.min(MIN_W, sw - 8), Ui.fitW(sw, PANEL_W));
        int x = Ui.midX(sw, w), y = 8;
        int h = PAD + 11 + 3 + 16 + 5 + 15 + PAD - 2;
        Draw.card(ctx, x, y, w, h, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        Draw.rect(ctx, x + Ui.R, y + 1, w - 2 * Ui.R, 1, Ui.sheen());

        int cy = y + PAD;
        String title = switch (op) {
            case MOVE -> "ДВИГАТЬ" + (axis < 0 ? "" : (planeMode ? " · плоскость " : " · ось ")
                    + "XYZ".charAt(axis));
            case LOOK -> "ВЗГЛЯД ИЗ ТОЧКИ";
            default -> "МЕСТОПОЛОЖЕНИЕ";
        };
        String hint = op == Op.NONE ? "Enter — готово" : "Enter — применить";
        Draw.textFit(ctx, tr, title, x + PAD, cy, w - PAD * 2 - tr.width(hint) - 8,
                op == Op.NONE ? Theme.TEXT : Theme.ACCENT, false);
        Draw.textRight(ctx, tr, hint, x + w - PAD, cy, Theme.TEXT_FAINT, false);
        cy += 11 + 3;

        int inner = w - PAD * 2;
        int cw = (inner - 4 * 3) / 5;
        String[] caps = {"X", "Y", "Z", "yaw", "pitch"};
        int[] cols = {AX_X, AX_Y, AX_Z, ROT, ROT};
        String[] vals = {f3(px), f3(py), f3(pz), f1(Mth.wrapDegrees(pyaw)), f1(ppitch)};
        int fx = x + PAD;
        for (int i = 0; i < 5; i++) {
            boolean lit = op == Op.MOVE ? (axis == i && !planeMode) : op == Op.LOOK && i >= 3;
            int typeCol = op == Op.MOVE ? typeAxis : typeAxis + 3;
            boolean typing = op != Op.NONE && !typed.isEmpty() && i == typeCol;
            field(ctx, tr, fx, cy, cw, caps[i], typing ? typed + "_" : vals[i], cols[i], lit, typing);
            fx += cw + 3;
        }
        cy += 16 + 5;

        Draw.textFit(ctx, tr, "ПРИЛИПАНИЕ", x + PAD, cy + 4, 70, Theme.TEXT_FAINT, false);
        int sx2 = x + PAD + tr.width("ПРИЛИПАНИЕ") + 6;
        int limit = x + w - PAD - tr.width("Tab") - 6;
        for (Snap s : Snap.values()) {
            int pw = tr.width(s.label) + 12;
            if (sx2 + pw > limit) break;
            pill(ctx, tr, sx2, cy, pw, s.label, s == snap);
            sx2 += pw + 3;
        }
        Draw.textRight(ctx, tr, "Tab", x + w - PAD, cy + 4, Theme.TEXT_FAINT, false);

        keyStrip(ctx, tr, sw, sh);
        if (op != Op.NONE) opReadout(ctx, tr, sw, sh);
        else if (hover >= 0) hoverHint(ctx, tr, sw, sh);
    }

    private static void field(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                              int x, int y, int w, String cap, String value, int color,
                              boolean lit, boolean typing) {
        int border = lit || typing ? color : Ui.LINE_IN;
        Draw.card(ctx, x, y, w, 16, Ui.R_SM, Draw.opaque(lit ? Draw.mix(Ui.WELL, color, 0.14f)
                : Ui.WELL), Draw.opaque(border));
        Draw.roundRect(ctx, x + 1, y + 1, 3, 14, Ui.R_SM - 1, 0, 0, Ui.R_SM - 1,
                Draw.opaque(color));
        Draw.textFit(ctx, tr, cap, x + 6, y + 4, w - 12, color, false);
        String s = Draw.fit(tr, value, w - (9 + tr.width(cap)));
        Draw.textRight(ctx, tr, s, x + w - 4, y + 4, typing ? Theme.ACCENT : Theme.TEXT, false);
    }

    private static void pill(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                             int x, int y, int w, String label, boolean on) {
        int accent = Theme.ACCENT;
        Draw.pill(ctx, x, y, w, 15, Draw.opaque(on ? Draw.shade(accent, -0.30f) : Ui.LINE_IN));
        Draw.pillGrad(ctx, x + 1, y + 1, w - 2, 13,
                Draw.opaque(on ? accent : Ui.WELL),
                Draw.opaque(on ? Draw.shade(accent, -0.16f) : Ui.WELL));
        Draw.textFit(ctx, tr, label, x + 6, y + 4, w - 12,
                on ? Theme.ON_ACCENT : Theme.TEXT_DIM, false);
    }

    private static void keyStrip(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                 int sw, int sh) {
        String[][] items = op == Op.MOVE ? (byHandle ? new String[][]{
                {"мышь", "тянуть по оси"}, {"Ctrl", "прилипание"}, {"цифры", "точно"},
                {"отпустить ПКМ", "готово"}}
                : new String[][]{
                {"мышь", "двигать"}, {"X/Y/Z", "ось"}, {"Shift+ось", "плоскость"},
                {"Ctrl", "прилипание"}, {"W/S", "дальше/ближе"}, {"цифры", "точно"},
                {"Tab", "след. ось"}, {"ЛКМ", "применить"}, {"ПКМ", "отмена"}})
                : op == Op.LOOK ? new String[][]{
                {"мышь", "куда будет смотреть"}, {"цифры", "точный угол"}, {"Tab", "yaw ▸ pitch"},
                {"ЛКМ", "принять"}, {"ПКМ", "отмена"}}
                : new String[][]{
                {"ПКМ по стрелке", "тянуть ось"}, {"ПКМ мимо", "поставить точку"},
                {"G", "двигать"}, {"R", "куда смотрит"}, {"N", "ввести числа"},
                {"F", "к точке"}, {"Alt+R", "сбросить поворот"}, {"Ctrl+Z", "отменить"},
                {"ЛКМ", "готово"}, {"Esc", "выйти"}};

        int n = items.length;
        int total;
        while (true) {
            total = 0;
            for (int i = 0; i < n; i++)
                total += tr.width(items[i][0]) + 4 + tr.width(items[i][1]) + (i > 0 ? 10 : 0);
            if (total <= sw - 16 || n <= 1) break;
            n--;
        }
        int y = sh - 20;
        Draw.rect(ctx, 0, y - 5, sw, 25, Draw.argb(0x66, 0x05070B));
        int x = (sw - total) / 2;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                Draw.text(ctx, tr, "·", x + 3, y, Theme.TEXT_FAINT, false);
                x += 10;
            }
            Draw.text(ctx, tr, items[i][0], x, y, Theme.ACCENT, false);
            x += tr.width(items[i][0]) + 4;
            Draw.text(ctx, tr, items[i][1], x, y, Theme.TEXT_DIM, false);
            x += tr.width(items[i][1]);
        }
    }

    private static void opReadout(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                  int sw, int sh) {
        String s;
        if (op == Op.MOVE) {
            double dx = px - sx, dy = py - sy, dz = pz - sz;
            if (axis < 0 || planeMode) {
                s = sign(dx) + "  " + sign(dy) + "  " + sign(dz);
            } else {
                double d = axis == 0 ? dx : axis == 1 ? dy : dz;
                s = "XYZ".charAt(axis) + "  " + sign(d);
            }
        } else {
            s = "yaw " + f1(Mth.wrapDegrees(pyaw)) + "   pitch " + f1(ppitch);
        }
        centerLabel(ctx, tr, sw, sh, s, Theme.ACCENT);
    }

    private static void hoverHint(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                  int sw, int sh) {
        centerLabel(ctx, tr, sw, sh, "ПКМ — тянуть по " + "XYZ".charAt(hover), axisColor(hover));
    }

    private static void centerLabel(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                    int sw, int sh, String s, int color) {
        int w = tr.width(s) + 14;
        int x = (sw - w) / 2, y = sh / 2 + 12;
        Draw.card(ctx, x, y, w, 15, Ui.R_SM, Draw.argb(0xD8, Ui.WELL), Draw.opaque(color));
        Draw.textCenter(ctx, tr, s, x, y + 4, w, w - 8, color, false);
    }

    private static void crosshair(GuiGraphicsExtractor ctx, int sw, int sh) {
        int cx2 = sw / 2, cy2 = sh / 2;
        int argb = Draw.argb(0xB4, hover >= 0 ? axisColor(hover) : 0xFFFFFF);
        Draw.rect(ctx, cx2 - 5, cy2, 4, 1, argb);
        Draw.rect(ctx, cx2 + 2, cy2, 4, 1, argb);
        Draw.rect(ctx, cx2, cy2 - 5, 1, 4, argb);
        Draw.rect(ctx, cx2, cy2 + 2, 1, 4, argb);
    }

    private LocationPick() {}
}
