package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xerocode.code.CodeFonts;
import com.xerocode.web.Emits;
import com.xerocode.web.Glyphs;
import com.xerocode.web.Js;
import com.xerocode.web.Renderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;

final class CodeInk {
    static final int PIXEL_CELL = 6, PIXEL_ROW = 11;
    private static final String FONTS = "assets/fonts/";

    static final class Line {
        final int[] glyphs, colors;
        final float[] xs;
        final int count;

        Line(int[] glyphs, int[] colors, float[] xs, int count) {
            this.glyphs = glyphs;
            this.colors = colors;
            this.xs = xs;
            this.count = count;
        }
    }

    private static final class Placed implements Font.PreparedText {
        private final Line line;
        private final float x, y;

        Placed(Line line, float x, float y) {
            this.line = line;
            this.x = x;
            this.y = y;
        }

        @Override
        public void visit(Font.GlyphVisitor visitor) {
            for (int i = 0; i < line.count; i++)
                visitor.glyph(line.glyphs[i], x + line.xs[i], y, line.colors[i], 0, Style.EMPTY, false);
        }

        @Override
        public ScreenRectangle bounds() { return new ScreenRectangle((int) x, (int) y, 4000, 12); }
    }

    private static final class Run implements GuiElementRenderState, Emits {
        private final Matrix3x2f pose;
        private final ScreenRectangle clip;
        private final List<Line> lines = new ArrayList<>();
        private int[] at = new int[128];

        Run(Matrix3x2f pose, ScreenRectangle clip) {
            this.pose = pose;
            this.clip = clip;
        }

        void add(Line line, int x, int y) {
            int n = lines.size();
            if (n * 2 + 2 > at.length) at = Arrays.copyOf(at, at.length * 2);
            at[n * 2] = x;
            at[n * 2 + 1] = y;
            lines.add(line);
        }

        @Override
        public void emit(Renderer r) {
            r.enter(Js.codeTexture(), false, Renderer.BLEND_ALPHA, clip);
            int cols = atlasCols, cw = atlasCellW, ch = atlasCellH;
            float s = 1f / atlasSize;
            for (int k = 0; k < lines.size(); k++) {
                Line l = lines.get(k);
                int ox = at[k * 2], oy = at[k * 2 + 1];
                for (int i = 0; i < l.count; i++) {
                    int g = l.glyphs[i];
                    int px = g % cols * cw, py = g / cols * ch;
                    float x0 = ox + l.xs[i], y0 = oy;
                    r.codeBox(pose, x0, y0, x0 + cw, y0 + ch, px * s, py * s, (px + cw) * s, (py + ch) * s, l.colors[i]);
                }
            }
        }

        @Override public void buildVertices(VertexConsumer vc) {}

        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }

        @Override public TextureSetup textureSetup() { return null; }

        @Override public ScreenRectangle scissorArea() { return clip; }

        @Override public ScreenRectangle bounds() { return clip; }
    }

    private static ScreenRectangle clip;
    private static Run run;
    private static String faceId = "";
    private static int facePx;
    private static boolean pixel = true;
    private static int cell = PIXEL_CELL, row = PIXEL_ROW, stamp = 1, seen;
    private static int atlasCols = 1, atlasCellW = 1, atlasCellH = 1, atlasSize = 1;

    private CodeInk() {}

    static int stamp() {
        sync();
        return stamp;
    }

    static boolean pixel() { return pixel; }

    static int cell() { return cell; }

    static int row() { return row; }

    private static void sync() {
        if (pixel) return;
        int now = Js.codeInfo(0);
        if (now == seen) return;
        seen = now;
        cell = Js.codeInfo(1);
        row = Js.codeInfo(2);
        atlasCellW = Js.codeInfo(3);
        atlasCellH = Js.codeInfo(4);
        atlasCols = Js.codeInfo(5);
        atlasSize = Js.codeInfo(6);
        stamp++;
    }

    static void use(String id, int px) {
        if (id.equals(faceId) && px == facePx) {
            sync();
            return;
        }
        faceId = id;
        facePx = px;
        stamp++;
        pixel = CodeFonts.PIXEL.equals(id);
        if (pixel) {
            cell = PIXEL_CELL;
            row = PIXEL_ROW;
            return;
        }
        if (id.startsWith(CodeFonts.CUSTOM)) {
            String name = CodeFonts.title(id);
            try {
                byte[] bytes = Files.readAllBytes(Path.of(id.substring(CodeFonts.CUSTOM.length())));
                Js.codeFontBytes(name, Base64.getEncoder().encodeToString(bytes), px);
            } catch (Exception e) {
                Js.codeFont("monospace", "", px);
            }
        } else {
            CodeFonts.Entry e = CodeFonts.find(id);
            if (e == null) e = CodeFonts.LIST.get(0);
            Js.codeFont(e.title(), FONTS + e.file(), px);
        }
        seen = 0;
        sync();
    }

    static Line bake(Font tr, String text, int[] colors, int[] xs) {
        int n = text.length();
        int[] glyphs = new int[n];
        int[] argb = new int[n];
        float[] at = new float[n];
        int count = 0;
        for (int i = 0; i < n; ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (cp != ' ' && cp != '\t') {
                if (!pixel) {
                    glyphs[count] = Js.codeGlyph(cp);
                    argb[count] = colors[i];
                    at[count] = xs[i];
                    count++;
                } else {
                    int glyph = Glyphs.pick(cp, false);
                    if (Glyphs.visible(glyph)) {
                        int advance = (int) Math.ceil(Glyphs.advance(cp, false));
                        glyphs[count] = glyph;
                        argb[count] = colors[i];
                        at[count] = xs[i] + Math.max(0, (PIXEL_CELL - advance) / 2);
                        count++;
                    }
                }
            }
            i += len;
        }
        return new Line(glyphs, argb, at, count);
    }

    static void begin(GuiGraphicsExtractor ctx, ScreenRectangle area) {
        clip = area;
        if (pixel) return;
        run = new Run(new Matrix3x2f(ctx.pose()), area);
        ctx.guiRenderState.addBlitToCurrentLayer(run);
    }

    static void draw(GuiGraphicsExtractor ctx, Line line, int x, int y) {
        if (line.count == 0) return;
        if (run != null) run.add(line, x, y);
        else ctx.guiRenderState.addText(new GuiTextRenderState(new Matrix3x2f(ctx.pose()), new Placed(line, x, y), clip));
    }

    static void end() {
        clip = null;
        run = null;
    }
}
