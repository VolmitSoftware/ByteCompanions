package com.volmit.bytecompanions.platform.paper.configuration.companion;

import org.bukkit.inventory.EquipmentSlot;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;

import java.util.Collections;
import java.util.Map;

@ConfigSerializable
public final class OverlayConfig {

    @Setting("lock-head-yaw")
    private boolean lockHeadYaw = false;

    @Setting("equipment")
    private Map<EquipmentSlot, ItemStackProvider> equipment = Collections.emptyMap();

    public boolean lockHeadYaw() {
        return lockHeadYaw;
    }

    public Map<EquipmentSlot, ItemStackProvider> equipment() {
        return equipment;
    }
}