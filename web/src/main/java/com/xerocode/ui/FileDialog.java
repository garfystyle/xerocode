package com.xerocode.ui;

import com.xerocode.web.Files2;
import java.nio.file.Path;
import java.util.function.Consumer;

final class FileDialog {
    private FileDialog() {}

    static String hint() { return "выбери файл"; }

    static void open(String title, String start, String[] masks, String kind, Consumer<Path> done) {
        Files2.pick(masks, done);
    }
}
