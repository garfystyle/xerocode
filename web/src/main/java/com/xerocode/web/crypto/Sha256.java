package com.xerocode.web.crypto;

public final class Sha256 {
    private static final int[] K = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    private final int[] h = new int[8];
    private final byte[] block = new byte[64];
    private final int[] w = new int[64];
    private int fill;
    private long total;

    public Sha256() {
        reset();
    }

    public void reset() {
        h[0] = 0x6a09e667;
        h[1] = 0xbb67ae85;
        h[2] = 0x3c6ef372;
        h[3] = 0xa54ff53a;
        h[4] = 0x510e527f;
        h[5] = 0x9b05688c;
        h[6] = 0x1f83d9ab;
        h[7] = 0x5be0cd19;
        fill = 0;
        total = 0;
    }

    public void update(byte[] data, int off, int len) {
        total += len;
        for (int i = 0; i < len; i++) {
            block[fill++] = data[off + i];
            if (fill == 64) {
                compress();
                fill = 0;
            }
        }
    }

    public void update(byte b) {
        update(new byte[]{b}, 0, 1);
    }

    public byte[] digest() {
        long bits = total * 8;
        update(new byte[]{(byte) 0x80}, 0, 1);
        while (fill != 56) update(new byte[]{0}, 0, 1);
        byte[] len = new byte[8];
        for (int i = 0; i < 8; i++) len[i] = (byte) (bits >>> (56 - 8 * i));
        update(len, 0, 8);
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[4 * i] = (byte) (h[i] >>> 24);
            out[4 * i + 1] = (byte) (h[i] >>> 16);
            out[4 * i + 2] = (byte) (h[i] >>> 8);
            out[4 * i + 3] = (byte) h[i];
        }
        reset();
        return out;
    }

    private void compress() {
        for (int i = 0; i < 16; i++)
            w[i] = ((block[4 * i] & 0xFF) << 24) | ((block[4 * i + 1] & 0xFF) << 16)
                    | ((block[4 * i + 2] & 0xFF) << 8) | (block[4 * i + 3] & 0xFF);
        for (int i = 16; i < 64; i++) {
            int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
            int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
            int ch = (e & f) ^ (~e & g);
            int t1 = hh + s1 + ch + K[i] + w[i];
            int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
            int maj = (a & b) ^ (a & c) ^ (b & c);
            int t2 = s0 + maj;
            hh = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
        h[5] += f;
        h[6] += g;
        h[7] += hh;
    }

    public static byte[] hmac(byte[] key, byte[] message) {
        Sha256 s = new Sha256();
        byte[] k = key;
        if (k.length > 64) {
            s.update(k, 0, k.length);
            k = s.digest();
        }
        byte[] pad = new byte[64];
        System.arraycopy(k, 0, pad, 0, k.length);
        byte[] ipad = new byte[64], opad = new byte[64];
        for (int i = 0; i < 64; i++) {
            ipad[i] = (byte) (pad[i] ^ 0x36);
            opad[i] = (byte) (pad[i] ^ 0x5c);
        }
        s.update(ipad, 0, 64);
        s.update(message, 0, message.length);
        byte[] inner = s.digest();
        s.update(opad, 0, 64);
        s.update(inner, 0, inner.length);
        return s.digest();
    }
}
