package com.xerocode.web;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Js {
    private Js() {}

    @JSFunctor
    public interface Done extends JSObject {
        void run();
    }

    @JSFunctor
    public interface Loaded extends JSObject {
        void loaded(int id, int w, int h);
    }

    @JSFunctor
    public interface Picked extends JSObject {
        void picked(String name, String base64);
    }

    @Async
    public static native void nextFrame();

    private static void nextFrame(AsyncCallback<Void> cb) {
        requestFrame(() -> cb.complete(null));
    }

    @JSBody(params = "f", script = "XC.nextFrame(f);")
    private static native void requestFrame(Done f);

    @Async
    public static native void ready();

    private static void ready(AsyncCallback<Void> cb) {
        whenReady(() -> cb.complete(null));
    }

    @JSBody(params = "f", script = "XC.ready(f);")
    private static native void whenReady(Done f);

    @JSBody(script = "return XC.width();")
    public static native int width();

    @JSBody(script = "return XC.height();")
    public static native int height();

    @JSBody(script = "return XC.ratio();")
    public static native double ratio();

    @JSBody(params = {"verts", "vcount", "cmds", "ccount", "scale"},
            script = "XC.render(verts, vcount, cmds, ccount, scale);")
    public static native void render(@JSByRef int[] verts, int vcount, @JSByRef int[] cmds, int ccount, double scale);

    @JSBody(script = "return XC.pollEvents();")
    public static native JSObject pollEvents();

    @JSBody(params = "q", script = "return q.length;")
    public static native int length(JSObject q);

    @JSBody(params = {"q", "i", "j"}, script = "return +q[i][j] || 0;")
    public static native double num(JSObject q, int i, int j);

    @JSBody(params = {"q", "i", "j"}, script = "var v = q[i][j]; return v == null ? '' : '' + v;")
    public static native String str(JSObject q, int i, int j);

    @JSBody(params = "css", script = "XC.cursor(css);")
    public static native void cursor(String css);

    @JSBody(params = "text", script = "XC.setClipboard(text);")
    public static native void setClipboard(String text);

    @JSBody(script = "return XC.clipboard();")
    public static native String clipboard();

    @JSBody(params = {"on", "x", "y", "rects", "scale", "sel", "all"},
            script = "XC.textFocus(on, x, y, rects, scale, sel, all);")
    public static native void textFocus(boolean on, double x, double y, String rects, int scale,
                                        String sel, String all);

    @JSBody(script = "return XC.touch();")
    public static native boolean touch();

    @JSBody(script = "return XC.coarse();")
    public static native boolean coarse();

    @JSBody(params = {"level", "msg"}, script = "XC.log(level, msg);")
    public static native void log(String level, String msg);

    @JSBody(params = "msg", script = "XC.toast(msg);")
    public static native void toast(String msg);

    @JSBody(script = "return XC.storedPaths();")
    public static native String storedPaths();

    @JSBody(params = "path", script = "return XC.stored(path);")
    public static native String stored(String path);

    @JSBody(params = {"path", "base64"}, script = "XC.store(path, base64);")
    public static native void store(String path, String base64);

    @JSBody(params = "path", script = "XC.unstore(path);")
    public static native void unstore(String path);

    @JSBody(params = {"accept", "f"}, script = "XC.pickFile(accept, f);")
    public static native void pickFile(String accept, Picked f);

    @JSBody(params = {"name", "base64", "mime"}, script = "XC.saveFile(name, base64, mime);")
    public static native void saveFile(String name, String base64, String mime);

    @JSBody(params = "name", script = "return XC.texture(name);")
    public static native int texture(String name);

    @JSBody(params = {"url", "f"}, script = "XC.loadTexture(url, f);")
    public static native void loadTexture(String url, Loaded f);

    @JSBody(params = "id", script = "XC.releaseTexture(id);")
    public static native void releaseTexture(int id);

    @JSBody(script = "return performance.now();")
    public static native double now();

    @JSBody(params = "key", script = "return XC.param(key);")
    public static native String param(String key);

    @JSBody(params = "f", script = "window.addEventListener('pagehide', function(){ f(); }); "
            + "document.addEventListener('visibilitychange', function(){ if (document.hidden) f(); });")
    public static native void onHide(Done f);

    @JSBody(params = "id", script = "XC.sound.want(id);")
    public static native void soundWant(String id);

    @JSBody(script = "return XC.sound.state();")
    public static native int soundState();

    @JSBody(script = "return XC.sound.duration();")
    public static native double soundDuration();

    @JSBody(script = "return XC.sound.position();")
    public static native double soundPosition();

    @JSBody(script = "return XC.sound.playing();")
    public static native boolean soundPlaying();

    @JSBody(script = "XC.sound.play();")
    public static native void soundPlay();

    @JSBody(script = "XC.sound.pause();")
    public static native void soundPause();

    @JSBody(script = "XC.sound.stop();")
    public static native void soundStop();

    @JSBody(params = "t", script = "XC.sound.seek(t);")
    public static native void soundSeek(double t);

    @JSBody(params = "on", script = "XC.sound.setLoop(on);")
    public static native void soundLoop(boolean on);

    @JSBody(script = "return XC.sound.loop();")
    public static native boolean soundLooping();

    @JSBody(params = {"volume", "pitch"}, script = "XC.sound.mix(volume, pitch);")
    public static native void soundMix(double volume, double pitch);
}
