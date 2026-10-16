package com.volmit.bytecompanions.api.companion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ConfigurationData {

    boolean has(final @NotNull String path);

    @Nullable Object get(final @NotNull String path);

    @Nullable ConfigurationData section(final @NotNull String path);

    @Nullable String getString(final @NotNull String path);

    default @NotNull String getStringOrEmpty(final @NotNull String path) {
        return this.getStringOr(path, "");
    }

    @NotNull String getStringOr(
            final @NotNull String path,
            final @NotNull String defaultValue
    );

    @Nullable List<String> getStringList(final @NotNull String path);

    int getIntOr(
            final @NotNull String path,
            final int defaultValue
    );

    double getDoubleOr(
            final @NotNull String path,
            final double defaultValue
    );

    float getFloatOr(
            final @NotNull String path,
            final float defaultValue
    );

    long getLongOr(
            final @NotNull String path,
            final long defaultValue
    );

    boolean getBooleanOr(
            final @NotNull String path,
            final boolean defaultValue
    );

}