package com.xerocode.ui;

import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ItemStudio {
    public interface Done { void apply(Value value); }

    private static final int PAD = 10;
    private static final int HEAD_H = 26;
    private static final int FOOT_H = 26;
    private static final int CAP = 11;
    private static final int INPUT_H = 20;
    private static final int BTN_H = 17;
    private static final int ROW = 15;
    private static final int ADD_H = 15;
    private static final int GUTTER = 12;
    private static final int CARD_H = 30;

    private static final int NAME = 0, COUNT = 1, DAMAGE = 2, MODEL = 3, NBT = 4;

    private static final String GLINT_LABEL = "БЛЕСК ЧАР";
    private static final List<String> GLINTS = List.of("как обычно", "всегда", "никогда");

    private static final String HIDE_LABEL = "СКРЫТЬ";
    private static final List<String> HIDE_NAMES = List.of("чары", "неразрушимость", "атрибуты");
    private static final List<String> HIDE_IDS = List.of(
            "minecraft:enchantments", "minecraft:unbreakable", "minecraft:attribute_modifiers");

    private final TextRenderer tr;
    private final int screenW, screenH;
    private final Done done;
    private final Value v;

    private final TextFieldWidget nameField, countField, damageField, modelField;
    private final EditBoxWidget nbtBox;
    private Ui.Chips modeChips, glintChips, hideChips;

    private ItemPicker picker;
    private TextStudio studio;
    private CatalogPicker enchPicker;
    private int studioLine = -1;

    private int x, y, w, h, lw, rw;
    private int cardY, pickY, numsY, nameY, modeY, nameBtnY, loreY, enchY, propY, glintY, hideY;
    private int prevY, prevH, nbtY, nbtH, nbtMsgY, nbtBtnY, footY;
    private int loreRows, enchRows, loreScroll, enchScroll;
    private int focus = NAME;
    private int lastSig;
    private int squeeze;
    private boolean closed;

    private static final int[] LORE_ROWS = {4, 4, 3, 3, 2};
    private static final int[] ENCH_ROWS = {3, 2, 2, 1, 1};
    private static final int[] NBT_HEIGHT = {76, 76, 64, 52, 40};
    private String flash = "";
    private long flashAt;

    public ItemStudio(TextRenderer tr, int screenW, int screenH, Value value, Done done) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.done = done;
        this.v = value;

        nameField = field(v.itemName, "название предмета", 256);
        countField = field(String.valueOf(v.itemCount), "1", 3);
        damageField = field(v.itemDamage > 0 ? String.valueOf(v.itemDamage) : "", "0", 6);
        modelField = field(v.modelData >= 0 ? String.valueOf(v.modelData) : "", "нет", 9);

        nbtBox = EditBoxWidget.builder()
                .placeholder(Text.literal("{\"minecraft:custom_data\":{}}")
                        .withColor(Theme.TEXT_FAINT))
                .textColor(Draw.opaque(Theme.TEXT))
                .textShadow(false)
                .cursorColor(Draw.opaque(Theme.ACCENT))
                .hasBackground(false)
                .hasOverlay(false)
                .build(tr, rightColW(screenW) - 8, 60, Text.literal("компоненты"));
        nbtBox.setMaxLength(16384);
        nbtBox.setText(v.components);
        nbtBox.setChangeListener(s -> v.components = s);

        nameField.setFocused(true);
        layout();
        writeNbt();
        lastSig = fieldSig();
    }

    private int fieldSig() {
        int h = v.itemId.hashCode();
        h = h * 31 + v.itemCount;
        h = h * 31 + v.itemName.hashCode();
        h = h * 31 + v.itemParsing.hashCode();
        h = h * 31 + v.lore.hashCode();
        for (Value.Ench e : v.enchants) h = h * 31 + e.id.hashCode() * 31 + e.level;
        h = h * 31 + (v.unbreakable ? 1 : 0) + (v.hideTooltip ? 2 : 0) + v.glint * 4;
        h = h * 31 + v.itemDamage;
        h = h * 31 + v.modelData;
        h = h * 31 + v.hidden.hashCode();
        return h;
    }

    private void writeNbt() {
        if (v.itemId.isEmpty()) return;
        String all = Stacks.print(v);
        if (all.equals(v.components)) return;
        v.components = all;
        nbtBox.setText(all);
    }

    private void readNbt() {
        Stacks.readText(v);
        syncWidgets();
        lastSig = fieldSig();
    }

    private TextFieldWidget field(String text, String placeholder, int max) {
        return Ui.field(tr, text, placeholder, max);
    }

    public boolean isClosed() { return closed; }

    private int inner() { return w - PAD * 2; }
    private int leftX()  { return x + PAD; }
    private int rightX() { return x + PAD + lw + GUTTER; }

    private static int panelW(int screenW) { return Math.min(660, Math.max(400, screenW - 24)); }

    private static int leftColW(int screenW) {
        return (panelW(screenW) - PAD * 2 - GUTTER) * 55 / 100;
    }

    private static int rightColW(int screenW) {
        return panelW(screenW) - PAD * 2 - GUTTER - leftColW(screenW);
    }

    private void layout() {
        w = panelW(screenW);
        lw = leftColW(screenW);
        rw = rightColW(screenW);
        loreRows = LORE_ROWS[squeeze];
        enchRows = ENCH_ROWS[squeeze];

        List<String> modes = new ArrayList<>();
        for (Values.Parsing p : Values.PARSINGS) modes.add(p.name());
        modeChips = new Ui.Chips(tr, modes, lw, ROW, 3, true);
        glintChips = new Ui.Chips(tr, GLINTS, lw - glintLabelW(), ROW, 3, true);
        hideChips = new Ui.Chips(tr, HIDE_NAMES, lw - hideLabelW(), ROW, 3, true);

        int at = HEAD_H + 1 + 8;
        cardY = at;                          at += CARD_H + 4;
        pickY = at;                          at += BTN_H + 8;
        numsY = at + CAP;                    at = numsY + INPUT_H + 8;
        nameY = at + CAP;                    at = nameY + INPUT_H + 4;
        modeY = at;                          at += modeChips.height() + 4;
        nameBtnY = at;                       at += BTN_H + 8;
        loreY = at + CAP;                    at = loreY + loreRows * (ROW + 2) + ADD_H + 8;
        enchY = at + CAP;                    at = enchY + enchRows * (ROW + 2) + ADD_H + 8;
        propY = at + CAP;                    at = propY + BTN_H + 4;
        glintY = at;                         at += glintChips.height() + 4;
        hideY = at;                          at += hideChips.height();

        int right = HEAD_H + 1 + 8;
        prevY = right + CAP;
        int avail = at - prevY;
        prevH = Math.max(squeeze == 0 ? 76 : 56, avail * 42 / 100);
        int tail = 8 + CAP + 3 + 11 + 3 + BTN_H;
        nbtH = Math.max(NBT_HEIGHT[squeeze] - 20, avail - prevH - tail);
        right = prevY + prevH + 8;
        nbtY = right + CAP;                  right = nbtY + nbtH + 3;
        nbtMsgY = right;                     right += 11 + 3;
        nbtBtnY = right;                     right += BTN_H;

        h = Math.max(at, right) + 8 + 1 + FOOT_H;
        x = (screenW - w) / 2;
        y = Math.max(4, (screenH - h) / 2);
        footY = h - FOOT_H;
        if (h > screenH - 8 && squeeze < LORE_ROWS.length - 1) { squeeze++; layout(); return; }

        nameField.setX(leftX() + 7);
        nameField.setY(y + nameY + (INPUT_H - 12) / 2 + 2);
        nameField.setWidth(lw - 14);
        int cw = numW();
        for (int i = 0; i < 3; i++) {
            TextFieldWidget f = numField(i);
            f.setX(leftX() + i * (cw + 4) + 6);
            f.setY(y + numsY + (INPUT_H - 12) / 2 + 2);
            f.setWidth(cw - 12);
        }
        nbtBox.setX(rightX() + 4);
        nbtBox.setY(y + nbtY + 3);
        nbtBox.setWidth(rw - 8);
        nbtBox.setHeight(nbtH - 6);
    }

    private int numW() { return (lw - 8) / 3; }

    private int glintLabelW() { return tr.getWidth(GLINT_LABEL) + 7; }

    private int hideLabelW() { return tr.getWidth(HIDE_LABEL) + 7; }

    private boolean[] hidden() {
        boolean[] on = new boolean[HIDE_IDS.size()];
        for (int i = 0; i < on.length; i++) on[i] = v.hidden.contains(HIDE_IDS.get(i));
        return on;
    }

    private String pickLabel() { return v.itemId.isEmpty() ? "Выбрать предмет" : "Другой предмет"; }

    private Ui.Cluster itemRow() {
        return new Ui.Cluster(leftX(), lw, 5,
                Ui.buttonW(tr, Draw.SEARCH, pickLabel()),
                Ui.buttonW(tr, Draw.LOAD, "Взять из руки"));
    }

    private Ui.Cluster nameRow() {
        return new Ui.Cluster(leftX(), lw, 5,
                Ui.buttonW(tr, Draw.WINDOW, "Расширенный редактор"),
                Ui.buttonW(tr, "убрать название"));
    }

    private Ui.Cluster loreAdd() {
        return new Ui.Cluster(leftX(), lw, 5, Ui.buttonW(tr, Draw.PLUS, "добавить строку"));
    }

    private Ui.Cluster enchAdd() {
        return new Ui.Cluster(leftX(), lw, 5, Ui.buttonW(tr, Draw.PLUS, "добавить чары"));
    }

    private Ui.Cluster nbtRow() {
        return new Ui.Cluster(rightX(), rw, 4, Ui.buttonW(tr, "в столбик"),
                Ui.buttonW(tr, "копировать"), Ui.buttonW(tr, "очистить"));
    }

    private TextFieldWidget numField(int i) {
        return i == 0 ? countField : i == 1 ? damageField : modelField;
    }

    private int absY(int rel) { return y + rel; }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (picker != null) { picker.render(ctx, mouseX, mouseY, delta); return; }
        if (studio != null) { studio.render(ctx, mouseX, mouseY, delta); return; }
        if (enchPicker != null) { enchPicker.render(ctx, mouseX, mouseY, delta); return; }

        int sig = fieldSig();
        if (sig != lastSig) {
            lastSig = sig;
            writeNbt();
        }

        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        int accent = Values.color(Value.ITEM);

        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.round(ctx, x + PAD, y + 8, 3, 10, 1, Draw.opaque(accent));
        Draw.textFit(ctx, tr, "Редактор предмета", x + PAD + 9, y + 9, inner() - 40,
                Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, y + 6, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        drawLeft(ctx, mouseX, mouseY, delta);
        drawRight(ctx, mouseX, mouseY, delta);

        Ui.hairline(ctx, x + 1, absY(footY) - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY);
    }

    private void drawLeft(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ItemStack st = Stacks.preview(v);

        Ui.well(ctx, leftX(), absY(cardY), lw, CARD_H);
        if (st.isEmpty()) {
            Draw.glyph(ctx, Draw.WARN, leftX() + 8, absY(cardY) + (CARD_H - 6) / 2, 0xFFE066);
            Draw.textFit(ctx, tr, v.itemId.isEmpty() ? "предмет не выбран" : v.itemId,
                    leftX() + 20, absY(cardY) + (CARD_H - Ui.TEXT_H) / 2, lw - 26,
                    Theme.TEXT_DIM, false);
        } else {
            ctx.drawItem(st, leftX() + 7, absY(cardY) + (CARD_H - 16) / 2);
            ctx.drawStackOverlay(tr, st, leftX() + 7, absY(cardY) + (CARD_H - 16) / 2);
            int top = absY(cardY) + (CARD_H - (11 + Ui.TEXT_H)) / 2;
            ctx.drawText(tr, McText.fit(tr, McText.runsOf(st.getName()), lw - 40),
                    leftX() + 28, top, Draw.opaque(Theme.TEXT), false);
            Draw.textFit(ctx, tr, v.itemId, leftX() + 28, top + 11, lw - 36,
                    Theme.TEXT_FAINT, false);
        }
        Ui.Cluster items = itemRow();
        Ui.glyphButton(ctx, tr, mouseX, mouseY, items.x(0), absY(pickY), items.w(0), BTN_H,
                Draw.SEARCH, pickLabel(), Ui.GHOST, true);
        Ui.glyphButton(ctx, tr, mouseX, mouseY, items.x(1), absY(pickY), items.w(1), BTN_H,
                Draw.LOAD, "Взять из руки", Ui.GHOST, held() != null);

        int cw = numW();
        String[] caps = {"КОЛИЧЕСТВО", "ПРОЧНОСТЬ", "МОДЕЛЬ"};
        for (int i = 0; i < 3; i++) {
            int cx = leftX() + i * (cw + 4);
            Draw.textFit(ctx, tr, caps[i], cx + 2, absY(numsY) - CAP, cw - 4, Theme.TEXT_FAINT, false);
            Ui.input(ctx, cx, absY(numsY), cw, INPUT_H, focus == COUNT + i);
            numField(i).render(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, tr, numField(i));
        }

        Ui.caption(ctx, tr, "НАЗВАНИЕ", leftX(), absY(nameY) - CAP, lw);
        Ui.input(ctx, leftX(), absY(nameY), lw, INPUT_H, focus == NAME);
        nameField.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, nameField);
        modeChips.render(ctx, tr, mouseX, mouseY, leftX(), absY(modeY),
                parsingIndex(v.itemParsing), Values.color(Value.TEXT));
        Ui.Cluster names = nameRow();
        Ui.glyphButton(ctx, tr, mouseX, mouseY, names.x(0), absY(nameBtnY), names.w(0), BTN_H,
                Draw.WINDOW, "Расширенный редактор", Ui.GHOST, true);
        Ui.button(ctx, tr, mouseX, mouseY, names.x(1), absY(nameBtnY), names.w(1),
                BTN_H, "убрать название", Ui.GHOST, !v.itemName.isEmpty());

        Ui.caption(ctx, tr, "ОПИСАНИЕ", leftX(), absY(loreY) - CAP, lw,
                v.lore.isEmpty() ? "" : v.lore.size() + " стр.");
        int shownLore = Math.max(1, Math.min(v.lore.size(), loreRows));
        if (v.lore.isEmpty()) {
            Draw.round(ctx, leftX(), absY(loreY), lw, ROW, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, "строк пока нет", leftX() + 8, absY(loreY) + 4, lw - 16,
                    Theme.TEXT_FAINT, false);
        }
        for (int r = 0; r < shownLore; r++) {
            int i = loreScroll + r;
            if (i >= v.lore.size()) break;
            int ry = absY(loreY) + r * (ROW + 2);
            int rowW = lw - ROW - 2 - (maxLoreScroll() > 0 ? 5 : 0);
            boolean hov = Ui.hit(mouseX, mouseY, leftX(), ry, rowW, ROW);
            Draw.round(ctx, leftX(), ry, rowW, ROW, Ui.R_SM, Draw.opaque(hov ? Ui.BTN_HOVER : Ui.WELL));
            Draw.textRight(ctx, tr, String.valueOf(i + 1), leftX() + 14, ry + 4,
                    Theme.TEXT_FAINT, false);
            String line = v.lore.get(i);
            if (line.isEmpty()) {
                Draw.textFit(ctx, tr, "пустая строка", leftX() + 20, ry + 4, rowW - 26,
                        Theme.TEXT_FAINT, false);
            } else {
                ctx.drawText(tr, McText.fit(tr, McText.runs(line, v.itemParsing), rowW - 26),
                        leftX() + 20, ry + 4, Draw.opaque(Theme.TEXT), false);
            }
            Ui.iconButton(ctx, mouseX, mouseY, leftX() + lw - ROW, ry, ROW, Draw.CROSS,
                    Ui.DANGER, true);
        }
        if (maxLoreScroll() > 0)
            Ui.scrollbar(ctx, leftX() + lw - ROW - 6, absY(loreY), shownLore * (ROW + 2),
                    v.lore.size() * (ROW + 2), shownLore * (ROW + 2), loreScroll * (ROW + 2));
        Ui.Cluster loreBtn = loreAdd();
        Ui.glyphButton(ctx, tr, mouseX, mouseY, loreBtn.x(0), loreAddY(), loreBtn.w(0), ADD_H,
                Draw.PLUS, "добавить строку", Ui.GHOST, v.lore.size() < 32);

        Ui.caption(ctx, tr, "ЧАРЫ", leftX(), absY(enchY) - CAP, lw,
                v.hidden.contains("minecraft:enchantments") ? "скрыты в подсказке"
                        : v.enchants.isEmpty() ? "" : String.valueOf(v.enchants.size()));
        if (v.enchants.isEmpty()) {
            Draw.round(ctx, leftX(), absY(enchY), lw, ROW, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, "чар пока нет", leftX() + 8, absY(enchY) + 4, lw - 16,
                    Theme.TEXT_FAINT, false);
        }
        for (int r = 0; r < shownEnch(); r++) {
            int i = enchScroll + r;
            if (i >= v.enchants.size()) break;
            Value.Ench e = v.enchants.get(i);
            int ry = enchRowY(r), stepX = enchStepX();
            Draw.round(ctx, leftX(), ry, stepX - leftX() - 3, ROW, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.textFit(ctx, tr, Stacks.enchLabel(e.id, e.level), leftX() + 7, ry + 4,
                    stepX - leftX() - 14, Theme.TEXT, false);
            Ui.iconButton(ctx, mouseX, mouseY, stepX, ry, ROW, Draw.MINUS, Ui.GHOST, e.level > 1);
            Ui.iconButton(ctx, mouseX, mouseY, stepX + ROW + 1, ry, ROW, Draw.PLUS, Ui.GHOST, true);
            Ui.iconButton(ctx, mouseX, mouseY, leftX() + lw - ROW, ry, ROW, Draw.CROSS,
                    Ui.DANGER, true);
        }
        Ui.Cluster enchBtn = enchAdd();
        Ui.glyphButton(ctx, tr, mouseX, mouseY, enchBtn.x(0), enchAddY(), enchBtn.w(0), ADD_H,
                Draw.PLUS, "добавить чары", Ui.GHOST, true);

        int half = (lw - 5) / 2;
        Ui.caption(ctx, tr, "СВОЙСТВА", leftX(), absY(propY) - CAP, lw);
        Ui.toggle(ctx, tr, mouseX, mouseY, leftX(), absY(propY), half, BTN_H,
                "неразрушимый", v.unbreakable);
        Ui.toggle(ctx, tr, mouseX, mouseY, leftX() + half + 5, absY(propY), lw - half - 5, BTN_H,
                "скрыть подсказку", v.hideTooltip);
        Draw.text(ctx, tr, GLINT_LABEL, leftX(), absY(glintY) + (ROW - Ui.TEXT_H) / 2,
                Theme.TEXT_FAINT, false);
        glintChips.render(ctx, tr, mouseX, mouseY, leftX() + glintLabelW(), absY(glintY), v.glint,
                Values.color(Value.ITEM));
        Draw.text(ctx, tr, HIDE_LABEL, leftX(), absY(hideY) + (ROW - Ui.TEXT_H) / 2,
                Theme.TEXT_FAINT, false);
        hideChips.render(ctx, tr, mouseX, mouseY, leftX() + hideLabelW(), absY(hideY), hidden(),
                Values.color(Value.ITEM));
    }

    private int loreAddY() {
        return absY(loreY) + Math.max(1, Math.min(v.lore.size(), loreRows)) * (ROW + 2);
    }

    private int enchAddY() {
        return enchRowY(shownEnch());
    }

    private int maxLoreScroll() { return Math.max(0, v.lore.size() - loreRows); }
    private int maxEnchScroll() { return Math.max(0, v.enchants.size() - enchRows); }

    private int shownEnch() { return Math.max(1, Math.min(v.enchants.size(), enchRows)); }

    private int enchRowY(int r) { return absY(enchY) + r * (ROW + 2); }

    private int enchStepX() { return leftX() + lw - ROW - 2 - 2 * ROW - 2; }

    private void drawRight(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ItemStack st = Stacks.preview(v);

        Ui.caption(ctx, tr, "ПРЕДПРОСМОТР", rightX(), absY(prevY) - CAP, rw);
        Ui.well(ctx, rightX(), absY(prevY), rw, prevH);
        if (st.isEmpty()) {
            Draw.textFit(ctx, tr, "нечего показывать", rightX() + 8,
                    absY(prevY) + (prevH - Ui.TEXT_H) / 2, rw - 16, Theme.TEXT_FAINT, false);
        } else {
            List<Text> lines = Stacks.tooltip(st);
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(rightX() + 8, absY(prevY) + 6);
            m.scale(2, 2);
            ctx.drawItem(st, 0, 0);
            m.popMatrix();
            int nameX = rightX() + 46;
            ctx.drawText(tr, McText.fit(tr, McText.runsOf(
                            lines.isEmpty() ? st.getName() : lines.get(0)), rw - 54), nameX,
                    absY(prevY) + 10, Draw.opaque(lines.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT),
                    false);
            String amount = "×" + st.getCount() + " · " + v.itemId;
            Draw.textFit(ctx, tr, amount, nameX, absY(prevY) + 24, rw - 54, Theme.TEXT_FAINT, false);
            int at = absY(prevY) + 42, bottom = absY(prevY) + prevH - 4;
            if (lines.isEmpty())
                for (String line : Ui.wrap(tr,
                        "подсказка скрыта целиком: при наведении игрок не увидит ни названия, "
                                + "ни чар, ни описания. Название с предмета при этом не снято",
                        rw - 16, Math.max(1, (bottom - at) / 10))) {
                    Draw.text(ctx, tr, line, rightX() + 8, at, Theme.TEXT_FAINT, false);
                    at += 10;
                }
            for (int i = 1; i < lines.size(); i++) {
                if (at + 10 > bottom) {
                    Draw.textFit(ctx, tr, "…ещё " + (lines.size() - i), rightX() + 8, at, rw - 16,
                            Theme.TEXT_FAINT, false);
                    break;
                }
                ctx.drawText(tr, McText.fit(tr, McText.runsOf(lines.get(i)), rw - 16),
                        rightX() + 8, at, Draw.opaque(Theme.TEXT_DIM), false);
                at += 10;
            }
        }

        String error = Stacks.error(v.components);
        int count = Stacks.componentCount(v.components);
        Ui.caption(ctx, tr, "КОМПОНЕНТЫ", rightX(), absY(nbtY) - CAP, rw,
                count == 0 ? "" : String.valueOf(count));
        Ui.input(ctx, rightX(), absY(nbtY), rw, nbtH, focus == NBT);
        if (error != null)
            Draw.roundOutline(ctx, rightX(), absY(nbtY), rw, nbtH, Ui.R_SM,
                    Draw.opaque(Theme.DANGER));
        nbtBox.render(ctx, mouseX, mouseY, delta);
        if (error == null) {
            Draw.textFit(ctx, tr, v.components.isBlank()
                            ? "как в предмете: {\"minecraft:custom_data\":{…}}"
                            : "разобрано · компонентов " + count,
                    rightX() + 2, absY(nbtMsgY), rw - 4, Theme.TEXT_FAINT, false);
        } else {
            Draw.textFit(ctx, tr, error, rightX() + 2, absY(nbtMsgY), rw - 4, Theme.DANGER, false);
        }
        Ui.Cluster nbt = nbtRow();
        Ui.button(ctx, tr, mouseX, mouseY, nbt.x(0), absY(nbtBtnY), nbt.w(0), BTN_H,
                "в столбик", Ui.GHOST, !v.components.isBlank());
        Ui.button(ctx, tr, mouseX, mouseY, nbt.x(1), absY(nbtBtnY), nbt.w(1), BTN_H,
                "копировать", Ui.GHOST, true);
        Ui.button(ctx, tr, mouseX, mouseY, nbt.x(2), absY(nbtBtnY), nbt.w(2), BTN_H,
                "очистить", Ui.GHOST, !v.components.isBlank());
    }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY) {
        int fy = absY(footY) + (FOOT_H - 16) / 2;
        String hint = System.currentTimeMillis() - flashAt < 1800 ? flash : Stacks.summary(v);
        if (hint.isEmpty()) hint = "Enter — готово, Esc — отмена";
        Draw.textFit(ctx, tr, hint, x + PAD, fy + 4, inner() - 120, Theme.TEXT_FAINT, false);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 56, fy, 56, 16, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 114, fy, 52, 16, "Отмена", Ui.GHOST);
    }

    private static int parsingIndex(String id) {
        for (int i = 0; i < Values.PARSINGS.size(); i++)
            if (Values.PARSINGS.get(i).id().equals(id)) return i;
        return 0;
    }

    private void toast(String s) {
        flash = s;
        flashAt = System.currentTimeMillis();
    }

    private ItemStack held() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        ItemStack st = player.getMainHandStack();
        return st == null || st.isEmpty() ? null : st;
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        if (picker != null) {
            picker.mouseClicked(click, doubled);
            if (picker.isClosed()) picker = null;
            return true;
        }
        if (studio != null) {
            studio.mouseClicked(click, doubled);
            if (studio.isClosed()) studio = null;
            return true;
        }
        if (enchPicker != null) {
            enchPicker.mouseClicked(click, doubled);
            if (enchPicker.isClosed()) enchPicker = null;
            return true;
        }

        int mx = (int) click.x(), my = (int) click.y();
        if (Ui.hit(mx, my, x + w - PAD - 14, y + 6, 14, 14)) { closed = true; return true; }

        int half = (lw - 5) / 2;
        Ui.Cluster items = itemRow();
        if (items.hit(0, mx, my, absY(pickY), BTN_H)
                || Ui.hit(mx, my, leftX(), absY(cardY), lw, CARD_H)) {
            openPicker();
            return true;
        }
        if (items.hit(1, mx, my, absY(pickY), BTN_H)) {
            ItemStack st = held();
            if (st != null) {
                readForm();
                Stacks.read(v, st.copy());
                sync();
                toast("взято из руки");
            }
            return true;
        }

        int cw = numW();
        for (int i = 0; i < 3; i++)
            if (Ui.hit(mx, my, leftX() + i * (cw + 4), absY(numsY), cw, INPUT_H))
                return takeFocus(click, doubled, COUNT + i);

        if (Ui.hit(mx, my, leftX(), absY(nameY), lw, INPUT_H)) return takeFocus(click, doubled, NAME);
        int mi = modeChips.indexAt(mx, my, leftX(), absY(modeY));
        if (mi >= 0) {
            Values.Parsing p = Values.PARSINGS.get(mi);
            if (!p.id().equals(v.itemParsing)) {
                readForm();
                v.itemName = McText.convert(v.itemName, v.itemParsing, p.id());
                for (int i = 0; i < v.lore.size(); i++)
                    v.lore.set(i, McText.convert(v.lore.get(i), v.itemParsing, p.id()));
                v.itemParsing = p.id();
                sync();
            }
            return true;
        }
        Ui.Cluster names = nameRow();
        if (names.hit(0, mx, my, absY(nameBtnY), BTN_H)) { openStudio(-1); return true; }
        if (names.hit(1, mx, my, absY(nameBtnY), BTN_H)) {
            v.itemName = "";
            nameField.setText("");
            return true;
        }

        int shownLore = Math.max(1, Math.min(v.lore.size(), loreRows));
        for (int r = 0; r < shownLore; r++) {
            int i = loreScroll + r;
            if (i >= v.lore.size()) break;
            int ry = absY(loreY) + r * (ROW + 2);
            if (Ui.hit(mx, my, leftX() + lw - ROW, ry, ROW, ROW)) {
                v.lore.remove(i);
                loreScroll = Math.min(loreScroll, maxLoreScroll());
                relayout();
                return true;
            }
            if (Ui.hit(mx, my, leftX(), ry, lw - ROW - 2, ROW)) { openStudio(i); return true; }
        }
        if (loreAdd().hit(0, mx, my, loreAddY(), ADD_H) && v.lore.size() < 32) {
            v.lore.add("");
            loreScroll = maxLoreScroll();
            relayout();
            openStudio(v.lore.size() - 1);
            return true;
        }

        for (int r = 0; r < shownEnch(); r++) {
            int i = enchScroll + r;
            if (i >= v.enchants.size()) break;
            Value.Ench e = v.enchants.get(i);
            int ry = enchRowY(r), stepX = enchStepX();
            if (Ui.hit(mx, my, leftX() + lw - ROW, ry, ROW, ROW)) {
                v.enchants.remove(i);
                enchScroll = Math.min(enchScroll, maxEnchScroll());
                relayout();
                return true;
            }
            if (Ui.hit(mx, my, stepX, ry, ROW, ROW)) { e.level = Math.max(1, e.level - 1); return true; }
            if (Ui.hit(mx, my, stepX + ROW + 1, ry, ROW, ROW)) {
                e.level = Math.min(255, e.level + 1);
                return true;
            }
            if (Ui.hit(mx, my, leftX(), ry, stepX - leftX() - 3, ROW)) { openEnchPicker(i); return true; }
        }
        if (enchAdd().hit(0, mx, my, enchAddY(), ADD_H)) { openEnchPicker(-1); return true; }

        if (Ui.hit(mx, my, leftX(), absY(propY), half, BTN_H)) {
            v.unbreakable = !v.unbreakable;
            return true;
        }
        if (Ui.hit(mx, my, leftX() + half + 5, absY(propY), lw - half - 5, BTN_H)) {
            v.hideTooltip = !v.hideTooltip;
            return true;
        }
        int gi = glintChips.indexAt(mx, my, leftX() + glintLabelW(), absY(glintY));
        if (gi >= 0) { v.glint = gi; return true; }
        int hi = hideChips.indexAt(mx, my, leftX() + hideLabelW(), absY(hideY));
        if (hi >= 0) {
            String id = HIDE_IDS.get(hi);
            if (!v.hidden.remove(id)) v.hidden.add(id);
            return true;
        }

        if (Ui.hit(mx, my, rightX(), absY(nbtY), rw, nbtH)) {
            focus = NBT;
            focusFields();
            nbtBox.mouseClicked(click, doubled);
            return true;
        }
        Ui.Cluster nbt = nbtRow();
        if (nbt.hit(0, mx, my, absY(nbtBtnY), BTN_H)) {
            String indented = Stacks.indent(v.components);
            if (indented.equals(v.components)) toast("нечего выпрямлять");
            else { v.components = indented; nbtBox.setText(indented); }
            return true;
        }
        if (nbt.hit(1, mx, my, absY(nbtBtnY), BTN_H)) {
            String all = Stacks.print(v);
            MinecraftClient.getInstance().keyboard.setClipboard(all);
            toast(all.isEmpty() ? "копировать нечего" : "компоненты в буфере");
            return true;
        }
        if (nbt.hit(2, mx, my, absY(nbtBtnY), BTN_H)) {
            v.components = "";
            nbtBox.setText("");
            readNbt();
            return true;
        }

        int fy = absY(footY) + (FOOT_H - 16) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 56, fy, 56, 16)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 114, fy, 52, 16)) { closed = true; return true; }
        return true;
    }

    private boolean takeFocus(Click click, boolean doubled, int which) {
        focus = which;
        focusFields();
        TextFieldWidget f = widget(which);
        if (f != null && !f.mouseClicked(click, doubled)) f.onClick(click, doubled);
        return true;
    }

    private TextFieldWidget widget(int which) {
        return switch (which) {
            case NAME -> nameField;
            case COUNT -> countField;
            case DAMAGE -> damageField;
            case MODEL -> modelField;
            default -> null;
        };
    }

    private void focusFields() {
        nameField.setFocused(focus == NAME);
        countField.setFocused(focus == COUNT);
        damageField.setFocused(focus == DAMAGE);
        modelField.setFocused(focus == MODEL);
        nbtBox.setFocused(focus == NBT);
    }

    private void openPicker() {
        readForm();
        picker = new ItemPicker(tr, screenW, screenH, Values.color(Value.ITEM), Stacks.build(v),
                stack -> {
                    Stacks.read(v, stack);
                    sync();
                });
    }

    private void openStudio(int line) {
        readForm();
        studioLine = line;
        String text = line < 0 ? v.itemName
                : line < v.lore.size() ? v.lore.get(line) : "";
        studio = new TextStudio(tr, screenW, screenH, text, v.itemParsing, (edited, parsing) -> {
            v.itemParsing = parsing;
            if (studioLine < 0) {
                v.itemName = edited;
                nameField.setText(edited);
            } else if (studioLine < v.lore.size()) {
                v.lore.set(studioLine, edited);
            }
        });
    }

    private void openEnchPicker(int index) {
        readForm();
        List<CatalogPicker.Item> items = new ArrayList<>();
        for (Stacks.Ench e : Stacks.enchantments())
            items.add(new CatalogPicker.Item(e.id(), e.name(), "Чары",
                    "minecraft:enchanted_book", e.description(), "", ""));
        if (items.isEmpty()) { toast("чары этого мира не читаются"); return; }
        String current = index >= 0 && index < v.enchants.size() ? v.enchants.get(index).id : "";
        enchPicker = new CatalogPicker(tr, screenW, screenH, "Чары", Values.color(Value.ITEM),
                items, null, current, null, id -> {
                    Stacks.Ench e = Stacks.ench(id);
                    int level = e == null ? 1 : e.max();
                    if (index >= 0 && index < v.enchants.size()) {
                        v.enchants.get(index).id = id;
                        v.enchants.get(index).level = Math.min(v.enchants.get(index).level, level);
                    } else {
                        v.enchants.add(new Value.Ench(id, level));
                        enchScroll = maxEnchScroll();
                    }
                    relayout();
                });
    }

    public boolean mouseDragged(Click click, double dx, double dy) {
        if (picker != null) return picker.mouseDragged(click, dx, dy);
        if (studio != null) return studio.mouseDragged(click, dx, dy);
        if (enchPicker != null) return enchPicker.mouseDragged(click, dx, dy);
        if (focus == NBT) return nbtBox.mouseDragged(click, dx, dy);
        TextFieldWidget f = widget(focus);
        return f != null && f.mouseDragged(click, dx, dy);
    }

    public void mouseReleased() {
        if (studio != null) studio.mouseReleased();
        if (enchPicker != null) enchPicker.mouseReleased();
        if (picker != null) picker.mouseReleased();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (picker != null) return picker.mouseScrolled(mx, my, amount);
        if (studio != null) return studio.mouseScrolled(mx, my, amount);
        if (enchPicker != null) return enchPicker.mouseScrolled(mx, my, amount);
        if (Ui.hit(mx, my, rightX(), absY(nbtY), rw, nbtH))
            return nbtBox.mouseScrolled(mx, my, 0, amount);
        int shownLore = Math.max(1, Math.min(v.lore.size(), loreRows));
        if (maxLoreScroll() > 0 && Ui.hit(mx, my, leftX(), absY(loreY), lw,
                shownLore * (ROW + 2))) {
            loreScroll = Math.max(0, Math.min(maxLoreScroll(),
                    loreScroll - (int) Math.signum(amount)));
            return true;
        }
        if (maxEnchScroll() > 0 && Ui.hit(mx, my, leftX(), absY(enchY), lw,
                shownEnch() * (ROW + 2))) {
            enchScroll = Math.max(0, Math.min(maxEnchScroll(),
                    enchScroll - (int) Math.signum(amount)));
            return true;
        }
        int cw = numW();
        for (int i = 0; i < 3; i++)
            if (Ui.hit(mx, my, leftX() + i * (cw + 4), absY(numsY), cw, INPUT_H)) {
                bump(COUNT + i, (int) Math.signum(amount));
                return true;
            }
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (picker != null) {
            picker.keyPressed(input);
            if (picker.isClosed()) picker = null;
            return true;
        }
        if (studio != null) {
            studio.keyPressed(input);
            if (studio.isClosed()) studio = null;
            return true;
        }
        if (enchPicker != null) {
            enchPicker.keyPressed(input);
            if (enchPicker.isClosed()) enchPicker = null;
            return true;
        }
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            focus = (focus + 1) % 5;
            focusFields();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && focus != NBT) {
            finish();
            return true;
        }
        if (focus == NBT) {
            boolean typed = nbtBox.keyPressed(input);
            v.components = nbtBox.getText();
            readNbt();
            return typed;
        }
        TextFieldWidget f = widget(focus);
        boolean used = f != null && f.keyPressed(input);
        readForm();
        return used;
    }

    public boolean charTyped(CharInput input) {
        if (picker != null) return picker.charTyped(input);
        if (studio != null) return studio.charTyped(input);
        if (enchPicker != null) return enchPicker.charTyped(input);
        if (focus == NBT) {
            boolean typed = nbtBox.charTyped(input);
            v.components = nbtBox.getText();
            readNbt();
            return typed;
        }
        TextFieldWidget f = widget(focus);
        boolean used = f != null && f.charTyped(input);
        readForm();
        return used;
    }

    private void bump(int which, int delta) {
        TextFieldWidget f = widget(which);
        if (f == null) return;
        int now = 0;
        try {
            String s = f.getText().trim();
            if (!s.isEmpty()) now = Integer.parseInt(s);
        } catch (NumberFormatException ignored) { }
        int next = now + delta;
        if (which == COUNT) next = Math.max(1, Math.min(99, next));
        else next = Math.max(0, next);
        f.setText(String.valueOf(next));
        f.setCursorToEnd(false);
        focus = which;
        focusFields();
        readForm();
    }

    private void readForm() {
        v.itemName = nameField.getText();
        v.itemCount = Math.max(1, Math.min(99, (int) number(countField.getText(), 1)));
        v.itemDamage = Math.max(0, (int) number(damageField.getText(), 0));
        String model = modelField.getText().trim();
        v.modelData = model.isEmpty() ? -1 : Math.max(0, (int) number(model, -1));
        v.components = nbtBox.getText();
    }

    private static double number(String s, double fallback) {
        try {
            String t = s.trim();
            return t.isEmpty() ? fallback : Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void syncWidgets() {
        nameField.setText(v.itemName);
        countField.setText(String.valueOf(v.itemCount));
        damageField.setText(v.itemDamage > 0 ? String.valueOf(v.itemDamage) : "");
        modelField.setText(v.modelData >= 0 ? String.valueOf(v.modelData) : "");
        relayout();
    }

    private void sync() {
        syncWidgets();
        nbtBox.setText(v.components);
        lastSig = fieldSig();
    }

    private void relayout() {
        layout();
        focusFields();
    }

    private void finish() {
        readForm();
        writeNbt();
        done.apply(v);
        closed = true;
    }
}
