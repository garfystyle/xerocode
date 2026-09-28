package com.xerocode.web.crypto;

import java.util.Arrays;

public final class AesGcm {
    private static final int[] SBOX = new int[256];
    private static final int[] RCON = {0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36};

    static {
        int p = 1, q = 1;
        boolean first = true;
        do {
            p = p ^ ((p << 1) & 0xFF) ^ (((p & 0x80) != 0) ? 0x1B : 0);
            q ^= q << 1;
            q ^= q << 2;
            q ^= q << 4;
            q &= 0xFF;
            if ((q & 0x80) != 0) q ^= 0x09;
            int x = q ^ rotl8(q, 1) ^ rotl8(q, 2) ^ rotl8(q, 3) ^ rotl8(q, 4);
            SBOX[p] = (x ^ 0x63) & 0xFF;
            first = false;
        } while (p != 1 || first);
        SBOX[0] = 0x63;
    }

    private static int rotl8(int x, int s) { return ((x << s) | (x >>> (8 - s))) & 0xFF; }

    private final int[] roundKeys;
    private final int rounds;

    public AesGcm(byte[] key) {
        int nk = key.length / 4;
        rounds = nk + 6;
        roundKeys = new int[4 * (rounds + 1)];
        for (int i = 0; i < nk; i++)
            roundKeys[i] = ((key[4 * i] & 0xFF) << 24) | ((key[4 * i + 1] & 0xFF) << 16)
                    | ((key[4 * i + 2] & 0xFF) << 8) | (key[4 * i + 3] & 0xFF);
        for (int i = nk; i < roundKeys.length; i++) {
            int t = roundKeys[i - 1];
            if (i % nk == 0) t = sub(Integer.rotateLeft(t, 8)) ^ (RCON[i / nk - 1] << 24);
            else if (nk > 6 && i % nk == 4) t = sub(t);
            roundKeys[i] = roundKeys[i - nk] ^ t;
        }
    }

    private static int sub(int w) {
        return (SBOX[(w >>> 24) & 0xFF] << 24) | (SBOX[(w >>> 16) & 0xFF] << 16)
                | (SBOX[(w >>> 8) & 0xFF] << 8) | SBOX[w & 0xFF];
    }

    private static int xt(int b) { return ((b << 1) ^ (((b & 0x80) != 0) ? 0x1B : 0)) & 0xFF; }

    private void encryptBlock(byte[] in, byte[] out) {
        int[] s = new int[16];
        for (int i = 0; i < 16; i++) s[i] = in[i] & 0xFF;
        addRound(s, 0);
        for (int r = 1; r <= rounds; r++) {
            for (int i = 0; i < 16; i++) s[i] = SBOX[s[i]];
            int[] t = s.clone();
            for (int c = 0; c < 4; c++)
                for (int row = 0; row < 4; row++) s[c * 4 + row] = t[((c + row) % 4) * 4 + row];
            if (r != rounds) {
                for (int c = 0; c < 4; c++) {
                    int a0 = s[c * 4], a1 = s[c * 4 + 1], a2 = s[c * 4 + 2], a3 = s[c * 4 + 3];
                    int all = a0 ^ a1 ^ a2 ^ a3;
                    s[c * 4] = a0 ^ all ^ xt(a0 ^ a1);
                    s[c * 4 + 1] = a1 ^ all ^ xt(a1 ^ a2);
                    s[c * 4 + 2] = a2 ^ all ^ xt(a2 ^ a3);
                    s[c * 4 + 3] = a3 ^ all ^ xt(a3 ^ a0);
                }
            }
            addRound(s, r);
        }
        for (int i = 0; i < 16; i++) out[i] = (byte) s[i];
    }

    private void addRound(int[] s, int r) {
        for (int c = 0; c < 4; c++) {
            int k = roundKeys[r * 4 + c];
            s[c * 4] ^= (k >>> 24) & 0xFF;
            s[c * 4 + 1] ^= (k >>> 16) & 0xFF;
            s[c * 4 + 2] ^= (k >>> 8) & 0xFF;
            s[c * 4 + 3] ^= k & 0xFF;
        }
    }

    private static void gmul(long[] x, long hHi, long hLo) {
        long zHi = 0, zLo = 0, vHi = hHi, vLo = hLo;
        for (int i = 0; i < 128; i++) {
            long bit = i < 64 ? (x[0] >>> (63 - i)) & 1 : (x[1] >>> (127 - i)) & 1;
            if (bit != 0) {
                zHi ^= vHi;
                zLo ^= vLo;
            }
            boolean lsb = (vLo & 1) != 0;
            vLo = (vLo >>> 1) | (vHi << 63);
            vHi >>>= 1;
            if (lsb) vHi ^= 0xE100000000000000L;
        }
        x[0] = zHi;
        x[1] = zLo;
    }

    private static long load(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[off + i] & 0xFF);
        return v;
    }

    private void ghash(long[] y, byte[] data, int off, int len, long hHi, long hLo) {
        byte[] block = new byte[16];
        for (int i = 0; i < len; i += 16) {
            Arrays.fill(block, (byte) 0);
            System.arraycopy(data, off + i, block, 0, Math.min(16, len - i));
            y[0] ^= load(block, 0);
            y[1] ^= load(block, 8);
            gmul(y, hHi, hLo);
        }
    }

    private byte[] tag(byte[] aad, byte[] cipher, int coff, int clen, byte[] j0, int tagLen) {
        byte[] hb = new byte[16];
        encryptBlock(new byte[16], hb);
        long hHi = load(hb, 0), hLo = load(hb, 8);
        long[] y = {0, 0};
        ghash(y, aad, 0, aad.length, hHi, hLo);
        ghash(y, cipher, coff, clen, hHi, hLo);
        y[0] ^= (long) aad.length * 8;
        y[1] ^= (long) clen * 8;
        gmul(y, hHi, hLo);
        byte[] s = new byte[16];
        for (int i = 0; i < 8; i++) {
            s[i] = (byte) (y[0] >>> (56 - 8 * i));
            s[8 + i] = (byte) (y[1] >>> (56 - 8 * i));
        }
        byte[] ek = new byte[16];
        encryptBlock(j0, ek);
        byte[] t = new byte[tagLen];
        for (int i = 0; i < tagLen; i++) t[i] = (byte) (s[i] ^ ek[i]);
        return t;
    }

    private void ctr(byte[] j0, byte[] in, int off, int len, byte[] out, int outOff) {
        byte[] counter = j0.clone();
        byte[] ks = new byte[16];
        for (int i = 0; i < len; i += 16) {
            for (int k = 15; k >= 12; k--) if (++counter[k] != 0) break;
            encryptBlock(counter, ks);
            for (int k = 0; k < 16 && i + k < len; k++) out[outOff + i + k] = (byte) (in[off + i + k] ^ ks[k]);
        }
    }

    private byte[] j0(byte[] iv) {
        if (iv.length != 12) throw new IllegalArgumentException("IV 12 байт");
        byte[] j = new byte[16];
        System.arraycopy(iv, 0, j, 0, 12);
        j[15] = 1;
        return j;
    }

    public byte[] seal(byte[] iv, byte[] aad, byte[] plain, int off, int len, int tagLen) {
        byte[] j = j0(iv);
        byte[] out = new byte[len + tagLen];
        ctr(j, plain, off, len, out, 0);
        byte[] t = tag(aad, out, 0, len, j, tagLen);
        System.arraycopy(t, 0, out, len, tagLen);
        return out;
    }

    public byte[] open(byte[] iv, byte[] aad, byte[] sealed, int off, int len, int tagLen) {
        if (len < tagLen) throw new IllegalArgumentException("короткий шифротекст");
        byte[] j = j0(iv);
        int clen = len - tagLen;
        byte[] t = tag(aad, sealed, off, clen, j, tagLen);
        int diff = 0;
        for (int i = 0; i < tagLen; i++) diff |= t[i] ^ sealed[off + clen + i];
        if (diff != 0) throw new SecurityException("тег не сошёлся");
        byte[] out = new byte[clen];
        ctr(j, sealed, off, clen, out, 0);
        return out;
    }
}
