package com.volmit.bytecompanions.platform.paper.companion.animations.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationFactory;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;

import java.util.*;

public final class DefaultAnimationRegistry implements AnimationRegistry {

    private final EnumMap<AnimationChannel, Map<String, AnimationFactory<?>>> factories = new EnumMap<>(AnimationChannel.class);

    @Override
    public void register(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id,
            final @NotNull AnimationFactory<?> factory
    ) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Animation ID must not be blank.");
        }

        final Map<String, AnimationFactory<?>> animationFactories = this.factories.computeIfAbsent(
                animationChannel, k -> new HashMap<>()
        );

        animationFactories.put(id, factory);
    }

    @Override
    public @NotNull Optional<AnimationFactory<?>> find(final @NotNull AnimationChannel animationChannel, final @NotNull String id) {
        final Map<String, AnimationFactory<?>> animationFactories = this.factories.get(animationChannel);
        if (animationFactories == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(animationFactories.get(id));
    }

    @Override
    public @NotNull AnimationFactory<?> get(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    ) {

        if (id.isBlank()) {
            throw new IllegalArgumentException("Animation ID must not be blank.");
        }

        final Map<String, AnimationFactory<?>> animationFactories = this.factories.getOrDefault(animationChannel, Collections.emptyMap());
        final AnimationFactory<?> factory = animationFactories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No animation registered with id: '" + id + "'");
        }

        return factory;
    }

    @Override
    public boolean isRegistered(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    ) {
        return this.factories.getOrDefault(animationChannel, Collections.emptyMap()).containsKey(id);
    }

    @Override
    public @NotNull @Unmodifiable Set<String> registeredIds(final @NotNull AnimationChannel animationChannel) {
        return Collections.unmodifiableSet(this.factories.getOrDefault(animationChannel, Collections.emptyMap()).keySet());
    }

    @Override
    public void unregister(
            final @NotNull AnimationChannel animationChannel,
            final @NotNull String id
    ) {
        this.factories.getOrDefault(animationChannel, Collections.emptyMap()).remove(id);
    }

}
