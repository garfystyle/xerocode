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

    static void save(String title, Path suggested, String[] masks, String kind, byte[] bytes,
                     Consumer<Path> done) {
        Files2.save(suggested.getFileName().toString(), bytes, "application/json");
        done.accept(suggested.getFileName());
    }
}
