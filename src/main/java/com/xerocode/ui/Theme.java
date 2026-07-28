package com.xerocode.ui;

public final class Theme {
    public static int CANVAS        = 0x0F1117;
    public static int GRID_DOT      = 0x232833;
    public static int GRID_DOT_BIG  = 0x333B4C;
    public static int PANEL         = 0x171A21;
    public static int PANEL_RAISED  = 0x1D212A;
    public static int LINE          = 0x272C37;
    public static int SURFACE       = 0x232833;
    public static int SURFACE_HOVER = 0x2E3441;
    public static int SURFACE_DOWN  = 0x3B4354;

    public static int TEXT          = 0xEEF1F6;
    public static int TEXT_DIM      = 0x9BA5B7;
    public static int TEXT_FAINT    = 0x6B7488;
    public static int ON_ACCENT     = 0xFFFFFF;

    public static int ACCENT        = 0x59A6FF;
    public static int DANGER        = 0xFF7373;
    public static int OK            = 0x63D68E;

    public static int GRID          = 0x188FA0C0;
    public static int GRID_STRONG   = 0x2A8FA0C0;
    public static int SHADOW        = 0x3A000000;
    public static int SHADOW_SOFT   = 0x18000000;
    public static int SCRIM         = 0xC005070B;

    public static boolean LIGHT;

    public static int MARKER_BORDER = 0x424B5B;
    public static int MARKER_TOP    = 0x2A303D;
    public static int MARKER_BOTTOM = 0x1E232D;

    public static int PALETTE_W = 218;
    public static final int PALETTE_MIN_W = 150;
    public static final int PALETTE_MAX_W = 460;
    public static final int TOPBAR_H  = 30;

    public static final int SEARCH_H     = 20;
    public static final int CRUMB_H      = 20;
    public static final int ROW_ACTION_H = 20;
    public static final int ROW_CAT_H    = 24;
    public static final int ROW_HEAD_H   = 17;
    public static final int ROW_GAP      = 2;

    public static void dark() {
        LIGHT         = false;
        CANVAS        = 0x0F1117;
        GRID_DOT      = 0x232833;
        GRID_DOT_BIG  = 0x333B4C;
        PANEL         = 0x171A21;
        PANEL_RAISED  = 0x1D212A;
        LINE          = 0x272C37;
        SURFACE       = 0x232833;
        SURFACE_HOVER = 0x2E3441;
        SURFACE_DOWN  = 0x3B4354;
        TEXT          = 0xEEF1F6;
        TEXT_DIM      = 0x9BA5B7;
        TEXT_FAINT    = 0x6B7488;
        ON_ACCENT     = 0xFFFFFF;
        ACCENT        = 0x59A6FF;
        DANGER        = 0xFF7373;
        OK            = 0x63D68E;
        GRID          = 0x188FA0C0;
        GRID_STRONG   = 0x2A8FA0C0;
        SHADOW        = 0x3A000000;
        SHADOW_SOFT   = 0x18000000;
        SCRIM         = 0xC005070B;
        MARKER_BORDER = 0x424B5B;
        MARKER_TOP    = 0x2A303D;
        MARKER_BOTTOM = 0x1E232D;

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
    }

    public static void light() {
        LIGHT         = true;
        CANVAS        = 0xE9ECF2;
        GRID_DOT      = 0xC9D0DC;
        GRID_DOT_BIG  = 0xB2BCCC;
        PANEL         = 0xFFFFFF;
        PANEL_RAISED  = 0xF4F6FA;
        LINE          = 0xD9DEE7;
        SURFACE       = 0xE7EBF2;
        SURFACE_HOVER = 0xD8DFEA;
        SURFACE_DOWN  = 0xC4CEDD;
        TEXT          = 0x1A1F28;
        TEXT_DIM      = 0x4E586A;
        TEXT_FAINT    = 0x818B9C;
        ON_ACCENT     = 0xFFFFFF;
        ACCENT        = 0x2F7BE0;
        DANGER        = 0xC93B3B;
        OK            = 0x2C9557;
        GRID          = 0x1C4A5A78;
        GRID_STRONG   = 0x3A4A5A78;
        SHADOW        = 0x1E1B2333;
        SHADOW_SOFT   = 0x0E1B2333;
        SCRIM         = 0x662A3040;
        MARKER_BORDER = 0xA9B3C3;
        MARKER_TOP    = 0xF3F5FA;
        MARKER_BOTTOM = 0xE2E7F0;

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
    }

    private Theme() {}
}
