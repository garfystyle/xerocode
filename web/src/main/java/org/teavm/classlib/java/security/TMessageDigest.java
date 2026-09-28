package org.teavm.classlib.java.security;

import com.xerocode.web.crypto.Sha256;
import java.security.NoSuchAlgorithmException;

public class TMessageDigest {
    private final String algorithm;
    private final Sha256 sha = new Sha256();

    protected TMessageDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    public static TMessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
        String a = algorithm.toUpperCase().replace("-", "");
        if (!a.equals("SHA256")) throw new NoSuchAlgorithmException(algorithm);
        return new TMessageDigest(algorithm);
    }

    public String getAlgorithm() { return algorithm; }

    public void update(byte b) { sha.update(b); }

    public void update(byte[] input) { sha.update(input, 0, input.length); }

    public void update(byte[] input, int offset, int len) { sha.update(input, offset, len); }

    public byte[] digest() { return sha.digest(); }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public void reset() { sha.reset(); }

    public int getDigestLength() { return 32; }

    public static boolean isEqual(byte[] a, byte[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        int d = 0;
        for (int i = 0; i < a.length; i++) d |= a[i] ^ b[i];
        return d == 0;
    }
}
