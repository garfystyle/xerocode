package com.xerocode.web;

import com.xerocode.Collab;
import com.xerocode.Market;
import com.xerocode.XeroCode;
import com.xerocode.ui.EditorScreen;
import com.xerocode.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.teavm.jso.JSObject;

public final class Main {
    private static final Renderer RENDERER = new Renderer();
    private static int width, height, scale;
    private static double mouseX, mouseY;
    private static int heldButton = -1;
    private static long lastClick;
    private static int lastClickButton = -1;
    private static boolean scrolling;
    private static double scrollY;
    private static double edited, saved;
    private static double tickAccum;
    private static double lastTime;
    private static int mods;
    private static int savedPrint;

    private Main() {}

    public static void main(String[] args) {
        Js.ready();
        try {
            Storage.restore();
            Glyphs.load();
            ItemData.load();
            Minecraft mc = Minecraft.getInstance();
            resize(mc);
            XeroCode.init();
            mc.onScreenClosed(() -> XeroCode.openCanvas(mc));
            XeroCode.openCanvas(mc);
            Js.onHide(() -> {
                XeroCode.saveAll();
                Storage.sync();
            });
        } catch (Throwable e) {
            fail(e);
            return;
        }
        lastTime = Js.now();
        while (true) {
            Js.nextFrame();
            try {
                frame();
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    private static void fail(Throwable e) {
        report(e);
        Js.toast("Не запустилось: " + e);
    }

    private static void report(Throwable e) {
        StringBuilder sb = new StringBuilder(String.valueOf(e));
        for (StackTraceElement el : e.getStackTrace()) sb.append("\n  at ").append(el);
        Throwable c = e.getCause();
        if (c != null) sb.append("\nCaused by: ").append(c);
        Js.log("error", sb.toString());
    }

    private static int autoScale(int w, int h) {
        double target = 900;
        if (Js.coarse()) target = Math.max(320, Math.min(900, w / Js.ratio() / 2));
        int s = Math.max(1, (int) (Js.coarse() ? Math.floor(w / target) : Math.round(w / target)));
        int chosen = com.xerocode.Settings.get().webScale;
        if (chosen > 0) s = chosen;
        while (s > 1 && (w / s < 320 || h / s < 240)) s--;
        String forced = Js.param("scale");
        if (forced != null && !forced.isEmpty()) {
            try {
                s = Math.max(1, Integer.parseInt(forced));
            } catch (NumberFormatException ignored) {
            }
        }
        return s;
    }

    private static void resize(Minecraft mc) {
        int w = Math.max(1, Js.width()), h = Math.max(1, Js.height());
        int s = autoScale(w, h);
        if (w == width && h == height && s == scale) return;
        width = w;
        height = h;
        scale = s;
        mc.getWindow().setSize(w, h, s, Js.ratio());
        Screen screen = mc.gui.screen();
        if (screen != null) screen.resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static void frame() {
        Minecraft mc = Minecraft.getInstance();
        resize(mc);
        events(mc);
        double now = Js.now();
        tickAccum += Math.min(250, now - lastTime);
        lastTime = now;
        while (tickAccum >= 50) {
            tickAccum -= 50;
            Screen s = mc.gui.screen();
            if (s != null) s.tick();
            try {
                Collab.tick();
                Market.tick();
            } catch (Throwable e) {
                report(e);
            }
        }
        mc.runTasks();
        Screen screen = mc.gui.screen();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor ctx = new GuiGraphicsExtractor(mc, state);
        Ui.clearDragZones();
        if (screen != null) {
            int mx = (int) Math.floor(mouseX), my = (int) Math.floor(mouseY);
            screen.extractRenderStateWithTooltipAndSubtitles(ctx, mx, my, (float) (tickAccum / 50.0));
        }
        RENDERER.begin(scale);
        RENDERER.draw(state);
        Js.render(RENDERER.verts(), RENDERER.vertexCount(), RENDERER.cmds(), RENDERER.cmdCount(), scale);
        Js.cursor(ctx.pendingCursor().css());
        Input.flushFocus();
        autosave(now);
        Storage.tick();
    }

    private static void events(Minecraft mc) {
        JSObject q = Js.pollEvents();
        int n = Js.length(q);
        for (int i = 0; i < n; i++) {
            String type = Js.str(q, i, 0);
            if (!type.equals("m") && !type.equals("b")) edited = Js.now();
            Screen screen = mc.gui.screen();
            try {
                switch (type) {
                    case "k" -> {
                        int key = (int) Js.num(q, i, 1);
                        mods = (int) Js.num(q, i, 3);
                        Input.key(key, true);
                        if (screen != null) screen.keyPressed(new KeyEvent(key, (int) Js.num(q, i, 2), mods));
                    }
                    case "u" -> {
                        int key = (int) Js.num(q, i, 1);
                        mods = (int) Js.num(q, i, 3);
                        Input.key(key, false);
                        if (screen != null) screen.keyReleased(new KeyEvent(key, (int) Js.num(q, i, 2), mods));
                    }
                    case "c" -> {
                        int cp = (int) Js.num(q, i, 1);
                        if (screen != null) screen.charTyped(new CharacterEvent(cp));
                    }
                    case "m" -> {
                        if (scrolling) scrollTo(screen, Js.num(q, i, 2) / scale);
                        else move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                    }
                    case "d" -> press(screen, Js.num(q, i, 1), Js.num(q, i, 2), (int) Js.num(q, i, 3),
                            (int) Js.num(q, i, 4), Js.num(q, i, 5) == 1);
                    case "g" -> {
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        double dx = Js.num(q, i, 3), dy = Js.num(q, i, 4);
                        if (Math.abs(dy) > Math.abs(dx) && scrolls(screen)) {
                            scrolling = true;
                            scrollY = mouseY;
                        } else {
                            press(screen, Js.num(q, i, 1), Js.num(q, i, 2), 0, 0, true);
                        }
                    }
                    case "r" -> {
                        if (scrolling) { scrolling = false; break; }
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        int button = (int) Js.num(q, i, 3);
                        mods = (int) Js.num(q, i, 4);
                        Input.button(button, false);
                        if (heldButton == button) heldButton = -1;
                        if (screen != null)
                            screen.mouseReleased(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, mods)));
                    }
                    case "h" -> {
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        if (screen instanceof EditorScreen editor) editor.touchHold(mouseX, mouseY);
                    }
                    case "w" -> {
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        if (screen != null) screen.mouseScrolled(mouseX, mouseY, Js.num(q, i, 3), Js.num(q, i, 4));
                    }
                    case "p" -> {
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        double dx = Js.num(q, i, 3) / scale, dy = Js.num(q, i, 4) / scale;
                        if (screen instanceof EditorScreen editor && editor.touchPan(mouseX, mouseY, dx, dy)) break;
                        if (screen != null && dy != 0) screen.mouseScrolled(mouseX, mouseY, 0, dy / 18);
                    }
                    case "z" -> {
                        move(screen, Js.num(q, i, 1), Js.num(q, i, 2));
                        if (screen instanceof EditorScreen editor) editor.touchZoom(mouseX, mouseY, Js.num(q, i, 3));
                    }
                    case "b" -> {
                        Input.releaseAll();
                        heldButton = -1;
                        scrolling = false;
                    }
                    default -> { }
                }
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    private static void autosave(double now) {
        if (edited <= saved) return;
        if (now - edited < 1500 && now - saved < 10000) return;
        saved = now;
        try {
            int print = XeroCode.script().fingerprint();
            if (print == savedPrint) return;
            savedPrint = print;
            XeroCode.saveAll();
        } catch (Throwable e) {
            report(e);
        }
    }

    private static void press(Screen screen, double rawX, double rawY, int button, int held, boolean touch) {
        move(screen, rawX, rawY);
        mods = held;
        Input.button(button, true);
        long t = (long) Js.now();
        int window = touch ? 400 : 250;
        boolean dbl = button == lastClickButton && t - lastClick < window;
        lastClick = t;
        lastClickButton = button;
        heldButton = button;
        if (screen != null)
            screen.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, mods)), dbl);
    }

    private static boolean scrolls(Screen screen) {
        if (screen == null || Ui.inDragZone(mouseX, mouseY)) return false;
        return !(screen instanceof EditorScreen editor) || editor.touchScrolls(mouseX, mouseY);
    }

    private static void scrollTo(Screen screen, double y) {
        double d = y - scrollY;
        scrollY = y;
        if (screen != null && d != 0) screen.mouseScrolled(mouseX, mouseY, 0, d / 18);
    }

    private static void move(Screen screen, double rawX, double rawY) {
        Input.mouse(rawX, rawY);
        double nx = rawX / scale, ny = rawY / scale;
        double dx = nx - mouseX, dy = ny - mouseY;
        mouseX = nx;
        mouseY = ny;
        if (screen == null || (dx == 0 && dy == 0)) return;
        screen.mouseMoved(nx, ny);
        if (heldButton >= 0)
            screen.mouseDragged(new MouseButtonEvent(nx, ny, new MouseButtonInfo(heldButton, mods)), dx, dy);
    }
}
