package com.volmit.bytecompanions.platform.paper.util;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Color;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

public final class ColorUtil {

    private ColorUtil() {
        throw new NonInstantiableClassException();
    }

    public static @Nullable Color parseColor(final @Nullable String color) {
        if (color == null || color.isBlank()) {
            return null;
        }

        final String[] split = StringUtils.split(color, ",");
        if (split == null || split.length == 0) {
            return null;
        }

        try {
            final int r = Integer.parseInt(split[0].trim());
            final int g = Integer.parseInt(split[1].trim());
            final int b = Integer.parseInt(split[2].trim());
            return Color.fromRGB(r, g, b);
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }

    }

}
