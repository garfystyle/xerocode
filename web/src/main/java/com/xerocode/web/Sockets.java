package com.xerocode.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Sockets {
    public interface Events {
        void opened(Socket socket);

        void text(String data);

        void closed(int code, String reason);

        void failed(String why);
    }

    public static final class Socket {
        private final int id;

        Socket(int id) {
            this.id = id;
        }

        public void send(String text) { sendJs(id, text); }

        public void close(int code, String reason) { closeJs(id, code, reason); }
    }

    @JSFunctor
    interface Sink extends JSObject {
        void event(int id, String kind, String data, int code);
    }

    private Sockets() {}

    public static void open(String url, Events events) {
        openJs(url, (id, kind, data, code) -> new Thread(() -> {
            switch (kind) {
                case "open" -> events.opened(new Socket(id));
                case "text" -> events.text(data);
                case "close" -> events.closed(code, data);
                default -> events.failed(data);
            }
        }).start());
    }

    @JSBody(params = {"url", "f"}, script = "XC.socket(url, f);")
    private static native void openJs(String url, Sink f);

    @JSBody(params = {"id", "text"}, script = "XC.socketSend(id, text);")
    private static native void sendJs(int id, String text);

    @JSBody(params = {"id", "code", "reason"}, script = "XC.socketClose(id, code, reason);")
    private static native void closeJs(int id, int code, String reason);
}
