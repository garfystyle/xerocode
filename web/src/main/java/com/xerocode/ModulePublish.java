package com.xerocode;

import java.util.List;
import java.util.regex.Pattern;

public final class ModulePublish {
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{3,12}");
    public interface Report {
        void step(String text);
        void done(boolean ok, String text);
    }

    private ModulePublish() {}

    public static boolean busy() { return false; }

    public static String start(List<Integer> positions, String id, String category, boolean makePublic,
                               Report report) {
        return "выкладывать модуль можно только в игре, в /dev";
    }
}
