package com.volmit.bytecompanions.platform.paper.configuration.companion;

import org.bukkit.inventory.EquipmentSlot;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.CompanionModel;

import java.util.Collections;
import java.util.Map;

@ConfigSerializable
public final class CompanionTypeConfig {

    @Setting("exclusive")
    private boolean exclusive = false;

    @Setting("display")
    private Display display = new Display();

    @Setting("model")
    private CompanionModel model = null;

    @Setting("appearance")
    private CompanionAppearanceConfig appearance = new CompanionAppearanceConfig();

    @Setting("equipment")
    private Map<EquipmentSlot, ItemStackProvider> equipment = Collections.emptyMap();

    @Setting("overlays")
    private Map<String, OverlayConfig> overlays = Collections.emptyMap();

    @Setting("animations")
    private ConfigurationNode animations = null;

    public boolean exclusive() {
        return this.exclusive;
    }

    public Display display() {
        return this.display;
    }

    public CompanionModel miniatureModel() {
        return this.model;
    }

    public CompanionAppearanceConfig appearance() {
        return this.appearance;
    }

    public Map<EquipmentSlot, ItemStackProvider> equipment() {
        return this.equipment;
    }

    public Map<String, OverlayConfig> overlays() {
        return this.overlays;
    }

    public ConfigurationNode animations() {
        return this.animations;
    }

    @ConfigSerializable
    public static final class Display {

        @Setting("item")
        private ConfigurationNode item = null;

        public ConfigurationNode item() {
            return this.item;
        }
    }

}