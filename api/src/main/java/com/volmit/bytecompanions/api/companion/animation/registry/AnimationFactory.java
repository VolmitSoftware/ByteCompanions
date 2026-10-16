package com.volmit.bytecompanions.api.companion.animation.registry;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;

@FunctionalInterface
public interface AnimationFactory<O extends Animation<?>> {

    @NotNull O create(final @NotNull AnimationData data);

}
