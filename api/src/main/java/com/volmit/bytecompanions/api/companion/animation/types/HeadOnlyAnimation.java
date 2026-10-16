package com.volmit.bytecompanions.api.companion.animation.types;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.output.HeadOnlyOutput;

public interface HeadOnlyAnimation extends Animation<HeadOnlyOutput> {

    @Override
    default @NotNull AnimationChannel channel() {
        return AnimationChannel.HEAD_ONLY;
    }

}
