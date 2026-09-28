package com.xerocode.web;

import java.util.Base64;
import java.util.List;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Http {
    public record Result(int status, byte[] body, String error, boolean timeout) {}

    @JSFunctor
    interface Done extends JSObject {
        void done(int status, String base64, String error, boolean timeout);
    }

    private Http() {}

    public static Result fetch(String url, String method, List<String> headers, byte[] body, long timeoutMs) {
        StringBuilder h = new StringBuilder();
        for (int i = 0; i + 1 < headers.size(); i += 2)
            h.append(headers.get(i)).append('\n').append(headers.get(i + 1)).append('\n');
        return run(url, method, h.toString(), body == null ? null : Base64.getEncoder().encodeToString(body),
                (int) Math.min(Integer.MAX_VALUE, timeoutMs));
    }

    @Async
    private static native Result run(String url, String method, String headers, String body, int timeout);

    private static void run(String url, String method, String headers, String body, int timeout,
                            AsyncCallback<Result> cb) {
        js(url, method, headers, body, timeout, (status, base64, error, timedOut) -> {
            byte[] bytes = base64 == null || base64.isEmpty() ? new byte[0] : Base64.getDecoder().decode(base64);
            cb.complete(new Result(status, bytes, error == null || error.isEmpty() ? null : error, timedOut));
        });
    }

    @JSBody(params = {"url", "method", "headers", "body", "timeout", "f"},
            script = "XC.fetch(url, method, headers, body, timeout, f);")
    private static native void js(String url, String method, String headers, String body, int timeout, Done f);
}
