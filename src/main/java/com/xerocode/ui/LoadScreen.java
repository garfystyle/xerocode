package com.xerocode.ui;

import com.xerocode.Net;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

final class LoadScreen extends DialogScreen {
    private static final String TITLE = "ЗАГРУЗИТЬ КОД";
    private static final String HOW = "В /dev набери /editor download и вставь сюда ссылку из чата.";
    private static final String LOAD = "Загрузить";
    private static final String OR = "или";
    private static final String CANCEL = "Отмена";
    private static final String FILE = "Выбрать .json с компьютера";
    private static final String PROXY = "https://2-27-249-33.sslip.io/xerocode/justmc?u=";
    private static final Path SAVED = Path.of("/upload", "justmc.json");
    private static final Net.Who WHO = new Net.Who("нет сети", "сервер не отвечает", "сервер не ответил вовремя");
    private static final int FIELD_H = 20;

    private final Screen parent;
    private final Consumer<Path> done;
    private final Ui.Grab grab = new Ui.Grab();
    private EditBox link;
    private String note = "";
    private boolean failed;
    private volatile boolean busy;

    LoadScreen(Screen parent, Consumer<Path> done) {
        super(360);
        this.parent = parent;
        this.done = done;
    }

    @Override
    protected void init() {
        String text = link == null ? "" : link.getValue();
        link = Ui.field(font, 0, 0, 40, 12, "http://api.creative.justmc.io/…");
        link.setMaxLength(Ui.TEXT_MAX);
        link.setValue(text);
        addWidget(link);
        setFocused(link);
        link.setFocused(true);
    }

    @Override
    protected String title() { return TITLE; }

    private int howRows() { return paragraphRows(HOW, bodyW()); }

    private int fieldY() { return bodyY() + howRows() * ROW + GAP; }

    private int loadW() { return primaryW(LOAD); }

    private int noteY() { return fieldY() + FIELD_H + 5; }

    private int orY() { return noteY() + ROW + 2; }

    private int fileY() { return orY() + ROW + 2; }

    @Override
    protected int bodyH() {
        return howRows() * ROW + GAP + FIELD_H + 5 + ROW + 2 + ROW + 2 + BTN_H + GAP + BTN_H;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        paragraph(ctx, HOW, x, y, w, Theme.TEXT_DIM);
        int fy = fieldY(), fw = w - loadW() - 6;
        Ui.input(ctx, x, fy, fw, FIELD_H, link.isFocused());
        link.setX(x + 6);
        link.setY(fy + (FIELD_H - Ui.TEXT_H) / 2);
        Ui.width(link, fw - 12);
        link.extractRenderState(ctx, mouseX, mouseY, 0);
        Ui.button(ctx, font, mouseX, mouseY, x + w - loadW(), fy, loadW(), FIELD_H,
                busy ? "…" : LOAD, Ui.ACCENT);
        if (!note.isEmpty())
            Draw.textFit(ctx, font, note, x, noteY(), w, failed ? Theme.DANGER : Theme.TEXT_FAINT, false);
        Draw.textCenter(ctx, font, OR, x, orY(), w, w, Theme.TEXT_FAINT, false);
        Ui.button(ctx, font, mouseX, mouseY, x, fileY(), w, BTN_H, FILE, Ui.GHOST);
        buttons(ctx, mouseX, mouseY, x, w, null, CANCEL);
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int x = bodyX(), w = bodyW(), fy = fieldY(), fw = w - loadW() - 6;
        if (Ui.hit(mx, my, x, fy, fw, FIELD_H)) return false;
        if (Ui.hit(mx, my, x + w - loadW(), fy, loadW(), FIELD_H)) { fetch(); return true; }
        if (Ui.hit(mx, my, x, fileY(), w, BTN_H)) { pickFile(); return true; }
        if (hitGhost(mx, my, null, CANCEL)) { onClose(); return true; }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int x = bodyX(), fy = fieldY(), fw = bodyW() - loadW() - 6;
        if (Ui.hit(click.x(), click.y(), x, fy, fw, FIELD_H)) {
            setFocused(link);
            link.setFocused(true);
            grab.take(link);
            if (!link.mouseClicked(click, doubled)) link.onClick(click, doubled);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return grab.drag(click, dx, dy) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        grab.release();
        return super.mouseReleased(click);
    }

    @Override
    protected boolean onEnter() {
        fetch();
        return true;
    }

    private void pickFile() {
        FileDialog.open("Загрузить json", "", new String[]{"*.json"}, "код JustMC (*.json)", file -> {
            if (file == null) return;
            finish(file);
        });
    }

    private void fetch() {
        if (busy) return;
        String url = link.getValue().trim();
        if (!url.contains("justmc.io/") || !url.contains("token=")) {
            say("это не ссылка из /editor download", true);
            return;
        }
        busy = true;
        say("загружаю…", false);
        Minecraft mc = Minecraft.getInstance();
        Net.threads("xerocode-load").newThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(PROXY
                                + URLEncoder.encode(url, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(30)).GET().build();
                HttpResponse<byte[]> answer = Net.client(Duration.ofSeconds(10), true)
                        .send(request, HttpResponse.BodyHandlers.ofByteArray());
                int code = answer.statusCode();
                if (code != 200) {
                    mc.execute(() -> fail(code == 429 ? "слишком часто — подожди минуту"
                            : "JustMC не отдал код — ссылка одноразовая и живёт недолго"));
                    return;
                }
                if (!Files.isDirectory(SAVED.getParent())) Files.createDirectories(SAVED.getParent());
                Files.write(SAVED, answer.body());
                mc.execute(() -> { busy = false; finish(SAVED); });
            } catch (Throwable e) {
                mc.execute(() -> fail(Net.reason(e, WHO)));
            }
        }).start();
    }

    private void fail(String text) {
        busy = false;
        say(text, true);
    }

    private void say(String text, boolean bad) {
        note = text;
        failed = bad;
    }

    private void finish(Path file) {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(parent);
        done.accept(file);
    }

    @Override
    public void onClose() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(parent);
    }
}
