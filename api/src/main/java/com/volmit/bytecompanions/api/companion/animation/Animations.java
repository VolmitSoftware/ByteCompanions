package com.volmit.bytecompanions.api.companion.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;

import java.util.Collection;
import java.util.Map;

public interface Animations {

    void add(
            final @NotNull String animationId,
            final @NotNull Animation<?> animation
    );

    boolean remove(final @NotNull AnimationChannel channel);

    boolean remove(final @NotNull String animationId);

    boolean remove(
            final @NotNull AnimationChannel channel,
            final @NotNull String animationId
    );

    boolean contains(final @NotNull String animationId);

    boolean contains(
            final @NotNull AnimationChannel channel,
            final @NotNull String animationId
    );

    @NotNull
    @UnmodifiableView
    Map<String, Animation<?>> getAll(final @NotNull AnimationChannel channel);

    @NotNull
    @UnmodifiableView
    Collection<? extends Animation<?>> getAnimations(final @NotNull AnimationChannel channel);

    boolean isEmpty(final @NotNull AnimationChannel channel);

    boolean isEmpty();

    int size();

    void clear();

}
