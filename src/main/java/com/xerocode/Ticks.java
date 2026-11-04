package com.xerocode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ticks {

    public enum Unit {
        TICKS(50), MILLIS(1), SECONDS(1000), MINUTES(60_000);

        final double millis;

        Unit(double millis) { this.millis = millis; }
    }

    private static final String TIME_UNIT = "Единица времени";
    private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?|[.,]\\d+)\\s*([a-zа-яё]+)");
    private static final Map<String, Double> MILLIS = new HashMap<>();

    static {
        put(50, "t", "tick", "ticks", "т", "тик", "тика", "тиков", "тики");
        put(1, "ms", "мс");
        put(1000, "s", "sec", "secs", "second", "seconds", "с", "сек", "секунда", "секунды", "секунд",
                "секунду");
        put(60_000, "m", "min", "mins", "minute", "minutes", "м", "мин", "минута", "минуты", "минут",
                "минуту");
        put(3_600_000, "h", "hr", "hour", "hours", "ч", "час", "часа", "часов");
        put(86_400_000, "d", "day", "days", "д", "дн", "день", "дня", "дней");
        put(604_800_000, "w", "week", "weeks", "нед", "неделя", "недели", "недель", "неделю");
        put(31_536_000_000L, "y", "yr", "year", "years", "г", "год", "года", "лет");
    }

    private static void put(double millis, String... names) {
        for (String n : names) MILLIS.put(n, millis);
    }

    public static Double convert(String text, Unit unit) {
        if (text == null || unit == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        Matcher m = PART.matcher(s);
        double total = 0;
        int at = 0;
        boolean any = false;
        while (at < s.length()) {
            if (Character.isWhitespace(s.charAt(at))) {
                at++;
                continue;
            }
            if (!m.find(at) || m.start() != at) return null;
            Double per = MILLIS.get(m.group(2));
            if (per == null) return null;
            total += Double.parseDouble(m.group(1).replace(',', '.')) * per;
            any = true;
            at = m.end();
        }
        if (!any) return null;
        double out = total / unit.millis;
        if (unit == Unit.TICKS || unit == Unit.MILLIS) return (double) Math.round(out);
        return Math.round(out * 1000) / 1000.0;
    }

    public static Unit unitFor(String purpose, Script.Node node) {
        String p = purpose == null ? "" : purpose.toLowerCase(Locale.ROOT);
        if (p.contains("миллисекунд")) return Unit.MILLIS;
        if (p.contains("секунд")) return Unit.SECONDS;
        if (p.contains("минут")) return Unit.MINUTES;
        if (node == null) return Unit.TICKS;
        List<Catalog.Setting> settings = node.settings();
        for (int i = 0; i < settings.size(); i++) {
            if (!TIME_UNIT.equals(settings.get(i).label) || node.markerVar(i) != null) continue;
            String chosen = node.marker(i);
            if ("Секунды".equals(chosen)) return Unit.SECONDS;
            if ("Минуты".equals(chosen)) return Unit.MINUTES;
        }
        return Unit.TICKS;
    }

    private Ticks() {}
}
