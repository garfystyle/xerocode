package net.minecraft.util;

import java.util.List;
import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSequence {
    FormattedCharSequence EMPTY = sink -> true;

    boolean accept(FormattedCharSink sink);

    static FormattedCharSequence codepoint(int codepoint, Style style) {
        return sink -> sink.accept(0, style, codepoint);
    }

    static FormattedCharSequence forward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterate(text, style, sink);
    }

    static FormattedCharSequence forward(String text, Style style, java.util.function.Function<Integer, Integer> modifier) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterate(text, style,
                (pos, st, cp) -> sink.accept(pos, st, modifier.apply(cp)));
    }

    static FormattedCharSequence backward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterateBackwards(text, style, sink);
    }

    static FormattedCharSequence composite() {
        return EMPTY;
    }

    static FormattedCharSequence composite(FormattedCharSequence part) {
        return part;
    }

    static FormattedCharSequence composite(FormattedCharSequence first, FormattedCharSequence second) {
        return sink -> first.accept(sink) && second.accept(sink);
    }

    static FormattedCharSequence composite(FormattedCharSequence... parts) {
        return composite(List.of(parts));
    }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        int size = parts.size();
        if (size == 0) return EMPTY;
        if (size == 1) return parts.get(0);
        if (size == 2) return composite(parts.get(0), parts.get(1));
        List<FormattedCharSequence> copy = List.copyOf(parts);
        return sink -> {
            for (FormattedCharSequence p : copy) if (!p.accept(sink)) return false;
            return true;
        };
    }
}
