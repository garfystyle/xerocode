package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;

public final class MutableComponent implements Component {
    private final String contents;
    private final List<Component> siblings = new ArrayList<>();
    private Style style = Style.EMPTY;

    private MutableComponent(String contents) {
        this.contents = contents;
    }

    public static MutableComponent create(String contents) {
        return new MutableComponent(contents);
    }

    @Override
    public String contents() { return contents; }

    @Override
    public List<Component> getSiblings() { return siblings; }

    @Override
    public Style getStyle() { return style; }

    public MutableComponent setStyle(Style style) {
        this.style = style;
        return this;
    }

    public MutableComponent append(String text) {
        return append(Component.literal(text));
    }

    public MutableComponent append(Component component) {
        siblings.add(component);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> updater) {
        setStyle(updater.apply(style));
        return this;
    }

    public MutableComponent withStyle(Style patch) {
        setStyle(patch.applyTo(style));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formats) {
        setStyle(style.applyFormats(formats));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting format) {
        setStyle(style.applyFormat(format));
        return this;
    }

    public MutableComponent withColor(int color) {
        setStyle(style.withColor(color));
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MutableComponent m && contents.equals(m.contents)
                && style.equals(m.style) && siblings.equals(m.siblings));
    }

    @Override
    public int hashCode() { return Objects.hash(contents, style, siblings); }

    @Override
    public String toString() { return getString(); }
}
