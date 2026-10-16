package com.volmit.bytecompanions.api.access;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Holds the global singleton instance of {@link ByteCompanions}.
 * Managed internally by the plugin — external code should never call set/reset.
 */
public final class ByteCompanionsProvider {

    private static volatile ByteCompanions instance;

    private ByteCompanionsProvider() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull ByteCompanions getInstance() {
        return Objects.requireNonNull(instance, "ByteCompanions API is not available yet. Is the plugin enabled?");
    }

    public static void setInstance(final @NotNull ByteCompanions api) {
        Objects.requireNonNull(api, "API instance must not be null.");
        if (instance != null) {
            throw new IllegalStateException("ByteCompanionsAPIProvider instance is already set.");
        }

        instance = api;
    }

    public static boolean hasInstance() {
        return instance != null;
    }

    public static void resetInstance() {
        instance = null;
    }

}
