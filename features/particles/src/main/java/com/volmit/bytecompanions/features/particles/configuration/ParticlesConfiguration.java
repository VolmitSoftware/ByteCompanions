package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class ParticlesConfiguration {

    @Setting("settings")
    private SettingConfiguration settings = new SettingConfiguration();

    public SettingConfiguration settings() {
        return this.settings;
    }
}
