package org.teavm.classlib.java.net.http;

import com.xerocode.web.Sockets;
import java.net.URI;
import java.time.Duration;
import org.teavm.classlib.java.util.concurrent.TCompletableFuture;
import org.teavm.classlib.java.util.concurrent.TCompletionStage;

public interface TWebSocket {
    int NORMAL_CLOSURE = 1000;

    TCompletableFuture<TWebSocket> sendText(CharSequence data, boolean last);

    TCompletableFuture<TWebSocket> sendClose(int statusCode, String reason);

    void request(long n);

    void abort();

    interface Listener {
        default void onOpen(TWebSocket ws) {
            ws.request(1);
        }

        default TCompletionStage<?> onText(TWebSocket ws, CharSequence data, boolean last) {
            ws.request(1);
            return null;
        }

        default TCompletionStage<?> onClose(TWebSocket ws, int statusCode, String reason) {
            return null;
        }

        default void onError(TWebSocket ws, Throwable error) {}
    }

    interface Builder {
        Builder connectTimeout(Duration timeout);

        Builder header(String name, String value);

        TCompletableFuture<TWebSocket> buildAsync(URI uri, Listener listener);
    }

    final class BuilderImpl implements Builder {
        @Override public Builder connectTimeout(Duration timeout) { return this; }
        @Override public Builder header(String name, String value) { return this; }

        @Override
        public TCompletableFuture<TWebSocket> buildAsync(URI uri, Listener listener) {
            TCompletableFuture<TWebSocket> f = new TCompletableFuture<>();
            Sockets.open(uri.toString(), new Sockets.Events() {
                private TWebSocket self;

                @Override
                public void opened(Sockets.Socket socket) {
                    self = new TWebSocket() {
                        @Override
                        public TCompletableFuture<TWebSocket> sendText(CharSequence data, boolean last) {
                            socket.send(data.toString());
                            return TCompletableFuture.completedFuture(this);
                        }

                        @Override
                        public TCompletableFuture<TWebSocket> sendClose(int code, String reason) {
                            socket.close(code, reason);
                            return TCompletableFuture.completedFuture(this);
                        }

                        @Override public void request(long n) {}
                        @Override public void abort() { socket.close(1000, ""); }
                    };
                    listener.onOpen(self);
                    f.complete(self);
                }

                @Override
                public void text(String data) { listener.onText(self, data, true); }

                @Override
                public void closed(int code, String reason) {
                    if (self != null) listener.onClose(self, code, reason);
                    else f.completeExceptionally(new java.net.ConnectException("соединение не открылось"));
                }

                @Override
                public void failed(String why) {
                    if (self != null) listener.onError(self, new java.io.IOException(why));
                    else f.completeExceptionally(new java.net.ConnectException(why));
                }
            });
            return f;
        }
    }
}
