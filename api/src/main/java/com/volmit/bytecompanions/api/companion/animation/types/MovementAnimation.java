package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;

public interface MovementAnimation extends Animation<MovementOutput> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.MOVEMENT;
    }

}
