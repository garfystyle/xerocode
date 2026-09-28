package org.teavm.classlib.java.net.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public interface THttpResponse<T> {
    int statusCode();

    T body();

    THttpRequest request();

    interface BodyHandler<T> {
        T convert(byte[] raw);
    }

    final class BodyHandlers {
        private BodyHandlers() {}

        public static BodyHandler<String> ofString() {
            return raw -> raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        }

        public static BodyHandler<byte[]> ofByteArray() {
            return raw -> raw == null ? new byte[0] : raw;
        }

        public static BodyHandler<InputStream> ofInputStream() {
            return raw -> new ByteArrayInputStream(raw == null ? new byte[0] : raw);
        }

        public static BodyHandler<Void> discarding() {
            return raw -> null;
        }
    }
}
