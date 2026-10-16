package com.volmit.bytecompanions.platform.paper.component.adapter;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

import java.util.Locale;

public final class ComponentSerializerFactory {

    private ComponentSerializerFactory() {
        throw new NonInstantiableClassException();
    }

    public static @NotNull ComponentSerializerAdapter createAdapter(final @NotNull String typeName) {
        return switch (typeName.toLowerCase(Locale.ROOT)) {
            case "mini_message" -> new MiniMessageComponentSerializerAdapter();
            case "legacy_ampersand" -> new LegacyAmpersandComponentSerializerAdapter();
            default -> new PlainComponentSerializerAdapter();
        };
    }

}