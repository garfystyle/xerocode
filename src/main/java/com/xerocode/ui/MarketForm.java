package com.xerocode.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xerocode.Backpack;
import com.xerocode.Market;
import com.xerocode.Script;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class MarketForm extends MarketPanel {
    private static final int NAME_MAX = 48, SUM_MAX = 120, DESC_MAX = 1200, TAGS_MAX = 90;

    private static final int[][] PRESETS = {
            {0x59A6FF, 0x1E2430}, {0x63D68E, 0x14261C}, {0xE0654F, 0x2B1512},
            {0xE0A83C, 0x2C1F0D}, {0xC77BE0, 0x241430}, {0x4FBF8B, 0x0F2A22},
            {0x8E7BE0, 0x171533}, {0x6E7C93, 0x161A22}};

    private final Market.Module editing;

    private TextFieldWidget name, summary, tags;
    private EditBoxWidget descr;
    private int descrW = -1;

    private String cat = "";
    private String icon = "", banner = "";
    private static final int SRC_KEEP = -1, SRC_CANVAS = 0, SRC_PACK = 1;

    private int source;
    private String packId = "";
    private final List<Script.Root> onCanvas = new ArrayList<>();

    private boolean sending;

    public MarketForm(MarketScreen owner, Market.Module editing) {
        super(owner);
        this.editing = editing;
        name = Ui.field(tr, editing == null ? "" : editing.name, "как называется", NAME_MAX);
        summary = Ui.field(tr, editing == null ? "" : editing.summary,
                "одной строкой, чтобы понять с ходу", SUM_MAX);
        tags = Ui.field(tr, editing == null ? "" : String.join(" ", editing.tags),
                "метки через пробел", TAGS_MAX);
        cat = editing == null ? firstCat() : editing.cat;
        icon = editing == null ? "" : editing.icon;
        banner = editing == null ? MarketArt.gradRef(PRESETS[0][0], PRESETS[0][1], 1)
                : editing.banner;
        source = editing == null ? SRC_CANVAS : SRC_KEEP;
        fields.add(name);
        fields.add(summary);
        fields.add(tags);
    }

    private String firstCat() {
        List<String> all = Market.categories();
        return all.isEmpty() ? "" : all.get(all.size() - 1);
    }

    private void guessName(String said) {
        if (said == null || said.isBlank() || !name.getText().trim().isEmpty()) return;
        name.setText(said);
        name.setCursorToStart(false);
    }

    @Override
    public String title() { return editing == null ? "Выложить модуль" : "Изменить модуль"; }

    @Override
    protected void placed() {
        int room = fieldW();
        if (descr == null || descrW != room) {
            String was = descr == null ? (editing == null ? "" : editing.descr) : descr.getText();
            descr = EditBoxWidget.builder()
                    .placeholder(Text.literal("что делает, как ставить, что настроить")
                            .withColor(Theme.TEXT_FAINT))
                    .textColor(Draw.opaque(Theme.TEXT))
                    .textShadow(false)
                    .cursorColor(Draw.opaque(Theme.ACCENT))
                    .hasBackground(false)
                    .hasOverlay(false)
                    .build(tr, room - 10, 62, Text.literal("описание"));
            descr.setMaxLength(DESC_MAX);
            descr.setText(was);
            descrW = room;
            fields.removeIf(f -> f instanceof EditBoxWidget);
            fields.add(descr);
        }
        Ui.width(name, room - 10);
        Ui.width(summary, room - 10);
        Ui.width(tags, room - 10);
    }

    @Override
    public void draw(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        hits.clear();
        int col = columnW();
        ctx.enableScissor(x, y, x + col, y + h);
        int at = y + PAD - scroll;
        at = drawSource(ctx, at, mouseX, mouseY);
        at = drawField(ctx, at, "Название", name, "name", left(name, NAME_MAX),
                mouseX, mouseY, delta);
        at = drawField(ctx, at, "Одной строкой", summary, "summary", left(summary, SUM_MAX),
                mouseX, mouseY, delta);
        at = drawDescr(ctx, at, mouseX, mouseY, delta);
        at = drawCats(ctx, at, mouseX, mouseY);
        at = drawField(ctx, at, "Метки", tags, "tags", null, mouseX, mouseY, delta);
        at = drawIcon(ctx, at, mouseX, mouseY);
        at = drawBanner(ctx, at, mouseX, mouseY);
        content = at + scroll - y + PAD;
        ctx.disableScissor();
        drawScrollBar(ctx, mouseX, mouseY);
        if (split()) {
            Ui.vline(ctx, x + col, y, h);
            drawShowcase(ctx, x + col + 1, y, w - col - 1);
        }
    }

    private Market.Module shown() {
        Market.Module m = new Market.Module();
        String typed = name.getText().trim();
        m.name = typed.isEmpty() ? "Название модуля" : typed;
        m.summary = summary.getText().trim();
        m.descr = descr == null ? "" : descr.getText();
        m.cat = cat;
        m.icon = icon;
        m.banner = banner;
        Market.Me me = Market.me();
        m.author = me == null ? Market.playerName() : me.name;
        m.authorIcon = me == null ? "" : me.icon;
        m.authorOk = me != null && me.verified;
        for (String tag : tags.getText().trim().split("[\\s,]+"))
            if (!tag.isBlank() && m.tags.size() < 5) m.tags.add(tag);
        if (editing != null) {
            m.blocks = editing.blocks;
            m.roots = editing.roots;
            m.size = editing.size;
            m.likes = editing.likes;
            m.downloads = editing.downloads;
            m.version = editing.version;
        }
        switch (source) {
            case SRC_CANVAS -> {
                if (!onCanvas.isEmpty()) {
                    m.blocks = Script.blocksIn(onCanvas);
                    m.roots = onCanvas.size();
                }
            }
            case SRC_PACK -> {
                Backpack.Item item = Backpack.byId(packId);
                if (item != null) {
                    m.blocks = item.blocks();
                    m.roots = item.roots().size();
                }
            }
            default -> { }
        }
        return m;
    }

    private void drawShowcase(DrawContext ctx, int sx, int sy, int sw) {
        int pad = 14;
        int cardW = Math.max(150, Math.min(MarketArt.CARD_MIN + 40, sw - pad * 2));
        int cx = sx + (sw - cardW) / 2;
        int at = sy + pad;
        Ui.caption(ctx, tr, "КАК УВИДЯТ", sx + pad, at, sw - pad * 2);
        at += 13;
        MarketArt.card(ctx, tr, shown(), cx, at, cardW, false, true);
    }

    private String left(TextFieldWidget field, int max) {
        int used = field.getText().length();
        return used > max - 12 ? (max - used) + "" : "";
    }

    private int drawDescr(DrawContext ctx, int at, int mouseX, int mouseY, float delta) {
        int room = fieldW();
        caption(ctx, "Описание", at, descr.getText().length() + "/" + DESC_MAX);
        at += LABEL;
        Ui.input(ctx, fieldX(), at, room, 66, descr.isFocused());
        descr.setPosition(fieldX() + 5, at + 3);
        descr.render(ctx, mouseX, mouseY, delta);
        hits.add(new Hit("descr", fieldX(), at, room, 66));
        return at + 66 + GAP;
    }

    private int drawCats(DrawContext ctx, int at, int mouseX, int mouseY) {
        List<String> all = Market.categories();
        if (all.isEmpty()) return at;
        if (cat.isEmpty() || !all.contains(cat)) cat = firstCat();
        caption(ctx, "Раздел", at, null);
        at += LABEL;
        Ui.Chips chips = new Ui.Chips(tr, all, inner(), 16, 4);
        chips.render(ctx, tr, mouseX, mouseY, fieldX(), at, all.indexOf(cat), Theme.ACCENT);
        for (Ui.Chips.Cell cell : chips.cells)
            hits.add(new Hit("cat", fieldX() + cell.dx(), at + cell.dy(), cell.w(), 16,
                    cell.index()));
        return at + chips.height() + GAP;
    }

    private int drawSource(DrawContext ctx, int at, int mouseX, int mouseY) {
        List<String> modes = new ArrayList<>();
        if (editing != null) modes.add("Не менять");
        modes.add("С холста");
        modes.add("Из рюкзака");
        caption(ctx, "Что выкладываем", at, null);
        at += LABEL;
        int base = editing != null ? -1 : 0;
        Ui.Chips chips = new Ui.Chips(tr, modes, inner(), 16, 4);
        chips.render(ctx, tr, mouseX, mouseY, fieldX(), at, source - base, Theme.ACCENT);
        for (Ui.Chips.Cell cell : chips.cells)
            hits.add(new Hit("source", fieldX() + cell.dx(), at + cell.dy(), cell.w(), 16,
                    cell.index() + base));
        at += chips.height() + 5;
        String said = sourceLine();
        String pick = pickLabel();
        int bw = pick.isEmpty() ? 0 : Ui.buttonW(tr, pick);
        Draw.textFit(ctx, tr, said, fieldX(), at, inner() - bw - 10,
                sourceReady() ? Theme.TEXT_DIM : Theme.DANGER, false);
        if (!pick.isEmpty()) {
            int bx = fieldX() + inner() - bw;
            Ui.button(ctx, tr, mouseX, mouseY, bx, at - 4, bw, 16, pick, Ui.GHOST);
            hits.add(new Hit("pick-source", bx, at - 4, bw, 16));
        }
        return at + 12 + GAP;
    }

    private String pickLabel() {
        return switch (source) {
            case SRC_CANVAS -> "Выделить…";
            case SRC_PACK -> "Рюкзак…";
            default -> "";
        };
    }

    private String sourceLine() {
        return switch (source) {
            case SRC_KEEP -> "код останется прежним, поменяется только описание";
            case SRC_CANVAS -> {
                if (onCanvas.isEmpty()) yield "код не выбран";
                yield "выделено: "
                        + Ui.plural(Script.blocksIn(onCanvas), "блок", "блока", "блоков")
                        + " · " + Ui.plural(onCanvas.size(), "кусок", "куска", "кусков");
            }
            default -> {
                Backpack.Item item = Backpack.byId(packId);
                yield item == null ? "выбери кусок из рюкзака"
                        : item.name + " · " + item.blocksText();
            }
        };
    }

    private boolean sourceReady() {
        return switch (source) {
            case SRC_KEEP -> true;
            case SRC_CANVAS -> !onCanvas.isEmpty();
            default -> Backpack.byId(packId) != null;
        };
    }

    private int drawIcon(DrawContext ctx, int at, int mouseX, int mouseY) {
        caption(ctx, "Значок", at, null);
        at += LABEL;
        MarketArt.avatar(ctx, icon, fieldX(), at, 34, MarketArt.catColor(cat),
                name.getText(), tr);
        int bx = fieldX() + 42;
        bx = button(ctx, "Предмет…", "icon-item", bx, at + 1, mouseX, mouseY);
        bx = button(ctx, "Картинка…", "icon-image", bx, at + 1, mouseX, mouseY);
        if (!icon.isEmpty()) button(ctx, "Убрать", "icon-clear", bx, at + 1, mouseX, mouseY);
        return at + 34 + GAP;
    }

    private int drawBanner(DrawContext ctx, int at, int mouseX, int mouseY) {
        caption(ctx, "Полоса", at, null);
        at += LABEL;
        int bw = Math.min(inner(), 300), bh = 44;
        MarketArt.banner(ctx, banner, fieldX(), at, bw, bh, MarketArt.catColor(cat),
                Ui.R_SM, Ui.R_SM, Ui.R_SM, Ui.R_SM, Draw.opaque(Ui.WELL));
        Draw.roundOutline(ctx, fieldX(), at, bw, bh, Ui.R_SM, Draw.opaque(Ui.LINE_IN));
        at += bh + 6;

        int sw = 18;
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = fieldX() + i * (sw + 4);
            if (sx + sw > fieldX() + inner()) break;
            boolean on = banner.startsWith("grad:")
                    && banner.contains(String.format("%06x", PRESETS[i][0]));
            Draw.round(ctx, sx, at, sw, 14, 3, Draw.opaque(PRESETS[i][0]));
            Draw.rect(ctx, sx, at + 7, sw, 7, Draw.opaque(PRESETS[i][1]));
            Draw.roundOutline(ctx, sx, at, sw, 14, 3,
                    Draw.opaque(on ? Theme.ACCENT : Ui.LINE_IN));
            hits.add(new Hit("banner-preset", sx, at, sw, 14, i));
        }
        at += 20;
        List<String> looks = List.of("плоско", "сверху вниз", "вбок", "полосы", "точки", "сетка");
        Ui.Chips chips = new Ui.Chips(tr, looks, inner(), 15, 4);
        chips.render(ctx, tr, mouseX, mouseY, fieldX(), at, pattern(), Theme.ACCENT);
        for (Ui.Chips.Cell cell : chips.cells)
            hits.add(new Hit("banner-pattern", fieldX() + cell.dx(), at + cell.dy(),
                    cell.w(), 15, cell.index()));
        at += chips.height() + 6;
        int bx = fieldX();
        bx = button(ctx, "Картинка…", "banner-image", bx, at, mouseX, mouseY);
        if (banner.startsWith("img:")) button(ctx, "Убрать", "banner-clear", bx, at, mouseX, mouseY);
        return at + 18 + GAP;
    }

    private int pattern() {
        return banner.startsWith("grad:") ? MarketArt.gradOf(banner, 0)[2] : -1;
    }

    @Override
    public String hint() {
        if (!busy.isEmpty()) return busy;
        if (!trouble.isEmpty()) return trouble;
        Market.Me me = Market.me();
        if (me == null) return "нужен аккаунт";
        if (name.getText().trim().length() < 2) return "нет названия";
        if (!sourceReady()) return "код не выбран";
        if (editing == null)
            return "сегодня можно выложить ещё " + me.publishLeft()
                    + " · всего " + me.modules + " из " + me.limit("modules");
        return "";
    }

    @Override
    public String action() { return editing == null ? "Выложить" : "Сохранить"; }

    @Override
    public boolean actionOn() {
        return !sending && name.getText().trim().length() >= 2 && sourceReady();
    }

    @Override
    public void act() { send(); }

    private JsonObject payload() {
        List<Script.Root> roots = new ArrayList<>();
        switch (source) {
            case SRC_CANVAS -> roots.addAll(onCanvas);
            case SRC_PACK -> {
                Backpack.Item item = Backpack.byId(packId);
                if (item != null) roots.addAll(item.roots());
            }
            default -> { return null; }
        }
        return roots.isEmpty() ? null : Market.payloadOf(roots);
    }

    private void send() {
        if (sending) return;
        JsonObject form = new JsonObject();
        form.addProperty("name", name.getText().trim());
        form.addProperty("summary", summary.getText().trim());
        form.addProperty("descr", descr.getText());
        form.addProperty("cat", cat);
        form.addProperty("icon", icon);
        form.addProperty("banner", banner);
        JsonArray marks = new JsonArray();
        for (String tag : tags.getText().trim().split("[\\s,]+"))
            if (!tag.isBlank() && marks.size() < 5) marks.add(tag);
        form.add("tags", marks);
        JsonObject code = payload();
        if (editing == null && code == null) {
            trouble = "нечего выкладывать — выбери источник";
            return;
        }
        if (code != null) form.add("payload", code);
        sending = true;
        trouble = "";
        busy = editing == null ? "выкладываю…" : "сохраняю…";
        if (editing == null) {
            Market.publish(form, made -> {
                sending = false;
                busy = "";
                owner.toast("«" + made.name + "» на витрине");
                owner.showList();
                owner.openModule(made);
                owner.reload();
            }, this::stopped);
            return;
        }
        form.addProperty("id", editing.id);
        Market.change(form, made -> {
            sending = false;
            busy = "";
            owner.toast("изменения сохранены");
            owner.openModule(made);
            owner.reload();
        }, this::stopped);
    }

    private void stopped(String said, String why) {
        sending = false;
        failed(said);
    }

    @Override
    protected void tapped(Hit hit, Click click, boolean doubled) {
        switch (hit.what()) {
            case "name" -> focus(name, click, doubled);
            case "summary" -> focus(summary, click, doubled);
            case "tags" -> focus(tags, click, doubled);
            case "descr" -> focus(descr, click, doubled);
            case "cat" -> {
                blur();
                List<String> all = Market.categories();
                if (hit.index() >= 0 && hit.index() < all.size()) cat = all.get(hit.index());
            }
            case "source" -> {
                blur();
                source = hit.index();
                startPick();
            }
            case "pick-source" -> startPick();
            case "icon-item" -> pickItem();
            case "icon-image" -> pickImage("Значок модуля", false, ref -> {
                icon = ref;
                owner.toast("картинка на месте");
            });
            case "icon-clear" -> icon = "";
            case "banner-image" -> pickImage("Полоса модуля", true, ref -> {
                banner = ref;
                owner.toast("картинка на месте");
            });
            case "banner-clear" -> banner = MarketArt.gradRef(PRESETS[0][0], PRESETS[0][1], 1);
            case "banner-preset" -> {
                int i = hit.index();
                banner = MarketArt.gradRef(PRESETS[i][0], PRESETS[i][1],
                        Math.max(0, pattern()));
            }
            case "banner-pattern" -> {
                int[] g = MarketArt.gradOf(banner, MarketArt.catColor(cat));
                banner = MarketArt.gradRef(g[0], g[1], hit.index());
            }
            default -> { }
        }
    }

    private void startPick() {
        switch (source) {
            case SRC_CANVAS -> owner.pickOnCanvas(this);
            case SRC_PACK -> pickPack();
            default -> { }
        }
    }

    void tookFromCanvas(List<Script.Root> roots) {
        onCanvas.clear();
        onCanvas.addAll(roots);
        source = SRC_CANVAS;
        if (roots.isEmpty()) {
            owner.toast("ничего не выделено — код модуля прежний");
            return;
        }
        guessName(Backpack.suggestAll(roots));
        owner.toast("взято с холста: "
                + Ui.plural(Script.blocksIn(roots), "блок", "блока", "блоков"));
    }

    private void pickPack() {
        if (Backpack.all().isEmpty()) {
            owner.toast("рюкзак пуст");
            return;
        }
        owner.openPicker(new BackpackPanel(tr, owner.width, owner.height,
                item -> {
                    packId = item.id;
                    source = SRC_PACK;
                    guessName(item.name);
                    owner.toast("«" + item.name + "» · " + item.blocksText());
                }, "Выбрать", packId));
    }

    private void pickItem() {
        owner.openPicker(new ItemPicker(tr, owner.width, owner.height, Theme.ACCENT,
                MarketArt.itemOf(icon), stack -> icon = MarketArt.refOf(stack)));
    }

    @Override
    public boolean wheel(double mx, double my, double amount) {
        if (descr.isFocused() && Ui.hit(mx, my, descr.getX() - 6, descr.getY() - 4,
                descr.getWidth() + 12, 66))
            return descr.mouseScrolled(mx, my, 0, amount);
        return super.wheel(mx, my, amount);
    }
}
