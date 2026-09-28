package org.teavm.classlib.java.security;

import com.xerocode.web.crypto.Ed25519;
import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class TSignature {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private TEdKey key;

    protected TSignature() {}

    public static TSignature getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equalsIgnoreCase("Ed25519")) throw new NoSuchAlgorithmException(algorithm);
        return new TSignature();
    }

    public final void initSign(TPrivateKey privateKey) throws InvalidKeyException {
        if (!(privateKey instanceof TEdKey k) || !k.isPrivate()) throw new InvalidKeyException("не Ed25519");
        key = k;
        data.reset();
    }

    public final void update(byte[] b) throws SignatureException {
        data.write(b, 0, b.length);
    }

    public final void update(byte[] b, int off, int len) throws SignatureException {
        data.write(b, off, len);
    }

    public final byte[] sign() throws SignatureException {
        if (key == null) throw new SignatureException("нет ключа");
        try {
            return Ed25519.sign(key.getEncoded(), data.toByteArray());
        } catch (RuntimeException e) {
            throw new SignatureException(e.getMessage());
        } finally {
            data.reset();
        }
    }
}
