package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Market;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public final class MarketLook implements MarketScreen.Panel {
    private static final int HEAD = 22, ICON = 16;

    private final MarketScreen owner;
    private final TextRenderer tr;
    private final Market.Module module;
    private final BlockView.Look look = new BlockView.Look();
    private final CodeStage stage = new CodeStage(0.2, 3.0, 1.4);
    private final ModuleCode code;

    private int x, y, w, h, mx, my;

    public MarketLook(MarketScreen owner, Market.Module module, JsonObject payload) {
        this.owner = owner;
        this.tr = MinecraftClient.getInstance().textRenderer;
        this.module = module;
        this.code = new ModuleCode(module.id, tr);
        code.adopt(payload);
    }

    @Override
    public String title() { return module.name; }

    @Override
    public void place(int px, int py, int pw, int ph) {
        this.x = px;
        this.y = py;
        this.w = pw;
        this.h = ph;
        code.forget();
        stage.place(x, y + HEAD + 1, w, Math.max(20, h - HEAD - 1));
    }

    @Override
    public void draw(DrawContext ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Theme.CANVAS));
        Layout l = code.layout();
        if (l == null) {
            Draw.textCenter(ctx, tr, code.trouble().isEmpty() ? "гружу код модуля…" : code.trouble(),
                    x, y + h / 2 - 4, w, w - 30,
                    code.trouble().isEmpty() ? Theme.TEXT_FAINT : Theme.DANGER, false);
        } else {
            if (stage.needsFit()) stage.fit(l, 12);
            look.hover = stage.boxAt(l, mx, my);
            look.mx = Integer.MIN_VALUE;
            look.my = Integer.MIN_VALUE;
            stage.draw(ctx, tr, l, look);
        }
        drawHead(ctx, mouseX, mouseY);
    }

    private int zoomW() { return tr.getWidth("999%") + 4; }

    private int btnX(int i) { return x + w - 8 - ICON * (3 - i) - 3 * (2 - i); }

    private int packW() { return Ui.buttonW(tr, "В рюкзак"); }

    private int packX() { return btnX(0) - zoomW() - 8 - packW(); }

    private void drawHead(DrawContext ctx, int mouseX, int mouseY) {
        Draw.rect(ctx, x, y, w, HEAD, Draw.opaque(Ui.RAIL));
        Ui.hairline(ctx, x, y + HEAD, w);
        int ty = y + (HEAD - Ui.TEXT_H) / 2;
        String said = module.blocksText() + " · "
                + Ui.plural(module.roots, "стопка", "стопки", "стопок");
        int room = packX() - x - 16;
        if (room > 60) Draw.textFit(ctx, tr, said, x + 8, ty, room, Theme.TEXT_FAINT, false);
        if (packX() > x + 8)
            Ui.button(ctx, tr, mouseX, mouseY, packX(), y + 3, packW(), ICON,
                    "В рюкзак", Ui.GHOST, code.ready());
        Draw.textRight(ctx, tr, stage.zoomText(), btnX(0) - 6, ty, Theme.TEXT_FAINT, false);
        Ui.iconButton(ctx, mouseX, mouseY, btnX(0), y + 3, ICON, Draw.MINUS, Ui.GHOST,
                !stage.atMin());
        Ui.iconButton(ctx, mouseX, mouseY, btnX(1), y + 3, ICON, Draw.PLUS, Ui.GHOST,
                !stage.atMax());
        Ui.iconButton(ctx, mouseX, mouseY, btnX(2), y + 3, ICON, Draw.FIT, Ui.GHOST, true);
    }

    @Override
    public String hint() {
        if (!code.trouble().isEmpty()) return code.trouble();
        return code.ready() ? "" : "гружу код модуля…";
    }

    @Override
    public String action() { return code.ready() ? "На полотно" : "Гружу…"; }

    @Override
    public boolean actionOn() { return code.ready(); }

    @Override
    public void act() {
        if (code.ready()) owner.install(module, code.payload(), false);
    }

    @Override
    public boolean click(Click click, boolean doubled) {
        double atX = click.x(), atY = click.y();
        if (atY < y + HEAD) {
            if (Ui.hit(atX, atY, btnX(0), y + 3, ICON, ICON)) { stage.step(1 / 1.12); return true; }
            if (Ui.hit(atX, atY, btnX(1), y + 3, ICON, ICON)) { stage.step(1.12); return true; }
            if (Ui.hit(atX, atY, btnX(2), y + 3, ICON, ICON)) { refit(); return true; }
            if (code.ready() && Ui.hit(atX, atY, packX(), y + 3, packW(), ICON))
                owner.install(module, code.payload(), true);
            return true;
        }
        if (doubled) refit();
        else stage.grab();
        return true;
    }

    private void refit() { stage.fit(code.layout(), 12); }

    @Override
    public boolean drag(Click click, double dx, double dy) {
        if (!stage.panning()) return false;
        stage.drag(dx, dy);
        return true;
    }

    @Override
    public void release() { stage.release(); }

    @Override
    public boolean wheel(double atX, double atY, double amount) {
        stage.wheel(atX, atY, amount);
        return true;
    }

    @Override
    public boolean key(KeyInput in) {
        switch (in.key()) {
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> refit();
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> stage.step(1.12);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> stage.step(1 / 1.12);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> act();
            default -> { return false; }
        }
        return true;
    }
}
