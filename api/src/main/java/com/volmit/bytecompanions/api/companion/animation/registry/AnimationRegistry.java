package com.volmit.bytecompanions.api.companion.animation.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;

import java.util.Optional;
import java.util.Set;

public interface AnimationRegistry {

    void register(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id,
            final @NotNull AnimationFactory<?> factory
    );

    @NotNull Optional<AnimationFactory<?>> find(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    );

    @NotNull AnimationFactory<?> get(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    );

    boolean isRegistered(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    );

    @NotNull @Unmodifiable Set<String> registeredIds(final @NotNull AnimationChannel animationChannel);

    void unregister(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    );

}
