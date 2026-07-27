package com.xerocode.ui;

import com.xerocode.Codespace;
import com.xerocode.Exporter;
import com.xerocode.XeroCode;
import com.xerocode.Publish;
import com.xerocode.Script;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

public final class ExportScreen extends DialogScreen {
    private enum Phase { RUNNING, DONE }

    private static final int ANSWER_TICKS = 60;
    private static final String CANCEL = "Отмена";

    private final Script script;
    private final Screen parent;
    private final String exitTo;

    private Phase phase = Phase.RUNNING;
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
            Exporter.Report r = code.report();
            XeroCode.LOG.info("[xerocode] на отправку: {} строк, {} блоков, {} значений, {} маркеров, "
                            + "{} предметов; без события {}, нет на сервере {}, потеряно значений {}",
                    r.lines, r.blocks, r.values, r.markers, r.items, r.headless, r.unmapped,
                    r.lostValues);
            if (!r.problems.isEmpty())
                XeroCode.LOG.warn("[xerocode] не уедет: {}", String.join(", ", r.problems));
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName();
            XeroCode.LOG.error("[xerocode] сборка кода не удалась", e);
        }
        begin();
    }

    @Override
    protected int bodyH() {
        return phase == Phase.RUNNING
                ? ROW + 8 + BAR_H + 6 + ROW + 12 + BTN_H
                : ROW * doneLines().size() + 12 + BTN_H;
    }

    @Override
    protected int accent() { return failed() ? Theme.DANGER : Theme.ACCENT; }

    @Override
    protected String title() {
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
    protected void drawBody(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (phase == Phase.RUNNING) drawRunning(ctx, x, y, w);
        else drawDone(ctx, x, y, w);
        buttons(ctx, mouseX, mouseY, x, w, button(), null);
    }

    private void drawRunning(DrawContext ctx, int x, int y, int w) {
        Draw.textFit(ctx, textRenderer, "Загрузка кода…", x, y, w, Theme.TEXT, false);

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
        Draw.textFit(ctx, textRenderer, note, x, by + BAR_H + 6, w, Theme.TEXT_FAINT, false);
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
            lines.add("не уехало блоков: " + code.report().unmapped);
        lines.add(job == null || job.file == null
                ? "файлом сохранить не удалось" : "Файл: " + job.file.getFileName());
        return lines;
    }

    private void drawDone(DrawContext ctx, int x, int y, int w) {
        List<String> lines = doneLines();
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? Theme.TEXT
                    : i == 1 && failed() ? Theme.DANGER
                    : i == lines.size() - 1 ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
            Draw.textFit(ctx, textRenderer, lines.get(i), x, y + ROW * i, w, color, false);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
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
        if (phase == Phase.RUNNING) stop();
        else finish();
    }

    private void begin() {
        if (code == null) { phase = Phase.DONE; return; }
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        String world = mc.world == null ? "canvas" : Codespace.worldId(mc.world);
        if (!Codespace.inDev(mc.world))
            XeroCode.LOG.warn("[xerocode] отправка не из /dev — сервер такую команду не примет");
        job = Publish.start(code.json(), world, true);
    }

    private void stop() {
        if (job != null) job.cancel();
        close();
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
            if (job.state == Publish.State.SENT && parent instanceof EditorScreen editor)
                editor.markPublished();
            return;
        }
        if (listening <= 0 || job == null) return;
        listening--;
        List<String> said = Publish.answersSince(job.sentAt);
        if (said.size() != answers.size()) {
            answers = said;
            for (String line : said) XeroCode.LOG.info("[xerocode] сервер: {}", line);
        }
    }

    private void finish() {
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        if (exitTo == null) { mc.setScreen(parent); return; }
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(exitTo);
        XeroCode.canvasClosed();
        XeroCode.cover("build".equals(exitTo) ? "Режим строительства…" : "Запуск мира…", null);
    }

    @Override
    public void close() {
        if (job != null && job.state == Publish.State.UPLOADING) job.cancel();
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        mc.setScreen(parent);
    }
}
