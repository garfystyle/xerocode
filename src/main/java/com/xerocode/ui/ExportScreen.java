package com.xerocode.ui;

import com.xerocode.Codespace;
import com.xerocode.Exporter;
import com.xerocode.Sync;
import com.xerocode.XeroCode;
import com.xerocode.Publish;
import com.xerocode.Script;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

public final class ExportScreen extends DialogScreen {
    private enum Phase { CONFIRM, RUNNING, DONE }

    private static final int ANSWER_TICKS = 60;
    private static final String CANCEL = "Отмена";
    private static final String SEND = "Отправить", RELOAD = "Перечитать мир";

    private final Script script;
    private final Screen parent;
    private final String exitTo;

    private Phase phase = Phase.RUNNING;
    private Sync.State sync = Sync.State.UNKNOWN;
    private int worldLines = -1;
    private Exporter.Result code;
    private Publish.Job job;
    private String failure;
    private int spin;
    private int listening;
    private List<String> answers = List.of();

    public ExportScreen(Script script, Screen parent, String exitTo) {
        super(344);
        this.script = script;
        this.parent = parent;
        this.exitTo = exitTo;
    }

    @Override
    protected void init() {
        if (code != null || failure != null) return;
        try {
            code = Exporter.export(script);
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName();
            XeroCode.LOG.error("[xerocode] сборка кода не удалась", e);
        }
        Minecraft mc = mc();
        sync = Sync.state(script, mc.level);
        worldLines = Sync.worldLines(mc.level);
        if (risky()) { phase = Phase.CONFIRM; return; }
        begin();
    }

    private boolean risky() {
        if (code == null) return false;
        int canvas = code.report().lines;
        if (worldLines > 0 && canvas == 0) return true;
        if (sync.risky()) return true;
        return worldLines > canvas;
    }

    private int canvasLines() { return code == null ? 0 : code.report().lines; }

    private String confirmWhat() {
        if (canvasLines() == 0) return "Полотно пусто — код мира будет стёрт.";
        if (sync == Sync.State.DIVERGED) return "Мир и полотно разошлись.";
        if (sync == Sync.State.WORLD_AHEAD) return "Код в мире менялся мимо редактора.";
        return "В мире строк больше, чем на полотне.";
    }

    private String confirmCounts() {
        int canvas = canvasLines();
        String counts = "в мире " + (worldLines < 0 ? "?" : String.valueOf(worldLines))
                + "   ·   на полотне " + canvas;
        return worldLines > canvas ? counts + "   ·   исчезнет " + (worldLines - canvas) : counts;
    }

    private int confirmH() {
        return paragraphRows(confirmWhat(), bodyW()) * ROW + GAP + ROW;
    }

    private void drawConfirm(GuiGraphics ctx, int mouseX, int mouseY, int x, int y, int w) {
        int at = y + paragraph(ctx, confirmWhat(), x, y, w, Theme.TEXT) * ROW + GAP;
        Draw.textFit(ctx, font, confirmCounts(), x, at, w,
                worldLines > canvasLines() ? Theme.DANGER : Theme.TEXT_DIM, false);
        rowButtons(ctx, mouseX, mouseY, x, w, new int[]{Ui.GHOST, Ui.ACCENT, Ui.DANGER},
                CANCEL, RELOAD, SEND);
    }

    private Minecraft mc() { return minecraft == null ? Minecraft.getInstance() : minecraft; }

    private void reread() {
        Minecraft mc = mc();
        if (mc.level == null) { onClose(); return; }
        List<BlockPos> lines = Codespace.lines(mc.level);
        if (lines.isEmpty()) { onClose(); return; }
        mc.setScreen(new ImportScreen(script, lines, ImportScreen.Mode.RELOAD));
    }

    @Override
    protected int bodyH() {
        return switch (phase) {
            case CONFIRM -> confirmH() + 12 + BTN_H;
            case RUNNING -> ROW + 8 + BAR_H + 6 + ROW + 12 + BTN_H;
            case DONE -> ROW * doneLines().size() + 12 + BTN_H;
        };
    }

    @Override
    protected int accent() {
        return failed() || phase == Phase.CONFIRM ? Theme.DANGER : Theme.ACCENT;
    }

    @Override
    protected String title() {
        if (phase == Phase.CONFIRM) return "ОТПРАВКА СОТРЁТ КОД МИРА";
        if (phase == Phase.RUNNING) return "ОТПРАВКА КОДА";
        return failed() ? "НЕ ОТПРАВИЛОСЬ" : "КОД ОТПРАВЛЕН";
    }

    private boolean failed() {
        return failure != null || (job != null && job.state == Publish.State.FAILED);
    }

    private String button() {
        return phase == Phase.RUNNING ? CANCEL : exitTo == null ? "Готово" : "Выйти";
    }

    @Override
    protected void drawBody(GuiGraphics ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (phase == Phase.CONFIRM) { drawConfirm(ctx, mouseX, mouseY, x, y, w); return; }
        if (phase == Phase.RUNNING) drawRunning(ctx, x, y, w);
        else drawDone(ctx, x, y, w);
        buttons(ctx, mouseX, mouseY, x, w, button(), null);
    }

    private void drawRunning(GuiGraphics ctx, int x, int y, int w) {
        Draw.textFit(ctx, font, "Загрузка кода…", x, y, w, Theme.TEXT, false);

        int by = y + ROW + 8;
        barTrack(ctx, x, by, w);
        int run = Math.max(40, w / 4);
        int pos = (int) ((spin * 3L) % (w + run)) - run;
        barFill(ctx, x, by, Math.max(0, pos), Math.min(w - 2, pos + run));

        String note = code == null ? ""
                : code.report().lines == 0 ? "полотно пусто — код мира будет очищен"
                : Ui.plural(code.report().lines, "строка", "строки", "строк")
                        + " · " + code.report().blocks + " блоков";
        if (job != null && code != null && code.report().lines > 0)
            note += "   ·   " + job.bytes() / 1024 + " КБ";
        Draw.textFit(ctx, font, note, x, by + BAR_H + 6, w, Theme.TEXT_FAINT, false);
    }

    private List<String> doneLines() {
        List<String> lines = new ArrayList<>();
        if (failed()) {
            lines.add("Сервер кода не получил.");
            lines.add(job == null ? String.valueOf(failure) : job.error);
        } else {
            lines.add(code != null && code.report().lines == 0
                    ? "Отправлено пустое — сервер чистит код мира."
                    : "Код отправлен — сервер раскладывает его блоками.");
            for (String answer : answers) {
                if (lines.size() >= 4) break;
                lines.add(answer);
            }
            if (answers.isEmpty())
                lines.add(listening > 0 ? "Ждём ответа сервера…" : "Сервер ничего не ответил.");
        }
        if (code != null && code.report().unmapped > 0)
            lines.add("не уехало блоков: " + code.report().unmapped
                    + " (" + String.join(", ", code.report().problems) + ")");
        lines.add(job == null || job.file == null
                ? "файлом сохранить не удалось" : "Файл: " + job.file.getFileName());
        return lines;
    }

    private void drawDone(GuiGraphics ctx, int x, int y, int w) {
        List<String> lines = doneLines();
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? Theme.TEXT
                    : i == 1 && failed() ? Theme.DANGER
                    : i == lines.size() - 1 ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
            Draw.textFit(ctx, font, lines.get(i), x, y + ROW * i, w, color, false);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        if (phase == Phase.CONFIRM) {
            int hit = hitRow(mx, my, bodyX(), bodyW(), CANCEL, RELOAD, SEND);
            if (hit == 0) { onClose(); return true; }
            if (hit == 1) { reread(); return true; }
            if (hit == 2) { phase = Phase.RUNNING; begin(); return true; }
            return false;
        }
        if (!hitPrimary(mx, my, button())) return false;
        if (phase == Phase.RUNNING) stop();
        else finish();
        return true;
    }

    @Override
    protected boolean onEnter() {
        if (phase != Phase.DONE) return false;
        finish();
        return true;
    }

    @Override
    protected void onEscape() {
        if (phase == Phase.CONFIRM) { onClose(); return; }
        if (phase == Phase.RUNNING) stop();
        else finish();
    }

    private void begin() {
        if (code == null) { phase = Phase.DONE; return; }
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        String world = mc.level == null ? "canvas" : Codespace.worldId(mc.level);
        job = Publish.start(code.json(), world, true);
    }

    private void stop() {
        if (job != null) job.cancel();
        onClose();
    }

    @Override
    public void tick() {
        spin++;
        if (phase == Phase.RUNNING) {
            if (job == null) { phase = Phase.DONE; return; }
            job.tick();
            if (job.state == Publish.State.UPLOADING) return;
            phase = Phase.DONE;
            listening = job.state == Publish.State.SENT ? ANSWER_TICKS : 0;
            if (job.state == Publish.State.SENT) {
                Sync.published(script);
                if (parent instanceof EditorScreen editor) editor.markPublished();
            }
            return;
        }
        if (listening <= 0 || job == null) return;
        listening--;
        List<String> said = Publish.answersSince(job.sentAt);
        if (said.size() != answers.size()) answers = said;
    }

    private void finish() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        if (exitTo == null) { mc.setScreen(parent); return; }
        XeroCode.canvasClosed();
        if (XeroCode.RESTART.equals(exitTo)) {
            XeroCode.restart();
            return;
        }
        if (mc.getConnection() != null) mc.getConnection().sendCommand(exitTo);
        XeroCode.cover("build".equals(exitTo) ? "Режим строительства…" : "Запуск мира…", null);
    }

    @Override
    public void onClose() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.setScreen(parent);
    }
}
