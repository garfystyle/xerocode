package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class LinePut {
    private static final double NEAR = 6.0;
    private static final int NEAR_TICKS = 80, HAND_TICKS = 4, WIPE_TICKS = 40, SETTLE_TICKS = 6;
    private static final int GIVE_TICKS = 4, BUILD_TICKS = 40, COOLDOWN_TICKS = 40, TRIES = 2;
    private static final String SAID_BROKEN = "Шаблон повреждён";

    private enum Step { NEAR, HAND, WIPE, PAUSE, GIVE, BUILD }

    private static LinePut current;

    private final ClientLevel world;
    private final LineEdit.Where at;
    private final JsonObject fresh, original;
    private final int slot, selectedBefore;
    private final ItemStack slotBefore;
    private final Vec3 origin;
    private final String backup;
    private Step step = Step.NEAR;
    private JsonObject placing;
    private int timer = NEAR_TICKS, steady, tries;
    private boolean restoring, refused;

    private LinePut(Minecraft client, LineEdit.Where at, JsonObject fresh, JsonObject original,
                    String backup) {
        Inventory inv = client.player.getInventory();
        this.world = client.level;
        this.at = at;
        this.fresh = fresh;
        this.original = original;
        this.backup = backup;
        this.slot = freeSlot(inv);
        this.slotBefore = inv.getItem(slot).copy();
        this.selectedBefore = inv.getSelectedSlot();
        this.origin = client.player.position();
        this.placing = fresh;
    }

    public static boolean busy() { return current != null; }

    public static String start(Minecraft client, LineEdit.Where at, JsonObject fresh, JsonObject original) {
        if (current != null) return "строка уже ставится";
        if (client.player == null || client.level == null || client.gameMode == null) return "нужен мир";
        if (!Codespace.inDev(client.level)) return "поставить строку можно только в /dev";
        if (at == null) return "неизвестно, откуда строка — возьми её в руку";
        if (fresh == null) return "строка начинается с события, функции или процесса";
        String backup = original == null ? "" : saveBackup(client.level, at, original);
        current = new LinePut(client, at, fresh, original, backup);
        current.teleport(client);
        LineEdit.say(client, "ставлю строку на место · " + at.said(), false);
        return null;
    }

    public static void serverSaid(String text) {
        if (current != null && text.contains(SAID_BROKEN)) current.refused = true;
    }

    public static void tick(Minecraft client) {
        LinePut put = current;
        if (put == null) return;
        if (client.player == null || client.gameMode == null || client.level != put.world) {
            current = null;
            LineEdit.say(client, "мир сменился — строка не поставлена" + put.backupSaid(), true);
            return;
        }
        put.step(client);
    }

    private void step(Minecraft client) {
        LocalPlayer player = client.player;
        BlockPos head = at.event();
        switch (step) {
            case NEAR -> {
                double away = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(head)));
                if (away > NEAR && --timer > 0) return;
                if (away > NEAR) { finish(client, "не дошёл до строки — она не тронута", true, false); return; }
                hold(client, ItemStack.EMPTY);
                step = Step.HAND;
                timer = HAND_TICKS;
            }
            case HAND -> {
                if (--timer > 0) return;
                if (world.getBlockState(head).isAir()) { give(client, fresh); return; }
                look(client, head);
                client.gameMode.startDestroyBlock(head, Direction.UP);
                step = Step.WIPE;
                timer = WIPE_TICKS;
                steady = 0;
            }
            case WIPE -> {
                steady = world.getBlockState(head).isAir() ? steady + 1 : 0;
                if (steady >= SETTLE_TICKS) { give(client, fresh); return; }
                if (--timer <= 0) finish(client, "сервер не дал стереть строку — она не тронута", true, false);
            }
            case PAUSE -> {
                if (--timer <= 0) give(client, placing);
            }
            case GIVE -> {
                if (--timer > 0) return;
                BlockPos floor = head.below();
                look(client, floor);
                refused = false;
                client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                        new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5),
                        Direction.UP, floor, false));
                step = Step.BUILD;
                timer = BUILD_TICKS;
                steady = 0;
            }
            case BUILD -> {
                boolean built = !world.getBlockState(head).isAir() && !world.getBlockState(head.east()).isAir();
                steady = built ? steady + 1 : 0;
                if (steady >= SETTLE_TICKS) {
                    if (restoring) finish(client, "новая строка не встала — вернул прежнюю", true, false);
                    else finish(client, "строка на месте · " + at.said(), false, false);
                    return;
                }
                if (!refused && --timer > 0) return;
                if (!refused && ++tries < TRIES) { pause(placing); return; }
                if (!restoring && original != null) {
                    restoring = true;
                    tries = 0;
                    pause(original);
                    return;
                }
                finish(client, "строку стёр, а поставить не вышло" + backupSaid(), true, true);
            }
        }
    }

    private void pause(JsonObject handler) {
        placing = handler;
        step = Step.PAUSE;
        timer = COOLDOWN_TICKS;
    }

    private void give(Minecraft client, JsonObject handler) {
        placing = handler;
        hold(client, LineEdit.stack(handler, restoring ? "прежняя строка · " + at.said() : at.said()));
        step = Step.GIVE;
        timer = GIVE_TICKS;
    }

    private void hold(Minecraft client, ItemStack stack) {
        Inventory inv = client.player.getInventory();
        inv.setItem(slot, stack);
        client.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        inv.setSelectedSlot(slot);
    }

    private void look(Minecraft client, BlockPos pos) {
        LocalPlayer player = client.player;
        Vec3 eye = player.getEyePosition();
        double dx = pos.getX() + 0.5 - eye.x, dy = pos.getY() + 1.0 - eye.y, dz = pos.getZ() + 0.5 - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        ClientPacketListener net = client.getConnection();
        if (net != null) net.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(),
                player.horizontalCollision));
    }

    private void teleport(Minecraft client) {
        ClientPacketListener net = client.getConnection();
        BlockPos head = at.event();
        if (net != null) net.sendCommand(String.format(Locale.ROOT,
                "editor tp %.2f %d %.1f", 2.85, head.getY(), head.getZ() + 0.5));
    }

    private void finish(Minecraft client, String said, boolean bad, boolean keepInHand) {
        current = null;
        if (!keepInHand) {
            Inventory inv = client.player.getInventory();
            inv.setItem(slot, slotBefore);
            client.gameMode.handleCreativeModeItemAdd(slotBefore, 36 + slot);
            inv.setSelectedSlot(selectedBefore);
            ClientPacketListener net = client.getConnection();
            if (net != null) net.sendCommand(String.format(Locale.ROOT,
                    "editor tp %.2f %.2f %.2f", origin.x, origin.y, origin.z));
        }
        LineEdit.say(client, keepInHand ? said + " · прежняя строка в руке" : said, bad);
    }

    private String backupSaid() {
        return backup.isEmpty() ? "" : " · копия: " + backup;
    }

    private static int freeSlot(Inventory inv) {
        for (int i = Inventory.getSelectionSize() - 1; i >= 0; i--) if (inv.getItem(i).isEmpty()) return i;
        return inv.getSelectedSlot();
    }

    private static String saveBackup(ClientLevel world, LineEdit.Where at, JsonObject original) {
        try {
            String name = "line_" + Codespace.worldId(world) + "-" + at.floor() + "-" + at.line() + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH.mm.ss")) + ".json";
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            list.add(original);
            root.add("handlers", list);
            Json.write(Codespace.savedDir().resolve(name), root);
            return "saved/" + name;
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] копия строки не записалась", e);
            return "";
        }
    }
}
