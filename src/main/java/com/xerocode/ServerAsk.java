package com.xerocode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;

public final class ServerAsk {
    private static final String ACCEPT = "принять";

    private static Screen before;

    private ServerAsk() {}

    static void tick(Minecraft client) {
        Screen screen = client.gui.screen();
        if (!(screen instanceof BookViewScreen book)) {
            before = screen;
            return;
        }
        Publish.Job job = Publish.awaitingConfirm();
        ClientPacketListener net = client.getConnection();
        if (job == null || net == null) return;
        Component page = page(book, job.url);
        if (page == null) return;
        ClickEvent.Custom yes = accept(page);
        if (yes == null) return;
        net.send(new ServerboundCustomClickActionPacket(yes.id(), yes.payload()));
        job.confirmed(question(page.getString(), job.url));
        client.gui.setScreen(before);
    }

    private static Component page(BookViewScreen book, String url) {
        if (url.isEmpty()) return null;
        try {
            Field field = BookViewScreen.class.getDeclaredField("bookAccess");
            field.setAccessible(true);
            BookViewScreen.BookAccess access = (BookViewScreen.BookAccess) field.get(book);
            for (Component page : access.pages())
                if (page.getString().contains(url)) return page;
        } catch (ReflectiveOperationException | RuntimeException e) {
            XeroCode.LOG.warn("[xerocode] книга сервера не читается", e);
        }
        return null;
    }

    private static ClickEvent.Custom accept(Component page) {
        List<Component> queue = new ArrayList<>(List.of(page));
        while (!queue.isEmpty()) {
            Component part = queue.removeFirst();
            if (part.getStyle().getClickEvent() instanceof ClickEvent.Custom custom
                    && part.getString().toLowerCase(Locale.ROOT).contains(ACCEPT)) return custom;
            queue.addAll(part.getSiblings());
        }
        return null;
    }

    private static String question(String text, String url) {
        String plain = text.replace(url, "").replaceAll("\\s+", " ").trim();
        int buttons = plain.toLowerCase(Locale.ROOT).indexOf(ACCEPT);
        return (buttons > 0 ? plain.substring(0, buttons) : plain).trim();
    }
}
