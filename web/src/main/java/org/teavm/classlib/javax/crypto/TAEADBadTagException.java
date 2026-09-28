package org.teavm.classlib.javax.crypto;

import java.security.GeneralSecurityException;

public class TAEADBadTagException extends GeneralSecurityException {
    public TAEADBadTagException(String message) {
        super(message);
    }
}
