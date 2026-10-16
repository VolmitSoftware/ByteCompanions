package com.volmit.bytecompanions.features.buffs.configuration.buff;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import com.volmit.bytecompanions.features.buffs.configuration.feedback.FeedBack;
import com.volmit.bytecompanions.features.buffs.util.Operation;

@ConfigSerializable
public class Buff {

    @Setting("enabled")
    private boolean enabled = true;

    @Setting("priority")
    private Integer priority = null;

    @Setting("operation")
    private Operation operation = Operation.SUM;

    @Setting("value")
    private double value = 0.0;

    @Setting("cooldown")
    private long cooldown = 0L;

    @Setting("feedback")
    private FeedBack feedBack = new FeedBack();

    public boolean enabled() {
        return this.enabled;
    }

    public @Nullable Integer rawPriority() {
        return this.priority;
    }

    public Operation operation() {
        return this.operation;
    }

    public double value() {
        return this.value;
    }

    public long cooldown() {
        return this.cooldown;
    }

    public FeedBack feedBack() {
        return this.feedBack;
    }
}