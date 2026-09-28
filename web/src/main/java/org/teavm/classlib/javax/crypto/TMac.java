package org.teavm.classlib.javax.crypto;

import com.xerocode.web.crypto.Sha256;
import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

public class TMac {
    private byte[] key;
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();

    protected TMac() {}

    public static TMac getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equalsIgnoreCase("HmacSHA256")) throw new NoSuchAlgorithmException(algorithm);
        return new TMac();
    }

    public final void init(Key k) throws InvalidKeyException {
        byte[] raw = k.getEncoded();
        if (raw == null) throw new InvalidKeyException("пустой ключ");
        key = raw;
        data.reset();
    }

    public final void update(byte[] input) {
        data.write(input, 0, input.length);
    }

    public final byte[] doFinal() {
        byte[] out = Sha256.hmac(key, data.toByteArray());
        data.reset();
        return out;
    }

    public final byte[] doFinal(byte[] input) {
        update(input);
        return doFinal();
    }
}
