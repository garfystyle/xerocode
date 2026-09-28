package org.teavm.classlib.java.net.http;

import com.xerocode.web.Http;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executor;

public class THttpClient {
    public enum Redirect { NEVER, ALWAYS, NORMAL }

    public enum Version { HTTP_1_1, HTTP_2 }

    private final long connectMillis;

    protected THttpClient(long connectMillis) {
        this.connectMillis = connectMillis;
    }

    public static THttpClient newHttpClient() { return new THttpClient(30000); }

    public static Builder newBuilder() { return new BuilderImpl(); }

    public interface Builder {
        Builder connectTimeout(Duration duration);

        Builder followRedirects(Redirect policy);

        Builder executor(Executor executor);

        Builder version(Version version);

        THttpClient build();
    }

    private static final class BuilderImpl implements Builder {
        private long connect = 30000;

        @Override
        public Builder connectTimeout(Duration d) {
            connect = d.toMillis();
            return this;
        }

        @Override public Builder followRedirects(Redirect policy) { return this; }
        @Override public Builder executor(Executor executor) { return this; }
        @Override public Builder version(Version version) { return this; }
        @Override public THttpClient build() { return new THttpClient(connect); }
    }

    public <T> THttpResponse<T> send(THttpRequest request, THttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        long timeout = request.timeoutMillis() > 0 ? request.timeoutMillis() : 60000;
        Http.Result r = Http.fetch(request.uri().toString(), request.method(), request.headerList(),
                request.body(), timeout);
        if (r.error() != null) {
            if (r.timeout()) throw new THttpTimeoutException(r.error());
            throw new java.net.ConnectException(r.error());
        }
        return new Response<>(request, r.status(), handler.convert(r.body()));
    }

    public TWebSocket.Builder newWebSocketBuilder() { return new TWebSocket.BuilderImpl(); }

    private record Response<T>(THttpRequest request, int statusCode, T body) implements THttpResponse<T> {}
}
