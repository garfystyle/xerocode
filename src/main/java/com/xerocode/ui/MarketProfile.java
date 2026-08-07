package com.xerocode.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xerocode.Json;
import com.xerocode.Market;
import com.xerocode.MarketId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;

public final class MarketProfile extends MarketPanel {
    private TextFieldWidget bio;
    private String icon = "";
    private String seeded = "";
    private String watched = "";

    private boolean saving, confirmForget, keyOpen;

    public MarketProfile(MarketScreen owner) {
        super(owner);
        seed();
    }

    private void seed() {
        Market.Me me = Market.me();
        String from = me == null ? "" : me.id;
        if (from.equals(seeded) && bio != null) return;
        seeded = from;
        bio = Ui.field(tr, me == null ? "" : me.bio, "пара слов о себе", 160);
        fields.clear();
        fields.add(bio);
        icon = me == null ? "" : me.icon;
    }

    @Override
    public String title() { return "Профиль"; }

    @Override
    protected void placed() { Ui.width(bio, fieldW() - 10); }

    @Override
    public void draw(DrawContext ctx, int mouseX, int mouseY, float delta) {
        seed();
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        hits.clear();
        Market.Me me = Market.me();
        int col = columnW();
        ctx.enableScissor(x, y, x + col, y + h);
        int at = y + PAD - scroll;
        if (me == null) {
            drawJoin(ctx, at, mouseX, mouseY);
            ctx.disableScissor();
            return;
        }

        if (!split()) at = drawHead(ctx, at, me);
        at = drawStanding(ctx, at, me);
        at = drawField(ctx, at, "О себе", bio, "bio", null, mouseX, mouseY, delta);
        at = drawIcon(ctx, at, mouseX, mouseY);
        if (!split()) at = drawQuotas(ctx, at, me, fieldX(), inner());
        at = drawFacts(ctx, at, me, mouseX, mouseY);
        content = at + PAD + scroll - y;
        ctx.disableScissor();
        drawScrollBar(ctx, mouseX, mouseY);
        if (split()) {
            Ui.vline(ctx, x + col, y, h);
            drawSide(ctx, x + col + 1, y, w - col - 1, me);
        }
    }

    private void drawJoin(DrawContext ctx, int at, int mouseX, int mouseY) {
        Draw.textFit(ctx, tr, Market.joining() ? "завожу аккаунт…" : "аккаунта пока нет",
                fieldX(), at, inner(), Theme.TEXT_DIM, false);
        at += 16;
        for (String line : Ui.wrap(tr,
                "Аккаунт магазина — это ключ в файле xerocode/market-id.json. "
                        + "Ни почты, ни пароля: заводится одним нажатием.", inner(), 4)) {
            Draw.text(ctx, tr, line, fieldX(), at, Theme.TEXT_FAINT, false);
            at += 11;
        }
        at += 8;
        int bw = Ui.buttonW(tr, "Завести аккаунт");
        Ui.button(ctx, tr, mouseX, mouseY, fieldX(), at, bw, ROW, "Завести аккаунт",
                Ui.ACCENT, !Market.joining());
        hits.add(new Hit("make", fieldX(), at, bw, ROW));
        content = at + ROW + PAD + scroll - y;
    }

    private void drawSide(DrawContext ctx, int sx, int sy, int sw, Market.Me me) {
        int pad = 14, room = sw - pad * 2;
        if (room < 80) return;
        int at = sy + pad;
        MarketArt.avatar(ctx, me.icon, me.name, sx + pad, at, 44, Theme.ACCENT, me.name, tr);
        int tx = sx + pad + 54, tw = room - 54;
        who(ctx, me, tx, at, tw);
        Draw.textFit(ctx, tr, me.verified ? "свой" : "новичок", tx, at + 19, tw,
                me.verified ? Theme.OK : Theme.TEXT_FAINT, false);
        Draw.textFit(ctx, tr, "выложено " + Ui.plural(me.modules, "модуль", "модуля", "модулей"),
                tx, at + 31, tw, Theme.TEXT_FAINT, false);
        at += 44 + 12;
        if (!me.bio.isBlank()) {
            for (String line : Ui.wrap(tr, me.bio, room, 3)) {
                Draw.text(ctx, tr, line, sx + pad, at, Theme.TEXT_DIM, false);
                at += 11;
            }
            at += 6;
        }
        Ui.hairline(ctx, sx + pad, at, room);
        drawQuotas(ctx, at + 8, me, sx + pad, room);
    }

    private void who(DrawContext ctx, Market.Me me, int tx, int at, int room) {
        Draw.textFit(ctx, tr, me.name, tx, at + 6, room, Theme.TEXT, false);
        if (!me.verified) return;
        int nw = Math.min(room, tr.getWidth(me.name));
        Draw.glyph(ctx, Draw.CHECK, tx + nw + 5, at + 6, Theme.OK);
    }

    private int drawHead(DrawContext ctx, int at, Market.Me me) {
        MarketArt.avatar(ctx, me.icon, me.name, fieldX(), at, 44, Theme.ACCENT, me.name, tr);
        int tx = fieldX() + 54, room = inner() - 60;
        who(ctx, me, tx, at, room);
        Draw.textFit(ctx, tr, me.verified
                        ? "подтверждён · " + me.uuid.substring(0, Math.min(8, me.uuid.length()))
                        : "не подтверждён",
                tx, at + 19, room, me.verified ? Theme.OK : Theme.TEXT_FAINT, false);
        Draw.textFit(ctx, tr, "выложено " + Ui.plural(me.modules, "модуль", "модуля", "модулей"),
                tx, at + 31, room, Theme.TEXT_FAINT, false);
        return at + 44 + GAP;
    }

    private int drawQuotas(DrawContext ctx, int at, Market.Me me, int qx, int qw) {
        Ui.caption(ctx, tr, "СЕГОДНЯ", qx, at, qw);
        at += LABEL;
        at = meter(ctx, at, qx, qw, "Публикации", me.spent("publish"), me.limit("publish_day"));
        at = meter(ctx, at, qx, qw, "Отметки", me.spent("like"), me.limit("likes_day"));
        at = meter(ctx, at, qx, qw, "Картинки", me.spent("image"), me.limit("images_day"));
        at += 4;
        Ui.caption(ctx, tr, "ВСЕГО", qx, at, qw);
        at += LABEL;
        at = meter(ctx, at, qx, qw, "Модулей", me.modules, me.limit("modules"));
        Draw.textFit(ctx, tr, "вес модуля до " + (me.limit("payload") / 1024) + " КБ",
                qx, at, qw, Theme.TEXT_FAINT, false);
        return at + 12 + GAP;
    }

    private int meter(DrawContext ctx, int at, int qx, int qw, String label, int used, int cap) {
        String num = used + " / " + cap;
        int nw = tr.getWidth(num);
        Draw.textFit(ctx, tr, label, qx, at, qw - nw - 8, Theme.TEXT_DIM, false);
        Draw.textRight(ctx, tr, num, qx + qw, at, Theme.TEXT_FAINT, false);
        at += 11;
        Draw.round(ctx, qx, at, qw, 4, 2, Draw.opaque(Ui.INPUT));
        int fill = cap <= 0 ? 0 : Math.min(qw, qw * used / cap);
        if (fill > 0)
            Draw.round(ctx, qx, at, Math.max(3, fill), 4, 2,
                    Draw.opaque(used >= cap && cap > 0 ? Theme.DANGER : Theme.ACCENT));
        return at + 12;
    }

    private int drawStanding(DrawContext ctx, int at, Market.Me me) {
        Ui.caption(ctx, tr, "ИМЯ", fieldX(), at, fieldW());
        at += LABEL;
        Draw.textFit(ctx, tr, me.name, fieldX(), at, fieldW(), Theme.TEXT, false);
        at += 12 + GAP;
        if (me.verified || me.path.isEmpty()) return at;
        Ui.caption(ctx, tr, "ЧТОБЫ СТАТЬ СВОИМ", fieldX(), at, fieldW());
        at += LABEL;
        for (String line : Ui.wrap(tr, me.path, fieldW(), 3)) {
            Draw.text(ctx, tr, line, fieldX(), at, Theme.TEXT_DIM, false);
            at += 11;
        }
        return at + GAP;
    }

    private int drawIcon(DrawContext ctx, int at, int mouseX, int mouseY) {
        Ui.caption(ctx, tr, "ЗНАЧОК", fieldX(), at, fieldW());
        at += LABEL;
        Market.Me who = Market.me();
        String nick = who == null ? Market.playerName() : who.name;
        MarketArt.avatar(ctx, icon, nick, fieldX(), at, 30, Theme.ACCENT, nick, tr);
        int bx = fieldX() + 38;
        bx = button(ctx, "Предмет…", "icon-item", bx, at, mouseX, mouseY);
        bx = button(ctx, "Картинка…", "icon-image", bx, at, mouseX, mouseY);
        if (!icon.isEmpty()) button(ctx, "Убрать", "icon-clear", bx, at, mouseX, mouseY);
        return at + 30 + GAP;
    }

    private int drawFacts(DrawContext ctx, int at, Market.Me me, int mouseX, int mouseY) {
        Ui.hairline(ctx, fieldX(), at, inner());
        at += 8;
        if (me.admin) {
            Ui.caption(ctx, tr, "СМОТРИТЕЛЬ", fieldX(), at, inner());
            at += LABEL;
            Draw.textFit(ctx, tr, watched.isEmpty() ? "жалобы смотрятся отсюда" : watched,
                    fieldX(), at, inner(), Theme.TEXT_DIM, false);
            at += 12;
            button(ctx, "Жалобы", "watch", fieldX(), at, mouseX, mouseY);
            at += 22;
        }
        Ui.caption(ctx, tr, "КЛЮЧ АККАУНТА", fieldX(), at, inner());
        at += LABEL;
        int eye = 16;
        Ui.well(ctx, fieldX(), at, inner(), 18);
        Draw.textFit(ctx, tr, keyOpen ? me.id : hidden(me.id), fieldX() + 6, at + 5,
                inner() - eye - 14, keyOpen ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
        Ui.iconButton(ctx, mouseX, mouseY, fieldX() + inner() - eye - 1, at + 1, eye,
                keyOpen ? Draw.LOCK : Draw.SEARCH, Ui.GHOST, true);
        hits.add(new Hit("key-eye", fieldX() + inner() - eye - 1, at + 1, eye, eye));
        at += 24;
        for (String line : Ui.wrap(tr, "Ключ вместо пароля: он лежит в файле "
                + "xerocode/market-id.json. Скопируй его и вставь на другом компьютере — "
                + "аккаунт переедет вместе с модулями.", inner(), 4)) {
            Draw.text(ctx, tr, line, fieldX(), at, Theme.TEXT_FAINT, false);
            at += 11;
        }
        at += 6;
        int bx = button(ctx, "Скопировать", "key-copy", fieldX(), at, mouseX, mouseY);
        button(ctx, "Вставить", "key-paste", bx, at, mouseX, mouseY);
        at += 22;
        String label = confirmForget ? "Точно забыть ключ?" : "Забыть ключ";
        int fw = Ui.buttonW(tr, label);
        Ui.button(ctx, tr, mouseX, mouseY, fieldX(), at, fw, 16, label, Ui.DANGER, true);
        hits.add(new Hit("forget", fieldX(), at, fw, 16));
        return at + 16;
    }

    @Override
    public String hint() {
        if (!busy.isEmpty()) return busy;
        if (!trouble.isEmpty()) return trouble;
        return Market.me() == null ? "аккаунта нет" : "";
    }

    @Override
    public String action() { return Market.me() == null ? "" : "Сохранить"; }

    @Override
    public boolean actionOn() { return !saving; }

    @Override
    public void act() { save(); }

    private void save() {
        if (saving || Market.me() == null) return;
        saving = true;
        busy = "сохраняю…";
        trouble = "";
        Market.saveMe(bio.getText().trim(), icon, () -> {
            saving = false;
            busy = "";
            seeded = "";
            owner.toast("профиль сохранён");
        }, (said, why) -> {
            saving = false;
            failed(said);
        });
    }

    @Override
    protected void tapped(Hit hit, Click click, boolean doubled) {
        switch (hit.what()) {
            case "make" -> Market.hello();
            case "bio" -> focus(bio, click, doubled);
            case "key-eye" -> keyOpen = !keyOpen;
            case "key-copy" -> copyKey();
            case "key-paste" -> pasteKey();
            case "icon-item" -> owner.openPicker(new ItemPicker(tr, owner.width, owner.height,
                    Theme.ACCENT, MarketArt.itemOf(icon),
                    stack -> icon = MarketArt.refOf(stack)));
            case "icon-image" -> pickImage("Значок профиля", false, ref -> {
                icon = ref;
                owner.toast("значок готов — не забудь «Сохранить»");
            });
            case "icon-clear" -> icon = "";
            case "watch" -> watch();
            case "forget" -> forget();
            default -> { }
        }
    }

    private void watch() {
        busy = "спрашиваю магазин…";
        Market.watch(answer -> {
            busy = "";
            watched = "аккаунтов " + Json.num(answer, "accounts")
                    + " (подтверждённых " + Json.num(answer, "verified") + ") · модулей "
                    + Json.num(answer, "modules") + " · спрятано "
                    + Json.num(answer, "hidden");
            showReports(answer);
        }, (said, why) -> {
            busy = "";
            owner.toast(said);
        });
    }

    private void showReports(JsonObject answer) {
        List<Menu.Item> items = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (answer.has("reported"))
            for (JsonElement e : answer.getAsJsonArray("reported")) {
                JsonObject o = e.getAsJsonObject();
                ids.add(Json.str(o, "id"));
                items.add(Menu.Item.rich(Json.str(o, "name"), null,
                        Ui.plural(Json.num(o, "reports"), "жалоба", "жалобы", "жалоб"),
                        List.of()));
            }
        if (items.isEmpty()) {
            owner.toast("жалоб нет");
            return;
        }
        owner.openMenu(Menu.actions(owner.width, owner.height, x + PAD, y + 60, tr, items, i -> {
            if (i < 0 || i >= ids.size()) return;
            Market.one(ids.get(i), owner::openModule, (said, why) -> owner.toast(said));
        }));
    }

    private static String hidden(String id) {
        if (id.isEmpty()) return "";
        int tail = Math.min(4, id.length());
        return "·".repeat(Math.max(4, id.length() - tail)) + id.substring(id.length() - tail);
    }

    private void copyKey() {
        String key = MarketId.export();
        if (key.isEmpty()) {
            owner.toast("ключа нет");
            return;
        }
        MinecraftClient.getInstance().keyboard.setClipboard(key);
        owner.toast("ключ в буфере обмена — храни его как пароль");
    }

    private void pasteKey() {
        if (!MarketId.adopt(MinecraftClient.getInstance().keyboard.getClipboard())) {
            owner.toast("в буфере не ключ магазина");
            return;
        }
        seeded = "";
        Market.hello();
        owner.toast("ключ принят — вхожу");
    }

    private void forget() {
        if (!confirmForget) { confirmForget = true; return; }
        confirmForget = false;
        MarketId.forget();
        owner.toast("ключ забыт — следующий вход заведёт новый аккаунт");
        owner.showList();
    }
}
