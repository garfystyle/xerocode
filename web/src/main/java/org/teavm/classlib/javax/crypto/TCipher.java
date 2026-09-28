package org.teavm.classlib.javax.crypto;

import com.xerocode.web.crypto.AesGcm;
import java.io.ByteArrayOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import org.teavm.classlib.javax.crypto.spec.TGCMParameterSpec;

public class TCipher {
    public static final int ENCRYPT_MODE = 1;
    public static final int DECRYPT_MODE = 2;

    private int mode;
    private AesGcm aes;
    private byte[] iv;
    private int tagBytes;
    private final ByteArrayOutputStream aad = new ByteArrayOutputStream();

    protected TCipher() {}

    public static TCipher getInstance(String transformation) throws NoSuchAlgorithmException {
        if (!transformation.equalsIgnoreCase("AES/GCM/NoPadding")) throw new NoSuchAlgorithmException(transformation);
        return new TCipher();
    }

    public final void init(int mode, Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        Object p = params;
        if (!(p instanceof TGCMParameterSpec gcm)) throw new InvalidAlgorithmParameterException("нужен GCM");
        byte[] raw = key.getEncoded();
        if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32))
            throw new InvalidKeyException("ключ AES");
        this.mode = mode;
        this.aes = new AesGcm(raw);
        this.iv = gcm.getIV();
        this.tagBytes = gcm.getTLen() / 8;
        aad.reset();
    }

    public final void updateAAD(byte[] src) {
        aad.write(src, 0, src.length);
    }

    public final byte[] doFinal(byte[] input) throws TAEADBadTagException {
        return doFinal(input, 0, input.length);
    }

    public final byte[] doFinal(byte[] input, int off, int len) throws TAEADBadTagException {
        byte[] a = aad.toByteArray();
        aad.reset();
        if (mode == ENCRYPT_MODE) return aes.seal(iv, a, input, off, len, tagBytes);
        try {
            return aes.open(iv, a, input, off, len, tagBytes);
        } catch (RuntimeException e) {
            throw new TAEADBadTagException(e.getMessage());
        }
    }
}
