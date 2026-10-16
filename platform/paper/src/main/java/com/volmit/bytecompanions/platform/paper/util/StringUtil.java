package com.volmit.bytecompanions.platform.paper.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

public final class StringUtil {

    private StringUtil() {
        throw new NonInstantiableClassException();
    }

    public static @NotNull String @NotNull [] split(final @Nullable String input, final char splitChar) {
        if (input == null || input.isBlank()) {
            return new String[0];
        }

        final int indexChar = input.indexOf(splitChar);
        if (indexChar == -1) {
            return new String[]{input};
        }

        final String[] splitArray = new String[2];
        splitArray[0] = input.substring(0, indexChar);
        splitArray[1] = input.substring(indexChar + 1);

        return splitArray;
    }

    /**
     * Replaces multiple placeholders in a string with their corresponding values.
     * <p>
     * The arguments must come in pairs: {@code placeholder, value}.
     * For example:
     * <pre>{@code
     * StringUtil.replace("Hello {name}, your rank is {rank}",
     *                    "{name}", "Steve",
     *                    "{rank}", "Admin");
     * }</pre>
     *
     * @param input         the base input string
     * @param replacements  placeholder-value pairs (must be even length)
     * @return the string with all replacements applied
     * @throws IllegalArgumentException if an odd number of replacement arguments is provided
     */
    public static @NotNull String replace(
            final @Nullable String input,
            final @NotNull String @NotNull ... replacements
    ) {
        if (input == null || input.isBlank()) {
            return "";
        }

        if (replacements.length == 0) {
            return input;
        }

        if ((replacements.length & 1) != 0) {
            throw new IllegalArgumentException("Replacements must be provided in pairs: placeholder, value. " + replacements.length);
        }

        String result = input;
        for (int i = 0; i < replacements.length; i += 2) {
            final String placeholder = replacements[i];
            final String value = replacements[i + 1];

            result = replaceSingle(result, placeholder, value);
        }

        return result;
    }

    /**
     * Replaces all occurrences of a specific placeholder in the given string.
     * <p>
     * This method avoids regex and uses a manual search for maximum performance.
     *
     * @param input        the input text
     * @param placeholder  the substring to replace
     * @param replacement  the replacement text
     * @return the resulting string with replacements applied
     */
    public static @NotNull String replaceSingle(
            final @NotNull String input,
            final @NotNull String placeholder,
            final @NotNull String replacement
    ) {
        final int placeholderLength = placeholder.length();
        if (placeholderLength == 0 || input.isEmpty()) {
            return input;
        }

        final StringBuilder stringBuilder = new StringBuilder(input.length());
        int start = 0;
        int index;

        while ((index = input.indexOf(placeholder, start)) != -1) {
            stringBuilder
                    .append(input, start, index)
                    .append(replacement);

            start = index + placeholderLength;
        }

        // Append remaining part
        if (start < input.length()) {
            stringBuilder.append(input, start, input.length());
        }

        return stringBuilder.toString();
    }

}
