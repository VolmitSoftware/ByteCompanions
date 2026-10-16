package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Map;

@ConfigSerializable
public final class ParticleIconsConfiguration {
    @Setting("items")
    private Map<String, ConfigurationNode> items = Map.of();

    public Map<String, ConfigurationNode> items() {
        return Map.copyOf(this.items);
    }
}
