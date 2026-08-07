package com.xerocode;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class Png {
    private static final byte[] MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public record Bitmap(int[] argb, int w, int h) {
        public byte[] encode() { return Png.encode(argb, w, h); }
    }

    public static byte[] encode(int[] argb, int w, int h) {
        if (w <= 0 || h <= 0 || argb.length < w * h) return null;
        byte[] rows = new byte[h * (1 + w * 4)];
        int at = 0;
        for (int y = 0; y < h; y++) {
            rows[at++] = 0;
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                rows[at++] = (byte) (c >> 16);
                rows[at++] = (byte) (c >> 8);
                rows[at++] = (byte) c;
                rows[at++] = (byte) (c >>> 24);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(rows.length / 2 + 128);
        out.writeBytes(MAGIC);
        byte[] head = new byte[13];
        put32(head, 0, w);
        put32(head, 4, h);
        head[8] = 8;
        head[9] = 6;
        chunk(out, "IHDR", head);
        chunk(out, "IDAT", squeeze(rows));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    public static Bitmap shrink(Bitmap from, int maxW, int maxH) {
        int[] argb = from.argb();
        int w = from.w(), h = from.h();
        if (w <= maxW && h <= maxH) return from;
        double scale = Math.min(maxW / (double) w, maxH / (double) h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            int y0 = y * h / nh, y1 = Math.max(y0 + 1, (y + 1) * h / nh);
            for (int x = 0; x < nw; x++) {
                int x0 = x * w / nw, x1 = Math.max(x0 + 1, (x + 1) * w / nw);
                long a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int sy = y0; sy < y1 && sy < h; sy++)
                    for (int sx = x0; sx < x1 && sx < w; sx++) {
                        int c = argb[sy * w + sx];
                        int ca = c >>> 24;
                        a += ca;
                        r += (c >> 16 & 0xFF) * ca;
                        g += (c >> 8 & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                        n++;
                    }
                if (n == 0) continue;
                int oa = (int) (a / n);
                out[y * nw + x] = a == 0 ? 0
                        : (oa << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
            }
        }
        return new Bitmap(out, nw, nh);
    }

    public static Bitmap crop(Bitmap from, double ratio) {
        int[] argb = from.argb();
        int w = from.w(), h = from.h();
        int want = (int) Math.round(w / ratio);
        if (want >= h) {
            int cut = (int) Math.round(h * ratio);
            if (cut >= w) return from;
            int x0 = (w - cut) / 2;
            int[] out = new int[cut * h];
            for (int y = 0; y < h; y++)
                System.arraycopy(argb, y * w + x0, out, y * cut, cut);
            return new Bitmap(out, cut, h);
        }
        int y0 = (h - want) / 2;
        int[] out = new int[w * want];
        System.arraycopy(argb, y0 * w, out, 0, w * want);
        return new Bitmap(out, w, want);
    }

    private static byte[] squeeze(byte[] raw) {
        Deflater z = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            z.setInput(raw);
            z.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
            byte[] buffer = new byte[16384];
            while (!z.finished()) out.write(buffer, 0, z.deflate(buffer));
            return out.toByteArray();
        } finally {
            z.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String kind, byte[] body) {
        byte[] size = new byte[4];
        put32(size, 0, body.length);
        out.writeBytes(size);
        byte[] name = kind.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeBytes(name);
        out.writeBytes(body);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(body);
        byte[] sum = new byte[4];
        put32(sum, 0, (int) crc.getValue());
        out.writeBytes(sum);
    }

    private static void put32(byte[] to, int at, int value) {
        to[at] = (byte) (value >>> 24);
        to[at + 1] = (byte) (value >>> 16);
        to[at + 2] = (byte) (value >>> 8);
        to[at + 3] = (byte) value;
    }

    private Png() {}
}
