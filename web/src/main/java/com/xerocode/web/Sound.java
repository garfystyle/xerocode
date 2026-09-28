package com.xerocode.web;

public final class Sound {
    private Sound() {}

    public static void want(String id) { Js.soundWant(id == null ? "" : id); }
    public static int state() { return Js.soundState(); }
    public static double duration() { return Js.soundDuration(); }
    public static double position() { return Js.soundPosition(); }
    public static boolean playing() { return Js.soundPlaying(); }
    public static void play() { Js.soundPlay(); }
    public static void pause() { Js.soundPause(); }
    public static void stop() { Js.soundStop(); }
    public static void seek(double t) { Js.soundSeek(t); }
    public static boolean loop() { return Js.soundLooping(); }
    public static void setLoop(boolean on) { Js.soundLoop(on); }
    public static void mix(double volume, double pitch) { Js.soundMix(volume, pitch); }
}
