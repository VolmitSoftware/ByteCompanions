package com.volmit.bytecompanions.features.buffs.configuration.feedback;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class ActionBar {

    @Setting("enabled")
    private boolean enabled = false;

    @Setting("message")
    private String message = "";

    public boolean enabled() {
        return this.enabled;
    }

    public String message() {
        return this.message;
    }
}
