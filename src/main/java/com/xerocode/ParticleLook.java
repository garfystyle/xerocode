package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ParticleLook {
    public enum Motion { FALL, RISE, BURST, SWIRL, DRIP, DRIFT }

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
            "SWEEP_ATTACK", "SPIT", "FLASH", "SONIC_BOOM", "ELECTRIC_SPARK", "SCRAPE", "WAX_ON",
            "WAX_OFF", "EGG_CRACK", "SPLASH", "FISHING", "DUST", "DUST_COLOR_TRANSITION",
            "DUST_PILLAR", "BLOCK", "BLOCK_CRUMBLE", "ITEM", "ITEM_COBWEB", "ITEM_SNOWBALL",
            "ITEM_SLIME", "ELDER_GUARDIAN", "SCULK_CHARGE_POP", "VIBRATION", "DOLPHIN");

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
        MinecraftClient client = MinecraftClient.getInstance();
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

    public static Sprite sprite(Identifier texture) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            return client.getAtlasManager().getAtlasTexture(Atlases.PARTICLES).getSprite(texture);
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

    public static Motion motion(String enumName) {
        String id = enumName == null ? "" : enumName;
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
