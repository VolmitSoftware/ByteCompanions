package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;

public interface InteractionAnimation extends Animation<Void> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.FULL_BODY;
    }
}
