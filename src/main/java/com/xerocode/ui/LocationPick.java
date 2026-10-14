package com.xerocode.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xerocode.Script;
import com.xerocode.Value;
import com.xerocode.XeroCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class LocationPick {
    public enum Grid {
        BLOCK("блок", 1), HALF("0.5", 0.5), TENTH("0.1", 0.1), FREE("нет", 0.1);
        public final String label;
        public final double step;
        Grid(String label, double step) { this.label = label; this.step = step; }
    }

    public record Info(ItemStack icon, String text, int color) {}

    public static final int NONE = -1, AX = 0, AY = 1, AZ = 2, MOVE = 3, LOOK = 4;
    public static final int AX_X = 0xF0605E, AX_Y = 0x8FD94F, AX_Z = 0x5B8CF5, ROT = 0xFFD54A;
    public static final double EYE = 1.62;

    private static final int REACH = 160;
    private static final double SPEED = 0.32, FAST = 1.05, WALK = 0.2, WALK_FAST = 0.5;
    private static final double HANDLE_GUI = 44;
    private static final double FRAME_DIST = 6;
    private static final double GHOST_D = 0.4;
    private static final double SKIN = 0.002;
    private static final double HALF_W = 0.3, BODY_H = 1.8;
    private static final int POINT = 0xFFFFFF, BOX = 0xFF59A6FF, GHOST = 0x8FBFD4EA,
            BODY = 0x9BB4CC, BAD = 0xFF7373;

    private static Script.Node node;
    private static int argIndex, index;
    private static boolean active, placed, snapCam, eye, hudWas;
    private static Grid grid = Grid.BLOCK;

    private static double px, py, pz;
    private static float pyaw, ppitch;

    private static Entity cam;
    private static double cx, cy, cz;
    private static float camYaw, camPitch;
    private static double bx, by, bz;
    private static float byaw, bpitch;

    private static Vec3 frozen = Vec3.ZERO;
    private static float standYaw, standPitch;

    private static int hover = NONE, drag = NONE;
    private static double sx, sy, sz, grabT;
    private static double[] before;
    private static BlockPos hoverBlock;
    private static Vec3 hoverPoint;

    private static final Deque<double[]> undo = new ArrayDeque<>();
    private static final Deque<double[]> redo = new ArrayDeque<>();

    private LocationPick() {}

    public static boolean active() { return active; }

    public static InteractionResult interceptHands(Level world) {
        if (!active || !world.isClientSide()) return InteractionResult.PASS;
        return InteractionResult.FAIL;
    }

    public static void start(Script.Node target, int arg, int valueIndex) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        node = target;
        argIndex = arg;
        index = valueIndex;
        active = true;
        eye = false;
        drag = NONE;
        hoverNone();
        undo.clear();
        redo.clear();

        Value have = slotValue();
        placed = have != null && Value.LOCATION.equals(have.type)
                && !(have.x == 0 && have.y == 0 && have.z == 0 && have.yaw == 0 && have.pitch == 0);
        if (placed) {
            px = have.x;
            py = have.y;
            pz = have.z;
            pyaw = (float) have.yaw;
            ppitch = (float) have.pitch;
        }

        XeroCode.canvasClosed();
        XeroCode.cover("Выбор местоположения…", LocationPick::open);
        hudWas = client.gui.hud.isHidden();
        if (!hudWas) client.gui.hud.toggle();
        client.gameRenderer.setRenderBlockOutline(false);
        attach(client);

        if (!client.isLocalServer() && client.getConnection() != null)
            client.getConnection().sendCommand("build");
    }

    private static void open() {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.gui.screen() instanceof LocationScreen) return;
        placed = true;
        client.gui.setScreen(new LocationScreen());
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
        standYaw = Mth.wrapDegrees(player.getYRot());
        standPitch = Mth.clamp(player.getXRot(), -90f, 90f);
        Vec3 at = player.getEyePosition();
        cx = at.x;
        cy = at.y;
        cz = at.z;
        camYaw = player.getYRot();
        camPitch = player.getXRot();
        eye = false;
        drag = NONE;
        if (placed) frame();
        else firstPoint(client);
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

    private static void firstPoint(Minecraft client) {
        Vec3 from = new Vec3(cx, cy, cz), dir = look(camYaw, camPitch);
        BlockHitResult hit = clip(client, from, dir);
        Vec3 at = hit != null ? onBlock(client, hit, grid) : from.add(dir.scale(4));
        px = at.x;
        py = at.y;
        pz = at.z;
        pyaw = Math.round(Mth.wrapDegrees(camYaw));
        ppitch = 0;
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
        aimCam();
    }

    private static void aimCam() {
        if (cam == null) return;
        cam.setYRot(camYaw);
        cam.setXRot(camPitch);
        cam.yRotO = camYaw;
        cam.xRotO = camPitch;
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

    private static Vec3 point() { return new Vec3(px, py, pz); }

    private static Vec3 head() { return new Vec3(px, py + EYE, pz); }

    private static Vec3 axisVec(int i) {
        return i == 0 ? new Vec3(1, 0, 0) : i == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
    }

    public static int axisColor(int i) { return i == 0 ? AX_X : i == 1 ? AX_Y : AX_Z; }

    private static Camera camera() { return Minecraft.getInstance().gameRenderer.mainCamera(); }

    private static Vec3 vec(Vector3fc v) { return new Vec3(v.x(), v.y(), v.z()); }

    private static double tanHalf() {
        return Math.tan(Math.toRadians(Mth.clamp(camera().getFov(), 1f, 170f)) / 2.0);
    }

    private static double aspect() {
        var w = Minecraft.getInstance().getWindow();
        return w.getWidth() / (double) Math.max(1, w.getHeight());
    }

    private static Vec3 rayFrom() { return camera().position(); }

    private static Vec3 rayDir(double mx, double my, int gw, int gh) {
        Camera c = camera();
        double t = tanHalf();
        double nx = (2 * mx / Math.max(1, gw) - 1) * t * aspect();
        double ny = (1 - 2 * my / Math.max(1, gh)) * t;
        Vec3 f = vec(c.forwardVector()), u = vec(c.upVector()), l = vec(c.leftVector());
        return f.add(l.scale(-nx)).add(u.scale(ny)).normalize();
    }

    public static double[] project(Vec3 p, int gw, int gh) {
        Camera c = camera();
        Vec3 rel = p.subtract(c.position());
        Vec3 f = vec(c.forwardVector()), u = vec(c.upVector()), l = vec(c.leftVector());
        double z = rel.dot(f), x = -rel.dot(l), y = rel.dot(u);
        if (z < 0.05) {
            double n = Math.hypot(x, y);
            double dx = n < 1.0E-6 ? 0 : x / n, dy = n < 1.0E-6 ? -1 : -y / n;
            return new double[]{gw / 2.0 - dx * 1.0E5, gh / 2.0 + dy * 1.0E5, z};
        }
        double t = tanHalf();
        double sx = x / (z * t * aspect()), sy = y / (z * t);
        return new double[]{(sx + 1) / 2 * gw, (1 - sy) / 2 * gh, z};
    }

    public static double[] pointOnScreen(int gw, int gh) { return project(point(), gw, gh); }

    public static double[] labelOnScreen(int gw, int gh) {
        return project(point().add(0, BODY_H + 0.25, 0), gw, gh);
    }

    private static double depthOf(Vec3 p) {
        Camera c = camera();
        return p.subtract(c.position()).dot(vec(c.forwardVector()));
    }

    private static double handleLen(Vec3 p) {
        double d = Math.max(0.5, depthOf(p));
        double gh = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        return Mth.clamp(HANDLE_GUI * 2 * tanHalf() * d / gh, 0.2, 32);
    }

    private static Vec3 lookTip(double len) {
        return head().add(look(pyaw, ppitch).scale(len * 1.3));
    }

    private static BlockHitResult clip(Minecraft client, Vec3 from, Vec3 dir) {
        if (client.level == null || client.player == null) return null;
        BlockHitResult hit = client.level.clip(new ClipContext(from, from.add(dir.scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static double top(Minecraft client, BlockPos at) {
        VoxelShape shape = client.level.getBlockState(at).getCollisionShape(client.level, at);
        return shape.isEmpty() ? at.getY() : at.getY() + shape.max(Direction.Axis.Y);
    }

    private static Vec3 onBlock(Minecraft client, BlockHitResult hit, Grid g) {
        if (g != Grid.BLOCK) {
            Vec3 at = hit.getLocation();
            return new Vec3(snap(g, 0, at.x, at.x), snap(g, 1, at.y, at.y), snap(g, 2, at.z, at.z));
        }
        BlockPos at = hit.getBlockPos();
        if (hit.getDirection() == Direction.UP)
            return new Vec3(at.getX() + 0.5, top(client, at), at.getZ() + 0.5);
        BlockPos stand = at.relative(hit.getDirection());
        for (int d = 0; d <= 8; d++) {
            BlockPos floor = stand.below(d);
            if (!client.level.getBlockState(floor).getCollisionShape(client.level, floor).isEmpty())
                return new Vec3(stand.getX() + 0.5, top(client, floor), stand.getZ() + 0.5);
        }
        return new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
    }

    private static double snap(Grid g, int axis, double v, double from) {
        return switch (g) {
            case BLOCK -> axis == 1 ? from + Math.round(v - from) : Math.floor(v) + 0.5;
            case HALF -> Math.round(v * 2) / 2.0;
            case TENTH -> Math.round(v * 10) / 10.0;
            case FREE -> v;
        };
    }

    public static Grid grid() { return grid; }

    public static void grid(Grid g) { grid = g; }

    public static double[] values() { return new double[]{px, py, pz, pyaw, ppitch}; }

    public static void set(int i, double v) {
        if (!Double.isFinite(v)) return;
        switch (i) {
            case 0 -> px = v;
            case 1 -> py = v;
            case 2 -> pz = v;
            case 3 -> pyaw = Mth.wrapDegrees((float) v);
            default -> ppitch = Mth.clamp((float) v, -90f, 90f);
        }
    }

    public static void nudge(int axis, int dir) {
        push();
        double now = axis == 0 ? px : axis == 1 ? py : pz;
        set(axis, Math.round((now + dir * grid.step) * 1000.0) / 1000.0);
        dropIfSame();
    }

    public static void centerInBlock() {
        push();
        px = Math.floor(px) + 0.5;
        pz = Math.floor(pz) + 0.5;
        dropIfSame();
    }

    public static void toPlayer() {
        push();
        px = Math.round(frozen.x * 1000.0) / 1000.0;
        py = Math.round(frozen.y * 1000.0) / 1000.0;
        pz = Math.round(frozen.z * 1000.0) / 1000.0;
        pyaw = Math.round(standYaw * 10f) / 10f;
        ppitch = Math.round(standPitch * 10f) / 10f;
        dropIfSame();
        frame();
    }

    public static void lookLikeCamera() {
        push();
        pyaw = Math.round(Mth.wrapDegrees(camYaw) * 10f) / 10f;
        ppitch = Math.round(Mth.clamp(camPitch, -90f, 90f) * 10f) / 10f;
        dropIfSame();
    }

    public static void resetLook() {
        push();
        pyaw = 0;
        ppitch = 0;
        dropIfSame();
    }

    public static void frame() {
        if (eye) return;
        Vec3 target = point().add(0, BODY_H / 2, 0);
        Vec3 back = look(camYaw, camPitch).scale(FRAME_DIST);
        cx = target.x - back.x;
        cy = target.y - back.y;
        cz = target.z - back.z;
        snapCam = true;
        place(true);
    }

    public static boolean eyeMode() { return eye; }

    public static void toggleEye() {
        if (eye) leaveEye();
        else enterEye();
    }

    private static void enterEye() {
        if (drag != NONE) release();
        push();
        eye = true;
        hoverNone();
        bx = cx;
        by = cy;
        bz = cz;
        byaw = camYaw;
        bpitch = camPitch;
        cx = px;
        cy = py + EYE;
        cz = pz;
        camYaw = pyaw;
        camPitch = ppitch;
        snapCam = true;
        place(true);
    }

    private static void leaveEye() {
        eye = false;
        dropIfSame();
        cx = bx;
        cy = by;
        cz = bz;
        camYaw = byaw;
        camPitch = bpitch;
        Vec3 to = point().add(0, BODY_H / 2, 0).subtract(cx, cy, cz);
        double dist = to.length();
        boolean lost = dist > 24 || dist > 1.0E-3 && to.scale(1 / dist).dot(look(camYaw, camPitch)) < 0.8;
        if (lost) frame();
        snapCam = true;
        place(true);
    }

    public static void turnCamera(double dxPixels, double dyPixels) {
        Options o = Minecraft.getInstance().options;
        double s = o.sensitivity().get() * 0.6 + 0.2;
        double k = s * s * s * 8.0 * 0.15;
        camYaw += (float) (dxPixels * k * (o.invertMouseX().get() ? -1 : 1));
        camPitch = Mth.clamp(camPitch + (float) (dyPixels * k * (o.invertMouseY().get() ? -1 : 1)),
                -90f, 90f);
        if (eye) {
            pyaw = Mth.wrapDegrees(camYaw);
            ppitch = camPitch;
        }
        aimCam();
    }

    public static void dolly(double amount, double mx, double my, int gw, int gh, boolean fast) {
        if (eye) return;
        Vec3 d = rayDir(mx, my, gw, gh).scale(amount * (fast ? 5 : 1.5));
        cx += d.x;
        cy += d.y;
        cz += d.z;
    }

    public static void frameSync() {
        if (!active || !eye) return;
        camYaw = pyaw;
        camPitch = ppitch;
        aimCam();
    }

    public static void hoverNone() {
        hover = NONE;
        hoverBlock = null;
        hoverPoint = null;
    }

    public static int hover(double mx, double my, int gw, int gh) {
        hoverNone();
        if (!active || eye || drag != NONE) return hover;
        Minecraft client = Minecraft.getInstance();
        Vec3 o = rayFrom(), d = rayDir(mx, my, gw, gh);
        hover = pickHandle(o, d);
        if (hover != NONE) return hover;
        BlockHitResult hit = clip(client, o, d);
        if (hit == null) return hover;
        hoverBlock = hit.getBlockPos();
        hoverPoint = onBlock(client, hit, grid);
        return hover;
    }

    public static boolean overBlock() { return hoverPoint != null; }

    public static double[] axisOnScreen(int axis, int gw, int gh) {
        double[] a = project(point(), gw, gh);
        double[] b = project(point().add(axisVec(axis).scale(handleLen(point()))), gw, gh);
        return new double[]{b[0] - a[0], b[1] - a[1]};
    }

    private static int pickHandle(Vec3 o, Vec3 d) {
        Vec3 p = point();
        if (depthOf(p) <= 0.2 && depthOf(head()) <= 0.2) return NONE;
        double len = handleLen(p);
        if (rayToPoint(o, d, lookTip(len)) < len * 0.2) return LOOK;
        if (rayToPoint(o, d, p) < len * 0.22) return MOVE;
        int best = NONE;
        double bestDist = len * 0.3;
        for (int i = 0; i < 3; i++) {
            double dist = rayToSegment(o, d, p, p.add(axisVec(i).scale(len)));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static double rayToPoint(Vec3 o, Vec3 d, Vec3 p) {
        double s = Math.max(0, p.subtract(o).dot(d));
        return o.add(d.scale(s)).distanceTo(p);
    }

    private static double rayToSegment(Vec3 o, Vec3 dir, Vec3 a, Vec3 b) {
        Vec3 u = dir, v = b.subtract(a), w = o.subtract(a);
        double aa = u.dot(u), bb = u.dot(v), cc = v.dot(v);
        double dd = u.dot(w), ee = v.dot(w);
        double den = aa * cc - bb * bb;
        double s, t;
        if (Math.abs(den) < 1.0E-8) {
            s = 0;
            t = cc < 1.0E-8 ? 0 : ee / cc;
        } else {
            s = (bb * ee - cc * dd) / den;
            t = (aa * ee - bb * dd) / den;
        }
        s = Math.max(0, s);
        t = Mth.clamp(t, 0, 1);
        return o.add(u.scale(s)).distanceTo(a.add(v.scale(t)));
    }

    private static double alongAxis(Vec3 o, Vec3 d, Vec3 start, Vec3 a) {
        Vec3 w0 = start.subtract(o);
        double b = a.dot(d), dd = a.dot(w0), e = d.dot(w0);
        double den = 1 - b * b;
        if (den < 1.0E-3) return Double.NaN;
        return (b * e - dd) / den;
    }

    public static int dragging() { return drag; }

    public static boolean press(double mx, double my, int gw, int gh) {
        if (!active || eye) return false;
        Minecraft client = Minecraft.getInstance();
        Vec3 o = rayFrom(), d = rayDir(mx, my, gw, gh);
        int h = pickHandle(o, d);
        before = snapshot();
        if (h == NONE) {
            BlockHitResult hit = clip(client, o, d);
            if (hit == null) return false;
            Vec3 at = onBlock(client, hit, grid);
            px = at.x;
            py = at.y;
            pz = at.z;
            h = MOVE;
        }
        pushState(before);
        drag = h;
        sx = px;
        sy = py;
        sz = pz;
        if (h <= AZ) {
            double t = alongAxis(o, d, point(), axisVec(h));
            grabT = Double.isNaN(t) ? 0 : t;
        }
        hover = h;
        hoverBlock = null;
        hoverPoint = null;
        return true;
    }

    public static void dragTo(double mx, double my, int gw, int gh, boolean free) {
        if (drag == NONE) return;
        Minecraft client = Minecraft.getInstance();
        Vec3 o = rayFrom(), d = rayDir(mx, my, gw, gh);
        Grid g = free ? Grid.FREE : grid;
        if (drag <= AZ) {
            Vec3 start = new Vec3(sx, sy, sz);
            double t = alongAxis(o, d, start, axisVec(drag));
            if (Double.isNaN(t)) return;
            double from = drag == 0 ? sx : drag == 1 ? sy : sz;
            double v = from + Mth.clamp(t - grabT, -512, 512);
            set(drag, snap(g, drag, v, from));
        } else if (drag == MOVE) {
            BlockHitResult hit = clip(client, o, d);
            Vec3 at;
            if (hit != null) {
                at = onBlock(client, hit, g);
            } else {
                Vec3 f = vec(camera().forwardVector());
                double den = d.dot(f);
                if (den < 1.0E-3) return;
                double s = new Vec3(sx, sy, sz).subtract(o).dot(f) / den;
                if (s <= 0) return;
                Vec3 raw = o.add(d.scale(s));
                at = new Vec3(snap(g, 0, raw.x, sx), snap(g, 1, raw.y, sy), snap(g, 2, raw.z, sz));
            }
            px = at.x;
            py = at.y;
            pz = at.z;
        } else if (drag == LOOK) {
            Vec3 h = head();
            BlockHitResult hit = clip(client, o, d);
            Vec3 target = hit != null ? hit.getLocation() : o.add(d.scale(o.distanceTo(h)));
            Vec3 v = target.subtract(h);
            double len = v.length();
            if (len < 1.0E-4) return;
            double yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
            double pitch = -Math.toDegrees(Math.asin(Mth.clamp(v.y / len, -1, 1)));
            if (!free) {
                yaw = Math.round(yaw);
                pitch = Math.round(pitch);
            }
            set(3, yaw);
            set(4, pitch);
        }
    }

    public static void release() {
        if (drag == NONE) return;
        drag = NONE;
        dropIfSame();
    }

    public static void cancelDrag() {
        if (drag == NONE) return;
        drag = NONE;
        if (before != null) restore(before);
        if (!undo.isEmpty()) undo.pop();
    }

    public static Vec3 dragStart() { return new Vec3(sx, sy, sz); }

    private static double[] snapshot() { return new double[]{px, py, pz, pyaw, ppitch}; }

    private static void restore(double[] s) {
        px = s[0];
        py = s[1];
        pz = s[2];
        pyaw = (float) s[3];
        ppitch = (float) s[4];
    }

    public static void push() { pushState(snapshot()); }

    private static void pushState(double[] s) {
        undo.push(s);
        if (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private static void dropIfSame() {
        if (!undo.isEmpty() && Arrays.equals(undo.peek(), snapshot())) undo.pop();
    }

    public static void settle() { dropIfSame(); }

    public static boolean canUndo() { return !undo.isEmpty(); }

    public static boolean canRedo() { return !redo.isEmpty(); }

    public static void undo() {
        if (undo.isEmpty() || drag != NONE) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    public static void redo() {
        if (redo.isEmpty() || drag != NONE) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    public static Info info() {
        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null || client.player == null) return null;
        AABB body = new AABB(px - HALF_W, py, pz - HALF_W, px + HALF_W, py + BODY_H, pz + HALF_W)
                .deflate(1.0E-3);
        BlockState stuck = stuckIn(level, body);
        if (stuck != null) return new Info(iconOf(stuck), "застрянет: " + nameOf(stuck), Theme.DANGER);
        BlockPos below = BlockPos.containing(px, py - 0.01, pz);
        BlockState ground = level.getBlockState(below);
        VoxelShape shape = ground.getCollisionShape(level, below);
        if (!shape.isEmpty() && Math.abs(below.getY() + shape.max(Direction.Axis.Y) - py) < 0.05)
            return new Info(iconOf(ground), nameOf(ground), Theme.TEXT);
        Vec3 p = point();
        BlockHitResult hit = level.clip(new ClipContext(p, p.subtract(0, 64, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        String air = "в воздухе";
        if (hit != null && hit.getType() == HitResult.Type.BLOCK)
            air += " · " + Value.num(Math.round((py - hit.getLocation().y) * 10) / 10.0) + " до земли";
        return new Info(new ItemStack(Items.FEATHER), air, Theme.TEXT_DIM);
    }

    private static BlockState stuckIn(Level level, AABB body) {
        if (level.noCollision(body)) return null;
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            BlockState state = level.getBlockState(at);
            VoxelShape shape = state.getCollisionShape(level, at);
            if (!shape.isEmpty() && shape.bounds().move(at).intersects(body)) return state;
        }
        return null;
    }

    private static ItemStack iconOf(BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static String nameOf(BlockState state) { return state.getBlock().getName().getString(); }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.level == null && client.getConnection() == null) { abandon(); return; }
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        if (cam == null || cam.level() != client.level) { attach(client); return; }

        if (client.gui.screen() instanceof PauseScreen || client.gui.screen() instanceof ChatScreen)
            client.gui.setScreen(null);
        if (client.gui.screen() == null) open();

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(frozen.x, frozen.y, frozen.z);
        player.xo = player.xOld = frozen.x;
        player.yo = player.yOld = frozen.y;
        player.zo = player.zOld = frozen.z;
        player.fallDistance = 0;
        if (cam != null && client.getCameraEntity() != cam) client.setCameraEntity(cam);
        if (client.gameMode != null) client.gameMode.stopDestroyBlock();

        if (client.gui.screen() instanceof LocationScreen screen && !screen.typing()) move(client);
        if (eye) {
            cx = px;
            cy = py + EYE;
            cz = pz;
        }
        place(snapCam);
        snapCam = false;
    }

    private static boolean held(Minecraft client, KeyMapping key) {
        if (key == null || client.getWindow() == null) return false;
        InputConstants.Key bound = InputConstants.getKey(key.saveString());
        return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() > 0
                && InputConstants.isKeyDown(client.getWindow(), bound.getValue());
    }

    private static void move(Minecraft client) {
        Options o = client.options;
        double f = 0, r = 0, u = 0;
        if (held(client, o.keyUp)) f++;
        if (held(client, o.keyDown)) f--;
        if (held(client, o.keyRight)) r++;
        if (held(client, o.keyLeft)) r--;
        if (held(client, o.keyJump)) u++;
        if (held(client, o.keyShift)) u--;
        if (f == 0 && r == 0 && u == 0) return;
        boolean fast = held(client, o.keySprint);
        Vec3 fw = eye ? look(camYaw, 0) : look(camYaw, camPitch);
        Vec3 m = fw.scale(f).add(side(fw).scale(r)).add(0, u, 0);
        if (m.lengthSqr() < 1.0E-6) return;
        if (eye) {
            m = m.normalize().scale(fast ? WALK_FAST : WALK);
            px += m.x;
            py += m.y;
            pz += m.z;
            return;
        }
        m = m.normalize().scale(fast ? FAST : SPEED);
        cx += m.x;
        cy += m.y;
        cz += m.z;
    }

    public static void startTick(Minecraft client) {
        if (!active || client.gui.screen() != null) return;
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
        drain(o.keyUse);
    }

    private static void drain(KeyMapping key) {
        if (key == null) return;
        while (key.consumeClick()) { }
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
            else {
                slot.add(v);
                index = slot.size() - 1;
            }
        }
        v.x = r3(px);
        v.y = r3(py);
        v.z = r3(pz);
        v.yaw = r1(Mth.wrapDegrees(pyaw));
        v.pitch = r1(Mth.clamp(ppitch, -90f, 90f));
    }

    public static void done() {
        if (!active) return;
        write();
        finish(Minecraft.getInstance(), true);
    }

    public static void cancel() {
        if (!active) return;
        finish(Minecraft.getInstance(), false);
    }

    private static void finish(Minecraft client, boolean applied) {
        Script.Node target = node;
        int arg = argIndex, i = index;
        abandon();
        if (client.gui.screen() instanceof LocationScreen) client.gui.setScreen(null);
        if (applied && target != null) EditorScreen.openPanelAfter(target, arg, i);
        XeroCode.openCanvas(client);
    }

    public static void abandon() {
        Minecraft client = Minecraft.getInstance();
        active = false;
        if (cam != null) {
            try { client.setCameraEntity(client.player); } catch (Throwable ignored) { }
            cam = null;
        }
        if (client.gui.hud.isHidden() != hudWas) client.gui.hud.toggle();
        client.gameRenderer.setRenderBlockOutline(true);
        eye = false;
        drag = NONE;
        hoverNone();
        node = null;
    }

    private static Vec3 origin = Vec3.ZERO;
    private static double ghostScale = 1, baseScale = 1;

    private record Seg(float ax, float ay, float az, float bx, float by, float bz, Vector3f dir,
                       int argb, float width) {}

    private static final List<Seg> SEGMENTS = new ArrayList<>();
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
        if (matrices == null || client.level == null || client.player == null) return;
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
                vc.addVertex(pose, l.ax, l.ay, l.az).setColor(l.argb).setNormal(pose, l.dir)
                        .setLineWidth(l.width);
                vc.addVertex(pose, l.bx, l.by, l.bz).setColor(l.argb).setNormal(pose, l.dir)
                        .setLineWidth(l.width);
            }
        });
    }

    private static void collect(Minecraft client) {
        thick = Math.max(2f, client.getWindow().getAppropriateLineWidth() * 2f);
        hair = Math.max(1f, client.getWindow().getAppropriateLineWidth());
        if (eye) return;

        if (hoverBlock != null && drag == NONE) {
            VoxelShape shape = client.level.getBlockState(hoverBlock).getShape(client.level, hoverBlock);
            if (shape.isEmpty()) shape = Shapes.block();
            xray(Vec3.atCenterOf(hoverBlock));
            outline(shape.bounds().move(hoverBlock).inflate(SKIN), BOX, thick);
            if (hoverPoint != null && hoverPoint.distanceToSqr(point()) > 0.01) {
                xray(hoverPoint);
                body(hoverPoint, Draw.argb(0x50, 0xBFD4EA));
                marker(hoverPoint, GHOST, handleLen(hoverPoint) * 0.14);
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

    private static void gizmo(Minecraft client) {
        Vec3 p = point();
        double len = handleLen(p);
        AABB box = new AABB(px - HALF_W, py, pz - HALF_W, px + HALF_W, py + BODY_H, pz + HALF_W);
        boolean stuck = stuckIn(client.level, box.deflate(1.0E-3)) != null;

        layer(1.06);
        body(p, stuck ? Draw.argb(0xC0, BAD) : Draw.argb(0x78, BODY));
        drop(client, p, Draw.argb(0x50, BODY));
        if (drag >= AX && drag <= AZ) guide(p, axisVec(drag), len, drag);
        if (drag != NONE && drag != LOOK) {
            Vec3 s = dragStart();
            if (s.distanceToSqr(p) > 1.0E-4) {
                marker(s, Draw.argb(0x70, POINT), len * 0.10);
                dashed(s, p, Draw.argb(0x80, 0xFFFFFF), Math.max(0.15, len * 0.12));
            }
        }

        layer(1.0);
        for (int i = 0; i < 3; i++) {
            boolean lit = drag == i || drag == NONE && hover == i;
            boolean muted = drag != NONE && drag != i;
            int c = axisColor(i);
            if (lit) c = Draw.mix(c, 0xFFFFFF, 0.45f);
            arrow(p, axisVec(i), len, Draw.argb(muted ? 0x50 : lit ? 0xFF : 0xD8, c), lit ? thick : hair);
        }

        layer(0.94);
        boolean moveLit = drag == MOVE || drag == NONE && hover == MOVE;
        marker(p, Draw.argb(0xFF, moveLit ? Theme.ACCENT : POINT), len * (moveLit ? 0.2 : 0.16));
        boolean lookLit = drag == LOOK || drag == NONE && hover == LOOK;
        int rot = Draw.argb(lookLit ? 0xFF : 0xD8, lookLit ? Draw.mix(ROT, 0xFFFFFF, 0.4f) : ROT);
        Vec3 h = head(), tip = lookTip(len);
        line(h, tip, rot, lookLit ? thick : hair);
        marker(tip, rot, len * (lookLit ? 0.13 : 0.1));
        marker(h, Draw.argb(0x90, ROT), len * 0.05);
    }

    private static void body(Vec3 p, int argb) {
        outline(new AABB(p.x - HALF_W, p.y, p.z - HALF_W, p.x + HALF_W, p.y + BODY_H, p.z + HALF_W),
                argb, hair);
    }

    private static void outline(AABB b, int argb, float width) {
        double[][] xz = {{b.minX, b.minZ}, {b.maxX, b.minZ}, {b.maxX, b.maxZ}, {b.minX, b.maxZ}};
        for (int i = 0; i < 4; i++) {
            double[] a = xz[i], c = xz[(i + 1) % 4];
            line(new Vec3(a[0], b.minY, a[1]), new Vec3(c[0], b.minY, c[1]), argb, width);
            line(new Vec3(a[0], b.maxY, a[1]), new Vec3(c[0], b.maxY, c[1]), argb, width);
            line(new Vec3(a[0], b.minY, a[1]), new Vec3(a[0], b.maxY, a[1]), argb, width);
        }
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

    private static void drop(Minecraft client, Vec3 p, int argb) {
        if (client.level == null || client.player == null) return;
        Vec3 to = p.subtract(0, 40, 0);
        BlockHitResult hit = client.level.clip(new ClipContext(p, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        boolean ground = hit != null && hit.getType() == HitResult.Type.BLOCK;
        Vec3 end = ground ? hit.getLocation() : to;
        if (end.distanceToSqr(p) < 0.01) return;
        dashed(p, end, argb, 0.3);
        if (!ground) return;
        double r = 0.15;
        line(end.add(-r, 0.01, 0), end.add(r, 0.01, 0), argb, hair);
        line(end.add(0, 0.01, -r), end.add(0, 0.01, r), argb, hair);
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
}
