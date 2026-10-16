package com.volmit.bytecompanions.features.buffs.configuration.feedback;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class FeedBack {

    @Setting("action-bar")
    private ActionBar actionBar = new ActionBar();

    @Setting("sound")
    private Sound sound = new Sound();

    public ActionBar actionBar() {
        return this.actionBar;
    }

    public Sound sound() {
        return this.sound;
    }
}
