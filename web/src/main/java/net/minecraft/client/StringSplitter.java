package net.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

public final class StringSplitter {
    private final WidthProvider widthProvider;

    public StringSplitter(WidthProvider widthProvider) {
        this.widthProvider = widthProvider;
    }

    public float stringWidth(String str) {
        if (str == null) return 0;
        float[] sum = {0};
        StringDecomposer.iterateFormatted(str, Style.EMPTY, (pos, style, cp) -> {
            sum[0] += widthProvider.getWidth(cp, style);
            return true;
        });
        return sum[0];
    }

    public float stringWidth(FormattedText text) {
        float[] sum = {0};
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (pos, style, cp) -> {
            sum[0] += widthProvider.getWidth(cp, style);
            return true;
        });
        return sum[0];
    }

    public float stringWidth(FormattedCharSequence text) {
        float[] sum = {0};
        text.accept((pos, style, cp) -> {
            sum[0] += widthProvider.getWidth(cp, style);
            return true;
        });
        return sum[0];
    }

    public int plainIndexAtWidth(String str, int maxWidth, Style style) {
        WidthLimited sink = new WidthLimited(maxWidth);
        StringDecomposer.iterate(str, style, sink);
        return sink.position;
    }

    public String plainHeadByWidth(String str, int maxWidth, Style style) {
        return str.substring(0, plainIndexAtWidth(str, maxWidth, style));
    }

    public String plainTailByWidth(String str, int maxWidth, Style style) {
        float[] width = {0};
        int[] result = {str.length()};
        StringDecomposer.iterateBackwards(str, style, (pos, s, cp) -> {
            width[0] += widthProvider.getWidth(cp, s);
            if (width[0] > maxWidth) return false;
            result[0] = pos;
            return true;
        });
        return str.substring(result[0]);
    }

    public int formattedIndexByWidth(String str, int maxWidth, Style style) {
        WidthLimited sink = new WidthLimited(maxWidth);
        StringDecomposer.iterateFormatted(str, style, sink);
        return sink.position;
    }

    public String formattedHeadByWidth(String str, int maxWidth, Style style) {
        return str.substring(0, formattedIndexByWidth(str, maxWidth, style));
    }

    public FormattedText headByWidth(FormattedText text, int width, Style initialStyle) {
        WidthLimited sink = new WidthLimited(width);
        List<FormattedText> parts = new ArrayList<>();
        Optional<FormattedText> cut = text.visit((style, contents) -> {
            sink.position = 0;
            if (!StringDecomposer.iterateFormatted(contents, style, sink)) {
                String partial = contents.substring(0, sink.position);
                if (!partial.isEmpty()) parts.add(FormattedText.of(partial, style));
                return Optional.of(joined(parts));
            }
            if (!contents.isEmpty()) parts.add(FormattedText.of(contents, style));
            return Optional.empty();
        }, initialStyle);
        return cut.orElse(text);
    }

    private static FormattedText joined(List<FormattedText> parts) {
        if (parts.isEmpty()) return FormattedText.EMPTY;
        if (parts.size() == 1) return parts.get(0);
        return FormattedText.composite(new ArrayList<>(parts));
    }

    public int findLineBreak(String input, int max, Style initialStyle) {
        LineBreakFinder finder = new LineBreakFinder(max);
        StringDecomposer.iterateFormatted(input, initialStyle, finder);
        return finder.splitPosition();
    }

    public static int getWordPosition(String text, int dir, int from, boolean stripSpaces) {
        int result = from;
        boolean reverse = dir < 0;
        int abs = Math.abs(dir);
        for (int i = 0; i < abs; i++) {
            if (reverse) {
                while (stripSpaces && result > 0 && (text.charAt(result - 1) == ' ' || text.charAt(result - 1) == '\n')) result--;
                while (result > 0 && text.charAt(result - 1) != ' ' && text.charAt(result - 1) != '\n') result--;
            } else {
                int length = text.length();
                int space = text.indexOf(' ', result);
                int newline = text.indexOf('\n', result);
                if (space == -1 && newline == -1) result = -1;
                else if (space != -1 && newline != -1) result = Math.min(space, newline);
                else result = space != -1 ? space : newline;
                if (result == -1) {
                    result = length;
                } else {
                    while (stripSpaces && result < length && (text.charAt(result) == ' ' || text.charAt(result) == '\n')) result++;
                }
            }
        }
        return result;
    }

    public void splitLines(String input, int maxWidth, Style initialStyle, boolean includeAll, LinePosConsumer output) {
        int start = 0;
        int size = input.length();
        Style work = initialStyle;
        while (start < size) {
            LineBreakFinder finder = new LineBreakFinder(maxWidth);
            boolean end = StringDecomposer.iterateFormatted(input, start, work, initialStyle, finder);
            if (end) {
                output.accept(work, start, size);
                break;
            }
            int lineBreak = finder.splitPosition();
            char tail = input.charAt(lineBreak);
            int adjusted = tail != '\n' && tail != ' ' ? lineBreak : lineBreak + 1;
            output.accept(work, start, includeAll ? adjusted : lineBreak);
            start = adjusted;
            work = finder.splitStyle();
        }
    }

    public List<FormattedText> splitLines(String input, int maxWidth, Style initialStyle) {
        List<FormattedText> result = new ArrayList<>();
        splitLines(input, maxWidth, initialStyle, false,
                (style, start, end) -> result.add(FormattedText.of(input.substring(start, end), style)));
        return result;
    }

    public List<FormattedText> splitLines(FormattedText input, int maxWidth, Style initialStyle) {
        List<FormattedText> result = new ArrayList<>();
        splitLines(input, maxWidth, initialStyle, (text, wrapped) -> result.add(text));
        return result;
    }

    public void splitLines(FormattedText input, int maxWidth, Style initialStyle, BiConsumer<FormattedText, Boolean> output) {
        List<Part> list = new ArrayList<>();
        input.visit((style, contents) -> {
            if (!contents.isEmpty()) list.add(new Part(contents, style));
            return Optional.empty();
        }, initialStyle);
        Flat parts = new Flat(list);
        boolean restart = true;
        boolean forceNewLine = false;
        boolean wrapped = false;
        while (restart) {
            restart = false;
            LineBreakFinder finder = new LineBreakFinder(maxWidth);
            for (Part part : parts.parts) {
                boolean end = StringDecomposer.iterateFormatted(part.contents, 0, part.style, initialStyle, finder);
                if (!end) {
                    int lineBreak = finder.splitPosition();
                    Style breakStyle = finder.splitStyle();
                    char tail = parts.charAt(lineBreak);
                    boolean newLine = tail == '\n';
                    boolean skip = newLine || tail == ' ';
                    forceNewLine = newLine;
                    FormattedText line = parts.splitAt(lineBreak, skip ? 1 : 0, breakStyle);
                    output.accept(line, wrapped);
                    wrapped = !newLine;
                    restart = true;
                    break;
                }
                finder.offset += part.contents.length();
            }
        }
        FormattedText last = parts.remainder();
        if (last != null) output.accept(last, wrapped);
        else if (forceNewLine) output.accept(FormattedText.EMPTY, false);
    }

    private static final class Part implements FormattedText {
        final String contents;
        final Style style;

        Part(String contents, Style style) {
            this.contents = contents;
            this.style = style;
        }

        @Override
        public <T> Optional<T> visit(ContentConsumer<T> output) { return output.accept(contents); }

        @Override
        public <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) {
            return output.accept(style.applyTo(parentStyle), contents);
        }
    }

    private static final class Flat {
        final List<Part> parts;
        String flat;

        Flat(List<Part> parts) {
            this.parts = parts;
            StringBuilder sb = new StringBuilder();
            for (Part p : parts) sb.append(p.contents);
            flat = sb.toString();
        }

        char charAt(int position) { return flat.charAt(position); }

        FormattedText splitAt(int skipPosition, int skipSize, Style splitStyle) {
            List<FormattedText> result = new ArrayList<>();
            ListIterator<Part> it = parts.listIterator();
            int position = skipPosition;
            boolean inSkip = false;
            while (it.hasNext()) {
                Part element = it.next();
                String contents = element.contents;
                int size = contents.length();
                if (!inSkip) {
                    if (position > size) {
                        result.add(element);
                        it.remove();
                        position -= size;
                    } else {
                        String before = contents.substring(0, position);
                        if (!before.isEmpty()) result.add(FormattedText.of(before, element.style));
                        position += skipSize;
                        inSkip = true;
                    }
                }
                if (inSkip) {
                    if (position <= size) {
                        String after = contents.substring(position);
                        if (after.isEmpty()) it.remove();
                        else it.set(new Part(after, splitStyle));
                        break;
                    }
                    it.remove();
                    position -= size;
                }
            }
            flat = flat.substring(skipPosition + skipSize);
            return joined(result);
        }

        FormattedText remainder() {
            if (parts.isEmpty()) return null;
            List<FormattedText> result = new ArrayList<>(parts);
            parts.clear();
            return joined(result);
        }
    }

    private final class LineBreakFinder implements FormattedCharSink {
        private final float maxWidth;
        private int lineBreak = -1;
        private Style lineBreakStyle = Style.EMPTY;
        private boolean hadNonZeroWidth;
        private float width;
        private int lastSpace = -1;
        private Style lastSpaceStyle = Style.EMPTY;
        private int nextChar;
        int offset;

        LineBreakFinder(float maxWidth) {
            this.maxWidth = Math.max(maxWidth, 1.0f);
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            int at = position + offset;
            if (codepoint == '\n') return finish(at, style);
            if (codepoint == ' ') {
                lastSpace = at;
                lastSpaceStyle = style;
            }
            float w = widthProvider.getWidth(codepoint, style);
            width += w;
            if (!hadNonZeroWidth || !(width > maxWidth)) {
                hadNonZeroWidth |= w != 0.0f;
                nextChar = at + Character.charCount(codepoint);
                return true;
            }
            return lastSpace != -1 ? finish(lastSpace, lastSpaceStyle) : finish(at, style);
        }

        private boolean finish(int at, Style style) {
            lineBreak = at;
            lineBreakStyle = style;
            return false;
        }

        int splitPosition() { return lineBreak != -1 ? lineBreak : nextChar; }

        Style splitStyle() { return lineBreakStyle; }
    }

    private final class WidthLimited implements FormattedCharSink {
        private float maxWidth;
        int position;

        WidthLimited(float maxWidth) {
            this.maxWidth = maxWidth;
        }

        @Override
        public boolean accept(int pos, Style style, int codepoint) {
            maxWidth -= widthProvider.getWidth(codepoint, style);
            if (maxWidth >= 0.0f) {
                position = pos + Character.charCount(codepoint);
                return true;
            }
            return false;
        }
    }

    @FunctionalInterface
    public interface LinePosConsumer {
        void accept(Style style, int start, int end);
    }

    @FunctionalInterface
    public interface WidthProvider {
        float getWidth(int codepoint, Style style);
    }
}
