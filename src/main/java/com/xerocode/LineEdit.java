package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.ui.EditorScreen;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class LineEdit {
    public static final String TEMPLATE_KEY = "justmc:template";
    private static final int CHECK_TICKS = 12;

    public record Where(BlockPos event, int floor, int line) {
        public String said() { return "этаж " + floor + " · линия " + line; }
    }

    private static Codespace.Scan reading;
    private static Where readingAt;
    private static ItemStack given;
    private static int givenSlot = -1, checkIn;
    private static String fallback = "";

    public static Where where(BlockPos pos) {
        int floor = Math.floorDiv(pos.getY() - Codespace.FIRST_Y, Codespace.FLOOR_H);
        int line = Math.floorDiv(pos.getZ(), Codespace.LINE_STEP);
        if (floor < 0 || floor >= Codespace.FLOORS) return null;
        int z = line * Codespace.LINE_STEP;
        if (z < Codespace.FIRST_Z || z > Codespace.LAST_Z) return null;
        BlockPos event = new BlockPos(Codespace.LINE_X, Codespace.FIRST_Y + floor * Codespace.FLOOR_H, z);
        return new Where(event, floor + 1, line);
    }

    private static Where detect(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return null;
        Where here = where(player.blockPosition());
        if (here != null && !client.level.getBlockState(here.event()).isAir()) return here;
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            Where aimed = where(block.getBlockPos());
            if (aimed != null && !client.level.getBlockState(aimed.event()).isAir()) return aimed;
        }
        return null;
    }

    private static void say(Minecraft client, String text, boolean bad) {
        client.gui.hud.setOverlayMessage(Component.literal(text)
                .withStyle(bad ? ChatFormatting.RED : ChatFormatting.AQUA), false);
    }

    public static void pressed(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!Codespace.inDev(client.level)) { say(client, "строку можно взять только в /dev", true); return; }
        if (reading != null) return;
        String raw = Codespace.template(client.player.getMainHandItem());
        if (raw != null) {
            JsonObject handler = decode(raw);
            if (handler == null) { say(client, "шаблон в руке не разобрался", true); return; }
            open(client, handler, null);
            return;
        }
        Where at = detect(client);
        if (at == null) { say(client, "встань на строку кода или возьми её шаблон в руку", true); return; }
        readingAt = at;
        reading = Codespace.line(client.level, at.event());
        say(client, "читаю строку · " + at.said(), false);
    }

    public static void tick(Minecraft client) {
        if (reading != null && reading.state == Codespace.State.RUNNING) reading.tick();
        if (reading != null && reading.state != Codespace.State.RUNNING) {
            Codespace.Scan done = reading;
            Where at = readingAt;
            reading = null;
            readingAt = null;
            if (done.state == Codespace.State.DONE && !done.handlers().isEmpty())
                open(client, done.handlers().get(0).getAsJsonObject(), at);
            else say(client, done.error.isEmpty() ? "строка не прочиталась" : done.error, true);
        }
        if (given != null && --checkIn <= 0) {
            LocalPlayer player = client.player;
            boolean kept = player != null && givenSlot >= 0
                    && Codespace.template(player.getInventory().getItem(givenSlot)) != null;
            if (kept) say(client, "шаблон в руке — поставь его в /dev", false);
            else {
                client.keyboardHandler.setClipboard(fallback);
                say(client, "сервер не дал положить шаблон — код строки в буфере обмена", true);
            }
            given = null;
            givenSlot = -1;
        }
    }

    private static JsonObject decode(String raw) {
        try {
            String json = Codespace.decompress(raw);
            return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void open(Minecraft client, JsonObject handler, Where at) {
        Script scratch = Script.scratch(at == null ? "из шаблона" : at.said());
        JsonArray one = new JsonArray();
        one.add(handler);
        Importer.Result res = Importer.importInto(scratch, one, client.font);
        if (scratch.roots.isEmpty()) {
            say(client, "в строке нечего показывать", true);
            return;
        }
        scratch.fitOnOpen = true;
        client.gui.setScreen(new EditorScreen(scratch));
        if (res.unknownCount > 0) say(client, "не разобрано блоков: " + res.unknownCount, true);
    }

    public static String encode(JsonObject handler) {
        byte[] input = handler.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static JsonObject handlerOf(List<Script.Node> chain) {
        Script one = new Script();
        Script.Root r = new Script.Root(0, 0);
        r.chain.addAll(chain);
        one.roots.add(r);
        JsonArray handlers = Exporter.export(one).json().getAsJsonArray("handlers");
        if (handlers.isEmpty()) return null;
        JsonObject h = handlers.get(0).getAsJsonObject();
        h.remove("position");
        return h;
    }

    public static String toHand(List<Script.Node> chain) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return "нужен мир";
        JsonObject handler = handlerOf(chain);
        if (handler == null) return "строка начинается с события, функции или процесса";
        String title = chain.get(0).declares() ? Functions.nameOf(chain.get(0)) : chain.get(0).action.name;
        ItemStack stack = new ItemStack(Items.ENDER_CHEST);
        CompoundTag bukkit = new CompoundTag();
        bukkit.putString(TEMPLATE_KEY, encode(handler));
        CompoundTag data = new CompoundTag();
        data.put("PublicBukkitValues", bukkit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Шаблон: " + title)
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
        int slot = player.getInventory().getSelectedSlot();
        player.getInventory().setItem(slot, stack);
        client.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        JsonObject whole = new JsonObject();
        JsonArray list = new JsonArray();
        list.add(handler);
        whole.add("handlers", list);
        fallback = whole.toString();
        given = stack;
        givenSlot = slot;
        checkIn = CHECK_TICKS;
        return null;
    }

    private LineEdit() {}
}
