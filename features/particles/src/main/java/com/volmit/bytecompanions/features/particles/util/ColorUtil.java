package com.volmit.bytecompanions.features.particles.util;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public final class ColorUtil {

    private ColorUtil() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static final Vector3f RAINBOW_RED = new Vector3f(1.0f, 0.0f, 0.0f); // Red
    public static final Vector3f RAINBOW_ORANGE = new Vector3f(1.0f, 0.647f, 0.0f); // Orange
    public static final Vector3f RAINBOW_YELLOW = new Vector3f(1.0f, 1.0f, 0.0f); // Yellow
    public static final Vector3f RAINBOW_GREEN = new Vector3f(0.0f, 1.0f, 0.0f); // Green
    public static final Vector3f RAINBOW_BLUE = new Vector3f(0.0f, 0.0f, 1.0f); // Blue
    public static final Vector3f RAINBOW_INDIGO = new Vector3f(0.294f, 0.0f, 0.510f); // Indigo (from RGB(75, 0, 130))
    public static final Vector3f RAINBOW_VIOLET = new Vector3f(0.580f, 0.0f, 0.827f); // Violet (from RGB(148, 0, 211))

    public static final Vector3f GRAY = new Vector3f(0.278f, 0.310f, 0.321f); // Gray
    public static final Vector3f WHITE = new Vector3f(0.976f, 1.000f, 0.996f); // White
    public static final Vector3f BLACK = new Vector3f(0.114f, 0.114f, 0.129f); // Black
    public static final Vector3f LIGHT_BLUE = new Vector3f(0.227f, 0.702f, 0.855f); // Light Blue
    public static final Vector3f CYAN = new Vector3f(0.086f, 0.612f, 0.612f); // Cyan
    public static final Vector3f BLUE = new Vector3f(0.235f, 0.267f, 0.667f); // Blue
    public static final Vector3f PURPLE = new Vector3f(0.537f, 0.196f, 0.722f); // Purple

    public static final Vector3f PINK = new Vector3f(0.953f, 0.545f, 0.667f); // Pink
    public static final Vector3f MAGENTA = new Vector3f(0.780f, 0.306f, 0.741f); // Magenta
    public static final Vector3f LIME = new Vector3f(0.502f, 0.780f, 0.122f); // Lime
    public static final Vector3f GREEN = new Vector3f(0.369f, 0.486f, 0.086f); // Green
    public static final Vector3f YELLOW = new Vector3f(0.996f, 0.847f, 0.239f); // Yellow
    public static final Vector3f ORANGE = new Vector3f(0.976f, 0.502f, 0.114f); // Orange
    public static final Vector3f RED = new Vector3f(0.690f, 0.180f, 0.149f); // Red

    public static final Vector3f BROWN = new Vector3f(0.514f, 0.329f, 0.196f); // Brown

    public static int toRgb(final @NotNull Vector3f color) {
        final int red = Math.round(color.x() * 255.0f);
        final int green = Math.round(color.y() * 255.0f);
        final int blue = Math.round(color.z() * 255.0f);

        return (red << 16) | (green << 8) | blue;
    }

    public static Vector3f getByRainbowOrder(final int id) {
        return switch (id) {
            case 0 -> RAINBOW_RED;
            case 1 -> RAINBOW_ORANGE;
            case 2 -> RAINBOW_YELLOW;
            case 3 -> RAINBOW_GREEN;
            case 4 -> RAINBOW_BLUE;
            case 5 -> RAINBOW_VIOLET;
            case 6 -> RAINBOW_INDIGO;
            default -> BROWN;
        };
    }
}