package com.volmit.bytecompanions.features.buffs.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Collections;
import java.util.Map;

@ConfigSerializable
public final class Configuration {

    @Setting("companions")
    private Map<String, Companions> companions = Collections.emptyMap();

    public Map<String, Companions> companions() {
        return this.companions;
    }
}
