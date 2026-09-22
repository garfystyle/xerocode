package com.xerocode.ui;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

final class Icons {
    private static final int LIMIT = 4096;
    private static final TrackingItemStackRenderState MOVING = new TrackingItemStackRenderState();

    private static final Map<ItemStack, TrackingItemStackRenderState> cache = new IdentityHashMap<>();
    private static int epoch = Integer.MIN_VALUE;
    private static Object level;
    private static boolean broken;

    private Icons() {}

    static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        draw(ctx, stack, x, y, null);
    }

    static GuiItemRenderState draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y,
                                   GuiItemRenderState prev) {
        if (prev != null && epoch == Tape.epoch() && !broken && prev.x() == x && prev.y() == y
                && prev.pose().equals(ctx.pose(), 0f)
                && Objects.equals(prev.scissorArea(), ctx.scissorStack.peek())
                && cache.get(stack) == prev.itemStackRenderState()
                && Minecraft.getInstance().gui.overlay() == null) {
            ctx.guiRenderState.addItem(prev);
            return prev;
        }
        return drawFresh(ctx, stack, x, y);
    }

    private static GuiItemRenderState drawFresh(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (broken || mc.gui.overlay() != null) {
            cache.clear();
            return vanilla(ctx, stack, x, y);
        }
        if (epoch != Tape.epoch() || level != mc.level || cache.size() > LIMIT) {
            cache.clear();
            epoch = Tape.epoch();
            level = mc.level;
        }
        TrackingItemStackRenderState state = cache.get(stack);
        if (state == MOVING) return vanilla(ctx, stack, x, y);
        try {
            if (state == null) {
                state = new TrackingItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI,
                        mc.level, mc.player, 0);
                if (state.isAnimated()) {
                    cache.put(stack, MOVING);
                    return vanilla(ctx, stack, x, y);
                }
                cache.put(stack, state);
            }
            GuiItemRenderState made = new GuiItemRenderState(new Matrix3x2f(ctx.pose()), state, x, y,
                    ctx.scissorStack.peek());
            ctx.guiRenderState.addItem(made);
            return made;
        } catch (Throwable e) {
            broken = true;
            cache.clear();
            return vanilla(ctx, stack, x, y);
        }
    }

    private static GuiItemRenderState vanilla(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        ctx.item(stack, x, y);
        return null;
    }
}
