package com.xerocode.web;

public final class Toasts {
    private Toasts() {}

    public static void show(String text) {
        if (text != null && !text.isBlank()) Js.toast(text);
    }
}
