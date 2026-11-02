package com.volmit.bytecompanions.api.access;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Holds the global singleton instance of {@link ByteCompanions}.
 * Only the holder of the {@link Registration} returned by {@link #register(ByteCompanions)} can remove it,
 * so another plugin cannot reset the provider.
 */
public final class ByteCompanionsProvider {

    private static volatile ByteCompanions instance;
    private static Registration owner;

    private ByteCompanionsProvider() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull ByteCompanions getInstance() {
        return Objects.requireNonNull(instance, "ByteCompanions API is not available yet. Is the plugin enabled?");
    }

    public static synchronized @NotNull Registration register(final @NotNull ByteCompanions api) {
        Objects.requireNonNull(api, "API instance must not be null.");
        if (instance != null) {
            throw new IllegalStateException("ByteCompanionsAPIProvider instance is already set.");
        }
        owner = new Registration();
        instance = api;
        return owner;
    }

    public static boolean hasInstance() {
        return instance != null;
    }

    public static final class Registration {

        private Registration() {
        }

        public void unregister() {
            synchronized (ByteCompanionsProvider.class) {
                if (owner == this) {
                    owner = null;
                    instance = null;
                }
            }
        }
    }

}
