package com.volmit.bytecompanions.api.companion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public interface CompanionTypeRegistry {

    @Nullable CompanionType getOrNull(final @NotNull String id);

    @NotNull Optional<CompanionType> get(final @NotNull String id);

    void register(
            final @NotNull String id,
            final @NotNull CompanionType companionType
    );

    void clear();

    @NotNull Collection<CompanionType> all();

}
