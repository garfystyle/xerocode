package com.xerocode.ui;

import com.xerocode.Codespace;
import com.xerocode.Importer;
import com.xerocode.XeroCode;
import com.xerocode.Script;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class ImportScreen extends DialogScreen {
    private enum Phase { ASK, RUNNING, DONE }

    private static final String YES = "Да", NO = "Нет", CANCEL = "Отмена", OPEN = "Открыть редактор";
    private static final String GO_ON = "Продолжить", AFRESH = "Сначала";

    private final Script script;
    private final List<BlockPos> lines;
    private final Codespace.Memo memo;
    private final boolean resumable;
    private Phase phase = Phase.ASK;
    private Codespace.Scan scan;
    private Importer.Result result;
    private String failure;
    private boolean cancelled;

    public ImportScreen(Script script, List<BlockPos> lines) {
        super(320);
        this.script = script;
        this.lines = lines;
        MinecraftClient mc = MinecraftClient.getInstance();
        this.memo = mc.world == null ? null : Codespace.Memo.read(Codespace.worldId(mc.world));
        this.resumable = memo != null && memo.fits(lines.size());
    }

    @Override
    protected int bodyH() {
        return switch (phase) {
            case ASK -> ROW * 3 + 10 + BTN_H;
            case RUNNING -> ROW + 8 + BAR_H + 6 + ROW + 12 + BTN_H;
            case DONE -> ROW * (3 + extraRows()) + 12 + BTN_H;
        };
    }

    private boolean broke() {
        return scan != null && scan.state == Codespace.State.FAILED;
    }

    private int extraRows() {
        int rows = 0;
        if (broke()) rows += 2;
        if (scan != null && !scan.skipList().isEmpty()) rows++;
        if (failure != null || (result != null && result.brokenLines > 0)) rows++;
        if (result != null && result.unknownCount > 0) rows++;
        return rows;
    }

    @Override
    protected String title() {
        return switch (phase) {
            case ASK -> "ЗАГРУЗКА КОДА";
            case RUNNING -> "ЧТЕНИЕ КОДА ИЗ МИРА";
            case DONE -> cancelled ? "ЧТЕНИЕ ОСТАНОВЛЕНО"
                    : broke() ? "ЧТЕНИЕ ПРЕРВАНО" : "КОД ЗАГРУЖЕН";
        };
    }

    @Override
    protected void drawBody(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w) {
        switch (phase) {
            case ASK -> drawAsk(ctx, mouseX, mouseY, x, y, w);
            case RUNNING -> drawRunning(ctx, mouseX, mouseY, x, y, w);
            case DONE -> drawDone(ctx, mouseX, mouseY, x, y, w);
        }
    }

    private void drawAsk(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (resumable) {
            Draw.textFit(ctx, textRenderer,
                    "Прошлое чтение оборвалось на строке " + (memo.next) + ".", x, y, w,
                    Theme.TEXT, false);
            Draw.textFit(ctx, textRenderer,
                    "Прочитано " + memo.done() + " из " + lines.size()
                            + (memo.skip.isEmpty() ? ""
                            : " · сервер не отдаёт строк: " + memo.skip.size()),
                    x, y + ROW, w, Theme.TEXT_DIM, false);
            Draw.textFit(ctx, textRenderer, "Продолжить с " + (memo.next + 1) + "-й строки?",
                    x, y + ROW * 2, w, Theme.TEXT, false);
            buttons(ctx, mouseX, mouseY, x, w, GO_ON, AFRESH);
            return;
        }
        Draw.textFit(ctx, textRenderer, "В редакторе нет ни одного блока.", x, y, w,
                Theme.TEXT, false);
        Draw.textFit(ctx, textRenderer,
                "В этом мире " + lineWord(lines.size()) + " кода.", x, y + ROW, w,
                Theme.TEXT_DIM, false);
        Draw.textFit(ctx, textRenderer, "Загрузить их на полотно?", x, y + ROW * 2, w,
                Theme.TEXT, false);
        buttons(ctx, mouseX, mouseY, x, w, YES, NO);
    }

    private void drawRunning(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w) {
        int done = scan == null ? 0 : scan.index();
        int total = scan == null ? lines.size() : scan.total();
        float progress = scan == null ? 0 : scan.progress();

        Draw.textFit(ctx, textRenderer, "Строка " + done + " из " + total, x, y,
                w - 34, Theme.TEXT, false);
        Draw.textRight(ctx, textRenderer, Math.round(progress * 100) + " %", x + w, y,
                Theme.ACCENT, false);

        int by = y + ROW + 8;
        barTrack(ctx, x, by, w);
        barFill(ctx, x, by, 0, Math.max(0, Math.min(w - 2, Math.round((w - 2) * progress))));

        String note = "блоков прочитано: " + (scan == null ? 0 : scan.blocks());
        float left = scan == null ? -1 : scan.remaining();
        if (left >= 0) note += "   ·   осталось ~" + Math.max(1, Math.round(left)) + " с";
        Draw.textFit(ctx, textRenderer, note, x, by + BAR_H + 6, w, Theme.TEXT_FAINT, false);

        buttons(ctx, mouseX, mouseY, x, w, null, CANCEL);
    }

    private void drawDone(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w) {
        int blocks = result == null ? 0 : result.blocks;
        int roots = result == null ? 0 : result.lines;
        Draw.textFit(ctx, textRenderer,
                broke() ? "Прочитано " + lineWord(scan.blocks()) + " — отложены до продолжения"
                        : lineWord(roots) + " · " + blocks + " блоков на полотне", x, y, w,
                Theme.TEXT, false);

        String file = scan == null || scan.file == null
                ? "файл сохранить не удалось" : "Сохранено: " + scan.file.getFileName();
        Draw.textFit(ctx, textRenderer, file, x, y + ROW, w, Theme.TEXT_DIM, false);

        String time = scan == null ? "" : "за " + String.format("%.1f", scan.millis / 1000f) + " с";
        int failed = scan == null ? 0 : scan.failedLines();
        if (failed > 0) time += "   ·   не прочитано строк: " + failed;
        Draw.textFit(ctx, textRenderer, time, x, y + ROW * 2, w, Theme.TEXT_FAINT, false);

        int row = 3;
        if (broke()) {
            Draw.textFit(ctx, textRenderer, "оборвалось: " + scan.error,
                    x, y + ROW * row++, w, Theme.DANGER, false);
            Draw.textFit(ctx, textRenderer,
                    "зайдите в /dev снова — редактор предложит дочитать остальное",
                    x, y + ROW * row++, w, Theme.TEXT_DIM, false);
        }
        if (scan != null && !scan.skipList().isEmpty())
            Draw.textFit(ctx, textRenderer, "сервер не отдаёт строк: " + scan.skipList().size()
                            + " (" + String.join("  ", scan.skipList()) + ")",
                    x, y + ROW * row++, w, Theme.DANGER, false);
        if (failure != null)
            Draw.textFit(ctx, textRenderer, "разбор не удался: " + failure,
                    x, y + ROW * row++, w, Theme.DANGER, false);
        else if (result != null && result.brokenLines > 0)
            Draw.textFit(ctx, textRenderer, "строк не разобралось: " + result.brokenLines,
                    x, y + ROW * row++, w, Theme.DANGER, false);
        if (result != null && result.unknownCount > 0)
            Draw.textFit(ctx, textRenderer,
                    "нет в каталоге: " + result.unknownCount + " ("
                            + String.join(", ", result.unknown) + ")",
                    x, y + ROW * row++, w, Theme.DANGER, false);
        buttons(ctx, mouseX, mouseY, x, w, OPEN, null);
    }

    private static String lineWord(int n) {
        return Ui.plural(n, "строка", "строки", "строк");
    }

    @Override
    protected boolean onClick(double mx, double my) {
        switch (phase) {
            case ASK -> {
                if (resumable) {
                    if (hitPrimary(mx, my, GO_ON)) { begin(true); return true; }
                    if (hitGhost(mx, my, GO_ON, AFRESH)) { begin(false); return true; }
                    return false;
                }
                if (hitPrimary(mx, my, YES)) { begin(false); return true; }
                if (hitGhost(mx, my, YES, NO)) { close(); return true; }
            }
            case RUNNING -> {
                if (hitGhost(mx, my, null, CANCEL)) { stop(); return true; }
            }
            case DONE -> {
                if (hitPrimary(mx, my, OPEN)) { close(); return true; }
            }
        }
        return false;
    }

    @Override
    protected boolean onEnter() {
        if (phase == Phase.ASK) { begin(resumable); return true; }
        if (phase == Phase.DONE) { close(); return true; }
        return false;
    }

    @Override
    protected void onEscape() {
        if (phase == Phase.RUNNING) stop();
        else close();
    }

    private void begin(boolean carryOn) {
        if (client == null || client.world == null) { close(); return; }
        scan = Codespace.start(client.world, lines, carryOn && resumable ? memo
                : Codespace.Memo.fresh(Codespace.worldId(client.world), memo));
        phase = Phase.RUNNING;
    }

    private void stop() {
        if (scan != null) scan.cancel();
        cancelled = true;
        apply();
    }

    @Override
    public void tick() {
        if (phase != Phase.RUNNING || scan == null) return;
        scan.tick();
        if (scan.state != Codespace.State.RUNNING) apply();
    }

    private void apply() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        if (scan == null) return;
        if (broke()) return;
        try {
            result = Importer.importInto(script, scan.handlers(), textRenderer);
            if (result.lines > 0) script.fitOnOpen = true;
            script.save();
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName();
            XeroCode.LOG.error("[xerocode] импорт не удался", e);
        }
    }

    @Override
    public void close() {
        if (scan != null && scan.state == Codespace.State.RUNNING) scan.cancel();
        MinecraftClient mc = client == null ? MinecraftClient.getInstance() : client;
        mc.setScreen(new EditorScreen(script));
    }
}
