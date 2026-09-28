package org.teavm.classlib.javax.crypto.spec;

import org.teavm.classlib.java.security.spec.TAlgorithmParameterSpec;

public class TGCMParameterSpec implements TAlgorithmParameterSpec {
    private final int tLen;
    private final byte[] iv;

    public TGCMParameterSpec(int tLen, byte[] src) {
        this(tLen, src, 0, src.length);
    }

    public TGCMParameterSpec(int tLen, byte[] src, int offset, int len) {
        this.tLen = tLen;
        this.iv = new byte[len];
        System.arraycopy(src, offset, iv, 0, len);
    }

    public int getTLen() { return tLen; }

    public byte[] getIV() { return iv.clone(); }
}
