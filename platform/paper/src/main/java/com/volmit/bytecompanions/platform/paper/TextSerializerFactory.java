package com.volmit.bytecompanions.platform.paper;

import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializers;

import java.util.Locale;

public final class TextSerializerFactory {

    private TextSerializerFactory() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static TextSerializer create(final @Nullable String type) {
        if (type == null || type.isBlank()) {
            return TextSerializers.plain();
        }

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "mini_message" -> TextSerializers.miniMessage();
            case "legacy_ampersand" -> TextSerializers.legacyAmpersand();
            default -> TextSerializers.plain();
        };
    }

}
