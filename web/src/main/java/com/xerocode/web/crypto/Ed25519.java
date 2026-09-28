package com.xerocode.web.crypto;

import java.util.Base64;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Ed25519 {
    private Ed25519() {}

    @JSFunctor
    interface Two extends JSObject {
        void done(String a, String b, String error);
    }

    public record Pair(byte[] pkcs8, byte[] spki) {}

    @Async
    private static native String[] generateRaw();

    private static void generateRaw(AsyncCallback<String[]> cb) {
        generateJs((a, b, error) -> {
            if (error != null && !error.isEmpty()) cb.error(new IllegalStateException(error));
            else cb.complete(new String[]{a, b});
        });
    }

    @JSBody(params = "f", script = "XC.ed.generate(f);")
    private static native void generateJs(Two f);

    @Async
    private static native String signRaw(String pkcs8, String message);

    private static void signRaw(String pkcs8, String message, AsyncCallback<String> cb) {
        signJs(pkcs8, message, (a, b, error) -> {
            if (error != null && !error.isEmpty()) cb.error(new IllegalStateException(error));
            else cb.complete(a);
        });
    }

    @JSBody(params = {"k", "m", "f"}, script = "XC.ed.sign(k, m, f);")
    private static native void signJs(String pkcs8, String message, Two f);

    public static Pair generate() {
        String[] r = generateRaw();
        return new Pair(Base64.getDecoder().decode(r[0]), Base64.getDecoder().decode(r[1]));
    }

    public static byte[] sign(byte[] pkcs8, byte[] message) {
        String s = signRaw(Base64.getEncoder().encodeToString(pkcs8), Base64.getEncoder().encodeToString(message));
        return Base64.getDecoder().decode(s);
    }
}
