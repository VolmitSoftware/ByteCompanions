package com.volmit.bytecompanions.internal.itemblueprint.text;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.text.builtin.LegacyAmpersandTextSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.text.builtin.MiniMessageTextSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.text.builtin.PlainTextSerializer;

public final class TextSerializers {

    private static final @NotNull TextSerializer MINI_MESSAGE = new MiniMessageTextSerializer();
    private static final @NotNull TextSerializer LEGACY_AMPERSAND = new LegacyAmpersandTextSerializer();
    private static final @NotNull TextSerializer PLAIN = new PlainTextSerializer();

    private TextSerializers() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull TextSerializer miniMessage() {
        return MINI_MESSAGE;
    }

    public static @NotNull TextSerializer legacyAmpersand() {
        return LEGACY_AMPERSAND;
    }

    public static @NotNull TextSerializer plain() {
        return PLAIN;
    }
}
