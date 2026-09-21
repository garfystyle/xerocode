package com.xerocode.ui;

import com.xerocode.Codespace;
import com.xerocode.Importer;
import com.xerocode.Sync;
import com.xerocode.XeroCode;
import com.xerocode.Script;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public final class ImportScreen extends DialogScreen {
    public enum Mode { EMPTY, DIVERGED, RELOAD }

    private enum Phase { ASK, RUNNING, DONE }

    private static final String YES = "Да", NO = "Нет", CANCEL = "Отмена", OPEN = "Открыть редактор";
    private static final String GO_ON = "Продолжить", AFRESH = "Сначала";
    private static final String KEEP = "Оставить", BESIDE = "Рядом", RELOAD = "Перечитать";
    private static final int START_X = 40, BESIDE_STEP = 260;

    private final Script script;
    private final Mode mode;
    private final List<BlockPos> lines;
    private final Codespace.Memo memo;
    private final boolean resumable;
    private Phase phase = Phase.ASK;
    private Codespace.Scan scan;
    private Importer.Result result;
    private String failure;
    private boolean cancelled;
    private boolean replacing;
    private Path saved;

    public ImportScreen(Script script, List<BlockPos> lines) {
        this(script, lines, Mode.EMPTY);
    }

    public ImportScreen(Script script, List<BlockPos> lines, Mode mode) {
        super(mode == Mode.EMPTY ? 320 : 344);
        this.script = script;
        this.mode = mode;
        this.lines = lines;
        Minecraft mc = Minecraft.getInstance();
        this.memo = mc.level == null ? null : Codespace.Memo.read(Codespace.worldId(mc.level));
        this.resumable = mode == Mode.EMPTY && memo != null && memo.fits(lines.size());
    }

    @Override
    protected int bodyH() {
        return switch (phase) {
            case ASK -> (mode == Mode.EMPTY || resumable ? ROW * 3 : askH()) + 10 + BTN_H;
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
        if (saved != null) rows++;
        return rows;
    }

    @Override
    protected int accent() {
        return phase == Phase.ASK && mode == Mode.DIVERGED ? Theme.DANGER : Theme.ACCENT;
    }

    @Override
    protected String title() {
        return switch (phase) {
            case ASK -> mode == Mode.DIVERGED ? "МИР И ПОЛОТНО РАЗОШЛИСЬ" : "ЗАГРУЗКА КОДА";
            case RUNNING -> "ЧТЕНИЕ КОДА ИЗ МИРА";
            case DONE -> cancelled ? "ЧТЕНИЕ ОСТАНОВЛЕНО"
                    : broke() ? "ЧТЕНИЕ ПРЕРВАНО" : "КОД ЗАГРУЖЕН";
        };
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        switch (phase) {
            case ASK -> drawAsk(ctx, mouseX, mouseY, x, y, w);
            case RUNNING -> drawRunning(ctx, mouseX, mouseY, x, y, w);
            case DONE -> drawDone(ctx, mouseX, mouseY, x, y, w);
        }
    }

    private void drawAsk(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (resumable) {
            Draw.textFit(ctx, font,
                    "Прошлое чтение оборвалось на строке " + (memo.next) + ".", x, y, w,
                    Theme.TEXT, false);
            Draw.textFit(ctx, font,
                    "Прочитано " + memo.done() + " из " + lines.size()
                            + (memo.skip.isEmpty() ? ""
                            : " · сервер не отдаёт строк: " + memo.skip.size()),
                    x, y + ROW, w, Theme.TEXT_DIM, false);
            Draw.textFit(ctx, font, "Продолжить с " + (memo.next + 1) + "-й строки?",
                    x, y + ROW * 2, w, Theme.TEXT, false);
            buttons(ctx, mouseX, mouseY, x, w, GO_ON, AFRESH);
            return;
        }
        if (mode != Mode.EMPTY) {
            drawDivergedAsk(ctx, mouseX, mouseY, x, y, w);
            return;
        }
        Draw.textFit(ctx, font, "В редакторе нет ни одного блока.", x, y, w,
                Theme.TEXT, false);
        Draw.textFit(ctx, font,
                "В этом мире " + lineWord(lines.size()) + " кода.", x, y + ROW, w,
                Theme.TEXT_DIM, false);
        Draw.textFit(ctx, font, "Загрузить их на полотно?", x, y + ROW * 2, w,
                Theme.TEXT, false);
        buttons(ctx, mouseX, mouseY, x, w, YES, NO);
    }

    private String askWhat() {
        return mode == Mode.RELOAD ? "Полотно будет заменено кодом из мира."
                : "Код в мире менялся мимо редактора.";
    }

    private int askH() {
        return paragraphRows(askWhat(), bodyW()) * ROW + GAP + ROW;
    }

    private void drawDivergedAsk(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        int at = y + paragraph(ctx, askWhat(), x, y, w, Theme.TEXT) * ROW + GAP;
        Draw.textFit(ctx, font,
                "в мире " + lines.size() + "   ·   на полотне " + script.roots.size(),
                x, at, w, Theme.TEXT_DIM, false);
        if (mode == Mode.RELOAD) buttons(ctx, mouseX, mouseY, x, w, RELOAD, CANCEL);
        else rowButtons(ctx, mouseX, mouseY, x, w, Ui.ACCENT, KEEP, BESIDE, RELOAD);
    }

    private void drawRunning(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        int done = scan == null ? 0 : scan.index();
        int total = scan == null ? lines.size() : scan.total();
        float progress = scan == null ? 0 : scan.progress();

        Draw.textFit(ctx, font, "Строка " + done + " из " + total, x, y,
                w - 34, Theme.TEXT, false);
        Draw.textRight(ctx, font, Math.round(progress * 100) + " %", x + w, y,
                Theme.ACCENT, false);

        int by = y + ROW + 8;
        barTrack(ctx, x, by, w);
        barFill(ctx, x, by, 0, Math.max(0, Math.min(w - 2, Math.round((w - 2) * progress))));

        String note = "блоков прочитано: " + (scan == null ? 0 : scan.blocks());
        float left = scan == null ? -1 : scan.remaining();
        if (left >= 0) note += "   ·   осталось ~" + Math.max(1, Math.round(left)) + " с";
        Draw.textFit(ctx, font, note, x, by + BAR_H + 6, w, Theme.TEXT_FAINT, false);

        buttons(ctx, mouseX, mouseY, x, w, null, CANCEL);
    }

    private void drawDone(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        int blocks = result == null ? 0 : result.blocks;
        int roots = result == null ? 0 : result.lines;
        Draw.textFit(ctx, font,
                broke() ? "Прочитано " + lineWord(scan.blocks()) + " — отложены до продолжения"
                        : lineWord(roots) + " · " + blocks + " блоков на полотне", x, y, w,
                Theme.TEXT, false);

        String file = scan == null || scan.file == null
                ? "файл сохранить не удалось" : "Сохранено: " + scan.file.getFileName();
        Draw.textFit(ctx, font, file, x, y + ROW, w, Theme.TEXT_DIM, false);

        String time = scan == null ? "" : "за " + String.format("%.1f", scan.millis / 1000f) + " с";
        int failed = scan == null ? 0 : scan.failedLines();
        if (failed > 0) time += "   ·   не прочитано строк: " + failed;
        Draw.textFit(ctx, font, time, x, y + ROW * 2, w, Theme.TEXT_FAINT, false);

        int row = 3;
        if (saved != null)
            Draw.textFit(ctx, font, "прежнее полотно: " + saved.getFileName(),
                    x, y + ROW * row++, w, Theme.TEXT_FAINT, false);
        if (broke()) {
            Draw.textFit(ctx, font, "оборвалось: " + scan.error,
                    x, y + ROW * row++, w, Theme.DANGER, false);
            Draw.textFit(ctx, font,
                    "зайдите в /dev — предложу дочитать",
                    x, y + ROW * row++, w, Theme.TEXT_DIM, false);
        }
        if (scan != null && !scan.skipList().isEmpty())
            Draw.textFit(ctx, font, "сервер не отдаёт строк: " + scan.skipList().size()
                            + " (" + String.join("  ", scan.skipList()) + ")",
                    x, y + ROW * row++, w, Theme.DANGER, false);
        if (failure != null)
            Draw.textFit(ctx, font, "разбор не удался: " + failure,
                    x, y + ROW * row++, w, Theme.DANGER, false);
        else if (result != null && result.brokenLines > 0)
            Draw.textFit(ctx, font, "строк не разобралось: " + result.brokenLines,
                    x, y + ROW * row++, w, Theme.DANGER, false);
        if (result != null && result.unknownCount > 0)
            Draw.textFit(ctx, font,
                    "сохранены как есть, нет в каталоге: " + result.unknownCount
                            + " (" + String.join(", ", result.unknown) + ")",
                    x, y + ROW * row++, w, Theme.TEXT_DIM, false);
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
                    if (hitPrimary(mx, my, GO_ON)) { begin(true, false); return true; }
                    if (hitGhost(mx, my, GO_ON, AFRESH)) { begin(false, false); return true; }
                    return false;
                }
                if (mode == Mode.RELOAD) {
                    if (hitPrimary(mx, my, RELOAD)) { begin(false, true); return true; }
                    if (hitGhost(mx, my, RELOAD, CANCEL)) { onClose(); return true; }
                    return false;
                }
                if (mode == Mode.DIVERGED) {
                    int hit = hitRow(mx, my, bodyX(), bodyW(), KEEP, BESIDE, RELOAD);
                    if (hit == 0) { Sync.hush(script); onClose(); return true; }
                    if (hit == 1) { begin(false, false); return true; }
                    if (hit == 2) { begin(false, true); return true; }
                    return false;
                }
                if (hitPrimary(mx, my, YES)) { begin(false, false); return true; }
                if (hitGhost(mx, my, YES, NO)) { onClose(); return true; }
            }
            case RUNNING -> {
                if (hitGhost(mx, my, null, CANCEL)) { stop(); return true; }
            }
            case DONE -> {
                if (hitPrimary(mx, my, OPEN)) { onClose(); return true; }
            }
        }
        return false;
    }

    @Override
    protected boolean onEnter() {
        if (phase == Phase.ASK) {
            if (mode == Mode.DIVERGED) return false;
            begin(resumable, mode == Mode.RELOAD);
            return true;
        }
        if (phase == Phase.DONE) { onClose(); return true; }
        return false;
    }

    @Override
    protected void onEscape() {
        if (phase == Phase.RUNNING) stop();
        else {
            if (phase == Phase.ASK && mode == Mode.DIVERGED) Sync.hush(script);
            onClose();
        }
    }

    private void begin(boolean carryOn, boolean replace) {
        if (minecraft == null || minecraft.level == null) { onClose(); return; }
        replacing = replace;
        scan = Codespace.start(minecraft.level, lines, carryOn && resumable ? memo
                : Codespace.Memo.fresh(Codespace.worldId(minecraft.level), memo));
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
            int startX = START_X;
            boolean wasEmpty = script.roots.isEmpty();
            if (replacing) {
                saved = script.backup();
                script.roots.clear();
            } else if (!wasEmpty) {
                startX = (int) Math.round(script.lastRootX()) + BESIDE_STEP;
            }
            result = Importer.importInto(script, scan.handlers(), font, startX);
            if (result.lines > 0) script.fitOnOpen = true;
            script.save();
            if ((replacing || wasEmpty) && !cancelled && scan.state == Codespace.State.DONE
                    && scan.failedLines() == 0 && scan.skipList().isEmpty()
                    && result.brokenLines == 0)
                Sync.mark(script, minecraft == null ? null : minecraft.level);
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName();
            XeroCode.LOG.error("[xerocode] импорт не удался", e);
        }
    }

    @Override
    public void onClose() {
        if (scan != null && scan.state == Codespace.State.RUNNING) scan.cancel();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.gui.setScreen(new EditorScreen(script));
    }
}
