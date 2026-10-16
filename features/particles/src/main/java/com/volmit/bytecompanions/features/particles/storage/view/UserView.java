package com.volmit.bytecompanions.features.particles.storage.view;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record UserView(
        @NotNull UUID userId,
        @NotNull String companionTypeId,
        @NotNull String effectId
) {

    public UserView {
        Objects.requireNonNull(userId, "'userId' must not be null");
        Objects.requireNonNull(companionTypeId, "'companionTypeId' must not be null");
        Objects.requireNonNull(effectId, "'effectId' must not be null");
    }

}
