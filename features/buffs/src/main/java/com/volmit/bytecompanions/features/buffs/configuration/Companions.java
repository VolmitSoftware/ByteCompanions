package com.volmit.bytecompanions.features.buffs.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class Companions {

    @Setting("default-priority")
    private int defaultPriority = 0;

    @Setting("vanilla")
    private Vanilla vanilla = new Vanilla();

    public int defaultPriority() {
        return this.defaultPriority;
    }

    public Vanilla vanilla() {
        return this.vanilla;
    }

}
