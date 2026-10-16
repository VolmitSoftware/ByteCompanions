package com.volmit.bytecompanions.platform.paper.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

public final class DistanceUtil {

    private DistanceUtil() {
        throw new NonInstantiableClassException();
    }

    public static boolean isWithinRadiusOfPlayer(
            final @NotNull Player player,
            final @NotNull Location location,
            final int radius
    ) {

        Location playerLocation = player.getLocation();
        final double deltaX = playerLocation.getX() - location.getX();
        final double deltaY = playerLocation.getY() - location.getY();
        final double deltaZ = playerLocation.getZ() - location.getZ();

        final double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        final double radiusSquared = radius * radius;

        return distanceSquared <= radiusSquared;
    }

}
