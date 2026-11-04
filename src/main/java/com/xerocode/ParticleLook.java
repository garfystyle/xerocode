package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ParticleLook {
    public enum Motion { FALL, RISE, BURST, SWIRL, DRIP, DRIFT, FLASH }

    private static final Map<String, String> ALIASES = Map.of(
            "EXPLOSION_EMITTER", "EXPLOSION",
            "GUST_EMITTER_SMALL", "GUST",
            "GUST_EMITTER_LARGE", "GUST");

    private static final Map<String, String> ITEMS = Map.of(
            "ITEM_COBWEB", "minecraft:cobweb",
            "ITEM_SNOWBALL", "minecraft:snowball",
            "ITEM_SLIME", "minecraft:slime_ball",
            "ELDER_GUARDIAN", "minecraft:prismarine_shard");

    private static final Set<String> FALL = Set.of(
            "RAIN", "ASH", "WHITE_ASH", "CRIMSON_SPORE", "WARPED_SPORE", "SNOWFLAKE",
            "CHERRY_LEAVES", "PALE_OAK_LEAVES", "TINTED_LEAVES", "MYCELIUM", "SPORE_BLOSSOM_AIR",
            "CURRENT_DOWN", "SQUID_INK", "GLOW_SQUID_INK");

    private static final Set<String> RISE = Set.of(
            "SOUL", "LAVA", "BUBBLE", "BUBBLE_COLUMN_UP", "HEART", "NOTE", "HAPPY_VILLAGER",
            "ANGRY_VILLAGER", "WITCH", "EFFECT", "INSTANT_EFFECT", "ENTITY_EFFECT", "CLOUD",
            "COMPOSTER", "SCULK_SOUL", "SHRIEK", "DUST_PLUME", "GUST", "SMALL_GUST",
            "DRAGON_BREATH", "INFESTED", "SNEEZE", "TOTEM_OF_UNDYING");

    private static final Set<String> SWIRL = Set.of(
            "PORTAL", "REVERSE_PORTAL", "ENCHANT", "NAUTILUS", "END_ROD", "GLOW", "FIREFLY",
            "VAULT_CONNECTION", "TRIAL_SPAWNER_DETECTION", "TRIAL_SPAWNER_DETECTION_OMINOUS",
            "SCULK_CHARGE", "TRIAL_OMEN", "RAID_OMEN", "OMINOUS_SPAWNING", "TRAIL");

    private static final Set<String> BURST = Set.of(
            "POOF", "EXPLOSION", "FIREWORK", "CRIT", "ENCHANTED_HIT", "DAMAGE_INDICATOR",
            "SWEEP_ATTACK", "SPIT", "SONIC_BOOM", "ELECTRIC_SPARK", "SCRAPE", "WAX_ON",
            "WAX_OFF", "EGG_CRACK", "SPLASH", "FISHING", "DUST", "DUST_COLOR_TRANSITION",
            "DUST_PILLAR", "BLOCK", "BLOCK_CRUMBLE", "ITEM", "ITEM_COBWEB", "ITEM_SNOWBALL",
            "ITEM_SLIME", "ELDER_GUARDIAN", "SCULK_CHARGE_POP", "VIBRATION", "DOLPHIN");

    private static final Map<String, int[]> TINTS = Map.ofEntries(
            Map.entry("CLOUD", new int[]{0xB7B7B7, 0xC5C5C5, 0xD0D0D0,
                    0xDDDDDD, 0xEAEAEA, 0xF7F7F7}),
            Map.entry("CRIT", new int[]{0xA09A90, 0xADA69B, 0xB7AFA4,
                    0xC3BBB0, 0xD2CABD, 0xE0D7C9}),
            Map.entry("DAMAGE_INDICATOR", new int[]{0x9E988E, 0xACA59B, 0xB9B2A6,
                    0xC4BCB1, 0xD1C8BC, 0xDFD6C8}),
            Map.entry("FALLING_LAVA", new int[]{0xFF4815}),
            Map.entry("LANDING_LAVA", new int[]{0xFF4815}),
            Map.entry("DRIPPING_WATER", new int[]{0x334CFF}),
            Map.entry("FALLING_WATER", new int[]{0x334CFF}),
            Map.entry("ENCHANTED_HIT", new int[]{0x307C91, 0x34859C, 0x378EA7,
                    0x3B98B3, 0x3EA1BC, 0x43ACCA}),
            Map.entry("ENCHANT", new int[]{0x676773, 0x7C7C8A, 0x8F8F9F,
                    0xA5A5B7, 0xC0C0D5, 0xD7D7EF}),
            Map.entry("EXPLOSION", new int[]{0x727272, 0x868686, 0xA2A2A2,
                    0xBCBCBC, 0xD5D5D5, 0xF2F2F2}),
            Map.entry("SONIC_BOOM", new int[]{0x6E6E6E, 0x868686, 0xA0A0A0,
                    0xBFBFBF, 0xDADADA, 0xF4F4F4}),
            Map.entry("LARGE_SMOKE", new int[]{0x060606, 0x131313, 0x1F1F1F,
                    0x2D2D2D, 0x3A3A3A, 0x454545}),
            Map.entry("MYCELIUM", new int[]{0x343434, 0x383838, 0x3D3D3D,
                    0x414141, 0x454545, 0x494949}),
            Map.entry("POOF", new int[]{0xB7B7B7, 0xC5C5C5, 0xD2D2D2,
                    0xDFDFDF, 0xECECEC, 0xF9F9F9}),
            Map.entry("PORTAL", new int[]{0x692375, 0x7C298A, 0x9431A5,
                    0xAD39C0, 0xC240D7, 0xD948F1}),
            Map.entry("SMOKE", new int[]{0x060606, 0x151515, 0x212121,
                    0x2D2D2D, 0x393939, 0x464646}),
            Map.entry("WHITE_SMOKE", new int[]{0xBAB1C2}),
            Map.entry("SNEEZE", new int[]{0x38FF87}),
            Map.entry("SPIT", new int[]{0xB8B8B8, 0xC5C5C5, 0xD0D0D0,
                    0xDEDEDE, 0xEAEAEA, 0xF7F7F7}),
            Map.entry("SQUID_INK", new int[]{0x000000}),
            Map.entry("SWEEP_ATTACK", new int[]{0x707070, 0x8D8D8D, 0xA7A7A7,
                    0xBEBEBE, 0xD5D5D5, 0xEFEFEF}),
            Map.entry("TOTEM_OF_UNDYING", new int[]{0x2A721B, 0x27880F, 0x1D9E18,
                    0x2DA724, 0xB8A116, 0x9ECF26}),
            Map.entry("UNDERWATER", new int[]{0x6666B2}),
            Map.entry("WITCH", new int[]{0x630063, 0x740074, 0x860086,
                    0x9C009C, 0xB300B3, 0xCD00CD}),
            Map.entry("NAUTILUS", new int[]{0x676772, 0x7C7C8A, 0x9191A1,
                    0xAAAABD, 0xC7C7DD, 0xDADAF2}),
            Map.entry("DOLPHIN", new int[]{0x4C7FFF}),
            Map.entry("DRIPPING_HONEY", new int[]{0x9E8114}),
            Map.entry("FALLING_HONEY", new int[]{0x947214}),
            Map.entry("LANDING_HONEY", new int[]{0x856814}),
            Map.entry("FALLING_SPORE_BLOSSOM", new int[]{0x517F38}),
            Map.entry("ASH", new int[]{0x090909, 0x1D1D1D, 0x333333, 0x494949, 0x626262, 0x777777}),
            Map.entry("CRIMSON_SPORE", new int[]{0xE5667F}),
            Map.entry("WARPED_SPORE", new int[]{0x19194C}),
            Map.entry("SPORE_BLOSSOM_AIR", new int[]{0x517F38}),
            Map.entry("DRIPPING_OBSIDIAN_TEAR", new int[]{0x8207E3}),
            Map.entry("FALLING_OBSIDIAN_TEAR", new int[]{0x8207E3}),
            Map.entry("LANDING_OBSIDIAN_TEAR", new int[]{0x8207E3}),
            Map.entry("REVERSE_PORTAL", new int[]{0x672273, 0x822B91, 0x9933AA,
                    0xAC39C0, 0xC140D6, 0xDA48F3}),
            Map.entry("WHITE_ASH", new int[]{0xBAB1C2}),
            Map.entry("SNOWFLAKE", new int[]{0xEBF5FE}),
            Map.entry("FALLING_DRIPSTONE_LAVA", new int[]{0xFF4815}),
            Map.entry("DRIPPING_DRIPSTONE_WATER", new int[]{0x334CFF}),
            Map.entry("FALLING_DRIPSTONE_WATER", new int[]{0x334CFF}),
            Map.entry("GLOW_SQUID_INK", new int[]{0x33CC99}),
            Map.entry("GLOW", new int[]{0x146666, 0x99FFCC}),
            Map.entry("WAX_ON", new int[]{0xE88C14}),
            Map.entry("WAX_OFF", new int[]{0xFFE5FF}),
            Map.entry("ELECTRIC_SPARK", new int[]{0xFFE5FF}),
            Map.entry("SCRAPE", new int[]{0x499382, 0x6DC49E}),
            Map.entry("DUST_PLUME", new int[]{0x8C8394, 0x948B9C, 0x9C93A4,
                    0xA69DAE, 0xADA4B5, 0xB5ACBD}),
            Map.entry("VAULT_CONNECTION", new int[]{0x666671, 0x7E7E8C, 0x9898A9,
                    0xAFAFC2, 0xC4C4DA, 0xDBDBF4}),
            Map.entry("DRAGON_BREATH", new int[]{0xBC00DF, 0xC800D5, 0xCE00D6,
                    0xCD00E9, 0xCD00F7, 0xDC00E1}));

    private static final Set<String> COOLING = Set.of("DRIPPING_LAVA", "DRIPPING_DRIPSTONE_LAVA");

    private static final Map<String, List<Identifier>> TEXTURES = new HashMap<>();

    public static List<Identifier> textures(String enumName) {
        if (enumName == null || enumName.isEmpty()) return List.of();
        String key = ALIASES.getOrDefault(enumName, enumName);
        List<Identifier> known = TEXTURES.get(key);
        if (known != null) return known;
        List<Identifier> out = read(key);
        TEXTURES.put(key, out);
        return out;
    }

    private static List<Identifier> read(String enumName) {
        Identifier definition = Identifier.tryParse(
                "minecraft:particles/" + enumName.toLowerCase(Locale.ROOT) + ".json");
        Minecraft client = Minecraft.getInstance();
        if (definition == null || client.getResourceManager() == null) return List.of();
        try (BufferedReader in = client.getResourceManager().openAsReader(definition)) {
            JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
            if (!root.has("textures")) return List.of();
            List<Identifier> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("textures")) {
                Identifier id = Identifier.tryParse(e.getAsString());
                if (id != null) out.add(id);
            }
            return List.copyOf(out);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static TextureAtlasSprite sprite(Identifier texture) {
        Minecraft client = Minecraft.getInstance();
        try {
            return client.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(texture);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack item(String enumName, String material) {
        Pickers.Entry entry = Pickers.particle(enumName);
        if (entry != null && entry.has(Pickers.MATERIAL) && material != null
                && !material.isEmpty())
            return Catalog.stackOf(material);
        String fixed = ITEMS.get(enumName);
        if (fixed != null) return Catalog.stackOf(fixed);
        return entry == null ? ItemStack.EMPTY : Catalog.stackOf(entry.item);
    }

    public static int[] tints(String enumName) {
        return enumName == null ? null : TINTS.get(enumName);
    }

    public static boolean cools(String enumName) {
        return enumName != null && COOLING.contains(enumName);
    }

    public static int cooled(double progress) {
        double ticks = 40 * Math.max(0, Math.min(1, progress));
        int green = (int) Math.floor(16 / (ticks + 16) * 255);
        int blue = (int) Math.floor(4 / (ticks + 8) * 255);
        return 0xFF0000 | green << 8 | blue;
    }

    public static Motion motion(String enumName) {
        String id = enumName == null ? "" : enumName;
        if (id.equals("FLASH")) return Motion.FLASH;
        if (id.startsWith("DRIPPING_")) return Motion.DRIP;
        if (id.startsWith("FALLING_") || FALL.contains(id)) return Motion.FALL;
        if (id.startsWith("LANDING_") || id.startsWith("GUST_EMITTER")
                || id.equals("EXPLOSION_EMITTER") || BURST.contains(id)) return Motion.BURST;
        if (id.contains("FLAME") || id.contains("SMOKE") || id.startsWith("CAMPFIRE")
                || RISE.contains(id)) return Motion.RISE;
        if (SWIRL.contains(id)) return Motion.SWIRL;
        return Motion.DRIFT;
    }

    private ParticleLook() {}
}
