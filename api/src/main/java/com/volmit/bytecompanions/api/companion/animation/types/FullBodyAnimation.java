package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.output.FullBodyOutput;

public interface FullBodyAnimation extends Animation<FullBodyOutput> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.FULL_BODY;
    }

}
