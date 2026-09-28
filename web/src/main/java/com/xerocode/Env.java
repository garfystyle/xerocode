package com.xerocode;

public final class Env {
    public static boolean browser() { return true; }

    public static boolean touch() { return com.xerocode.web.Input.touch(); }

    private Env() {}
}
