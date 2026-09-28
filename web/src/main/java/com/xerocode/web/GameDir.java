package com.xerocode.web;

import java.nio.file.Path;

public final class GameDir {
    private final String path;

    public GameDir(String path) {
        this.path = path;
    }

    public Path toPath() { return Path.of(path); }

    @Override
    public String toString() { return path; }
}
