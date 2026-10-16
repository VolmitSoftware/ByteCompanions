package com.volmit.bytecompanions.core.companion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.Animations;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class AnimationsSet implements Animations {

    private final EnumMap<AnimationChannel, Map<String, Animation<?>>> animations = new EnumMap<>(AnimationChannel.class);

    /** Lazily set when the first animation context becomes available. */
    private @Nullable AnimationContext lastContext;

    /**
     * Updates the stored context. Called by the animator before ticking.
     * This allows lifecycle callbacks to receive a valid context.
     */
    public void updateContext(final @NotNull AnimationContext context) {
        boolean firstContext = this.lastContext == null;
        this.lastContext = context;
        if (firstContext) {
            for (Map<String, Animation<?>> channel : this.animations.values()) {
                for (Animation<?> animation : channel.values()) {
                    animation.onStart(context);
                }
            }
        }
    }

    @Override
    public void add(final @NotNull String animationId, final @NotNull Animation<?> animation) {
        final Map<String, Animation<?>> animationMap = this.animations.computeIfAbsent(
                animation.channel(),
                channel -> new HashMap<>()
        );

        if (animationMap.containsKey(animationId)) {
            throw new IllegalStateException("Duplicated animation id: " + animationId);
        }

        if (animation.channel().isSingleAnimation()) {
            if (this.lastContext != null) {
                for (final Animation<?> previous : animationMap.values()) {
                    previous.onStop(this.lastContext);
                }
            }

            animationMap.clear();
        }

        animationMap.put(animationId, animation);

        if (this.lastContext != null) {
            animation.onStart(this.lastContext);
        }
    }

    @Override
    public boolean remove(final @NotNull AnimationChannel channel) {
        final Map<String, Animation<?>> animationMap = this.animations.get(channel);
        if (animationMap == null) {
            return false;
        }
        final boolean isEmpty = animationMap.isEmpty();
        for (final Map.Entry<String, Animation<?>> entry : animationMap.entrySet()) {
            if (this.lastContext != null) {
                entry.getValue().onStop(this.lastContext);
            }
        }

        animationMap.clear();
        return !isEmpty;
    }

    @Override
    public boolean remove(final @NotNull String animationId) {
        for (final Map<String, Animation<?>> animationEntry : this.animations.values()) {
            final Animation<?> animation = animationEntry.remove(animationId);
            final boolean removed = animation != null;

            if (removed) {
                if (this.lastContext != null) {
                    animation.onStop(this.lastContext);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean remove(
            final @NotNull AnimationChannel channel,
            final @NotNull String animationId
    ) {

        final Map<String, Animation<?>> animationMap = this.animations.get(channel);
        if (animationMap == null) {
            return false;
        }

        final Animation<?> animation = animationMap.remove(animationId);

        if (animation != null) {
            if (this.lastContext != null) {
                animation.onStop(this.lastContext);
            }
        }

        return animation != null;
    }

    @Override
    public boolean contains(final @NotNull String animationId) {
        for (final Map<String, Animation<?>> animationEntry : this.animations.values()) {
            if (animationEntry.containsKey(animationId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean contains(final @NotNull AnimationChannel channel, final @NotNull String animationId) {
        return this.animations.getOrDefault(channel, Collections.emptyMap()).containsKey(animationId);
    }

    @Override
    public @NotNull @UnmodifiableView Map<String, Animation<?>> getAll(final @NotNull AnimationChannel channel) {
        return Collections.unmodifiableMap(this.animations.getOrDefault(channel, Collections.emptyMap()));
    }

    public Map<String, Animation<?>> getFastMapLookup(final @NotNull AnimationChannel channel) {
        return this.animations.getOrDefault(channel, Collections.emptyMap());
    }

    public @NotNull Collection<Animation<?>> getFastCollectionLookup(final @NotNull AnimationChannel channel) {
        return this.animations.getOrDefault(channel, Collections.emptyMap()).values();
    }

    @Override
    public @NotNull @UnmodifiableView Collection<? extends Animation<?>> getAnimations(final @NotNull AnimationChannel channel) {
        final Collection<Animation<?>> animations1 = this.animations.getOrDefault(channel, Collections.emptyMap()).values();
        return Collections.unmodifiableCollection(animations1);
    }

    @Override
    public boolean isEmpty(final @NotNull AnimationChannel channel) {
        return this.animations.getOrDefault(channel, Collections.emptyMap()).isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return this.animations.values().stream().allMatch(Map::isEmpty);
    }

    @Override
    public int size() {
        return this.animations.values()
                .stream()
                .mapToInt(Map::size)
                .sum();
    }

    @Override
    public void clear() {
        for (final Map<String, Animation<?>> animationEntry : this.animations.values()) {
            for (final Animation<?> animation : animationEntry.values()) {
                if (this.lastContext != null) {
                    animation.onStop(this.lastContext);
                }
            }
        }

        this.animations.clear();
        this.lastContext = null;
    }

}
