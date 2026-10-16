package com.volmit.bytecompanions.platform.paper.companion.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;

public interface Formation {

    @NotNull
    FormationTarget target(final @NotNull AnimationContext context);

}
