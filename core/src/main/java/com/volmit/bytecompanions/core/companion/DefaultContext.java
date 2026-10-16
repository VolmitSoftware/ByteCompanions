package com.volmit.bytecompanions.core.companion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;

import java.util.Collection;

/**
 * Default immutable record implementation of {@link AnimationContext}.
 *
 * <p>Created every tick by the NMS entity and passed into {@link com.volmit.bytecompanions.core.MiniatureAnimator}.
 * The {@link CompanionAnchor} is the only way animations access the "owner" data —
 * they never hold a direct reference to a {@link org.bukkit.entity.Player}.
 */
public record DefaultContext(
        @NotNull CompanionAnchor anchor,
        @NotNull Collection<Player> viewers,
        double previousAnchorX,
        double previousAnchorY,
        double previousAnchorZ,
        double currentX,
        double currentY,
        double currentZ,
        double previousX,
        double previousY,
        double previousZ,
        float bodyYaw,
        float headYaw,
        float headPitch,
        double deltaTime,
        double elapsedTime,
        int formatIndex,
        int formatCount
) implements AnimationContext {
}
