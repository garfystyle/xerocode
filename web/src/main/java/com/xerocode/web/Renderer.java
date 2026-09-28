package com.xerocode.web;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Matrix3x2fc;

public final class Renderer implements VertexConsumer {
    private static final int STRIDE = 5;
    private static final int CMD = 9;
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
                RenderPipeline p = e.pipeline();
                TextureSetup t = e.textureSetup();
                int blend = p == RenderPipelines.GUI_INVERT ? BLEND_SKIP
                        : p == RenderPipelines.GUI_TEXT_HIGHLIGHT ? BLEND_ADD
                        : p.blend() == RenderPipeline.Blend.PREMULTIPLIED ? BLEND_PREMULT : BLEND_ALPHA;
                if (blend == BLEND_SKIP) continue;
                int tex = p.textured() && t != null ? t.texture() : 0;
                state(tex, t != null && t.linear(), blend, e.scissorArea());
                if (blend == BLEND_ADD) {
                    pending = 0xFF0000FF;
                }
                e.buildVertices(this);
                pending = -1;
            }
            if (node.textList() != null) for (GuiTextRenderState text : node.textList()) text(text);
        });
        flush();
    }

    public int[] verts() { return verts; }
    public int vertexCount() { return vcount; }
    public int[] cmds() { return cmds; }
    public int cmdCount() { return ccount; }

    private void itemBlits(GuiItemRenderState item, List<GuiElementRenderState> out) {
        int icon = ItemIcons.icon(item.stack());
        if (icon >= 0 && (icon >= ItemData.CUBE ? cubeTexture : itemTexture) > 0) out.add(blit(item, icon, 0xFFFFFFFF));
        int overlay = ItemIcons.overlay(item.stack());
        if (overlay >= 0 && itemTexture > 0) out.add(blit(item, overlay, ItemIcons.tint(item.stack())));
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
