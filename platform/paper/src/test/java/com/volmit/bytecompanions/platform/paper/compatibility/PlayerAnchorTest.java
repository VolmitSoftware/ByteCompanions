package com.volmit.bytecompanions.platform.paper.compatibility;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerAnchorTest {
    @Test
    void followsLiveBukkitLocationWithoutReplacingIndependentBodyRotation() {
        Location current = new Location(null, 1, 2, 3, 95, -10);
        List<Location> destinations = new ArrayList<>();
        Player player = Player.class.cast(Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getLocation") && arguments != null && arguments.length == 1) {
                        Location destination = (Location) arguments[0];
                        destinations.add(destination);
                        destination.setWorld(current.getWorld());
                        destination.setX(current.getX());
                        destination.setY(current.getY());
                        destination.setZ(current.getZ());
                        destination.setYaw(current.getYaw());
                        destination.setPitch(current.getPitch());
                        return destination;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
        PlayerAnchor anchor = new PlayerAnchor(player) {
            @Override
            public float bodyYaw() {
                return -35;
            }
        };

        assertSame(player, anchor.player());
        assertEquals(1, anchor.x());
        assertEquals(2, anchor.y());
        assertEquals(3, anchor.z());
        assertEquals(95, anchor.yaw());
        assertEquals(-10, anchor.pitch());
        assertEquals(-35, anchor.bodyYaw());

        current.setX(11);
        current.setY(12);
        current.setZ(13);
        current.setYaw(170);
        current.setPitch(25);
        assertEquals(11, anchor.x());
        assertEquals(12, anchor.y());
        assertEquals(13, anchor.z());
        assertEquals(170, anchor.yaw());
        assertEquals(25, anchor.pitch());
        assertEquals(-35, anchor.bodyYaw());
        for (Location destination : destinations) {
            assertSame(destinations.getFirst(), destination);
        }
    }
}
