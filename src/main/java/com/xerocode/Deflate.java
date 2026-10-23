package com.xerocode;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Deflate {
    private static final int LIMIT = 64 << 20;

    private Deflate() {}

    public static byte[] pack(byte[] raw, int level, boolean bare) {
        Deflater z = new Deflater(level, bare);
        try {
            z.setInput(raw);
            z.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
            byte[] buffer = new byte[8192];
            while (!z.finished()) out.write(buffer, 0, z.deflate(buffer));
            return out.toByteArray();
        } finally {
            z.end();
        }
    }

    public static byte[] unpack(byte[] raw, boolean bare) throws DataFormatException {
        Inflater z = new Inflater(bare);
        try {
            z.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length * 3));
            byte[] buffer = new byte[8192];
            while (!z.finished()) {
                int n = z.inflate(buffer);
                if (n == 0 && (z.needsInput() || z.needsDictionary())) break;
                out.write(buffer, 0, n);
                if (out.size() > LIMIT) throw new DataFormatException("слишком много данных");
            }
            return out.toByteArray();
        } finally {
            z.end();
        }
    }
}
