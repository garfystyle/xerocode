package org.teavm.classlib.java.security;

import com.xerocode.web.crypto.Ed25519;
import java.security.NoSuchAlgorithmException;

public class TKeyPairGenerator {
    protected TKeyPairGenerator() {}

    public static TKeyPairGenerator getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equalsIgnoreCase("Ed25519")) throw new NoSuchAlgorithmException(algorithm);
        return new TKeyPairGenerator();
    }

    public TKeyPair generateKeyPair() {
        Ed25519.Pair p = Ed25519.generate();
        return new TKeyPair(new TEdKey(p.spki(), "X.509"), new TEdKey(p.pkcs8(), "PKCS#8"));
    }

    public TKeyPair genKeyPair() { return generateKeyPair(); }
}
