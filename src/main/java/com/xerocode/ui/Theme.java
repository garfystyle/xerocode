package com.xerocode.ui;

import com.xerocode.Env;

public final class Theme {
    public static int CANVAS;
    public static int PANEL;
    public static int PANEL_RAISED;
    public static int LINE;
    public static int SURFACE;
    public static int SURFACE_HOVER;

    public static int TEXT;
    public static int TEXT_DIM;
    public static int TEXT_FAINT;
    public static int ON_ACCENT;
    public static int ON_FILL;

    public static int ACCENT;
    public static int DANGER;
    public static int OK;
    public static int WARN;

    public static int GRID;
    public static int GRID_STRONG;
    public static int SHADOW;
    public static int SHADOW_SOFT;
    public static int SCRIM;

    public static boolean LIGHT;

    public static int MARKER_BORDER;
    public static int MARKER_TOP;
    public static int MARKER_BOTTOM;

    public static int GHOST;

    public static int CHIP_TOP;
    public static int CHIP_BOTTOM;
    public static int PEER_BACK;
    public static int KNOB_OFF;
    public static int KNOB_DOT;
    public static int THUMB;
    public static int THUMB_HOT;

    public static int PALETTE_W = 218;
    public static final int PALETTE_MIN_W = 150;
    public static final int PALETTE_MAX_W = 460;
    public static final int STATUS_H  = 14;

    public static int topH() { return Env.touch() ? 38 : 30; }

    public static final int SEARCH_H     = 20;
    public static final int ROW_ACTION_H = 20;
    public static final int ROW_CAT_H    = 24;
    public static final int ROW_HEAD_H   = 17;
    public static final int ROW_GAP      = 2;

    private static final int[] DARK_DIM = {0x70, 0xC0, 0xE8};
    private static final int[] LIGHT_DIM = {0x30, 0x66, 0xA0};
    private static final int[] DARK_SAMPLE = {0x0F1117, 0x171A21, 0x232833, 0xEEF1F6, 0x59A6FF, 0x272C37};
    private static final int[] LIGHT_SAMPLE = {0xE9ECF2, 0xFFFFFF, 0xE7EBF2, 0x1A1F28, 0x2F7BE0, 0xD9DEE7};

    static {
        dark();
    }

    public static void paint(boolean light, Gamma gamma, int dim) {
        if (light) light(); else dark();
        if (gamma != null) tune(gamma);
        SCRIM = (light ? LIGHT_DIM : DARK_DIM)[dim] << 24 | SCRIM & 0xFFFFFF;
    }

    public static int[] sample(boolean light, Gamma gamma) {
        int[] base = light ? LIGHT_SAMPLE : DARK_SAMPLE;
        if (gamma == null) return base;
        return new int[]{gamma.surface(base[0]), gamma.surface(base[1]), gamma.surface(base[2]),
                gamma.plainTint(base[3]), gamma.accent(base[4]), gamma.edge(base[5])};
    }

    private static void tune(Gamma g) {
        CANVAS = g.surface(CANVAS);
        PANEL = g.surface(PANEL);
        PANEL_RAISED = g.surface(PANEL_RAISED);
        LINE = g.edge(LINE);
        SURFACE = g.surface(SURFACE);
        SURFACE_HOVER = g.surface(SURFACE_HOVER);
        TEXT_DIM = g.ink(TEXT_DIM, TEXT);
        TEXT_FAINT = g.ink(TEXT_FAINT, TEXT);
        TEXT = g.plainTint(TEXT);
        ACCENT = g.accent(ACCENT);
        ON_FILL = Gamma.onFill(ACCENT);
        GRID = g.plainTint(GRID);
        GRID_STRONG = g.plainTint(GRID_STRONG);
        SCRIM = g.plainTint(SCRIM);
        MARKER_BORDER = g.edge(MARKER_BORDER);
        MARKER_TOP = g.surface(MARKER_TOP);
        MARKER_BOTTOM = g.surface(MARKER_BOTTOM);
        GHOST = g.surface(GHOST);
        CHIP_TOP = g.surface(CHIP_TOP);
        CHIP_BOTTOM = g.surface(CHIP_BOTTOM);
        PEER_BACK = g.surface(PEER_BACK);
        KNOB_OFF = g.surface(KNOB_OFF);
        KNOB_DOT = g.plainTint(KNOB_DOT);
        THUMB = g.plainTint(THUMB);
        THUMB_HOT = g.plainTint(THUMB_HOT);

        Ui.PANEL = g.surface(Ui.PANEL);
        Ui.HEAD = g.surface(Ui.HEAD);
        Ui.RAIL = g.surface(Ui.RAIL);
        Ui.WELL = g.surface(Ui.WELL);
        Ui.INPUT = g.surface(Ui.INPUT);
        Ui.LINE = g.edge(Ui.LINE);
        Ui.LINE_IN = g.edge(Ui.LINE_IN);
        Ui.BORDER = g.edge(Ui.BORDER);
        Ui.BTN = g.surface(Ui.BTN);
        Ui.BTN_HOVER = g.surface(Ui.BTN_HOVER);
        Ui.ROW_HOVER = g.surface(Ui.ROW_HOVER);
        Ui.BTN_ON = g.accented(Ui.BTN_ON);
        Ui.PRIMARY = g.accented(Ui.PRIMARY);
        Ui.PRIMARY_H = g.accented(Ui.PRIMARY_H);
        Ui.ROW_ON = g.accented(Ui.ROW_ON);
    }

    public static void dark() {
        LIGHT         = false;
        CANVAS        = 0x0F1117;
        PANEL         = 0x171A21;
        PANEL_RAISED  = 0x1D212A;
        LINE          = 0x272C37;
        SURFACE       = 0x232833;
        SURFACE_HOVER = 0x2E3441;
        TEXT          = 0xEEF1F6;
        TEXT_DIM      = 0x9BA5B7;
        TEXT_FAINT    = 0x6B7488;
        ON_ACCENT     = 0xFFFFFF;
        ON_FILL       = 0xFFFFFF;
        ACCENT        = 0x59A6FF;
        DANGER        = 0xFF7373;
        OK            = 0x63D68E;
        WARN          = 0xFFD24A;
        GRID          = 0x188FA0C0;
        GRID_STRONG   = 0x2A8FA0C0;
        SHADOW        = 0x3A000000;
        SHADOW_SOFT   = 0x18000000;
        SCRIM         = 0xC005070B;
        MARKER_BORDER = 0x424B5B;
        MARKER_TOP    = 0x2A303D;
        MARKER_BOTTOM = 0x1E232D;
        GHOST         = 0x5A6579;
        CHIP_TOP      = 0xB411151D;
        CHIP_BOTTOM   = 0xB4090C12;
        PEER_BACK     = 0x141821;
        KNOB_OFF      = 0x39414F;
        KNOB_DOT      = 0x8A93A6;
        THUMB         = 0x5A6478;
        THUMB_HOT     = 0x8D9AB4;

        Ui.PANEL   = 0x171A21;
        Ui.HEAD    = 0x1C212B;
        Ui.RAIL    = 0x13161D;
        Ui.WELL    = 0x11141A;
        Ui.INPUT   = 0x0E1116;
        Ui.LINE    = 0x272C37;
        Ui.LINE_IN = 0x333B49;
        Ui.BORDER  = 0x3D4757;
        Ui.BTN       = 0x1E232D;
        Ui.BTN_HOVER = 0x2C3441;
        Ui.BTN_ON    = 0x2F4E76;
        Ui.PRIMARY   = 0x38506E;
        Ui.PRIMARY_H = 0x4E8FE0;
        Ui.DANGER_BG = 0x4A2A32;
        Ui.ROW_ON    = 0x22405F;
        Ui.ROW_HOVER = 0x1C222D;
        Ui.DANGER_WELL = 0x2A2028;
    }

    public static void light() {
        LIGHT         = true;
        CANVAS        = 0xE9ECF2;
        PANEL         = 0xFFFFFF;
        PANEL_RAISED  = 0xF4F6FA;
        LINE          = 0xD9DEE7;
        SURFACE       = 0xE7EBF2;
        SURFACE_HOVER = 0xD8DFEA;
        TEXT          = 0x1A1F28;
        TEXT_DIM      = 0x4E586A;
        TEXT_FAINT    = 0x818B9C;
        ON_ACCENT     = 0xFFFFFF;
        ON_FILL       = 0xFFFFFF;
        ACCENT        = 0x2F7BE0;
        DANGER        = 0xC93B3B;
        OK            = 0x2C9557;
        WARN          = 0xA36A00;
        GRID          = 0x1C4A5A78;
        GRID_STRONG   = 0x3A4A5A78;
        SHADOW        = 0x1E1B2333;
        SHADOW_SOFT   = 0x0E1B2333;
        SCRIM         = 0x662A3040;
        MARKER_BORDER = 0xA9B3C3;
        MARKER_TOP    = 0xF3F5FA;
        MARKER_BOTTOM = 0xE2E7F0;
        GHOST         = 0xBFC8D8;
        CHIP_TOP      = 0xC4F4F6FA;
        CHIP_BOTTOM   = 0xC4E4E8F0;
        PEER_BACK     = 0xF4F6FA;
        KNOB_OFF      = 0x39414F;
        KNOB_DOT      = 0x8A93A6;
        THUMB         = 0x5A6478;
        THUMB_HOT     = 0x8D9AB4;

        Ui.PANEL   = 0xFFFFFF;
        Ui.HEAD    = 0xF1F4FA;
        Ui.RAIL    = 0xF2F4F8;
        Ui.WELL    = 0xF2F4F8;
        Ui.INPUT   = 0xFFFFFF;
        Ui.LINE    = 0xDDE2EA;
        Ui.LINE_IN = 0xC6CDD9;
        Ui.BORDER  = 0xB6C0CE;
        Ui.BTN       = 0xE7EBF2;
        Ui.BTN_HOVER = 0xD7DEEA;
        Ui.BTN_ON    = 0x2F7BE0;
        Ui.PRIMARY   = 0x2F7BE0;
        Ui.PRIMARY_H = 0x4E93EA;
        Ui.DANGER_BG = 0xF2D3D3;
        Ui.ROW_ON    = 0xD6E5FA;
        Ui.ROW_HOVER = 0xE8EDF5;
        Ui.DANGER_WELL = 0xF8E7E7;
    }

    private Theme() {}
}
