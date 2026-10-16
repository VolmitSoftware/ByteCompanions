package com.volmit.bytecompanions.api.companion.anchor;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public interface CompanionAnchor {

    double x();
    double y();
    double z();

    float yaw();
    float pitch();
    float bodyYaw();

    double eyeHeight();
    double height();

    @NotNull World world();

}
