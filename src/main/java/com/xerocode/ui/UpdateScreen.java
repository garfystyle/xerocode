package com.xerocode.ui;

import com.xerocode.Release;
import com.xerocode.Swap;
import com.xerocode.XeroCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

public final class UpdateScreen extends DialogScreen {
    private static final int IN = 6;
    private static final int BAR_ROOM = 5;
    private static final int PARA = 4;
    private static final int SECTION = 10;
    private static final int MIN_ROWS = 3;

    private static final String LATER = "Позже", UPDATE = "Обновить", CANCEL = "Отмена",
            CLOSE = "Закрыть", PAGE = "Страница", AGAIN = "Ещё раз", DONE = "Готово";

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s*(.*?)\\s*#*$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3})[.)]\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+•]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern RULE = Pattern.compile("^([-*_]\\s*){3,}$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]*)[^)]*\\)");
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern URL = Pattern.compile("https?://([^/\\s]+)(/\\S*)?");

    private record Row(FormattedCharSequence text, String mark, int x, int y) {}

    private final Release.News news;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int laidW = -1;
    private int contentH;
    private int scroll;

    public UpdateScreen(Release.News news, Screen parent) {
        super(380);
        this.news = news;
        this.parent = parent;
        Swap.reset();
    }

    @Override
    protected String title() { return "Вышел XeroCode " + news.version(); }

    private String sub() {
        String have = "установлена " + Release.current();
        if (news.entries().size() > 1) return have + " · новых версий: " + news.entries().size();
        String day = Release.day(news.newest().date(), LocalDate.now().getYear());
        return day.isEmpty() ? have : have + " · новая вышла " + day;
    }

    private int indent() { return font.width("00.") + 4; }

    private int textW() { return bodyW() - IN * 2 - BAR_ROOM; }

    private void layout() {
        int w = textW();
        if (w == laidW) return;
        laidW = w;
        rows.clear();
        int y = 0;
        List<Release.Entry> entries = news.entries();
        boolean many = entries.size() > 1;
        int year = LocalDate.now().getYear();
        for (int i = 0; i < entries.size(); i++) {
            Release.Entry e = entries.get(i);
            if (many) {
                if (i > 0) y += SECTION;
                String day = Release.day(e.date(), year);
                MutableComponent head = Component.literal(e.version())
                        .withStyle(Style.EMPTY.withBold(true).withColor(Theme.ACCENT));
                if (!day.isEmpty()) head.append(Component.literal("  " + day)
                        .withStyle(Style.EMPTY.withBold(false).withColor(Theme.TEXT_FAINT)));
                y = wrap(head, null, 0, w, y);
                y += 2;
            }
            y = notes(e.notes(), w, y);
        }
        contentH = y;
    }

    private int notes(String body, int w, int y) {
        String[] lines = (body == null ? "" : body).replace("\r\n", "\n").replace('\r', '\n').split("\n");
        boolean gap = false, any = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || RULE.matcher(line).matches()) {
                gap = any;
                continue;
            }
            if (redundant(line)) continue;
            if (gap) y += PARA;
            gap = false;
            any = true;
            int depth = Math.min(2, (raw.length() - raw.stripLeading().length()) / 2);
            int x = depth * indent();
            Matcher m;
            if ((m = HEADING.matcher(line)).matches()) {
                y = wrap(inline(m.group(1), Theme.TEXT).withStyle(s -> s.withBold(true)), null, x, w, y);
            } else if ((m = NUMBERED.matcher(line)).matches()) {
                y = wrap(inline(m.group(2), Theme.TEXT), m.group(1) + ".", x + indent(), w, y);
            } else if ((m = BULLET.matcher(line)).matches()) {
                y = wrap(inline(m.group(1), Theme.TEXT), "•", x + indent(), w, y);
            } else if ((m = QUOTE.matcher(line)).matches()) {
                y = wrap(inline(m.group(1), Theme.TEXT_DIM), null, x + indent(), w, y);
            } else {
                y = wrap(inline(line, Theme.TEXT), null, x, w, y);
            }
        }
        if (!any) y = wrap(Component.literal("без описания")
                .withStyle(Style.EMPTY.withColor(Theme.TEXT_FAINT)), null, 0, w, y);
        return y;
    }

    private static boolean redundant(String line) {
        String bare = line.replaceAll("[#*_:!.\\s]", "").toLowerCase(Locale.ROOT);
        return bare.equals("чтонового") || bare.equals("whatsnew") || bare.equals("what'snew")
                || bare.equals("changelog");
    }

    private int wrap(Component text, String mark, int x, int w, int y) {
        List<FormattedCharSequence> parts = font.split(text, Math.max(20, w - x));
        for (int i = 0; i < parts.size(); i++) {
            rows.add(new Row(parts.get(i), i == 0 ? mark : null, x, y));
            y += ROW;
        }
        return y;
    }

    private static MutableComponent inline(String s, int color) {
        MutableComponent out = Component.literal("");
        StringBuilder buf = new StringBuilder();
        boolean bold = false, italic = false, strike = false;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && !Character.isLetterOrDigit(s.charAt(i + 1))) {
                buf.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (s.startsWith("**", i) || s.startsWith("__", i)) {
                flush(out, buf, color, bold, italic, strike);
                bold = !bold;
                i += 2;
                continue;
            }
            if (s.startsWith("~~", i)) {
                flush(out, buf, color, bold, italic, strike);
                strike = !strike;
                i += 2;
                continue;
            }
            if (c == '*' && (italic || (i + 1 < s.length() && s.charAt(i + 1) != ' '
                    && s.indexOf('*', i + 1) > i + 1))) {
                flush(out, buf, color, bold, italic, strike);
                italic = !italic;
                i++;
                continue;
            }
            if (c == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    flush(out, buf, color, bold, italic, strike);
                    out.append(Component.literal(s.substring(i + 1, end))
                            .withStyle(Style.EMPTY.withColor(Theme.ACCENT)));
                    i = end + 1;
                    continue;
                }
            }
            Matcher link = LINK.matcher(s);
            if (c == '!' && link.find(i + 1) && link.start() == i + 1) {
                i = link.end();
                continue;
            }
            if (c == '[' && link.find(i) && link.start() == i) {
                flush(out, buf, color, bold, italic, strike);
                String label = link.group(1).isBlank() ? shortUrl(link.group(2)) : link.group(1);
                out.append(Component.literal(label).withStyle(Style.EMPTY.withColor(Theme.ACCENT)));
                i = link.end();
                continue;
            }
            Matcher url = URL.matcher(s);
            if ((c == 'h' || c == 'H') && url.find(i) && url.start() == i) {
                flush(out, buf, color, bold, italic, strike);
                out.append(Component.literal(shortUrl(url.group()))
                        .withStyle(Style.EMPTY.withColor(Theme.ACCENT)));
                i = url.end();
                continue;
            }
            Matcher tag = TAG.matcher(s);
            if (c == '<' && tag.find(i) && tag.start() == i) {
                buf.append(' ');
                i = tag.end();
                continue;
            }
            buf.append(c);
            i++;
        }
        flush(out, buf, color, bold, italic, strike);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, int color,
                              boolean bold, boolean italic, boolean strike) {
        if (buf.isEmpty()) return;
        out.append(Component.literal(buf.toString()).withStyle(Style.EMPTY.withColor(color)
                .withBold(bold ? true : null).withItalic(italic ? true : null)
                .withStrikethrough(strike ? true : null)));
        buf.setLength(0);
    }

    private static String shortUrl(String url) {
        Matcher m = URL.matcher(url);
        if (!m.matches()) return url;
        String host = m.group(1).startsWith("www.") ? m.group(1).substring(4) : m.group(1);
        String path = m.group(2);
        return path == null || path.equals("/") ? host : host + "/…";
    }

    private String[] labels() {
        return switch (Swap.phase()) {
            case IDLE -> new String[]{LATER, UPDATE};
            case LOADING -> new String[]{CANCEL};
            case FAILED -> new String[]{CLOSE, PAGE, AGAIN};
            case RESTART -> new String[]{DONE};
        };
    }

    private String status() {
        return switch (Swap.phase()) {
            case IDLE -> "";
            case LOADING -> loaded();
            case FAILED -> "Не вышло: " + Swap.said();
            case RESTART -> Swap.said();
        };
    }

    private int statusColor() {
        return switch (Swap.phase()) {
            case FAILED -> Theme.DANGER;
            case RESTART -> Theme.OK;
            default -> Theme.TEXT_DIM;
        };
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0).replace('.', ',');
    }

    private static String loaded() {
        long got = Swap.got(), total = Swap.total();
        return total > 0 ? "Скачивается: " + mb(got) + " из " + mb(total) + " МБ"
                : "Скачивается: " + mb(got) + " МБ";
    }

    private int statusRows() {
        String s = status();
        return s.isEmpty() ? 0 : paragraphRows(s, bodyW());
    }

    private int footerH() {
        int h = BTN_H;
        int rows = statusRows();
        if (rows > 0) h += rows * ROW + GAP;
        if (Swap.phase() == Swap.Phase.LOADING) h += BAR_H + GAP;
        return h;
    }

    private int viewH() {
        layout();
        int want = contentH + IN * 2;
        int room = height - Ui.margin(height) * 2 - HEAD_H - PAD * 2 - ROW - GAP * 2 - footerH();
        int least = MIN_ROWS * ROW + IN * 2;
        return Math.max(least, Math.min(want, room));
    }

    private int maxScroll() { return Math.max(0, contentH - (viewH() - IN * 2)); }

    @Override
    protected int bodyH() { return ROW + GAP + viewH() + GAP + footerH(); }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        layout();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        Draw.textFit(ctx, font, sub(), x, y, w, Theme.TEXT_DIM, false);

        int wy = y + ROW + GAP, vh = viewH();
        Ui.well(ctx, x, wy, w, vh);
        ctx.enableScissor(x + 1, wy + 1, x + w - 1, wy + vh - 1);
        int left = x + IN, top = wy + IN - scroll;
        for (Row r : rows) {
            int ry = top + r.y();
            if (ry + ROW < wy || ry > wy + vh) continue;
            if (r.mark() != null)
                Draw.text(ctx, font, r.mark(), left + r.x() - indent(), ry, Theme.TEXT_DIM, false);
            Draw.text(ctx, font, r.text(), left + r.x(), ry, Theme.TEXT, false);
        }
        ctx.disableScissor();
        drawScrollbar(ctx, x + w - 4, wy + 3, vh - 6);

        int fy = wy + vh + GAP;
        String s = status();
        if (!s.isEmpty()) fy += paragraph(ctx, s, x, fy, w, statusColor()) * ROW + GAP;
        if (Swap.phase() == Swap.Phase.LOADING) {
            barTrack(ctx, x, fy, w);
            long total = Swap.total();
            int inner = w - 2;
            int fill = total > 0 ? (int) Math.min(inner, inner * Swap.got() / total) : inner / 3;
            barFill(ctx, x, fy, 0, fill);
        }
        String[] labels = labels();
        rowButtons(ctx, mouseX, mouseY, x, w,
                Swap.phase() == Swap.Phase.LOADING ? Ui.GHOST : Ui.ACCENT, labels);
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int x, int y, int h) {
        int max = maxScroll();
        if (max <= 0 || h <= 0) return;
        int view = viewH() - IN * 2;
        int thumb = Math.max(12, h * view / (view + max));
        int at = y + (h - thumb) * scroll / max;
        Draw.round(ctx, x, at, 2, thumb, 1, Draw.opaque(Theme.TEXT_FAINT));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - Ui.px(vAmount, ROW * 3)));
        return true;
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int hit = hitRow(mx, my, bodyX(), bodyW(), labels());
        if (hit < 0) {
            boolean inside = Ui.hit(mx, my, panelX(), panelY(), panelW(), panelH());
            if (!inside && Swap.phase() != Swap.Phase.LOADING) later();
            return true;
        }
        String label = labels()[hit];
        switch (label) {
            case UPDATE, AGAIN -> Swap.start(news.newest());
            case CANCEL -> Swap.cancel();
            case PAGE -> openPage();
            default -> later();
        }
        return true;
    }

    private void openPage() {
        String page = news.newest().page();
        if (page.isEmpty()) return;
        try {
            Util.getPlatform().openUri(page);
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] страница релиза не открылась", e);
        }
    }

    @Override
    protected boolean onEnter() {
        switch (Swap.phase()) {
            case IDLE, FAILED -> Swap.start(news.newest());
            case RESTART -> later();
            default -> { }
        }
        return true;
    }

    @Override
    protected void onEscape() {
        if (Swap.phase() == Swap.Phase.LOADING) Swap.cancel();
        else later();
    }

    @Override
    public void onClose() { later(); }

    private void later() {
        Swap.reset();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
