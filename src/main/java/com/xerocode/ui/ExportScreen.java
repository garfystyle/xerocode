package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Codespace;
import com.xerocode.Env;
import com.xerocode.Exporter;
import com.xerocode.Sync;
import com.xerocode.XeroCode;
import com.xerocode.Publish;
import com.xerocode.Script;
import com.xerocode.Tabs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

public final class ExportScreen extends DialogScreen {
    private enum Phase { CHOOSE, CONFIRM, RUNNING, DONE }

    private static final int ANSWER_TICKS = 1200;
    private static final String CANCEL = "Отмена";
    private static final String SEND = "Отправить", RELOAD = "Перечитать мир", ALL = "Все полотна";
    private static final int CHOOSE_ROWS = 6, NAME_ON_BUTTON = 14;

    private final Script script;
    private final Screen parent;
    private final String exitTo;
    private final Exporter.Result ready;
    private final boolean replacing;

    private Phase phase = Phase.RUNNING;
    private Sync.State sync = Sync.State.UNKNOWN;
    private int worldLines = -1;
    private Exporter.Result code;
    private Publish.Job job;
    private String failure;
    private int spin;
    private int listening;
    private long heardFrom;
    private List<String> answers = List.of();
    private boolean copied;
    private List<Script> filled = List.of();
    private int[] counts = {};
    private int twins;
    private boolean whole = true;

    private static final String COPY = "Копировать", COPIED = "Скопировано", DONE = "Готово";

    private boolean chatStep() {
        return Env.browser() && phase == Phase.DONE && !failed() && job != null && !job.url.isEmpty();
    }

    private String command() { return (replacing ? "/module load-url force " : "/module load-url ") + job.url; }

    public ExportScreen(Script script, Screen parent, String exitTo) {
        super(344);
        this.script = script;
        this.parent = parent;
        this.exitTo = exitTo;
        this.ready = null;
        this.replacing = true;
    }

    public ExportScreen(JsonObject json, int lines, boolean replace, Screen parent) {
        super(344);
        this.script = null;
        this.parent = parent;
        this.exitTo = null;
        Exporter.Report report = new Exporter.Report();
        report.lines = lines;
        report.blocks = Exporter.blocks(json);
        this.ready = new Exporter.Result(json, report);
        this.replacing = replace;
    }

    @Override
    protected void init() {
        if (code != null || failure != null || phase == Phase.CHOOSE) return;
        filled = script == null ? List.of() : Tabs.filled(script);
        if (filled.size() < 2) {
            send(filled);
            return;
        }
        counts = new int[filled.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = Exporter.positions(filled.get(i)).size();
        twins = Tabs.twins(filled);
        phase = Phase.CHOOSE;
    }

    private void send(List<Script> sending) {
        whole = sending.size() == filled.size();
        phase = Phase.RUNNING;
        try {
            code = ready != null ? ready : Exporter.forWorld(sending.isEmpty() ? List.of(script) : sending);
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName();
            XeroCode.LOG.error("[xerocode] сборка кода не удалась", e);
        }
        Minecraft mc = mc();
        if (script != null) sync = Sync.state(script, mc.level);
        worldLines = Sync.worldLines(mc.level);
        if (replacing && risky()) { phase = Phase.CONFIRM; return; }
        begin();
    }

    private String[] chooseLabels() {
        if (!filled.contains(script)) return new String[]{CANCEL, ALL};
        String name = Tabs.name(script);
        if (name.length() > NAME_ON_BUTTON) name = name.substring(0, NAME_ON_BUTTON - 1) + "…";
        return new String[]{CANCEL, "Только «" + name + "»", ALL};
    }

    private String twinsNote() {
        return "На разных полотнах одинаковые строки: " + twins + " — в мире они задвоятся.";
    }

    private int chooseRows() { return Math.min(filled.size(), CHOOSE_ROWS + 1); }

    private int chooseH() {
        return chooseRows() * ROW + (twins > 0 ? GAP + paragraphRows(twinsNote(), bodyW()) * ROW : 0);
    }

    private void drawChoose(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        int rows = chooseRows(), named = filled.size() > rows ? rows - 1 : rows;
        for (int i = 0; i < named; i++) {
            String count = Ui.plural(counts[i], "строка", "строки", "строк");
            int cw = font.width(count), at = y + ROW * i;
            Draw.textFit(ctx, font, Tabs.name(filled.get(i)), x, at, w - cw - 8,
                    filled.get(i) == script ? Theme.TEXT : Theme.TEXT_DIM, false);
            Draw.textFit(ctx, font, count, x + w - cw, at, cw, Theme.TEXT_FAINT, false);
        }
        if (named < rows)
            Draw.textFit(ctx, font, "и ещё полотен: " + (filled.size() - named), x, y + ROW * named, w,
                    Theme.TEXT_FAINT, false);
        if (twins > 0) paragraph(ctx, twinsNote(), x, y + rows * ROW + GAP, w, Theme.WARN);
        rowButtons(ctx, mouseX, mouseY, x, w, Ui.ACCENT, chooseLabels());
    }

    private String where() { return whole && filled.size() > 1 ? "на полотнах" : "на полотне"; }

    private boolean risky() {
        if (code == null) return false;
        int canvas = code.report().lines;
        if (worldLines > 0 && canvas == 0) return true;
        if (sync.risky()) return true;
        return worldLines > canvas;
    }

    private int canvasLines() { return code == null ? 0 : code.report().lines; }

    private String confirmWhat() {
        if (canvasLines() == 0) return ready != null ? "Код пуст — код мира будет стёрт." : "Полотно пусто — код мира будет стёрт.";
        if (sync == Sync.State.DIVERGED) return "Мир и полотно разошлись.";
        if (sync == Sync.State.WORLD_AHEAD) return "Код в мире менялся мимо редактора.";
        return ready != null ? "В мире строк больше, чем в коде." : "В мире строк больше, чем " + where() + ".";
    }

    private String confirmCounts() {
        int canvas = canvasLines();
        String counts = "в мире " + (worldLines < 0 ? "?" : String.valueOf(worldLines))
                + (ready != null ? "   ·   в коде " : "   ·   " + where() + " ") + canvas;
        return worldLines > canvas ? counts + "   ·   исчезнет " + (worldLines - canvas) : counts;
    }

    private int confirmH() {
        return paragraphRows(confirmWhat(), bodyW()) * ROW + GAP + ROW;
    }

    private void drawConfirm(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        int at = y + paragraph(ctx, confirmWhat(), x, y, w, Theme.TEXT) * ROW + GAP;
        Draw.textFit(ctx, font, confirmCounts(), x, at, w,
                worldLines > canvasLines() ? Theme.DANGER : Theme.TEXT_DIM, false);
        rowButtons(ctx, mouseX, mouseY, x, w, new int[]{Ui.GHOST, Ui.ACCENT, Ui.DANGER},
                CANCEL, RELOAD, SEND);
    }

    private Minecraft mc() { return minecraft == null ? Minecraft.getInstance() : minecraft; }

    private void reread() {
        Minecraft mc = mc();
        if (script == null || mc.level == null) { onClose(); return; }
        List<BlockPos> lines = Codespace.lines(mc.level);
        if (lines.isEmpty()) { onClose(); return; }
        mc.gui.setScreen(new ImportScreen(script, lines, ImportScreen.Mode.RELOAD));
    }

    @Override
    protected int bodyH() {
        return switch (phase) {
            case CHOOSE -> chooseH() + 12 + BTN_H;
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
        if (phase == Phase.CHOOSE) return "ЧТО ОТПРАВИТЬ";
        if (phase == Phase.CONFIRM) return "ОТПРАВКА СОТРЁТ КОД МИРА";
        if (phase == Phase.RUNNING) return "ОТПРАВКА КОДА";
        if (chatStep()) return "КОД ГОТОВ";
        return failed() ? "НЕ ОТПРАВИЛОСЬ" : "КОД ОТПРАВЛЕН";
    }

    private boolean failed() {
        return failure != null || (job != null && job.state == Publish.State.FAILED);
    }

    private String button() {
        if (chatStep()) return copied ? COPIED : COPY;
        return phase == Phase.RUNNING ? CANCEL : exitTo == null ? "Готово" : XeroCode.CLOSE.equals(exitTo) ? "Закрыть" : "Выйти";
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (phase == Phase.CHOOSE) { drawChoose(ctx, mouseX, mouseY, x, y, w); return; }
        if (phase == Phase.CONFIRM) { drawConfirm(ctx, mouseX, mouseY, x, y, w); return; }
        if (phase == Phase.RUNNING) drawRunning(ctx, x, y, w);
        else drawDone(ctx, x, y, w);
        buttons(ctx, mouseX, mouseY, x, w, button(), chatStep() ? DONE : null);
    }

    private void drawRunning(GuiGraphicsExtractor ctx, int x, int y, int w) {
        Draw.textFit(ctx, font, "Загрузка кода…", x, y, w, Theme.TEXT, false);

        int by = y + ROW + 8;
        barTrack(ctx, x, by, w);
        int run = Math.max(40, w / 4);
        int pos = (int) ((spin * 3L) % (w + run)) - run;
        barFill(ctx, x, by, Math.max(0, pos), Math.min(w - 2, pos + run));

        String note = code == null ? ""
                : code.report().lines == 0 ? "полотно пусто — код мира будет очищен"
                : Ui.plural(code.report().lines, "строка", "строки", "строк")
                        + " · " + Ui.plural(code.report().blocks, "блок", "блока", "блоков");
        if (job != null && code != null && code.report().lines > 0)
            note += "   ·   " + job.bytes() / 1024 + " КБ";
        Draw.textFit(ctx, font, note, x, by + BAR_H + 6, w, Theme.TEXT_FAINT, false);
    }

    private List<String> doneLines() {
        List<String> lines = new ArrayList<>();
        if (chatStep()) {
            lines.add("Вставь в чат JustMC, стоя в /dev:");
            lines.add("");
            lines.add("");
            lines.add("Ссылка одноразовая и живёт минуту.");
            if (code != null && code.report().unmapped > 0)
                lines.add("не уехало блоков: " + code.report().unmapped
                        + " (" + String.join(", ", code.report().problems) + ")");
            return lines;
        }
        if (failed()) {
            lines.add(job != null && !job.note.isEmpty() ? job.note : "Сервер кода не получил.");
            lines.add(job == null ? String.valueOf(failure) : job.error);
        } else {
            lines.add(code != null && code.report().lines == 0
                    ? "Отправлено пустое — сервер чистит код мира."
                    : "Код отправлен — сервер раскладывает его блоками.");
            if (job != null && !job.note.isEmpty()) lines.add(job.note);
            if (job != null && !job.asked.isEmpty()) lines.add("Сервер переспросил, подтверждено: " + job.asked);
            int room = Math.max(0, 4 - lines.size());
            for (int i = Math.max(0, answers.size() - room); i < answers.size(); i++) lines.add(answers.get(i));
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

    private void drawDone(GuiGraphicsExtractor ctx, int x, int y, int w) {
        List<String> lines = doneLines();
        if (chatStep()) {
            Draw.textFit(ctx, font, lines.get(0), x, y, w, Theme.TEXT, false);
            Ui.input(ctx, x, y + ROW + 2, w, ROW * 2 - 4, copied);
            Draw.textFit(ctx, font, command(), x + 6, y + ROW + 2 + (ROW * 2 - 4 - 8) / 2, w - 12, Theme.TEXT, false);
            for (int i = 3; i < lines.size(); i++)
                Draw.textFit(ctx, font, lines.get(i), x, y + ROW * i, w, Theme.TEXT_FAINT, false);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? Theme.TEXT
                    : i == 1 && failed() ? Theme.DANGER
                    : i == lines.size() - 1 ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
            Draw.textFit(ctx, font, lines.get(i), x, y + ROW * i, w, color, false);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        if (phase == Phase.CHOOSE) {
            String[] labels = chooseLabels();
            int hit = hitRow(mx, my, bodyX(), bodyW(), labels);
            if (hit == 0) { onClose(); return true; }
            if (hit == labels.length - 1) { send(filled); return true; }
            if (hit > 0) { send(List.of(script)); return true; }
            return false;
        }
        if (phase == Phase.CONFIRM) {
            int hit = hitRow(mx, my, bodyX(), bodyW(), CANCEL, RELOAD, SEND);
            if (hit == 0) { onClose(); return true; }
            if (hit == 1) { reread(); return true; }
            if (hit == 2) { phase = Phase.RUNNING; begin(); return true; }
            return false;
        }
        if (chatStep()) {
            if (hitPrimary(mx, my, button())) {
                Minecraft.getInstance().keyboardHandler.setClipboard(command());
                copied = true;
                return true;
            }
            if (hitGhost(mx, my, button(), DONE)) { finish(); return true; }
            return false;
        }
        if (!hitPrimary(mx, my, button())) return false;
        if (phase == Phase.RUNNING) stop();
        else finish();
        return true;
    }

    @Override
    protected boolean onEnter() {
        if (phase == Phase.CHOOSE) { send(filled); return true; }
        if (phase != Phase.DONE) return false;
        finish();
        return true;
    }

    @Override
    protected void onEscape() {
        if (phase == Phase.CHOOSE || phase == Phase.CONFIRM) { onClose(); return; }
        if (phase == Phase.RUNNING) stop();
        else finish();
    }

    private void begin() {
        if (code == null) { phase = Phase.DONE; return; }
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        String world = mc.level == null ? "canvas" : Codespace.worldId(mc.level);
        job = Publish.start(code.json(), world, replacing);
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
            heardFrom = job.sentAt;
            if (job.state == Publish.State.SENT) {
                if (script != null) Sync.published(script, whole);
                if (whole && script != null) Tabs.sent();
                if (parent instanceof EditorScreen editor) editor.markPublished();
            }
            return;
        }
        if (job == null) return;
        if (job.sentAt != heardFrom) {
            heardFrom = job.sentAt;
            listening = ANSWER_TICKS;
        }
        if (listening <= 0) return;
        listening--;
        List<String> said = Publish.answersSince(job.firstSentAt);
        if (said.size() != answers.size()) answers = said;
    }

    private void finish() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        if (exitTo == null) { mc.gui.setScreen(parent); return; }
        if (XeroCode.CLOSE.equals(exitTo)) {
            XeroCode.canvasShut();
            mc.gui.setScreen(null);
            return;
        }
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
        mc.gui.setScreen(parent);
    }
}
