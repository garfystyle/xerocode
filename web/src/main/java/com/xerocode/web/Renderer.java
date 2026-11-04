package com.xerocode.web;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xerocode.ui.Tape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class Renderer implements VertexConsumer {
    public static final int STRIDE = 5, CMD = 9;
    private static final Matrix3x2f FLAT = new Matrix3x2f();
    public static final int BLEND_ALPHA = 0, BLEND_PREMULT = 1, BLEND_ADD = 2, BLEND_SKIP = 3;

    private int[] verts = new int[STRIDE * 4 * 4096];
    private int vcount;
    private int[] cmds = new int[CMD * 256];
    private int ccount;
    private int curTexture = -1, curLinear = -1, curBlend = -1;
    private ScreenRectangle curScissor;
    private boolean scissorSet;
    private int drawStart;
    private int pending = -1;
    private float u, v;
    private int fontTexture, itemTexture, cubeTexture;
    private double guiScale = 1;

    public void begin(double guiScale) {
        this.guiScale = guiScale;
        vcount = 0;
        ccount = 0;
        curTexture = -1;
        curLinear = -1;
        curBlend = -1;
        scissorSet = false;
        drawStart = 0;
        fontTexture = Js.texture("font");
        itemTexture = Js.texture("items16");
        cubeTexture = Js.texture("items48");
    }

    public void draw(GuiRenderState state) {
        List<GuiElementRenderState> sorted = new ArrayList<>();
        state.forEachNode(node -> {
            sorted.clear();
            if (node.elementList() != null) sorted.addAll(node.elementList());
            if (node.itemList() != null) for (GuiItemRenderState item : node.itemList()) itemBlits(item, sorted);
            sorted.sort(GuiRenderState.ORDER);
            for (GuiElementRenderState e : sorted) {
                if (e == null) continue;
                if (e instanceof Tape.Run run) run.emit(this);
                else if (e instanceof Emits own) own.emit(this);
                else element(e);
            }
            if (node.textList() != null) for (GuiTextRenderState text : node.textList()) text(text);
        });
        flush();
    }

    private void element(GuiElementRenderState e) {
        RenderPipeline p = e.pipeline();
        TextureSetup t = e.textureSetup();
        int blend = p == RenderPipelines.GUI_INVERT ? BLEND_SKIP
                : p == RenderPipelines.GUI_TEXT_HIGHLIGHT ? BLEND_ADD
                : p.blend() == RenderPipeline.Blend.PREMULTIPLIED ? BLEND_PREMULT : BLEND_ALPHA;
        if (blend == BLEND_SKIP) return;
        int tex = p.textured() && t != null ? t.texture() : 0;
        state(tex, t != null && t.linear(), blend, e.scissorArea());
        if (blend == BLEND_ADD) {
            pending = 0xFF0000FF;
        }
        e.buildVertices(this);
        pending = -1;
    }

    public boolean ready() {
        return Js.texture("font") > 0 && Js.texture("items16") > 0 && Js.texture("items48") > 0;
    }

    public void clear() { begin(1); }

    public int seal() {
        flush();
        return ccount;
    }

    public ScreenRectangle word(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if ((color >>> 24) == 0) return null;
        GuiTextRenderState t = new GuiTextRenderState(font, text, FLAT, x, y, color, shadow, null);
        text(t);
        return t.bounds();
    }

    public void icon(ItemStack stack, int x, int y) {
        List<GuiElementRenderState> out = new ArrayList<>();
        itemBlits(new GuiItemRenderState(FLAT, stack, x, y, null), out);
        out.sort(GuiRenderState.ORDER);
        for (GuiElementRenderState e : out) element(e);
    }

    public void enter(int texture, boolean linear, int blend, ScreenRectangle scissor) {
        state(texture, linear, blend, scissor);
    }

    private void room(int vertices) {
        int need = (vcount + vertices) * STRIDE;
        if (need > verts.length) verts = java.util.Arrays.copyOf(verts, Math.max(need, verts.length * 2));
    }

    public void codeBox(Matrix3x2fc p, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                        int color) {
        room(4);
        corner(p, x0, y0, u0, v0, color);
        corner(p, x0, y1, u0, v1, color);
        corner(p, x1, y1, u1, v1, color);
        corner(p, x1, y0, u1, v0, color);
    }

    public void quads(Matrix3x2fc p, int[] q) {
        room(q.length / 6 * 4);
        float a = p.m00(), b = p.m10(), c = p.m20(), d = p.m01(), e = p.m11(), f = p.m21();
        for (int i = 0; i < q.length; i += 6) {
            float x0 = q[i], y0 = q[i + 1], x1 = q[i + 2], y1 = q[i + 3];
            int top = q[i + 4], bottom = q[i + 5];
            vertex(a * x0 + b * y0 + c, d * x0 + e * y0 + f, 0, 0, top);
            vertex(a * x0 + b * y1 + c, d * x0 + e * y1 + f, 0, 0, bottom);
            vertex(a * x1 + b * y1 + c, d * x1 + e * y1 + f, 0, 0, bottom);
            vertex(a * x1 + b * y0 + c, d * x1 + e * y0 + f, 0, 0, top);
        }
    }

    public void points(Matrix3x2fc p, double[] xy, int[] rest, int from, int count) {
        room(count);
        float a = p.m00(), b = p.m10(), c = p.m20(), d = p.m01(), e = p.m11(), f = p.m21();
        int[] v = verts;
        int at = vcount * STRIDE;
        for (int i = from, end = from + count; i < end; i++, at += STRIDE) {
            double x = xy[i * 2], y = xy[i * 2 + 1];
            v[at] = Float.floatToRawIntBits((float) (a * x + b * y + c));
            v[at + 1] = Float.floatToRawIntBits((float) (d * x + e * y + f));
            v[at + 2] = rest[i * 3];
            v[at + 3] = rest[i * 3 + 1];
            v[at + 4] = rest[i * 3 + 2];
        }
        vcount += count;
    }

    private double[] exact;
    public void keepExact(boolean on) { if (on && exact == null) exact = new double[8192]; }
    public double[] exact() { return exact; }
    public int[] verts() { return verts; }
    public int vertexCount() { return vcount; }
    public int[] cmds() { return cmds; }
    public int cmdCount() { return ccount; }

    public int print() {
        int h = vcount * 31 + ccount;
        int[] v = verts, c = cmds;
        for (int i = 0, n = vcount * STRIDE; i < n; i++) h = h * 31 + v[i];
        for (int i = 0, n = ccount * CMD; i < n; i++) h = h * 31 + c[i];
        return h;
    }

    private void itemBlits(GuiItemRenderState item, List<GuiElementRenderState> out) {
        Skins.Skin skin = Skins.of(item.stack());
        if (skin != null) {
            head(item, skin, 0, 1f, out);
            head(item, skin, 32, 1.08f, out);
            return;
        }
        int icon = ItemIcons.icon(item.stack());
        if (icon >= 0 && (icon >= ItemData.CUBE ? cubeTexture : itemTexture) > 0) out.add(blit(item, icon, 0xFFFFFFFF));
        int overlay = ItemIcons.overlay(item.stack());
        if (overlay >= 0 && itemTexture > 0) out.add(blit(item, overlay, ItemIcons.tint(item.stack())));
    }

    private static final float HEAD_HW = 6.2f, HEAD_Q = 3.1f, HEAD_H = 7.2f;
    private static final int[] HEAD_SHADE = {0xFFFFFFFF, 0xFFB8B8B8, 0xFF999999};

    private void head(GuiItemRenderState item, Skins.Skin skin, int du, float grow,
                      List<GuiElementRenderState> out) {
        float cx = item.x() + 8f, top = item.y() + (16f - 2 * HEAD_Q - HEAD_H) / 2;
        float cy = top + HEAD_Q + HEAD_H / 2;
        float hw = HEAD_HW * grow, q = HEAD_Q * grow, h = HEAD_H * grow;
        float ty = cy - (q + h / 2);
        float bx = cx, by = ty, lx = cx - hw, ly = ty + q, rx = cx + hw, ry = ty + q;
        float dx = cx, dy = ty + 2 * q;
        float[][] quads = {
                {lx, ly, dx, dy, rx, ry, bx, by},
                {lx, ly, lx, ly + h, dx, dy + h, dx, dy},
                {dx, dy, dx, dy + h, rx, ry + h, rx, ry}};
        int[][] tex = {{8, 0, 8, 8, 16, 8, 16, 0}, {0, 8, 0, 16, 8, 16, 8, 8}, {8, 8, 8, 16, 16, 16, 16, 8}};
        TextureSetup setup = TextureSetup.of(skin.handle(), false);
        for (int f = 0; f < 3; f++) {
            float[] uv = new float[8];
            for (int i = 0; i < 4; i++) {
                uv[2 * i] = (tex[f][2 * i] + du) / (float) skin.w();
                uv[2 * i + 1] = tex[f][2 * i + 1] / (float) skin.h();
            }
            out.add(new HeadQuad(setup, item.pose(), quads[f], uv, HEAD_SHADE[f], item.scissorArea(),
                    item.bounds()));
        }
    }

    private GuiElementRenderState blit(GuiItemRenderState item, int code, int color) {
        int kind = code >= ItemData.CUBE ? 1 : 0;
        int icon = code - (kind == 1 ? ItemData.CUBE : 0);
        int cell = ItemData.cell(kind), aw = ItemData.atlasW(kind), ah = ItemData.atlasH(kind);
        int cols = aw / cell;
        float cw = cell / (float) aw, ch = cell / (float) ah;
        float u0 = (icon % cols) * cw, v0 = (icon / cols) * ch;
        return new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.of(kind == 1 ? cubeTexture : itemTexture, true),
                item.pose(), item.x(), item.y(), item.x() + 16, item.y() + 16, u0, u0 + cw, v0, v0 + ch, color,
                item.scissorArea(), item.bounds());
    }

    private static boolean integral(double s) {
        return Math.abs(s - Math.rint(s)) < 0.01;
    }

    private void state(int texture, boolean linear, int blend, ScreenRectangle scissor) {
        int lin = linear ? 1 : 0;
        boolean same = texture == curTexture && lin == curLinear && blend == curBlend
                && scissorSet && java.util.Objects.equals(scissor, curScissor);
        if (same) return;
        flush();
        curTexture = texture;
        curLinear = lin;
        curBlend = blend;
        curScissor = scissor;
        scissorSet = true;
    }

    private void flush() {
        int count = vcount - drawStart;
        if (count > 0) {
            if ((ccount + 1) * CMD > cmds.length) cmds = java.util.Arrays.copyOf(cmds, cmds.length * 2);
            int at = ccount * CMD;
            cmds[at] = drawStart;
            cmds[at + 1] = count;
            cmds[at + 2] = curTexture;
            cmds[at + 3] = curLinear;
            cmds[at + 4] = curBlend;
            if (curScissor != null) {
                cmds[at + 5] = curScissor.left();
                cmds[at + 6] = curScissor.top();
                cmds[at + 7] = curScissor.width();
                cmds[at + 8] = curScissor.height();
            } else {
                cmds[at + 5] = 0;
                cmds[at + 6] = 0;
                cmds[at + 7] = -1;
                cmds[at + 8] = -1;
            }
            ccount++;
        }
        drawStart = vcount;
    }

    private void vertex(float x, float y, float uu, float vv, int color) {
        int at = vcount * STRIDE;
        if (at + STRIDE > verts.length) verts = java.util.Arrays.copyOf(verts, verts.length * 2);
        if (exact != null) {
            if (vcount * 2 + 2 > exact.length) exact = java.util.Arrays.copyOf(exact, exact.length * 2);
            exact[vcount * 2] = x;
            exact[vcount * 2 + 1] = y;
        }
        verts[at] = Float.floatToRawIntBits(x);
        verts[at + 1] = Float.floatToRawIntBits(y);
        verts[at + 2] = Float.floatToRawIntBits(uu);
        verts[at + 3] = Float.floatToRawIntBits(vv);
        verts[at + 4] = color;
        vcount++;
    }

    private float px, py;
    private boolean open;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (open) vertex(px, py, u, v, 0);
        px = x;
        py = y;
        u = 0;
        v = 0;
        open = true;
        return this;
    }

    @Override
    public VertexConsumer setUv(float uu, float vv) {
        u = uu;
        v = vv;
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        if (!open) return this;
        vertex(px, py, u, v, pending != -1 ? pending : argb);
        open = false;
        return this;
    }

    private void text(GuiTextRenderState text) {
        Font.PreparedText prepared = text.ensurePrepared();
        if (text.bounds() == null) return;
        Matrix3x2fc pose = text.pose;
        state(fontTexture, true, BLEND_ALPHA, text.scissor);
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void glyph(int g, float x, float y, int color, int shadowColor, Style style, boolean shadow) {
                if (g < 0) {
                    sprite(pose, -1 - g, x, y, color, text.scissor);
                    return;
                }
                boolean bold = style.isBold();
                boolean italic = style.isItalic();
                if (shadow) {
                    glyphQuad(pose, g, x + 1, y + 1, shadowColor, bold, italic);
                    if (bold) glyphQuad(pose, g, x + 2, y + 1, shadowColor, true, italic);
                }
                glyphQuad(pose, g, x, y, color, bold, italic);
                if (bold) glyphQuad(pose, g, x + 1, y, color, true, italic);
            }

            @Override
            public void effect(float x0, float y0, float x1, float y1, int color, int shadowColor, boolean shadow) {
                if (shadow) rect(pose, x0 + 1, y0 + 1, x1 + 1, y1 + 1, shadowColor);
                rect(pose, x0, y0, x1, y1, color);
            }
        });
    }

    private void sprite(Matrix3x2fc pose, int index, float x, float y, int color, ScreenRectangle scissor) {
        int texture = com.xerocode.ui.SpriteText.texture();
        if (texture <= 0) return;
        float[] uv = com.xerocode.ui.SpriteText.uv(index);
        float side = com.xerocode.ui.SpriteText.advance();
        state(texture, false, BLEND_ALPHA, scissor);
        corner(pose, x, y, uv[0], uv[2], color);
        corner(pose, x, y + side, uv[0], uv[3], color);
        corner(pose, x + side, y + side, uv[1], uv[3], color);
        corner(pose, x + side, y, uv[1], uv[2], color);
        state(fontTexture, true, BLEND_ALPHA, scissor);
    }

    private void glyphQuad(Matrix3x2fc pose, int g, float x, float y, int color, boolean bold, boolean italic) {
        float x0 = x + Glyphs.left(g), x1 = x + Glyphs.right(g);
        float y0 = y + Glyphs.up(g), y1 = y + Glyphs.down(g);
        float st = italic ? 1 - 0.25f * Glyphs.up(g) : 0, sb = italic ? 1 - 0.25f * Glyphs.down(g) : 0;
        float t = bold ? 0.1f : 0;
        float u0 = Glyphs.u0(g), u1 = Glyphs.u1(g), v0 = Glyphs.v0(g), v1 = Glyphs.v1(g);
        corner(pose, x0 + st - t, y0 - t, u0, v0, color);
        corner(pose, x0 + sb - t, y1 + t, u0, v1, color);
        corner(pose, x1 + sb + t, y1 + t, u1, v1, color);
        corner(pose, x1 + st + t, y0 - t, u1, v0, color);
    }

    private void rect(Matrix3x2fc pose, float x0, float y0, float x1, float y1, int color) {
        float u0 = Glyphs.whiteU(), v0 = Glyphs.whiteV();
        corner(pose, x0, y1, u0, v0, color);
        corner(pose, x1, y1, u0, v0, color);
        corner(pose, x1, y0, u0, v0, color);
        corner(pose, x0, y0, u0, v0, color);
    }

    private void corner(Matrix3x2fc p, float x, float y, float uu, float vv, int color) {
        vertex(p.m00() * x + p.m10() * y + p.m20(), p.m01() * x + p.m11() * y + p.m21(), uu, vv, color);
    }
}
