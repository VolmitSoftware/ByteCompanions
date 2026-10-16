package com.volmit.bytecompanions.features.buffs.configuration.feedback;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NonNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class Sound {

    @Setting("enabled")
    private boolean enabled = false;

    @Setting("key")
    private String key = "";

    public net.kyori.adventure.sound.@NonNull Sound adventureSound() {
        return net.kyori.adventure.sound.Sound.sound(
                Key.key(this.key),
                net.kyori.adventure.sound.Sound.Source.MASTER,
                1f,
                1f
        );
    }

    public boolean enabled() {
        return this.enabled;
    }

    public String key() {
        return this.key;
    }
}
