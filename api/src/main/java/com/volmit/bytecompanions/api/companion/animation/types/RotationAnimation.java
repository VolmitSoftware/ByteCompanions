package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.output.RotationOutput;

public interface RotationAnimation extends Animation<RotationOutput> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.ROTATION;
    }

}
