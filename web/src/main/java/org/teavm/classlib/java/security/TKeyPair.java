package org.teavm.classlib.java.security;

public final class TKeyPair {
    private final TPublicKey publicKey;
    private final TPrivateKey privateKey;

    public TKeyPair(TPublicKey publicKey, TPrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public TPublicKey getPublic() { return publicKey; }

    public TPrivateKey getPrivate() { return privateKey; }
}
