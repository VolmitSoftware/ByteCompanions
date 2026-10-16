package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class MovementConfiguration {

    @Setting("settle-delay-seconds")
    private double settleDelaySeconds = 0.4;

    @Setting("movement-epsilon-squared")
    private double movementEpsilonSquared = 0.0001;

    public double settleDelaySeconds() {
        return this.settleDelaySeconds;
    }

    public double movementEpsilonSquared() {
        return this.movementEpsilonSquared;
    }
}
