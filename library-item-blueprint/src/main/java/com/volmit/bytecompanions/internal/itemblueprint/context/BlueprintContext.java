package com.volmit.bytecompanions.internal.itemblueprint.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlueprintContext {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z0-9_-]+)}");
    private static final BlueprintContext EMPTY = new BlueprintContext(Map.of());

    private final @NotNull Map<String, String> placeholders;

    private BlueprintContext(final @NotNull Map<String, String> placeholders) {
        this.placeholders = placeholders;
    }

    public static @NotNull BlueprintContext empty() {
        return EMPTY;
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull String resolve(final @NotNull String raw) {
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
        final StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            final String replacement = this.placeholders.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static final class Builder {

        private final @NotNull Map<String, String> placeholders = new HashMap<>();

        private Builder() { }

        public @NotNull Builder placeholder(final @NotNull String key, final @NotNull String value) {
            this.placeholders.put(key, value);
            return this;
        }

        public @NotNull Builder placeholders(final @NotNull Map<String, String> values) {
            this.placeholders.putAll(values);
            return this;
        }

        @Contract(" -> new")
        public @NotNull BlueprintContext build() {
            return new BlueprintContext(new HashMap<>(this.placeholders));
        }

    }

}
