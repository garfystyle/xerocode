package com.xerocode.ui;

import com.xerocode.ModulePublish;
import com.xerocode.Modules;
import com.xerocode.Veil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

final class PublishModuleScreen extends DialogScreen {
    private static final String TITLE = "МОДУЛЬ JUSTMC";
    private static final String DONE_TITLE = "МОДУЛЬ ВЫЛОЖЕН", FAIL_TITLE = "НЕ ВЫЛОЖИЛСЯ";
    private static final String PUBLISH = "Выложить", CANCEL = "Отмена", CLOSE = "Готово";
    private static final String TIP = "Описание, иконку и цену меняют в /module → «Мои модули» → ПКМ по модулю.";
    private static final int FIELD_H = 20, ROW_H = 18;
    private static final String OPEN = "сразу сделать публичным — ставить смогут все";

    private final Screen parent;
    private final List<Integer> positions;
    private final Ui.Grab grab = new Ui.Grab();
    private EditBox id;
    private String note = "";
    private boolean failed;
    private String result;
    private boolean ok;
    private int category = Modules.CATEGORIES.size() - 1;
    private boolean makePublic = true;
    private String running;

    PublishModuleScreen(Screen parent, List<Integer> positions) {
        super(360);
        this.parent = parent;
        this.positions = positions;
    }

    private PublishModuleScreen(Screen parent, boolean ok, String result) {
        this(parent, List.of());
        this.ok = ok;
        this.result = result;
    }

    @Override
    protected void init() {
        if (result != null) return;
        String text = id == null ? "" : id.getValue();
        id = Ui.field(font, 0, 0, 40, 12, "айди: 3–12 символов");
        id.setMaxLength(12);
        id.setValue(text);
        addWidget(id);
        setFocused(id);
        id.setFocused(true);
    }

    private String how() {
        return "Строк в модуле: " + positions.size() + ". Мод выделит их манипулятором в /dev и сохранит модулем "
                + "под этим айди (латиница, цифры, _ и -).";
    }

    @Override
    protected String title() {
        if (running != null) return "ВЫКЛАДЫВАЮ " + id.getValue().trim();
        return result == null ? TITLE : ok ? DONE_TITLE : FAIL_TITLE;
    }

    @Override
    protected int accent() { return result != null && !ok ? Theme.DANGER : Theme.ACCENT; }

    private int fieldY() { return bodyY() + paragraphRows(how(), bodyW()) * ROW + GAP; }

    private int sectionY() { return fieldY() + FIELD_H + 5 + ROW; }

    private int publicY() { return sectionY() + ROW_H + 4; }

    private String section() { return "Раздел: " + Modules.CATEGORIES.get(category).label() + "  ›"; }

    @Override
    protected int bodyH() {
        if (running != null) return ROW * 2 + GAP;
        if (result != null)
            return (paragraphRows(result, bodyW()) + (ok ? paragraphRows(TIP, bodyW()) + 1 : 0)) * ROW + GAP + BTN_H;
        return paragraphRows(how(), bodyW()) * ROW + GAP + FIELD_H + 5 + ROW + ROW_H + 4 + ROW_H + GAP + BTN_H;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w) {
        if (running != null) {
            Draw.textFit(ctx, font, running, x, y, w, Theme.TEXT, false);
            Draw.textFit(ctx, font, "мод сам выделяет строки и сохраняет модуль — несколько секунд", x, y + ROW,
                    w, Theme.TEXT_FAINT, false);
            return;
        }
        if (result != null) {
            int rows = paragraph(ctx, result, x, y, w, ok ? Theme.TEXT : Theme.DANGER);
            if (ok) paragraph(ctx, TIP, x, y + (rows + 1) * ROW, w, Theme.TEXT_DIM);
            buttons(ctx, mouseX, mouseY, x, w, CLOSE, null);
            return;
        }
        paragraph(ctx, how(), x, y, w, Theme.TEXT_DIM);
        int fy = fieldY();
        Ui.input(ctx, x, fy, w, FIELD_H, id.isFocused());
        id.setX(x + 6);
        id.setY(fy + (FIELD_H - Ui.TEXT_H) / 2);
        Ui.width(id, w - 12);
        id.extractRenderState(ctx, mouseX, mouseY, 0);
        if (!note.isEmpty())
            Draw.textFit(ctx, font, note, x, fy + FIELD_H + 5, w, failed ? Theme.DANGER : Theme.TEXT_FAINT, false);
        Ui.button(ctx, font, mouseX, mouseY, x, sectionY(), Math.min(w, Ui.buttonW(font, section())), ROW_H,
                section(), Ui.GHOST);
        int py = publicY();
        boolean hot = Ui.hit(mouseX, mouseY, x, py, w, ROW_H);
        int box = 9, by = py + (ROW_H - box) / 2;
        Draw.roundOutline(ctx, x, by, box, box, 2, Draw.opaque(makePublic ? Theme.ACCENT : Theme.TEXT_FAINT));
        if (makePublic) Draw.glyph(ctx, Draw.CHECK, x + (box - Draw.glyphW(Draw.CHECK)) / 2,
                by + (box - Draw.glyphH(Draw.CHECK)) / 2, Theme.ACCENT);
        Draw.textFit(ctx, font, OPEN, x + box + 6, py + (ROW_H - Ui.TEXT_H) / 2, w - box - 6,
                hot ? Theme.TEXT : Theme.TEXT_DIM, false);
        buttons(ctx, mouseX, mouseY, x, w, PUBLISH, CANCEL);
    }

    @Override
    protected boolean onClick(double mx, double my) {
        if (running != null) return true;
        if (result != null) {
            if (hitPrimary(mx, my, CLOSE)) onClose();
            return true;
        }
        if (Ui.hit(mx, my, bodyX(), fieldY(), bodyW(), FIELD_H)) return false;
        if (Ui.hit(mx, my, bodyX(), sectionY(), Math.min(bodyW(), Ui.buttonW(font, section())), ROW_H)) {
            category = (category + 1) % Modules.CATEGORIES.size();
            return true;
        }
        if (Ui.hit(mx, my, bodyX(), publicY(), bodyW(), ROW_H)) {
            makePublic = !makePublic;
            return true;
        }
        if (hitPrimary(mx, my, PUBLISH)) { publish(); return true; }
        if (hitGhost(mx, my, PUBLISH, CANCEL)) { onClose(); return true; }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (result == null && Ui.hit(click.x(), click.y(), bodyX(), fieldY(), bodyW(), FIELD_H)) {
            setFocused(id);
            id.setFocused(true);
            grab.take(id);
            if (!id.mouseClicked(click, doubled)) id.onClick(click, doubled);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return grab.drag(click, dx, dy) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        grab.release();
        return super.mouseReleased(click);
    }

    @Override
    protected boolean onEnter() {
        if (running != null) return true;
        if (result != null) onClose();
        else publish();
        return true;
    }

    private void publish() {
        String name = id.getValue().trim();
        if (!ModulePublish.ID.matcher(name).matches()) {
            note = "айди: 3–12 символов — латиница, цифры, _ и -";
            failed = true;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen back = parent;
        String refused = ModulePublish.start(positions, name, Modules.CATEGORIES.get(category).label(), makePublic,
                new ModulePublish.Report() {
            @Override
            public void step(String text) {
                running = text;
            }

            @Override
            public void done(boolean success, String text) {
                mc.gui.setScreen(new PublishModuleScreen(back, success, text));
            }
        });
        if (!refused.isEmpty()) {
            note = refused;
            failed = true;
            return;
        }
        if (running == null) running = "начинаю";
        Veil.show(this);
    }

    @Override
    protected void onEscape() {
        if (running == null) onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
