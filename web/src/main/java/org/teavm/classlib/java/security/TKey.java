package org.teavm.classlib.java.security;

public interface TKey {
    String getAlgorithm();

    String getFormat();

    byte[] getEncoded();
}
