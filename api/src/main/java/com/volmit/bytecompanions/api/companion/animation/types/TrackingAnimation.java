package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;

public interface TrackingAnimation extends Animation<Void> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.TRACKING;
    }
}
