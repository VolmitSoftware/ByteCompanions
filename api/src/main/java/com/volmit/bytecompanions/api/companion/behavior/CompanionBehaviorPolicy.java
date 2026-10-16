package com.volmit.bytecompanions.api.companion.behavior;

import java.time.Duration;
import java.util.Objects;

public record CompanionBehaviorPolicy(boolean enabled, boolean idleReactions, Duration actionCooldown,
                                      Duration emoteDuration, Duration idleInterval, double maximumHoldDistance) {
    public CompanionBehaviorPolicy {
        Objects.requireNonNull(actionCooldown, "actionCooldown");
        Objects.requireNonNull(emoteDuration, "emoteDuration");
        Objects.requireNonNull(idleInterval, "idleInterval");
        if (actionCooldown.isNegative() || actionCooldown.compareTo(Duration.ofMinutes(1)) > 0
                || emoteDuration.compareTo(Duration.ofMillis(100)) < 0 || emoteDuration.compareTo(Duration.ofSeconds(10)) > 0
                || idleInterval.compareTo(Duration.ofSeconds(5)) < 0 || idleInterval.compareTo(Duration.ofMinutes(10)) > 0
                || !Double.isFinite(maximumHoldDistance) || maximumHoldDistance < 2 || maximumHoldDistance > 64) {
            throw new IllegalArgumentException("Invalid companion behavior policy");
        }
    }

    public static CompanionBehaviorPolicy disabled() {
        return new CompanionBehaviorPolicy(false, false, Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofSeconds(30), 16);
    }
}
