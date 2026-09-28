package com.xerocode;

import com.xerocode.web.Sound;

public final class Audio {
    public enum State { EMPTY, LOADING, READY, MISSING }

    private Audio() {}

    public static void want(String id) { Sound.want(id); }

    public static State state() {
        return switch (Sound.state()) {
            case 1 -> State.LOADING;
            case 2 -> State.READY;
            case 3 -> State.MISSING;
            default -> State.EMPTY;
        };
    }

    public static boolean ready() { return Sound.state() == 2; }

    public static double duration() { return Sound.duration(); }

    public static void tick() {}

    public static void play() { Sound.play(); }

    public static void pause() { Sound.pause(); }

    public static void toggle() {
        if (Sound.playing()) Sound.pause();
        else Sound.play();
    }

    public static void stop() { Sound.stop(); }

    public static void rewind() { Sound.seek(0); }

    public static boolean playing() { return Sound.playing(); }

    public static double position() { return Sound.position(); }

    public static void seek(double seconds) { Sound.seek(seconds); }

    public static boolean loop() { return Sound.loop(); }

    public static void setLoop(boolean loop) { Sound.setLoop(loop); }

    public static void mix(double volume, double pitch, String source) { Sound.mix(volume, pitch); }

    public static boolean muted() { return false; }

    public static boolean broken() { return false; }

    public static void release() { Sound.stop(); }
}
