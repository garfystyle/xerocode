package net.minecraft.network.chat;

import java.util.List;
import java.util.Optional;

public interface FormattedText {
    Optional<Object> STOP_ITERATION = Optional.of(Boolean.TRUE);

    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(ContentConsumer<T> output) { return Optional.empty(); }

        @Override
        public <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) { return Optional.empty(); }
    };

    <T> Optional<T> visit(ContentConsumer<T> output);

    <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle);

    static FormattedText of(String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> output) { return output.accept(text); }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) {
                return output.accept(parentStyle, text);
            }
        };
    }

    static FormattedText of(String text, Style style) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> output) { return output.accept(text); }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) {
                return output.accept(style.applyTo(parentStyle), text);
            }
        };
    }

    static FormattedText composite(FormattedText... parts) {
        return composite(List.of(parts));
    }

    static FormattedText composite(List<? extends FormattedText> parts) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> output) {
                for (FormattedText part : parts) {
                    Optional<T> r = part.visit(output);
                    if (r.isPresent()) return r;
                }
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> output, Style parentStyle) {
                for (FormattedText part : parts) {
                    Optional<T> r = part.visit(output, parentStyle);
                    if (r.isPresent()) return r;
                }
                return Optional.empty();
            }
        };
    }

    default String getString() {
        StringBuilder sb = new StringBuilder();
        visit(s -> {
            sb.append(s);
            return Optional.empty();
        });
        return sb.toString();
    }

    interface ContentConsumer<T> {
        Optional<T> accept(String contents);
    }

    interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String contents);
    }
}
