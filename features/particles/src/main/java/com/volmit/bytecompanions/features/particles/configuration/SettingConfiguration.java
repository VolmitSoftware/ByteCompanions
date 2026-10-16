package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class SettingConfiguration {

    @Setting("serializer")
    private String serializer = "";

    @Setting("movement")
    private MovementConfiguration movement = new MovementConfiguration();

    public String serializer() {
        return this.serializer;
    }

    public MovementConfiguration movement() {
        return this.movement;
    }
}
