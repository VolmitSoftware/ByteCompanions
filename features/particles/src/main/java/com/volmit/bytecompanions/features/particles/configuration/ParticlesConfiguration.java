package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class ParticlesConfiguration {

    @Setting("storage")
    private StorageConfiguration storage = new StorageConfiguration();

    @Setting("settings")
    private SettingConfiguration settings = new SettingConfiguration();

    public SettingConfiguration settings() {
        return this.settings;
    }

    public StorageConfiguration storage() {
        return this.storage;
    }
}
