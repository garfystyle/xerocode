package com.xerocode.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

public final class SpriteText {
    public static final List<String> GROUPS = List.of("Предметы", "Блоки");
    private static final String[] DIRS = {"item", "block"};
    private static final Identifier ITEMS = Identifier.withDefaultNamespace("items");
    private static final List<List<String>> LISTS = new ArrayList<>(List.of(List.of(), List.of()));
    private static final boolean[] READ = new boolean[DIRS.length];
    private static final String PLACEHOLDER = Component.object(new AtlasSprite(AtlasSprite.DEFAULT_ATLAS,
                    Identifier.withDefaultNamespace("block/stone")))
            .visit((style, text) -> Optional.of(text), Style.EMPTY).orElse(" ");

    public static boolean available() { return true; }

    public static List<String> ids(int group) {
        if (group < 0 || group >= DIRS.length) return List.of();
        if (!READ[group]) {
            READ[group] = true;
            List<String> out = new ArrayList<>();
            String dir = "textures/" + DIRS[group];
            try {
                for (Identifier id : Minecraft.getInstance().getResourceManager()
                        .listResources(dir, p -> p.getPath().endsWith(".png")).keySet()) {
                    if (!Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) continue;
                    String path = id.getPath();
                    out.add(path.substring("textures/".length(), path.length() - ".png".length()));
                }
            } catch (RuntimeException ignored) { }
            out.sort(null);
            LISTS.set(group, out);
        }
        return LISTS.get(group);
    }

    public static String tag(int group, String id) {
        return group == 0 ? "<sprite:items:" + id + ">" : "<sprite:" + id + ">";
    }

    public static McText.Run run(String atlas, String sprite, String raw, Style style) {
        Identifier a = atlasId(atlas);
        Identifier s = Identifier.tryParse(sprite);
        if (a == null || s == null) return new McText.Run(raw, style);
        return new McText.Run(PLACEHOLDER, style.withFont(new FontDescription.AtlasSprite(a, s)));
    }

    public static String tagOf(Style style) {
        if (!(style.getFont() instanceof FontDescription.AtlasSprite sprite)) return null;
        String path = shortId(sprite.spriteId());
        if (AtlasSprite.DEFAULT_ATLAS.equals(sprite.atlasId())) return "<sprite:" + path + ">";
        return "<sprite:" + shortId(sprite.atlasId()) + ":" + path + ">";
    }

    public static Component component(Style style) {
        if (!(style.getFont() instanceof FontDescription.AtlasSprite sprite)) return null;
        return Component.object(new AtlasSprite(sprite.atlasId(), sprite.spriteId()));
    }

    public static void draw(GuiGraphicsExtractor ctx, Font tr, int group, String id, int x, int y, int size) {
        Identifier s = Identifier.tryParse(id);
        if (s == null) return;
        Component c = Component.object(new AtlasSprite(group == 0 ? ITEMS : AtlasSprite.DEFAULT_ATLAS, s));
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(size / 8f, size / 8f);
        ctx.text(tr, c, 0, 0, 0xFFFFFFFF, false);
        m.popMatrix();
    }

    private static Identifier atlasId(String atlas) {
        if (atlas == null || atlas.isEmpty()) return AtlasSprite.DEFAULT_ATLAS;
        return Identifier.tryParse(atlas);
    }

    private static String shortId(Identifier id) {
        return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private SpriteText() {}
}
