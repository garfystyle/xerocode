package com.xerocode;

import java.nio.file.Path;
import java.util.List;

public final class Mcp {
    public record Tool(String id, String title, boolean found, boolean connected, String note, String error) {}

    public record State(String version, String root, List<Tool> tools, List<String> skills, boolean modules) {}

    public enum Job { NONE, CHECK, INSTALL, CONNECT, REMOVE, MODULES }

    private Mcp() {}

    public static void openNodePage() {}

    public static State state() { return null; }

    public static String node() { return ""; }

    public static Job job() { return Job.NONE; }

    public static boolean busy() { return false; }

    public static String failure() { return ""; }

    public static boolean agentLive() { return false; }

    public static List<String> tail() { return List.of(); }

    public static void look() {}

    public static void install() {}

    public static void connect() {}

    public static void remove() {}

    public static void modules() {}

    public static String startAgent(Path jar, String work, String token) {
        throw new UnsupportedOperationException("в браузере нет");
    }
}
