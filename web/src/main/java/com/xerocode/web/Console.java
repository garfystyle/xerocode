package com.xerocode.web;

public final class Console {
    private Console() {}

    public static void log(String level, String msg) {
        Js.log(level, msg);
    }
}
