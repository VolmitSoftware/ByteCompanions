package com.volmit.bytecompanions.api.companion.behavior;

import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import org.jetbrains.annotations.Nullable;

public interface CompanionBehavior {
    CompanionActionResult act(CompanionAction action);

    CompanionMode mode();

    void policy(CompanionBehaviorPolicy policy);

    void formation(@Nullable MovementAnimation animation);

    void resetPose();

    void reset();
}
