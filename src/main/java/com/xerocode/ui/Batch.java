package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public final class Batch implements GuiElementRenderState {
    private static final int STRIDE = 6;

    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;
    private int[] data;
    private int count;
    private Cache cache;

    static final class Cache {
        private final Matrix3x2f pose = new Matrix3x2f();
        private float[] xy;
        private boolean filled;
        private final Blob.Slot blob = new Blob.Slot();

        private boolean holds(Matrix3x2fc p) { return filled && pose.equals(p, 0f); }

        private void transform(Matrix3x2fc p, int[] data, int count) {
            if (xy == null || xy.length != count * 8) xy = new float[count * 8];
            Vector2f v = new Vector2f();
            for (int i = 0, at = 0, o = 0; i < count; i++, at += STRIDE, o += 8) {
                int x0 = data[at], y0 = data[at + 1], x1 = data[at + 2], y1 = data[at + 3];
                p.transformPosition(x0, y0, v); xy[o] = v.x; xy[o + 1] = v.y;
                p.transformPosition(x0, y1, v); xy[o + 2] = v.x; xy[o + 3] = v.y;
                p.transformPosition(x1, y1, v); xy[o + 4] = v.x; xy[o + 5] = v.y;
                p.transformPosition(x1, y0, v); xy[o + 6] = v.x; xy[o + 7] = v.y;
            }
            pose.set(p);
            filled = true;
            blob.drop();
        }
    }

    private Batch(Matrix3x2fc pose, ScreenRectangle scissor, ScreenRectangle bounds, int[] data) {
        this.pose = new Matrix3x2f(pose);
        this.scissor = scissor;
        this.bounds = bounds;
        this.data = data;
    }

    private static Batch add(GuiGraphicsExtractor ctx, ScreenRectangle scissor, ScreenRectangle bounds,
                             int[] data) {
        Batch batch = new Batch(ctx.pose(), scissor, bounds, data);
        ctx.guiRenderState.addGuiElement(batch);
        return batch;
    }

    public static Batch open(GuiGraphicsExtractor ctx, ScreenRectangle scissor, ScreenRectangle bounds, int capacity) {
        return add(ctx, scissor, bounds, new int[capacity * STRIDE]);
    }

    static Batch reuse(GuiGraphicsExtractor ctx, ScreenRectangle scissor, ScreenRectangle bounds, int[] buffer) {
        return add(ctx, scissor, bounds, buffer.length == 0 ? new int[STRIDE * 1024] : buffer);
    }

    static Batch replay(GuiGraphicsExtractor ctx, ScreenRectangle scissor, ScreenRectangle bounds, int[] data,
                        Cache cache) {
        Batch batch = add(ctx, scissor, bounds, data);
        batch.count = data.length / STRIDE;
        batch.cache = cache;
        return batch;
    }

    int[] buffer() { return data; }

    public void quad(int x0, int y0, int x1, int y1, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0) return;
        int at = count * STRIDE;
        if (at + STRIDE > data.length) {
            int[] bigger = new int[Math.max(data.length * 2, STRIDE * 64)];
            System.arraycopy(data, 0, bigger, 0, data.length);
            data = bigger;
        }
        data[at] = x0;
        data[at + 1] = y0;
        data[at + 2] = x1;
        data[at + 3] = y1;
        data[at + 4] = top;
        data[at + 5] = bottom;
        count++;
    }

    public int size() { return count; }

    int[] snapshot() { return copyFrom(0); }

    int[] copyFrom(int from) { return Arrays.copyOfRange(data, from * STRIDE, count * STRIDE); }

    void append(int[] more) {
        int n = more.length / STRIDE;
        if (n == 0) return;
        int need = (count + n) * STRIDE;
        if (need > data.length) data = Arrays.copyOf(data, Math.max(need, data.length * 2));
        System.arraycopy(more, 0, data, count * STRIDE, more.length);
        count += n;
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        if (count == 0) {
            for (int k = 0; k < 4; k++) vc.addVertexWith2DPose(pose, 0, 0).setColor(0);
            return;
        }
        if (cache != null) { buildCached(vc); return; }
        for (int i = 0, at = 0; i < count; i++, at += STRIDE) {
            int x0 = data[at], y0 = data[at + 1], x1 = data[at + 2], y1 = data[at + 3];
            int top = data[at + 4], bottom = data[at + 5];
            vc.addVertexWith2DPose(pose, x0, y0).setColor(top);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(bottom);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(bottom);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(top);
        }
    }

    private void buildCached(VertexConsumer vc) {
        Cache c = cache;
        int vertices = count * 4;
        boolean still = c.holds(pose);
        if (still && c.blob.push(vc, vertices)) return;
        if (!still) c.transform(pose, data, count);
        long mark = still ? Blob.start(vc) : -1;
        float[] xy = c.xy;
        for (int i = 0, at = 0, o = 0; i < count; i++, at += STRIDE, o += 8) {
            int top = data[at + 4], bottom = data[at + 5];
            vc.addVertex(xy[o], xy[o + 1], 0.0f).setColor(top);
            vc.addVertex(xy[o + 2], xy[o + 3], 0.0f).setColor(bottom);
            vc.addVertex(xy[o + 4], xy[o + 5], 0.0f).setColor(bottom);
            vc.addVertex(xy[o + 6], xy[o + 7], 0.0f).setColor(top);
        }
        if (still) c.blob.capture(vc, mark, vertices);
    }

    @Override
    public RenderPipeline pipeline() { return RenderPipelines.GUI; }

    @Override
    public TextureSetup textureSetup() { return TextureSetup.noTexture(); }

    @Override
    public ScreenRectangle scissorArea() { return scissor; }

    @Override
    public ScreenRectangle bounds() { return bounds; }
}
