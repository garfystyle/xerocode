package com.xerocode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ModulePublish {
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{3,12}");
    private static final String EQUIPMENT = "creative_plus:editor_equipment";
    private static final String TOOL = "Манипулятор";
    private static final String CHOSEN = "Выбрано:", CATEGORIES = "Категории модулей", MENU = "Модули";
    private static final String MINE = "Мои модули", SETTINGS = "Настройки модуля", PUBLIC = "⏺ Публичный";
    private static final int MINE_SLOT = 53, PRIVACY_SLOT = 12, PRIVACY_CLICKS = 4;
    private static final int LINES_PER_FLOOR = (Codespace.LAST_Z - Codespace.FIRST_Z) / Codespace.LINE_STEP + 1;
    private static final int SAVE_SLOT = 14, CLEAR_SLOT = 16;
    private static final int TP_TICKS = 10, PICK_TICKS = 4, STEP_TICKS = 100, CLEAR_TICKS = 15;
    private static final double REACH = 4.0;
    private static final String NOT_PUBLIC = " — публичным сделай сам: /module → «Мои модули» → ПКМ → «Доступность»";
    private static final Pattern COUNT = Pattern.compile("Выбрано:\\s*(\\d+)");
    private static final String[] REFUSALS = {"название модуля", "не менее одной строки", "лимит",
            "диапазон", "уже существует", "отменено"};

    public interface Report {
        void step(String text);
        void done(boolean ok, String text);
    }

    private enum Step { CLEAR, MOVE, PICK, MENU, CATEGORY, NAME, RESULT, MODULES, MY, SETTINGS }

    private static final class Job {
        final List<BlockPos> heads;
        final String id, category;
        final boolean makePublic;
        final Report report;
        final int selectedBefore;
        int slot = -1, index, ticks, clicks;
        boolean copied, clearSent;
        String saved = "";
        long since = System.currentTimeMillis();
        Step step = Step.CLEAR;

        Job(List<BlockPos> heads, String id, String category, boolean makePublic, Report report,
            int selectedBefore) {
            this.heads = heads;
            this.id = id;
            this.category = category;
            this.makePublic = makePublic;
            this.report = report;
            this.selectedBefore = selectedBefore;
        }
    }

    private static Job job;

    private ModulePublish() {}

    public static boolean busy() { return job != null; }

    public static BlockPos head(int position) {
        return new BlockPos(Codespace.LINE_X, Codespace.FIRST_Y + Codespace.FLOOR_H * (position / LINES_PER_FLOOR),
                Codespace.FIRST_Z + Codespace.LINE_STEP * (position % LINES_PER_FLOOR));
    }

    public static String start(List<Integer> positions, String id, String category, boolean makePublic,
                               Report report) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientPacketListener net = mc.getConnection();
        if (player == null || net == null || mc.level == null || !Codespace.inDev(mc.level))
            return "выкладывать модуль можно только в /dev";
        if (job != null) return "модуль уже выкладывается";
        if (positions.isEmpty()) return "нечего выкладывать";
        if (!ID.matcher(id).matches()) return "айди: 3–12 символов — латиница, цифры, _ и -";
        Inventory inv = player.getInventory();
        Job next = new Job(new ArrayList<>(), id, category, makePublic, report, inv.getSelectedSlot());
        for (int p : positions) next.heads.add(head(p));
        for (BlockPos pos : next.heads)
            if (mc.level.getBlockState(pos).isAir()) return "в мире нет строки " + (positions.get(next.heads.indexOf(pos)) + 1)
                    + " — сначала отправь код в мир";
        int tool = -1;
        for (int i = 0; i < 36 && tool < 0; i++) if (tool(inv.getItem(i))) tool = i;
        if (tool < 0) return "нет манипулятора — набери /editor items";
        if (tool < 9) next.slot = tool;
        else {
            for (int i = 0; i < 9 && next.slot < 0; i++) if (inv.getItem(i).isEmpty()) next.slot = i;
            if (next.slot < 0) return "освободи один слот в хотбаре — туда ляжет манипулятор";
            ItemStack copy = inv.getItem(tool).copy();
            inv.setItem(next.slot, copy);
            mc.gameMode.handleCreativeModeItemAdd(copy, 36 + next.slot);
            next.copied = true;
        }
        inv.setSelectedSlot(next.slot);
        net.send(new ServerboundSetCarriedItemPacket(next.slot));
        mc.gui.setScreen(null);
        job = next;
        report.step("снимаю старое выделение");
        return "";
    }

    static void tick(Minecraft mc) {
        Job j = job;
        if (j == null) return;
        LocalPlayer player = mc.player;
        ClientPacketListener net = mc.getConnection();
        if (player == null || net == null) {
            finish(mc, false, "связь с сервером пропала");
            return;
        }
        j.ticks++;
        Screen screen = mc.gui.screen();
        switch (j.step) {
            case CLEAR -> {
                if (!j.clearSent) {
                    swap(net);
                    j.clearSent = true;
                    return;
                }
                if (chosen(screen) >= 0) {
                    click(mc, (ContainerScreen) screen, CLEAR_SLOT);
                    next(j, Step.MOVE);
                } else if (j.ticks > CLEAR_TICKS) next(j, Step.MOVE);
            }
            case MOVE -> {
                if (screen != null) return;
                BlockPos head = j.heads.get(j.index);
                if (near(player, head)) {
                    next(j, Step.PICK);
                    return;
                }
                if (j.ticks == 1 || j.ticks % (TP_TICKS * 4) == 0)
                    net.sendCommand(String.format(Locale.ROOT, "editor tp %.2f %d %.1f",
                            Codespace.LINE_X - 1.15, head.getY(), head.getZ() + 0.5));
                if (j.ticks > STEP_TICKS) finish(mc, false, "не вышло подойти к строке " + (j.index + 1));
            }
            case PICK -> {
                if (j.ticks < PICK_TICKS) return;
                BlockPos head = j.heads.get(j.index);
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(head), Direction.WEST, head, false));
                j.index++;
                j.report.step("выделено строк: " + j.index + " из " + j.heads.size());
                next(j, j.index < j.heads.size() ? Step.MOVE : Step.MENU);
            }
            case MENU -> {
                if (j.ticks == PICK_TICKS) swap(net);
                int count = chosen(screen);
                if (count < 0) {
                    if (j.ticks > STEP_TICKS) finish(mc, false, "меню манипулятора не открылось");
                    return;
                }
                if (count != j.heads.size()) {
                    click(mc, (ContainerScreen) screen, CLEAR_SLOT);
                    finish(mc, false, "выделилось строк: " + count + " из " + j.heads.size() + " — попробуй ещё раз");
                    return;
                }
                click(mc, (ContainerScreen) screen, SAVE_SLOT);
                j.report.step("сохраняю модуль " + j.id);
                next(j, Step.CATEGORY);
            }
            case CATEGORY -> {
                if (!titled(screen, CATEGORIES)) {
                    if (j.ticks > STEP_TICKS) finish(mc, false, "сервер не открыл выбор раздела");
                    return;
                }
                int slot = named((ContainerScreen) screen, j.category);
                if (slot < 0) {
                    finish(mc, false, "в меню нет раздела «" + j.category + "»");
                    return;
                }
                j.since = System.currentTimeMillis();
                click(mc, (ContainerScreen) screen, slot);
                next(j, Step.NAME);
            }
            case NAME -> {
                for (String line : Publish.answersSince(j.since)) {
                    if (!line.toLowerCase(Locale.ROOT).contains("айди модуля")) continue;
                    net.sendChat(j.id);
                    j.since = System.currentTimeMillis();
                    next(j, Step.RESULT);
                    return;
                }
                if (j.ticks > STEP_TICKS) finish(mc, false, "сервер не спросил айди модуля");
            }
            case RESULT -> {
                for (String line : Publish.answersSince(j.since)) {
                    String low = line.toLowerCase(Locale.ROOT);
                    if (low.contains("сохранён")) {
                        j.saved = line;
                        if (!j.makePublic) {
                            finish(mc, true, line);
                            return;
                        }
                        j.report.step("делаю модуль публичным");
                        net.sendCommand("module");
                        next(j, Step.MODULES);
                        return;
                    }
                    for (String no : REFUSALS)
                        if (low.contains(no)) {
                            finish(mc, false, line);
                            return;
                        }
                }
                if (j.ticks > STEP_TICKS) finish(mc, false, "сервер не ответил, сохранён ли модуль");
            }
            case MODULES -> {
                if (titled(screen, MENU)) {
                    click(mc, (ContainerScreen) screen, MINE_SLOT);
                    next(j, Step.MY);
                } else if (j.ticks > STEP_TICKS) finish(mc, true, j.saved + NOT_PUBLIC);
            }
            case MY -> {
                if (!titled(screen, MINE)) {
                    if (j.ticks > STEP_TICKS) finish(mc, true, j.saved + NOT_PUBLIC);
                    return;
                }
                int slot = moduleSlot((ContainerScreen) screen, player.getName().getString() + "/" + j.id);
                if (slot < 0) {
                    if (j.ticks > STEP_TICKS / 2) finish(mc, true, j.saved + NOT_PUBLIC);
                    return;
                }
                mc.gameMode.handleContainerInput(((ContainerScreen) screen).getMenu().containerId, slot, 1,
                        ContainerInput.PICKUP, player);
                next(j, Step.SETTINGS);
            }
            case SETTINGS -> {
                if (!titled(screen, SETTINGS)) {
                    if (j.ticks > STEP_TICKS) finish(mc, true, j.saved + NOT_PUBLIC);
                    return;
                }
                ContainerScreen menu = (ContainerScreen) screen;
                if (lore(menu.getMenu().slots.get(PRIVACY_SLOT).getItem()).contains(PUBLIC)) {
                    finish(mc, true, "Модуль " + player.getName().getString() + "/" + j.id + " выложен и публичный");
                    return;
                }
                if (j.clicks >= PRIVACY_CLICKS) {
                    finish(mc, true, j.saved + NOT_PUBLIC);
                    return;
                }
                if (j.ticks % 10 == 1) {
                    j.clicks++;
                    click(mc, menu, PRIVACY_SLOT);
                }
            }
        }
    }

    private static boolean titled(Screen screen, String title) {
        return screen instanceof ContainerScreen c && title.equals(c.getTitle().getString());
    }

    private static int named(ContainerScreen c, String name) {
        for (int i = 0; i < c.getMenu().slots.size() - 36; i++)
            if (name.equals(c.getMenu().slots.get(i).getItem().getHoverName().getString())) return i;
        return -1;
    }

    private static int moduleSlot(ContainerScreen c, String id) {
        for (int i = 0; i < c.getMenu().slots.size() - 36; i++)
            if (lore(c.getMenu().slots.get(i).getItem()).contains("ID: " + id)) return i;
        return -1;
    }

    private static String lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder out = new StringBuilder();
        for (Component line : lore.lines()) out.append(line.getString()).append('\n');
        return out.toString();
    }

    private static void next(Job j, Step step) {
        j.step = step;
        j.ticks = 0;
    }

    private static void finish(Minecraft mc, boolean ok, String said) {
        Job j = job;
        job = null;
        LocalPlayer player = mc.player;
        if (player != null) {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            Inventory inv = player.getInventory();
            if (j.copied && tool(inv.getItem(j.slot))) {
                inv.setItem(j.slot, ItemStack.EMPTY);
                mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + j.slot);
            }
            inv.setSelectedSlot(j.selectedBefore);
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSetCarriedItemPacket(j.selectedBefore));
        }
        j.report.done(ok, said);
    }

    private static boolean tool(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.COMPARATOR) || !TOOL.equals(stack.getHoverName().getString())) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().toString().contains(EQUIPMENT);
    }

    private static boolean near(LocalPlayer player, BlockPos head) {
        return player.getEyePosition().distanceTo(Vec3.atCenterOf(head)) <= REACH;
    }

    private static int chosen(Screen screen) {
        if (!(screen instanceof ContainerScreen c)) return -1;
        Matcher m = COUNT.matcher(c.getTitle().getString());
        return c.getTitle().getString().startsWith(CHOSEN) && m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static void swap(ClientPacketListener net) {
        net.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO, Direction.DOWN));
    }

    private static void click(Minecraft mc, ContainerScreen c, int slot) {
        mc.gameMode.handleContainerInput(c.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
