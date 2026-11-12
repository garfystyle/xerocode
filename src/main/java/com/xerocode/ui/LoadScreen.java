package com.xerocode.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.Codespace;
import com.xerocode.Download;
import com.xerocode.Env;
import com.xerocode.Fetch;
import com.xerocode.Net;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

final class LoadScreen extends DialogScreen {
    private static final String TITLE = "ЗАГРУЗИТЬ КОД";
    private static final String HOW = "Вставь ссылку на код JustMC (json) — из /editor download или любую другую.";
    private static final String LOAD = "Загрузить";
    private static final String PASTE = "Вставить";
    private static final String NOT_LINK = "это не ссылка";
    private static final String NOT_CODE = "по ссылке не код JustMC";
    private static final String NOTHING = "по ссылке ничего нет";
    private static final String ONCE = "JustMC не отдал код — ссылка одноразовая и живёт недолго";
    private static final String SLOW_DOWN = "слишком часто — подожди минуту";
    private static final String EMPTY = "в буфере обмена пусто";
    private static final String DENIED = "браузер не дал буфер обмена";
    private static final String OR = "или";
    private static final String CANCEL = "Отмена";
    private static final String FILE = "Выбрать .json с компьютера";
    private static final String WORLD = "Скачать код этого мира";
    private static final String READING = "читаю код мира…";
    private static final String NO_DOWNLOAD = "сервер не даёт /editor download: зайди в /dev своего мира, нужен ранг Meteor+";
    private static final String NAME = "justmc.json";
    private static final String WORLD_NAME = "мир.json";
    private static final Path SITE_SAVED = Path.of("/upload", NAME);
    private static final Net.Who VIA_STORE = new Net.Who("нет сети", "сервер не отвечает", "сервер не ответил вовремя");
    private static final Net.Who DIRECT = new Net.Who("адрес не найден", "сайт по ссылке не отвечает",
            "сайт по ссылке не ответил вовремя");
    private static final Pattern WITH_SCHEME = Pattern.compile("(?i)https?://[^\\s/?#<>\"'`]+[^\\s<>\"'`]*");
    private static final Pattern BARE = Pattern.compile("^(?:[^\\s/:?#@]+\\.[^\\s/:?#@.\\d][^\\s/:?#@.]*"
            + "|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:[/?#]\\S*)?$");
    private static final Pattern TAIL = Pattern.compile("[.,;!?»\"')\\]]+$");
    private static final String HEX = "0123456789ABCDEF";
    private static final int FIELD_H = 20;
    private static final int STACK = 6;

    private final Screen parent;
    private final Consumer<Path> done;
    private final Ui.Grab grab = new Ui.Grab();
    private EditBox link;
    private String note = "";
    private boolean failed;
    private volatile boolean busy;
    private boolean choosing;
    private Download download;

    LoadScreen(Screen parent, Consumer<Path> done) {
        super(360);
        this.parent = parent;
        this.done = done;
    }

    @Override
    protected void init() {
        String text = link == null ? "" : link.getValue();
        link = Ui.field(font, 0, 0, 40, 12, "https://…");
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

    private int pasteW() { return ghostW(PASTE); }

    private int fieldW() { return bodyW() - loadW() - 6 - pasteW() - 6; }

    private int pasteX() { return bodyX() + fieldW() + 6; }

    private int noteY() { return fieldY() + FIELD_H + 5; }

    private int orY() { return noteY() + ROW + 2; }

    private boolean worldShown() { return !Env.browser(); }

    private int worldY() { return orY() + ROW + 2; }

    private int fileY() { return worldY() + (worldShown() ? BTN_H + STACK : 0); }

    @Override
    protected int bodyH() {
        return howRows() * ROW + GAP + FIELD_H + 5 + ROW + 2 + ROW + 2 + (worldShown() ? BTN_H + STACK : 0)
                + BTN_H + GAP + BTN_H;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        paragraph(ctx, HOW, x, y, w, Theme.TEXT_DIM);
        int fy = fieldY(), fw = fieldW();
        Ui.input(ctx, x, fy, fw, FIELD_H, link.isFocused());
        link.setX(x + 6);
        link.setY(fy + (FIELD_H - Ui.TEXT_H) / 2);
        Ui.width(link, fw - 12);
        link.extractRenderState(ctx, mouseX, mouseY, 0);
        Ui.button(ctx, font, mouseX, mouseY, pasteX(), fy, pasteW(), FIELD_H, PASTE, Ui.GHOST);
        Env.pasteButton(pasteX(), fy, pasteW(), FIELD_H);
        Ui.button(ctx, font, mouseX, mouseY, x + w - loadW(), fy, loadW(), FIELD_H,
                busy ? "…" : LOAD, Ui.ACCENT);
        if (!note.isEmpty())
            Draw.textFit(ctx, font, note, x, noteY(), w, failed ? Theme.DANGER : Theme.TEXT_FAINT, false);
        Draw.textCenter(ctx, font, OR, x, orY(), w, w, Theme.TEXT_FAINT, false);
        if (worldShown())
            Ui.button(ctx, font, mouseX, mouseY, x, worldY(), w, BTN_H, download != null ? READING : WORLD, Ui.GHOST,
                    download == null && !busy);
        Ui.button(ctx, font, mouseX, mouseY, x, fileY(), w, BTN_H, FILE, Ui.GHOST);
        buttons(ctx, mouseX, mouseY, x, w, null, CANCEL);
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int x = bodyX(), w = bodyW(), fy = fieldY();
        if (Ui.hit(mx, my, x, fy, fieldW(), FIELD_H)) return false;
        if (Ui.hit(mx, my, pasteX(), fy, pasteW(), FIELD_H)) { paste(); return true; }
        if (Ui.hit(mx, my, x + w - loadW(), fy, loadW(), FIELD_H)) { fetch(); return true; }
        if (worldShown() && Ui.hit(mx, my, x, worldY(), w, BTN_H)) { fromWorld(); return true; }
        if (Ui.hit(mx, my, x, fileY(), w, BTN_H)) { pickFile(); return true; }
        if (hitGhost(mx, my, null, CANCEL)) { onClose(); return true; }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (Ui.hit(click.x(), click.y(), bodyX(), fieldY(), fieldW(), FIELD_H)) {
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

    private void fromWorld() {
        if (busy || download != null) return;
        download = Download.start(minecraft == null ? Minecraft.getInstance() : minecraft);
        if (download == null) say(NO_DOWNLOAD, true);
        else say(READING, false);
    }

    @Override
    public void tick() {
        super.tick();
        Download asked = download;
        if (asked == null) return;
        asked.tick();
        if (asked.state == Download.State.FAILED) {
            download = null;
            say("мир не прочитан: " + asked.error, true);
        } else if (asked.state == Download.State.DONE) {
            download = null;
            JsonObject root = new JsonObject();
            root.add("handlers", asked.handlers);
            try {
                Path file = Codespace.savedDir().resolve(WORLD_NAME);
                Files.createDirectories(file.getParent());
                Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
                finish(file);
            } catch (Exception e) {
                say("код мира не сохранился: " + e.getMessage(), true);
            }
        }
    }

    private void pickFile() {
        if (choosing) return;
        choosing = true;
        String start = "";
        if (!Env.browser()) {
            Path dir = Codespace.savedDir();
            try {
                Files.createDirectories(dir);
                start = dir.toAbsolutePath() + File.separator;
            } catch (Exception ignored) {
                start = "";
            }
            say(FileDialog.hint(), false);
        }
        FileDialog.open("Загрузить json", start, new String[]{"*.json"}, "код JustMC (*.json)", file -> {
            choosing = false;
            if (file == null || !showing()) return;
            finish(file);
        });
    }

    private void paste() {
        Env.clipboard(text -> {
            if (text == null) { say(DENIED, true); return; }
            String pasted = text.strip();
            if (pasted.isEmpty()) { say(EMPTY, true); return; }
            setFocused(link);
            link.setFocused(true);
            link.moveCursorToEnd(false);
            link.setHighlightPos(0);
            link.insertText(pasted);
            if (linkIn(link.getValue()) != null) say("", false);
            else say(NOT_LINK, true);
        });
    }

    static String linkIn(String text) {
        String s = text == null ? "" : text.strip();
        Matcher found = WITH_SCHEME.matcher(s);
        String url;
        if (found.find()) url = found.group();
        else if (BARE.matcher(s).matches()) url = "https://" + s;
        else return null;
        url = TAIL.matcher(url).replaceFirst("");
        return WITH_SCHEME.matcher(url).matches() ? escaped(url) : null;
    }

    private static String escaped(String url) {
        int host = url.indexOf("://") + 3;
        int path = host;
        while (path < url.length() && "/?#".indexOf(url.charAt(path)) < 0) path++;
        StringBuilder out = new StringBuilder(url.length()).append(url, 0, path);
        for (byte b : url.substring(path).getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c > 0x20 && c < 0x7F && "\"<>\\^`{|}".indexOf(c) < 0) out.append((char) c);
            else out.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xF));
        }
        return out.toString();
    }

    private void fetch() {
        if (busy) return;
        String url = linkIn(link.getValue());
        if (url == null) {
            say(NOT_LINK, true);
            return;
        }
        boolean site = Env.browser();
        boolean once = url.contains("justmc.io/") && url.contains("token=");
        busy = true;
        say("загружаю…", false);
        Minecraft mc = Minecraft.getInstance();
        Net.threads("xerocode-load").newThread(() -> {
            try {
                Fetch.Got got = Fetch.code(url);
                byte[] body = withoutBom(got.body());
                String trouble = got.status() != 200 ? refusal(got.status(), once, got.viaStore())
                        : got.viaStore() || isCode(body) ? null : NOT_CODE;
                if (trouble != null) {
                    mc.execute(() -> fail(trouble));
                    return;
                }
                Path file = site ? SITE_SAVED : Codespace.savedDir().resolve(NAME);
                if (!Files.isDirectory(file.getParent())) Files.createDirectories(file.getParent());
                Files.write(file, body);
                mc.execute(() -> {
                    busy = false;
                    if (showing()) finish(file);
                });
            } catch (Throwable e) {
                mc.execute(() -> fail(Net.reason(e, site ? VIA_STORE : DIRECT)));
            }
        }).start();
    }

    private static String refusal(int code, boolean once, boolean viaStore) {
        if (code == 429) return SLOW_DOWN;
        if (once) return ONCE;
        if (code == 404 || code == 410) return NOTHING;
        if (!viaStore) return "сайт по ссылке ответил " + code;
        return switch (code) {
            case 400 -> "эту ссылку не открыть";
            case 413 -> "код по ссылке слишком большой";
            case 422 -> NOT_CODE;
            case 503 -> "хранилище занято — попробуй ещё раз";
            case 504 -> "сайт по ссылке не ответил вовремя";
            default -> "сайт по ссылке не отдал код";
        };
    }

    private static byte[] withoutBom(byte[] body) {
        if (body == null) return new byte[0];
        boolean bom = body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB
                && (body[2] & 0xFF) == 0xBF;
        return bom ? Arrays.copyOfRange(body, 3, body.length) : body;
    }

    static boolean isCode(byte[] body) {
        try {
            JsonElement root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            return root.isJsonObject() && root.getAsJsonObject().has("handlers")
                    && root.getAsJsonObject().get("handlers").isJsonArray();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean showing() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        return mc.gui.screen() == this;
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
        if (download != null) download.cancel();
        download = null;
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(parent);
    }
}
