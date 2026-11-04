package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xerocode.XeroCode;
import com.xerocode.code.CodeFonts;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3x2f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FreeType;

final class CodeInk {
    static final int PIXEL_CELL = 6, PIXEL_ROW = 11;
    private static final int ATLAS = 1024, GAP = 1;
    private static final int TARGET_LIGHT = 1 << 16, LOAD = FreeType.FT_LOAD_RENDER | TARGET_LIGHT;
    private static final byte[] CURVE = new byte[256];

    static {
        for (int i = 0; i < 256; i++) CURVE[i] = (byte) Math.round(255 * Math.pow(i / 255.0, 0.82));
    }

    static final class Line {
        final TextRenderable[] glyphs;
        final int[] boxes, colors;
        final float[] uvs;
        final int count;

        Line(TextRenderable[] glyphs) {
            this.glyphs = glyphs;
            boxes = colors = null;
            uvs = null;
            count = 0;
        }

        Line(int[] boxes, float[] uvs, int[] colors, int count) {
            this.glyphs = null;
            this.boxes = boxes;
            this.uvs = uvs;
            this.colors = colors;
            this.count = count;
        }
    }

    private record Glyph(int left, int top, int w, int h, float u0, float v0, float u1, float v1) {}

    private static final class Face {
        final ByteBuffer data;
        final FT_Face face;
        final int cell, row, base;
        final DynamicTexture texture;
        final Map<Integer, Glyph> glyphs = new HashMap<>();
        int penX = GAP, penY = GAP, shelf;
        boolean dirty;

        Face(ByteBuffer data, FT_Face face, int cell, int row, int base) {
            this.data = data;
            this.face = face;
            this.cell = cell;
            this.row = row;
            this.base = base;
            texture = new DynamicTexture(() -> "xerocode code font", ATLAS, ATLAS, true);
            texture.getPixels().fillRect(0, 0, ATLAS, ATLAS, 0);
        }

        void close() {
            texture.close();
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                FreeType.FT_Done_Face(face);
            }
            MemoryUtil.memFree(data);
        }
    }

    private static final class Run implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final ScreenRectangle clip;
        private final TextureSetup setup;
        private final List<Line> lines = new ArrayList<>();
        private int[] at = new int[128];

        Run(Matrix3x2f pose, ScreenRectangle clip, TextureSetup setup) {
            this.pose = pose;
            this.clip = clip;
            this.setup = setup;
        }

        void add(Line line, int x, int y) {
            int n = lines.size();
            if (n * 2 + 2 > at.length) at = Arrays.copyOf(at, at.length * 2);
            at[n * 2] = x;
            at[n * 2 + 1] = y;
            lines.add(line);
        }

        private void corner(VertexConsumer vc, float x, float y, float u, float v, int color) {
            vc.addVertex(pose.m00() * x + pose.m10() * y + pose.m20(), pose.m01() * x + pose.m11() * y + pose.m21(), 0)
                    .setUv(u, v).setColor(color);
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int k = 0; k < lines.size(); k++) {
                Line l = lines.get(k);
                int ox = at[k * 2], oy = at[k * 2 + 1];
                for (int i = 0; i < l.count; i++) {
                    int b = i * 4;
                    float x0 = ox + l.boxes[b], y0 = oy + l.boxes[b + 1], x1 = ox + l.boxes[b + 2], y1 = oy + l.boxes[b + 3];
                    float u0 = l.uvs[b], v0 = l.uvs[b + 1], u1 = l.uvs[b + 2], v1 = l.uvs[b + 3];
                    int c = l.colors[i];
                    corner(vc, x0, y0, u0, v0, c);
                    corner(vc, x0, y1, u0, v1, c);
                    corner(vc, x1, y1, u1, v1, c);
                    corner(vc, x1, y0, u1, v0, c);
                }
            }
        }

        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }

        @Override public TextureSetup textureSetup() { return setup; }

        @Override public ScreenRectangle scissorArea() { return clip; }

        @Override public ScreenRectangle bounds() { return null; }
    }

    private static Face face;
    private static String faceId = "";
    private static int facePx;
    private static int stamp = 1;
    private static Run run;

    private CodeInk() {}

    static int stamp() { return stamp; }

    static boolean pixel() { return face == null; }

    static int cell() { return face == null ? PIXEL_CELL : face.cell; }

    static int row() { return face == null ? PIXEL_ROW : face.row; }

    static void use(String id, int px) {
        if (id.equals(faceId) && px == facePx) return;
        drop();
        faceId = id;
        facePx = px;
        stamp++;
        if (CodeFonts.PIXEL.equals(id)) return;
        try {
            face = open(id, px);
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] шрифт кода не открылся: " + id, e);
            face = null;
        }
    }

    private static void drop() {
        if (face != null) face.close();
        face = null;
    }

    private static Face open(String id, int px) {
        byte[] bytes = CodeFonts.bytes(id);
        if (bytes == null) throw new IllegalStateException("нет файла шрифта");
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes).flip();
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.mallocPointer(1);
                FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, 0L, out), "шрифт");
                FT_Face ft = FT_Face.create(out.get(0));
                FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(ft, 0, px), "размер шрифта");
                FT_Size_Metrics m = ft.size().metrics();
                int ascent = (int) (m.ascender() >> 6), descent = (int) (-m.descender() >> 6);
                int row = Math.max(px + 2, ascent + descent + Math.max(1, px / 6));
                FreeTypeUtil.assertError(FreeType.FT_Load_Char(ft, 'M', LOAD), "глиф");
                int cell = Math.max(1, (int) Math.round(ft.glyph().advance().x() / 64.0));
                int base = (row - ascent - descent) / 2 + ascent;
                return new Face(data, ft, cell, row, base);
            }
        }
    }

    private static Glyph glyph(Face f, int cp) {
        Glyph known = f.glyphs.get(cp);
        if (known != null) return known;
        Glyph made;
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            if (FreeTypeUtil.checkError(FreeType.FT_Load_Char(f.face, cp, LOAD), "глиф")) return null;
            FT_GlyphSlot slot = f.face.glyph();
            FT_Bitmap bitmap = slot.bitmap();
            int w = bitmap.width(), h = bitmap.rows(), pitch = bitmap.pitch();
            if (w == 0 || h == 0) {
                made = new Glyph(0, 0, 0, 0, 0, 0, 0, 0);
            } else {
                if (f.penX + w + GAP >= ATLAS) {
                    f.penX = GAP;
                    f.penY += f.shelf + GAP;
                    f.shelf = 0;
                }
                if (f.penY + h + GAP >= ATLAS) {
                    reset(f);
                    return null;
                }
                ByteBuffer src = bitmap.buffer(Math.abs(pitch) * h);
                NativeImage image = f.texture.getPixels();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int a = CURVE[src.get(y * pitch + x) & 0xFF] & 0xFF;
                        image.setPixel(f.penX + x, f.penY + y, a << 24 | 0xFFFFFF);
                    }
                }
                float s = 1f / ATLAS;
                made = new Glyph(slot.bitmap_left(), slot.bitmap_top(), w, h, f.penX * s, f.penY * s,
                        (f.penX + w) * s, (f.penY + h) * s);
                f.penX += w + GAP;
                f.shelf = Math.max(f.shelf, h);
                f.dirty = true;
            }
        }
        f.glyphs.put(cp, made);
        return made;
    }

    private static void reset(Face f) {
        f.glyphs.clear();
        f.penX = GAP;
        f.penY = GAP;
        f.shelf = 0;
        f.texture.getPixels().fillRect(0, 0, ATLAS, ATLAS, 0);
        f.dirty = true;
        stamp++;
    }

    static Line bake(Font tr, String text, int[] colors, int[] xs) {
        return face == null ? pixelLine(tr, text, colors, xs) : faceLine(face, text, colors, xs);
    }

    private static Line faceLine(Face f, String text, int[] colors, int[] xs) {
        int n = text.length();
        int[] boxes = new int[n * 4], argb = new int[n];
        float[] uvs = new float[n * 4];
        int count = 0;
        for (int i = 0; i < n; ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (cp != ' ' && cp != '\t') {
                Glyph g = glyph(f, cp);
                if (g != null && g.w > 0) {
                    int b = count * 4;
                    boxes[b] = xs[i] + g.left;
                    boxes[b + 1] = f.base - g.top;
                    boxes[b + 2] = boxes[b] + g.w;
                    boxes[b + 3] = boxes[b + 1] + g.h;
                    uvs[b] = g.u0;
                    uvs[b + 1] = g.v0;
                    uvs[b + 2] = g.u1;
                    uvs[b + 3] = g.v1;
                    argb[count] = colors[i];
                    count++;
                }
            }
            i += len;
        }
        return new Line(boxes, uvs, argb, count);
    }

    private static Line pixelLine(Font tr, String text, int[] colors, int[] xs) {
        List<TextRenderable> out = new ArrayList<>();
        Font.GlyphVisitor sink = new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) { add(glyph); }

            @Override
            public void acceptEffect(TextRenderable rect) { add(rect); }

            private void add(TextRenderable r) {
                TextRenderable baked = SmoothText.bake(r);
                if (baked != null) out.add(baked);
            }
        };
        int n = text.length();
        for (int i = 0; i < n; ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (cp != ' ' && cp != '\t') {
                String ch = text.substring(i, i + len);
                int pad = Math.max(0, (PIXEL_CELL - tr.width(ch)) / 2);
                tr.prepareText(ch, xs[i] + pad, 0, colors[i], false, 0).visit(sink);
            }
            i += len;
        }
        return new Line(SmoothText.segments(out.toArray(new TextRenderable[0])));
    }

    static void begin(GuiGraphicsExtractor ctx, ScreenRectangle clip) {
        if (face == null) {
            SmoothText.clip(clip);
            return;
        }
        TextureSetup setup = TextureSetup.singleTexture(face.texture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        run = new Run(new Matrix3x2f(ctx.pose()), clip, setup);
        ctx.guiRenderState.addGlyphToCurrentLayer(run);
    }

    static void draw(GuiGraphicsExtractor ctx, Line line, int x, int y) {
        if (line.glyphs != null) {
            if (line.glyphs.length == 0) return;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            SmoothText.replay(ctx, line.glyphs, null);
            ctx.pose().popMatrix();
        } else if (run != null && line.count > 0) run.add(line, x, y);
    }

    static void end() {
        SmoothText.clip(null);
        run = null;
        if (face != null && face.dirty) {
            face.dirty = false;
            face.texture.upload();
        }
    }
}
