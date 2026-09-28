package org.teavm.classlib.java.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import org.teavm.classlib.java.security.spec.TPKCS8EncodedKeySpec;

public class TKeyFactory {
    protected TKeyFactory() {}

    public static TKeyFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equalsIgnoreCase("Ed25519")) throw new NoSuchAlgorithmException(algorithm);
        return new TKeyFactory();
    }

    public final TPrivateKey generatePrivate(KeySpec spec) throws InvalidKeySpecException {
        Object o = spec;
        if (!(o instanceof TPKCS8EncodedKeySpec p)) throw new InvalidKeySpecException("нужен PKCS#8");
        byte[] enc = p.getEncoded();
        if (enc.length < 34) throw new InvalidKeySpecException("короткий ключ");
        return new TEdKey(enc, "PKCS#8");
    }
}
