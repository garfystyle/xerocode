package org.teavm.classlib.javax.crypto.spec;

import org.teavm.classlib.java.security.spec.TKeySpec;
import org.teavm.classlib.javax.crypto.TSecretKey;

public class TSecretKeySpec implements TKeySpec, TSecretKey {
    private final byte[] key;
    private final String algorithm;

    public TSecretKeySpec(byte[] key, String algorithm) {
        this.key = key.clone();
        this.algorithm = algorithm;
    }

    @Override public String getAlgorithm() { return algorithm; }
    @Override public String getFormat() { return "RAW"; }
    @Override public byte[] getEncoded() { return key.clone(); }
}
