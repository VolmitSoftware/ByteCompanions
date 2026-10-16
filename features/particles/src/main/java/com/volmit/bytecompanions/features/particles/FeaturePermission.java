package com.volmit.bytecompanions.features.particles;

import org.jetbrains.annotations.NotNull;

public final class FeaturePermission {

    private FeaturePermission() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static final String MENU_COMMAND = "bytecompanions.command.particles.menu";
    public static final String EQUIP_COMMAND = "bytecompanions.command.particles.equip";
    public static final String UNEQUIP_COMMAND = "bytecompanions.command.particles.unequip";

    public static @NotNull String resolveEffect(final @NotNull String effect) {
        return "bytecompanions.particle." + effect;
    }

}
