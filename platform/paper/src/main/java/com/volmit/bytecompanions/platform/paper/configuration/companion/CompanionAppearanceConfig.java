package com.volmit.bytecompanions.platform.paper.configuration.companion;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class CompanionAppearanceConfig {

    @Setting("small")
    private boolean small = true;

    @Setting("visible")
    private boolean visible = true;

    @Setting("show-arms")
    private boolean showArms = true;

    @Setting("show-base-plate")
    private boolean showBasePlate = true;

    @Setting("marker")
    private boolean marker = false;

    @Setting("custom-name")
    private String customName = "Example Custom Name";

    @Setting("custom-name-visible")
    private boolean customNameVisible = false;

    @Setting("scale")
    private double scale = -1.0;

    public boolean small() {
        return this.small;
    }

    public boolean visible() {
        return this.visible;
    }

    public boolean showArms() {
        return this.showArms;
    }

    public boolean showBasePlate() {
        return this.showBasePlate;
    }

    public boolean marker() {
        return this.marker;
    }

    public String customName() {
        return this.customName;
    }

    public boolean customNameVisible() {
        return this.customNameVisible;
    }

    public double scale() {
        return this.scale;
    }

}
