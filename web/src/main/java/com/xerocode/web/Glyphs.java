package com.xerocode.web;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Glyphs {
    private static final int MISSING = 0;
    private static int atlasW = 1, atlasH = 1;
    private static int[] cps = new int[0];
    private static float[] adv = new float[0];
    private static int[] px, py, pw, ph;
    private static float[] scale, up;
    private static final int[] BMP = new int[65536];
    private static final Map<Integer, Integer> ASTRAL = new HashMap<>();
    private static final Map<Integer, List<Integer>> BY_WIDTH = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static boolean loaded;

    private Glyphs() {}

    public static void load() {
        if (loaded) return;
        loaded = true;
        List<String> lines = new ArrayList<>();
        try (InputStream in = Glyphs.class.getResourceAsStream("/web/font.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.isEmpty()) lines.add(line);
        } catch (Exception e) {
            throw new IllegalStateException("font.txt", e);
        }
        String[] head = lines.get(0).split(" ");
        atlasW = Integer.parseInt(head[1]);
        atlasH = Integer.parseInt(head[2]);
        int n = lines.size() - 1;
        cps = new int[n];
        adv = new float[n];
        px = new int[n];
        py = new int[n];
        pw = new int[n];
        ph = new int[n];
        scale = new float[n];
        up = new float[n];
        java.util.Arrays.fill(BMP, -1);
        for (int i = 0; i < n; i++) {
            String[] f = lines.get(i + 1).split(" ");
            cps[i] = Integer.parseInt(f[0]);
            adv[i] = Float.parseFloat(f[1]);
            px[i] = Integer.parseInt(f[2]);
            py[i] = Integer.parseInt(f[3]);
            pw[i] = Integer.parseInt(f[4]);
            ph[i] = Integer.parseInt(f[5]);
            scale[i] = Float.parseFloat(f[6]);
            up[i] = Float.parseFloat(f[7]);
            int cp = cps[i];
            if (cp < 0) continue;
            if (cp < 65536) BMP[cp] = i;
            else ASTRAL.put(cp, i);
            if (pw[i] > 0 && cp != ' ')
                BY_WIDTH.computeIfAbsent((int) Math.ceil(adv[i]), k -> new ArrayList<>()).add(i);
        }
    }

    public static int index(int cp) {
        if (cp >= 0 && cp < 65536) {
            int i = BMP[cp];
            return i < 0 ? MISSING : i;
        }
        Integer i = ASTRAL.get(cp);
        return i == null ? MISSING : i;
    }

    public static boolean known(int cp) {
        return index(cp) != MISSING;
    }

    public static float advance(int cp, boolean bold) {
        return adv[index(cp)] + (bold ? 1 : 0);
    }

    public static int pick(int cp, boolean obfuscated) {
        int i = index(cp);
        if (!obfuscated || cp == ' ') return i;
        List<Integer> same = BY_WIDTH.get((int) Math.ceil(adv[i]));
        return same == null || same.isEmpty() ? MISSING : same.get(RANDOM.nextInt(same.size()));
    }

    public static boolean visible(int g) { return pw[g] > 0; }
    public static float left(int g) { return 0; }
    public static float right(int g) { return pw[g] * scale[g]; }
    public static float up(int g) { return up[g]; }
    public static float down(int g) { return up[g] + ph[g] * scale[g]; }
    public static float u0(int g) { return px[g] / (float) atlasW; }
    public static float u1(int g) { return (px[g] + pw[g]) / (float) atlasW; }
    public static float v0(int g) { return py[g] / (float) atlasH; }
    public static float v1(int g) { return (py[g] + ph[g]) / (float) atlasH; }
    public static float whiteU() { return 0.5f / atlasW; }
    public static float whiteV() { return 0.5f / atlasH; }
}
