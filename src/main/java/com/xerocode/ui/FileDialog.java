package com.xerocode.ui;

import com.xerocode.XeroCode;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.function.Consumer;

final class FileDialog {
    static String hint() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getWindow() != null && client.getWindow().isFullscreen()
                ? "окно выбора файла открыто — сверни игру, оно позади"
                : "выбери файл в окне проводника";
    }

    static void open(String title, String start, String[] masks, String kind,
                     Consumer<Path> done) {
        MinecraftClient client = MinecraftClient.getInstance();
        Thread thread = new Thread(() -> {
            String picked = ask(title, start, masks, kind);
            client.execute(() -> done.accept(
                    picked == null || picked.isEmpty() ? null : Path.of(picked)));
        }, "xerocode-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private static String ask(String title, String start, String[] masks, String kind) {
        try {
            TinyFileDialogs.tinyfd_setGlobalInt("tinyfd_winUtf8", 1);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(masks.length);
                for (String mask : masks) filter.put(stack.UTF8(mask));
                filter.flip();
                return TinyFileDialogs.tinyfd_openFileDialog(title, start, filter, kind, false);
            }
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] окно выбора файла не открылось", e);
            return null;
        }
    }

    private FileDialog() {}
}
