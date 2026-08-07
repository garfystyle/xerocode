package com.xerocode.ui;

import com.xerocode.Market;
import com.xerocode.MarketImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

abstract class MarketPanel implements MarketScreen.Panel {
    protected static final int PAD = 14, ROW = 18, GAP = 12, LABEL = 11;

    protected record Hit(String what, int x, int y, int w, int h, int index) {
        Hit(String what, int x, int y, int w, int h) { this(what, x, y, w, h, 0); }
    }

    protected final MarketScreen owner;
    protected final TextRenderer tr;

    protected int x, y, w, h, scroll, content;
    protected final Ui.Bar bar = new Ui.Bar();
    protected final Ui.Grab grab = new Ui.Grab();
    protected final List<Hit> hits = new ArrayList<>();
    protected final List<ClickableWidget> fields = new ArrayList<>();

    protected String busy = "", trouble = "";
    private boolean choosingFile;

    protected MarketPanel(MarketScreen owner) {
        this.owner = owner;
        this.tr = MinecraftClient.getInstance().textRenderer;
    }

    @Override
    public void place(int px, int py, int pw, int ph) {
        this.x = px;
        this.y = py;
        this.w = pw;
        this.h = ph;
        placed();
    }

    protected void placed() { }

    protected boolean split() { return w >= 640; }

    protected int columnW() { return split() ? Math.max(300, w * 52 / 100) : w; }

    protected int inner() { return columnW() - PAD * 2; }

    protected int fieldW() { return Math.max(120, Math.min(430, inner())); }

    protected int fieldX() { return x + PAD; }

    protected int maxScroll() { return Math.max(0, content - h); }

    protected void drawScrollBar(DrawContext ctx, int mouseX, int mouseY) {
        if (content > h)
            bar.draw(ctx, x + columnW() - 5, y + 2, h - 4, content, h, scroll, mouseX, mouseY);
    }

    protected void caption(DrawContext ctx, String text, int at, String note) {
        Ui.caption(ctx, tr, text.toUpperCase(Locale.ROOT), fieldX(), at, fieldW(),
                note == null ? "" : note);
    }

    protected int drawField(DrawContext ctx, int at, String title, TextFieldWidget field,
                            String what, String note, int mouseX, int mouseY, float delta) {
        int room = fieldW();
        caption(ctx, title, at, note);
        at += LABEL;
        Ui.input(ctx, fieldX(), at, room, ROW, field.isFocused());
        field.setX(fieldX() + 6);
        field.setY(at + (ROW - Ui.TEXT_H) / 2 + 1);
        field.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, field);
        hits.add(new Hit(what, fieldX(), at, room, ROW));
        return at + ROW + GAP;
    }

    protected int button(DrawContext ctx, String text, String what, int bx, int by,
                         int mouseX, int mouseY) {
        int bw = Ui.buttonW(tr, text);
        if (bx + bw > fieldX() + inner()) return bx;
        Ui.button(ctx, tr, mouseX, mouseY, bx, by, bw, 16, text, Ui.GHOST);
        hits.add(new Hit(what, bx, by, bw, 16));
        return bx + bw + 6;
    }

    protected void blur() {
        for (ClickableWidget field : fields) field.setFocused(false);
    }

    protected void focus(ClickableWidget field, Click click, boolean doubled) {
        blur();
        field.setFocused(true);
        if (field instanceof TextFieldWidget text) {
            if (!text.mouseClicked(click, doubled)) text.onClick(click, doubled);
        } else {
            field.mouseClicked(click, doubled);
        }
        grab.take(field);
    }

    @Override
    public boolean click(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (bar.grabbed(mx, my, 1, maxScroll(), v -> scroll = v)) return true;
        if (mx < x || my < y || my >= y + h) return true;
        for (Hit hit : hits) {
            if (!Ui.hit(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            tapped(hit, click, doubled);
            return true;
        }
        blur();
        return true;
    }

    protected abstract void tapped(Hit hit, Click click, boolean doubled);

    @Override
    public boolean drag(Click click, double dx, double dy) {
        if (bar.dragged(click.y(), 1, maxScroll(), v -> scroll = v)) return true;
        return grab.drag(click, dx, dy);
    }

    @Override
    public void release() {
        bar.release();
        grab.release();
    }

    @Override
    public boolean wheel(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 30));
        return true;
    }

    @Override
    public boolean key(KeyInput in) {
        for (ClickableWidget field : fields)
            if (field.isFocused()) return field.keyPressed(in);
        return false;
    }

    @Override
    public boolean chars(CharInput in) {
        for (ClickableWidget field : fields)
            if (field.isFocused()) return field.charTyped(in);
        return false;
    }

    protected void failed(String said) {
        busy = "";
        trouble = said;
        owner.toast(said);
    }

    protected void pickImage(String title, boolean forBanner, Consumer<String> done) {
        Market.Me me = Market.me();
        if (me == null) { owner.toast("нужен аккаунт"); return; }
        if (me.limit("images_day") <= 0) {
            owner.toast("картинки — для подтверждённых аккаунтов, это в профиле");
            return;
        }
        if (me.imageLeft() <= 0) {
            owner.toast("на сегодня картинки кончились");
            return;
        }
        if (choosingFile) return;
        choosingFile = true;
        owner.toast(FileDialog.hint());
        FileDialog.open(title, System.getProperty("user.home", "") + File.separator,
                PICTURES, "картинка", file -> {
                    choosingFile = false;
                    if (file != null) upload(file, forBanner, done);
                });
    }

    private static final String[] PICTURES = {"*.png", "*.jpg", "*.jpeg", "*.bmp"};

    private void upload(Path file, boolean forBanner, Consumer<String> done) {
        busy = "готовлю картинку…";
        byte[] png;
        try {
            png = MarketImage.prepare(file, forBanner);
        } catch (Throwable e) {
            busy = "";
            owner.toast("картинка не подошла: " + e.getMessage());
            return;
        }
        busy = "отправляю картинку…";
        Market.sendImage(png, forBanner, ref -> {
            busy = "";
            done.accept(ref);
        }, (said, why) -> {
            busy = "";
            owner.toast(said);
        });
    }
}
