package com.xerocode.ui;

import com.xerocode.ParticleLook;
import com.xerocode.Pickers;
import com.xerocode.Value;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ParticleStage implements CatalogPicker.Extra {
    public static final int H = 46;
    private static final int CAP = 60;
    private static final int ITEM_CAP = 14;

    private static final class P {
        double x, y, vx, vy, age, life, scale, seed;
        double cx;
    }

    private final List<P> live = new ArrayList<>();
    private final Random rng = new Random();
    private final Value value;
    private String id = "";
    private long lastNanos;
    private double debt;

    public ParticleStage(Value value) {
        this.value = value;
    }

    @Override
    public int height() { return H; }

    @Override
    public void hover(String id) { show(id); }

    public void show(String particleId) {
        String next = particleId == null ? "" : particleId;
        if (next.equals(id)) return;
        id = next;
        live.clear();
        debt = 0;
    }

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        render(ctx, x, y, w, h, mouseX, mouseY, false);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                       boolean flush) {
        if (flush) Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        else Ui.well(ctx, x, y, w, h);
        if (id.isEmpty()) return;

        List<Identifier> textures = ParticleLook.textures(id);
        ItemStack stack = textures.isEmpty() ? ParticleLook.item(id, value == null ? "" : value.material)
                                             : ItemStack.EMPTY;
        ParticleLook.Motion motion = ParticleLook.motion(id);

        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.05, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;

        advance(dt, w, h, motion, textures.isEmpty());
        draw(ctx, x, y, w, h, textures, stack, motion);
    }

    private void advance(double dt, int w, int h, ParticleLook.Motion motion, boolean asItem) {
        double count = value == null ? 1 : Math.max(0, value.count);
        int cap = asItem ? ITEM_CAP : CAP;

        if (live.isEmpty()) prime(w, h, motion, cap);
        for (int i = live.size() - 1; i >= 0; i--) {
            P p = live.get(i);
            p.age += dt;
            if (p.age >= p.life) { live.remove(i); continue; }
            move(p, dt, h, motion);
        }

        if (motion == ParticleLook.Motion.BURST) {
            debt -= dt;
            if (debt <= 0) {
                debt = 0.6;
                double cx = lane(w);
                int n = (int) Math.min(16, 6 + count / 2);
                for (int i = 0; i < n && live.size() < cap; i++) live.add(spawn(w, h, motion, cx));
            }
            return;
        }
        debt += (12 + Math.min(24, count) * 1.6) * dt;
        while (debt >= 1) {
            debt -= 1;
            if (live.size() < cap) live.add(spawn(w, h, motion, lane(w)));
        }
    }

    private void prime(int w, int h, ParticleLook.Motion motion, int cap) {
        for (int i = 0; i < Math.min(cap, 5); i++) {
            P p = spawn(w, h, motion, lane(w));
            p.age = rng.nextDouble() * p.life * 0.6;
            p.x += p.vx * p.age;
            p.y += p.vy * p.age;
            live.add(p);
        }
    }

    private P spawn(int w, int h, ParticleLook.Motion motion, double cx) {
        double spread = value == null ? 0 : Math.max(0, Math.min(4, value.spread1));
        double sx = Math.min(w / 3.0, 4 + spread * 9);
        double push = value == null ? 0 : Math.max(-4, Math.min(4, value.my)) * 9;
        double drift = value == null ? 0 : Math.max(-4, Math.min(4, value.mx)) * 9;
        double cy = h / 2.0;

        P p = new P();
        p.seed = rng.nextDouble() * 6.28;
        p.scale = 0.8 + rng.nextDouble() * 0.5;
        p.cx = cx;
        switch (motion) {
            case FALL -> {
                p.x = rng.nextDouble() * w;
                p.y = -3;
                p.vx = (rng.nextDouble() - 0.5) * 7 + drift;
                p.vy = 22 + rng.nextDouble() * 20 - push;
                p.life = 1.3 + rng.nextDouble() * 0.7;
            }
            case RISE -> {
                p.x = cx + (rng.nextDouble() - 0.5) * sx * 2;
                p.y = h + 3;
                p.vx = (rng.nextDouble() - 0.5) * 9 + drift;
                p.vy = -(20 + rng.nextDouble() * 18) - push;
                p.life = 1.1 + rng.nextDouble() * 0.8;
            }
            case BURST -> {
                double a = rng.nextDouble() * 6.28, speed = 30 + rng.nextDouble() * 55;
                p.x = cx + (rng.nextDouble() - 0.5) * sx;
                p.y = cy;
                p.vx = Math.cos(a) * speed + drift;
                p.vy = Math.sin(a) * speed * 0.6 - push;
                p.life = 0.5 + rng.nextDouble() * 0.55;
            }
            case SWIRL -> {
                double a = rng.nextDouble() * 6.28;
                double r = h * (0.26 + rng.nextDouble() * 0.2);
                p.x = cx + Math.cos(a) * r * 2.4;
                p.y = cy + Math.sin(a) * r;
                p.life = 1.2 + rng.nextDouble() * 0.9;
            }
            case DRIP -> {
                p.x = cx + (rng.nextDouble() - 0.5) * sx;
                p.y = 5 + rng.nextDouble() * 4;
                p.life = 1.5;
            }
            case DRIFT -> {
                p.x = rng.nextDouble() * w;
                p.y = h * 0.25 + rng.nextDouble() * h * 0.5;
                p.vx = (rng.nextDouble() - 0.5) * 11 + drift;
                p.vy = (rng.nextDouble() - 0.5) * 9 - push * 0.5;
                p.life = 1.2 + rng.nextDouble() * 0.9;
            }
        }
        return p;
    }

    private double lane(int w) {
        int lanes = Math.max(1, Math.min(6, w / 110));
        return w * (rng.nextInt(lanes) + 0.5) / lanes;
    }

    private static void move(P p, double dt, int h, ParticleLook.Motion motion) {
        double cy = h / 2.0;
        switch (motion) {
            case FALL -> {
                p.vy += 9 * dt;
                p.x += Math.cos(p.age * 3 + p.seed) * 7 * dt;
            }
            case RISE -> {
                p.vy *= 1 - Math.min(0.9, 0.4 * dt);
                p.x += Math.cos(p.age * 4 + p.seed) * 6 * dt;
            }
            case BURST -> {
                double drag = 1 - Math.min(0.9, 2.6 * dt);
                p.vx *= drag;
                p.vy = p.vy * drag + 26 * dt;
            }
            case SWIRL -> {
                double dx = (p.x - p.cx) / 2.4, dy = p.y - cy;
                p.vx = (-dy * 1.9 - dx * 0.5) * 2.4;
                p.vy = dx * 1.9 - dy * 0.5;
            }
            case DRIP -> {
                if (p.age > 0.55) p.vy += 75 * dt;
            }
            case DRIFT -> {
                p.vx += Math.cos(p.age * 2 + p.seed) * 6 * dt;
                p.vy += Math.sin(p.age * 2.4 + p.seed) * 5 * dt;
            }
        }
        p.x += p.vx * dt;
        p.y += p.vy * dt;
    }

    private void draw(DrawContext ctx, int x, int y, int w, int h, List<Identifier> textures,
                      ItemStack stack, ParticleLook.Motion motion) {
        Pickers.Entry entry = Pickers.particle(id);
        boolean tinted = entry != null && entry.has(Pickers.COLOR);
        boolean fades = entry != null && entry.has(Pickers.TO_COLOR);
        double sized = entry != null && entry.has(Pickers.SIZE) && value != null
                ? Math.max(0.25, Math.min(3, value.size)) : 1;
        int from = value == null ? 0xFFFFFF : value.color & 0xFFFFFF;
        int to = value == null ? 0xFFFFFF : value.toColor & 0xFFFFFF;

        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (P p : live) {
            double t = Math.min(1, p.age / p.life);
            float alpha = (float) Math.min(1, Math.min(t / 0.15, (1 - t) / 0.35));
            if (alpha <= 0.02f) continue;
            double shrink = motion == ParticleLook.Motion.BURST ? 1 - 0.45 * t : 1;
            int size = (int) Math.round(6.5 * p.scale * sized * shrink);
            if (size < 2) size = 2;
            int px = (int) Math.round(x + p.x) - size / 2;
            int py = (int) Math.round(y + p.y) - size / 2;

            if (!textures.isEmpty()) {
                int rgb = !tinted ? 0xFFFFFF : (fades ? Draw.mix(from, to, (float) t) : from);
                Sprite sprite = ParticleLook.sprite(
                        textures.get(Math.min(textures.size() - 1, (int) (t * textures.size()))));
                if (sprite != null)
                    ctx.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, sprite, px, py,
                            size, size, Draw.argb((int) (alpha * 255), rgb));
            } else if (!stack.isEmpty()) {
                int side = Math.max(8, size + 4);
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(px, py);
                m.scale(side / 16f, side / 16f);
                ctx.drawItem(stack, 0, 0);
                m.popMatrix();
            }
        }
        ctx.disableScissor();
    }
}
