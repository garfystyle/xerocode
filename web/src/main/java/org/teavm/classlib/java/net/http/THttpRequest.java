package org.teavm.classlib.java.net.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public abstract class THttpRequest {
    protected THttpRequest() {}

    public abstract URI uri();

    public abstract String method();

    abstract List<String> headerList();

    abstract byte[] body();

    abstract long timeoutMillis();

    public static Builder newBuilder() { return new BuilderImpl(); }

    public static Builder newBuilder(URI uri) { return new BuilderImpl().uri(uri); }

    public interface BodyPublisher {
        byte[] bytes();
    }

    public static final class BodyPublishers {
        private BodyPublishers() {}

        public static BodyPublisher ofByteArray(byte[] data) {
            byte[] copy = data.clone();
            return () -> copy;
        }

        public static BodyPublisher ofString(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            return () -> b;
        }

        public static BodyPublisher noBody() {
            return () -> null;
        }
    }

    public interface Builder {
        Builder uri(URI uri);

        Builder header(String name, String value);

        Builder setHeader(String name, String value);

        Builder timeout(Duration duration);

        Builder GET();

        Builder POST(BodyPublisher body);

        Builder PUT(BodyPublisher body);

        Builder DELETE();

        Builder method(String method, BodyPublisher body);

        Builder expectContinue(boolean enable);

        Builder version(THttpClient.Version version);

        THttpRequest build();
    }

    private static final class BuilderImpl implements Builder {
        private URI uri;
        private String method = "GET";
        private byte[] body;
        private long timeout;
        private final List<String> headers = new ArrayList<>();

        @Override public Builder uri(URI u) { uri = u; return this; }

        @Override
        public Builder header(String name, String value) {
            headers.add(name);
            headers.add(value);
            return this;
        }

        @Override public Builder setHeader(String name, String value) { return header(name, value); }
        @Override public Builder timeout(Duration d) { timeout = d.toMillis(); return this; }
        @Override public Builder GET() { method = "GET"; body = null; return this; }
        @Override public Builder POST(BodyPublisher b) { method = "POST"; body = b.bytes(); return this; }
        @Override public Builder PUT(BodyPublisher b) { method = "PUT"; body = b.bytes(); return this; }
        @Override public Builder DELETE() { method = "DELETE"; body = null; return this; }

        @Override
        public Builder method(String m, BodyPublisher b) {
            method = m;
            body = b == null ? null : b.bytes();
            return this;
        }

        @Override public Builder expectContinue(boolean enable) { return this; }
        @Override public Builder version(THttpClient.Version version) { return this; }

        @Override
        public THttpRequest build() {
            URI u = uri;
            String m = method;
            byte[] b = body;
            long t = timeout;
            List<String> h = new ArrayList<>(headers);
            return new THttpRequest() {
                @Override public URI uri() { return u; }
                @Override public String method() { return m; }
                @Override List<String> headerList() { return h; }
                @Override byte[] body() { return b; }
                @Override long timeoutMillis() { return t; }
            };
        }
    }
}
