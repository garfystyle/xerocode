package net.minecraft.locale;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public final class Language {
    private static final Language INSTANCE = new Language();

    public static Language getInstance() { return INSTANCE; }

    public String getOrDefault(String key) { return key; }

    public String getOrDefault(String key, String fallback) { return fallback; }

    public boolean has(String key) { return false; }

    public boolean isDefaultRightToLeft() { return false; }

    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return sink -> text.visit((style, contents) -> StringDecomposer.iterateFormatted(contents, style, sink)
                ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isEmpty();
    }

    public List<FormattedCharSequence> getVisualOrder(List<FormattedText> lines) {
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (FormattedText t : lines) out.add(getVisualOrder(t));
        return out;
    }
}
