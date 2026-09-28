package org.teavm.classlib.java.net.http;

import java.io.IOException;

public class THttpTimeoutException extends IOException {
    public THttpTimeoutException(String message) {
        super(message);
    }
}
