package org.teavm.classlib.java.security;

final class TEdKey implements TPrivateKey, TPublicKey {
    private final byte[] encoded;
    private final String format;

    TEdKey(byte[] encoded, String format) {
        this.encoded = encoded.clone();
        this.format = format;
    }

    @Override public String getAlgorithm() { return "Ed25519"; }
    @Override public String getFormat() { return format; }
    @Override public byte[] getEncoded() { return encoded.clone(); }

    boolean isPrivate() { return format.equals("PKCS#8"); }
}
