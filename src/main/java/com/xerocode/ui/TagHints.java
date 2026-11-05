package com.xerocode.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TagHints {
    record Found(int start, List<Complete.Hint> hints) {}

    private record Tag(String name, String insert, String label, String kind, String about, int swatch,
                       List<String> aliases) {
        boolean matches(String q) {
            if (name.startsWith(q)) return true;
            for (String a : aliases) if (a.startsWith(q)) return true;
            return false;
        }

        boolean exact(String q) { return name.equals(q) || aliases.contains(q); }
    }

    private record Arg(String value, String insert, int back, String kind, String about, int swatch, String alt) {}

    private static final String COLOR = "Цвет", STYLE = "Стиль", ACTION = "Действие", PUT = "Вставка",
            CLOSE = "Закрыть", CLICK = "Клик", HOVER = "Наведение", FONT = "Шрифт", KEY = "Клавиша",
            SELECTOR = "Селектор", FLAG = "Флаг", SOURCE = "Источник", RAINBOW = "Радуга";

    private static final String[][] NAMED = {
            {"black", "чёрный", "000000"}, {"dark_blue", "тёмно-синий", "0000AA"},
            {"dark_green", "тёмно-зелёный", "00AA00"}, {"dark_aqua", "тёмно-бирюзовый", "00AAAA"},
            {"dark_red", "тёмно-красный", "AA0000"}, {"dark_purple", "фиолетовый", "AA00AA"},
            {"gold", "золотой", "FFAA00"}, {"gray", "серый", "AAAAAA"},
            {"dark_gray", "тёмно-серый", "555555"}, {"blue", "синий", "5555FF"},
            {"green", "зелёный", "55FF55"}, {"aqua", "бирюзовый", "55FFFF"},
            {"red", "красный", "FF5555"}, {"light_purple", "розовый", "FF55FF"},
            {"yellow", "жёлтый", "FFFF55"}, {"white", "белый", "FFFFFF"}};

    private static final Map<String, String> CANON = Map.ofEntries(
            Map.entry("c", "color"), Map.entry("colour", "color"), Map.entry("b", "bold"),
            Map.entry("i", "italic"), Map.entry("em", "italic"), Map.entry("u", "underlined"),
            Map.entry("st", "strikethrough"), Map.entry("obf", "obfuscated"), Map.entry("br", "newline"),
            Map.entry("tr", "lang"), Map.entry("translate", "lang"), Map.entry("tr_or", "lang_or"),
            Map.entry("translate_or", "lang_or"), Map.entry("sel", "selector"), Map.entry("data", "nbt"),
            Map.entry("grey", "gray"), Map.entry("dark_grey", "dark_gray"));

    private static final Set<String> SCOPED = Set.of("color", "gradient", "transition", "rainbow", "pride",
            "shadow", "bold", "italic", "underlined", "strikethrough", "obfuscated", "font", "click", "hover",
            "insert");

    private static final List<Tag> TAGS = new ArrayList<>();
    private static final List<Tag> NEGATED = new ArrayList<>();
    private static final List<Arg> ONE_COLOR = new ArrayList<>(), MANY_COLORS = new ArrayList<>();

    static {
        style("bold", "Жирный до </bold>. Коротко <b>; <!bold> выключает.", "b");
        style("italic", "Курсив до </italic>. Коротко <i> или <em>; <!italic> выключает.", "i", "em");
        style("underlined", "Подчёркнутый до </underlined>. Коротко <u>.", "u");
        style("strikethrough", "Зачёркнутый до </strikethrough>. Коротко <st>.", "st");
        style("obfuscated", "Мелькающие случайные символы до </obfuscated>. Коротко <obf>.", "obf");
        tag("reset", "<reset>", "<reset>", STYLE, "Закрывает все открытые теги разом; сам не закрывается.");
        tag("gradient", "<gradient:", "<gradient:…>", COLOR,
                "Плавный переход цвета по буквам: <gradient:red:blue>текст</gradient>. Цветов можно больше"
                        + " двух; число от -1 до 1 в конце сдвигает переход.");
        tag("rainbow", "<rainbow>", "<rainbow>", COLOR,
                "Радуга по буквам до </rainbow>. <rainbow:!> — в обратную сторону, <rainbow:2> — со сдвигом.");
        tag("color", "<color:", "<color:…>", COLOR,
                "Цвет по имени или коду: <color:red>, <color:#ff8800>. Коротко <c:…>.", "c", "colour");
        tag("#", "<#", "<#RRGGBB>", COLOR, "Любой цвет кодом: <#ff8800>текст.");
        tag("transition", "<transition:", "<transition:…>", COLOR,
                "Весь текст одним цветом между заданными, по фазе от -1 до 1: <transition:red:blue:0.5>.");
        tag("shadow", "<shadow:", "<shadow:…>", COLOR,
                "Цвет тени букв: <shadow:red>, <shadow:#ff000080>; второе число — прозрачность от 0 до 1"
                        + " (обычно 0.25). <!shadow> убирает тень.");
        tag("click", "<click:", "<click:…>", ACTION,
                "Действие по клику до </click>: открыть ссылку, выполнить или подставить команду,"
                        + " скопировать текст.");
        tag("hover", "<hover:", "<hover:…>", ACTION,
                "Подсказка при наведении до </hover>: текст, предмет или сущность.");
        tag("insert", "<insert:", "<insert:…>", ACTION,
                "Shift+клик по тексту вставит в строку чата то, что после двоеточия: <insert:/spawn>.");
        tag("newline", "<newline>", "<newline>", PUT, "Перенос строки. Коротко <br>.", "br");
        tag("key", "<key:", "<key:…>", PUT,
                "Клавиша, которую игрок назначил на действие: <key:key.jump> покажет его клавишу прыжка.");
        tag("lang", "<lang:", "<lang:…>", PUT,
                "Перевод игры по ключу на языке игрока: <lang:block.minecraft.diamond_block>. Значения для"
                        + " подстановки — через двоеточие.", "tr", "translate");
        tag("lang_or", "<lang_or:", "<lang_or:…>", PUT,
                "Перевод по ключу, а если ключа нет — запасной текст: <lang_or:ключ:запасной текст>.",
                "tr_or", "translate_or");
        tag("font", "<font:", "<font:…>", STYLE,
                "Шрифт до </font>: <font:minecraft:uniform>, <font:minecraft:alt> — руны стола зачарований.");
        tag("selector", "<selector:", "<selector:…>", PUT,
                "Имена тех, кого выберет селектор: <selector:@p>. Коротко <sel:…>.", "sel");
        tag("score", "<score:", "<score:…>", PUT, "Очки из таблицы счёта: <score:имя:цель>.");
        tag("nbt", "<nbt:", "<nbt:…>", PUT,
                "NBT-данные блока, сущности или хранилища: <nbt:entity:'@s':Health>. Коротко <data:…>.", "data");
        tag("sprite", "<sprite:", "<sprite:…>", PUT,
                "Картинка из атласа текстур: <sprite:block/stone>, предмет — <sprite:items:item/diamond>.");
        tag("head", "<head:", "<head:…>", PUT,
                "Голова игрока по нику или UUID: <head:Notch>; <head:Notch:false> — без второго слоя.");
        for (String[] c : NAMED) {
            int rgb = Integer.parseInt(c[2], 16);
            String ru = c[1].substring(0, 1).toUpperCase(Locale.ROOT) + c[1].substring(1);
            List<String> aliases = c[0].contains("gray") ? List.of(c[0].replace("gray", "grey")) : List.of();
            TAGS.add(new Tag(c[0], "<" + c[0] + ">", "<" + c[0] + ">", COLOR,
                    ru + " — цвет текста до </" + c[0] + "> или до следующего цвета.", rgb, aliases));
            ONE_COLOR.add(new Arg(c[0], c[0] + ">", 0, COLOR, ru + ".", rgb, c[1]));
            MANY_COLORS.add(new Arg(c[0], c[0], 0, COLOR, ru + ". Дальше — «:» и ещё цвет или «>».", rgb, c[1]));
        }
        Map<String, String> off = Map.of("bold", "жирный", "italic", "курсив", "underlined", "подчёркнутый",
                "strikethrough", "зачёркнутый", "obfuscated", "мелькающий");
        for (Tag t : List.copyOf(TAGS)) {
            if (!off.containsKey(t.name())) continue;
            NEGATED.add(new Tag(t.name(), "<!" + t.name() + ">", "<!" + t.name() + ">", STYLE,
                    "Выключает стиль «" + off.get(t.name()) + "».", -1, t.aliases()));
        }
        NEGATED.add(new Tag("shadow", "<!shadow>", "<!shadow>", COLOR, "Убирает тень букв.", -1, List.of()));
    }

    private static final List<Arg> CLICKS = List.of(
            new Arg("open_url", "open_url:", 0, CLICK, "Открыть ссылку: <click:open_url:https://…>.", -1, ""),
            new Arg("run_command", "run_command:", 0, CLICK,
                    "Выполнить команду от игрока: <click:run_command:/spawn>.", -1, ""),
            new Arg("suggest_command", "suggest_command:", 0, CLICK,
                    "Вставить текст в строку чата, не отправляя: <click:suggest_command:/msg >.", -1, ""),
            new Arg("copy_to_clipboard", "copy_to_clipboard:", 0, CLICK,
                    "Скопировать текст в буфер обмена игрока.", -1, ""),
            new Arg("change_page", "change_page:", 0, CLICK, "Перелистнуть книгу на страницу с номером.", -1, ""));

    private static final List<Arg> HOVERS = List.of(
            new Arg("show_text", "show_text:''>", 2, HOVER,
                    "Текст подсказки в кавычках, тоже с тегами: <hover:show_text:'<red>привет'>.", -1, ""),
            new Arg("show_item", "show_item:", 0, HOVER,
                    "Подсказка предмета: <hover:show_item:diamond:5>.", -1, ""),
            new Arg("show_entity", "show_entity:", 0, HOVER,
                    "Подсказка сущности: <hover:show_entity:pig:‹uuid›:имя>.", -1, ""));

    private static final List<Arg> FONTS = List.of(
            new Arg("minecraft:default", "minecraft:default>", 0, FONT, "Обычный шрифт игры.", -1, ""),
            new Arg("minecraft:uniform", "minecraft:uniform>", 0, FONT, "Юникод-шрифт.", -1, ""),
            new Arg("minecraft:alt", "minecraft:alt>", 0, FONT, "Руны стола зачарований.", -1, ""),
            new Arg("minecraft:illageralt", "minecraft:illageralt>", 0, FONT, "Письмена разбойников.", -1, ""));

    private static final List<Arg> KEYS = List.of(
            key("key.jump", "прыжок"), key("key.sneak", "красться"), key("key.sprint", "бег"),
            key("key.forward", "вперёд"), key("key.back", "назад"), key("key.left", "влево"),
            key("key.right", "вправо"), key("key.attack", "атаковать / разрушить"),
            key("key.use", "использовать / поставить"), key("key.pickItem", "выбрать блок"),
            key("key.drop", "выбросить предмет"), key("key.swapOffhand", "сменить руку"),
            key("key.inventory", "инвентарь"), key("key.chat", "чат"), key("key.command", "команда"),
            key("key.playerlist", "список игроков"), key("key.screenshot", "скриншот"));

    private static final List<Arg> SELECTORS = List.of(
            new Arg("@p", "@p>", 0, SELECTOR, "Ближайший игрок.", -1, ""),
            new Arg("@a", "@a>", 0, SELECTOR, "Все игроки.", -1, ""),
            new Arg("@r", "@r>", 0, SELECTOR, "Случайный игрок.", -1, ""),
            new Arg("@s", "@s>", 0, SELECTOR, "Тот, кто видит текст.", -1, ""),
            new Arg("@e", "@e>", 0, SELECTOR, "Все сущности.", -1, ""));

    private static final List<Arg> SOURCES = List.of(
            new Arg("block", "block:", 0, SOURCE, "Блок по координатам: <nbt:block:'1 2 3':путь>.", -1, ""),
            new Arg("entity", "entity:", 0, SOURCE, "Сущность по селектору: <nbt:entity:'@s':Health>.", -1, ""),
            new Arg("storage", "storage:", 0, SOURCE, "Хранилище данных по ключу.", -1, ""));

    private static final List<Arg> RAINBOWS = List.of(
            new Arg("!", "!>", 0, RAINBOW, "Радуга в обратную сторону.", -1, ""));

    private static final List<Arg> FLAGS = new ArrayList<>();

    static {
        for (String f : List.of("pride", "progress", "trans", "bi", "pan", "nb", "lesbian", "ace", "agender",
                "demisexual", "genderqueer", "genderfluid", "intersex", "aro", "baker", "philly", "queer",
                "gay", "bigender", "demigender"))
            FLAGS.add(new Arg(f, f + ">", 0, FLAG, "Цвета флага «" + f + "» по буквам.", -1, ""));
    }

    private TagHints() {}

    private static void style(String name, String about, String... aliases) {
        tag(name, "<" + name + ">", "<" + name + ">", STYLE, about, aliases);
    }

    private static void tag(String name, String insert, String label, String kind, String about,
                            String... aliases) {
        TAGS.add(new Tag(name, insert, label, kind, about, -1, List.of(aliases)));
    }

    private static Arg key(String id, String what) {
        return new Arg(id, id + ">", 0, KEY, "Клавиша действия «" + what + "».", -1, what);
    }

    static boolean word(char c) {
        return c == '_' || c == '#' || c == '.' || c == '-' || c == '@' || c == '!'
                || Character.isLetterOrDigit(c);
    }

    static Found find(String text, int cursor, int limit) {
        if (text == null || cursor <= 0 || cursor > text.length()) return null;
        int lt = text.lastIndexOf('<', cursor - 1);
        if (lt < 0 || escaped(text, lt)) return null;
        String body = text.substring(lt + 1, cursor);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '>' || c == '<' || c == '\'' || c == '"' || Character.isWhitespace(c)) return null;
        }
        int colon = body.indexOf(':');
        if (colon < 0) return names(text, lt, body, limit);
        String head = body.substring(0, colon).toLowerCase(Locale.ROOT);
        if (head.startsWith("!") || head.startsWith("/")) return null;
        String tag = CANON.getOrDefault(head, head);
        String rest = body.substring(colon + 1);
        int restAt = lt + 2 + colon;
        if (tag.equals("font")) return args(FONTS, restAt, rest, limit);
        int last = rest.lastIndexOf(':');
        int index = (int) rest.chars().filter(ch -> ch == ':').count();
        List<Arg> pool = switch (tag) {
            case "color", "shadow" -> index == 0 ? ONE_COLOR : null;
            case "gradient", "transition" -> MANY_COLORS;
            case "click" -> index == 0 ? CLICKS : null;
            case "hover" -> index == 0 ? HOVERS : null;
            case "pride" -> index == 0 ? FLAGS : null;
            case "rainbow" -> index == 0 ? RAINBOWS : null;
            case "key" -> index == 0 ? KEYS : null;
            case "selector" -> index == 0 ? SELECTORS : null;
            case "nbt" -> index == 0 ? SOURCES : null;
            default -> null;
        };
        return pool == null ? null : args(pool, restAt + last + 1, rest.substring(last + 1), limit);
    }

    private static Found names(String text, int lt, String body, int limit) {
        boolean closing = body.startsWith("/"), negated = body.startsWith("!");
        String q = body.substring(closing || negated ? 1 : 0).toLowerCase(Locale.ROOT);
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c != '_' && !Character.isLetterOrDigit(c)) return null;
        }
        List<Complete.Hint> out = new ArrayList<>();
        if (closing) {
            for (String open : openTags(text, lt))
                if (open.startsWith(q) && out.size() < limit)
                    out.add(new Complete.Hint("</" + open + ">", "</" + open + ">", open.length() + 3, CLOSE,
                            "Закрывает <" + open + ">, открытый раньше.", -1));
        } else {
            for (Tag t : rank(negated ? NEGATED : TAGS, q))
                if (out.size() < limit)
                    out.add(new Complete.Hint(t.label(), t.insert(), t.insert().length(), t.kind(), t.about(),
                            t.swatch()));
        }
        return out.isEmpty() ? null : new Found(lt, out);
    }

    private static List<Tag> rank(List<Tag> pool, String q) {
        if (q.isEmpty()) return pool;
        List<Tag> exact = new ArrayList<>(), prefix = new ArrayList<>(), part = new ArrayList<>();
        for (Tag t : pool) {
            if (t.exact(q)) exact.add(t);
            else if (t.matches(q)) prefix.add(t);
            else if (t.name().contains(q)) part.add(t);
        }
        prefix.sort(java.util.Comparator.comparingInt(t -> t.name().length()));
        exact.addAll(prefix);
        exact.addAll(part);
        return exact;
    }

    private static Found args(List<Arg> pool, int at, String partial, int limit) {
        String q = partial.toLowerCase(Locale.ROOT);
        List<Complete.Hint> head = new ArrayList<>(), tail = new ArrayList<>();
        for (Arg a : pool) {
            boolean starts = a.value().startsWith(q) || (!a.alt().isEmpty() && a.alt().startsWith(q));
            boolean inside = !starts && a.value().contains(q);
            if (!starts && !inside) continue;
            if (starts && a.value().equals(q) && a.insert().equals(q)) continue;
            Complete.Hint h = new Complete.Hint(a.value(), a.insert(), a.insert().length() - a.back(), a.kind(),
                    a.about(), a.swatch());
            (starts ? head : tail).add(h);
        }
        head.addAll(tail);
        if (head.size() > limit) head = head.subList(0, limit);
        return head.isEmpty() ? null : new Found(at, head);
    }

    private static boolean escaped(String text, int at) {
        int slashes = 0;
        for (int i = at - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashes++;
        return slashes % 2 == 1;
    }

    static List<String> openTags(String text, int until) {
        List<String> stack = new ArrayList<>();
        int i = 0;
        while (i < until) {
            int lt = text.indexOf('<', i);
            if (lt < 0 || lt >= until) break;
            if (escaped(text, lt)) { i = lt + 1; continue; }
            int gt = tagEnd(text, lt + 1, until);
            if (gt < 0) break;
            String inner = text.substring(lt + 1, gt);
            i = gt + 1;
            if (inner.isEmpty() || inner.endsWith("/")) continue;
            if (inner.startsWith("/")) {
                String name = written(inner.substring(1));
                int at = stack.lastIndexOf(name);
                if (at >= 0) stack.subList(at, stack.size()).clear();
                continue;
            }
            if (inner.startsWith("!")) continue;
            String name = canonical(inner);
            if (name.equals("reset")) { stack.clear(); continue; }
            if (SCOPED.contains(name) || name.startsWith("#") || named(name)) stack.add(written(inner));
        }
        LinkedHashSet<String> innerFirst = new LinkedHashSet<>();
        for (int k = stack.size() - 1; k >= 0; k--) innerFirst.add(stack.get(k));
        return new ArrayList<>(innerFirst);
    }

    private static int tagEnd(String text, int from, int until) {
        char quote = 0;
        for (int k = from; k < until; k++) {
            char c = text.charAt(k);
            if (quote != 0) {
                if (c == '\\') k++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '<') {
                return -1;
            } else if (c == '>') {
                return k;
            }
        }
        return -1;
    }

    private static String written(String inner) {
        int colon = inner.indexOf(':');
        return (colon < 0 ? inner : inner.substring(0, colon)).toLowerCase(Locale.ROOT).trim();
    }

    private static String canonical(String inner) {
        String head = written(inner);
        return CANON.getOrDefault(head, head);
    }

    private static boolean named(String name) {
        for (String[] c : NAMED) if (c[0].equals(name)) return true;
        return false;
    }
}
