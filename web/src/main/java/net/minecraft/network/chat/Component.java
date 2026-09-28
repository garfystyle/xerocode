package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

public interface Component extends FormattedText {
    Style getStyle();

    String contents();

    List<Component> getSiblings();

    @Override
    default <T> Optional<T> visit(ContentConsumer<T> output) {
        Optional<T> r = output.accept(contents());
        if (r.isPresent()) return r;
        for (Component sibling : getSiblings()) {
            r = sibling.visit(output);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    default <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) {
        Style style = getStyle().applyTo(parentStyle);
        Optional<T> r = output.accept(style, contents());
        if (r.isPresent()) return r;
        for (Component sibling : getSiblings()) {
            r = sibling.visit(output, style);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    default String getString(int limit) {
        StringBuilder sb = new StringBuilder();
        visit(s -> {
            int left = limit - sb.length();
            if (left <= 0) return STOP_ITERATION;
            sb.append(s.length() <= left ? s : s.substring(0, left));
            return Optional.empty();
        });
        return sb.toString();
    }

    default MutableComponent plainCopy() {
        return MutableComponent.create(contents());
    }

    default MutableComponent copy() {
        MutableComponent c = MutableComponent.create(contents());
        c.setStyle(getStyle());
        for (Component s : getSiblings()) c.append(s);
        return c;
    }

    default FormattedCharSequence getVisualOrderText() {
        return Language.getInstance().getVisualOrder(this);
    }

    default List<Component> toFlatList() {
        List<Component> out = new ArrayList<>();
        visit((style, str) -> {
            if (!str.isEmpty()) out.add(literal(str).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    static Component nullToEmpty(String text) {
        return text != null ? literal(text) : CommonComponents.EMPTY;
    }

    static MutableComponent literal(String text) {
        return MutableComponent.create(text == null ? "" : text);
    }

    static MutableComponent translatable(String key) {
        return MutableComponent.create(key);
    }

    static MutableComponent translatable(String key, Object... args) {
        return MutableComponent.create(key);
    }

    static MutableComponent empty() {
        return MutableComponent.create("");
    }
}
