package org.teavm.classlib.java.security.spec;

public class TPKCS8EncodedKeySpec implements TKeySpec {
    private final byte[] encoded;

    public TPKCS8EncodedKeySpec(byte[] encoded) {
        this.encoded = encoded.clone();
    }

    public byte[] getEncoded() { return encoded.clone(); }

    public String getFormat() { return "PKCS#8"; }
}
