package com.volmit.bytecompanions.spi.companions;

import org.bukkit.entity.Player;

import java.util.UUID;

public record ViewerSnapshot(UUID sessionId, Player player, UUID worldId, double x, double y, double z) {
    public boolean within(UUID world, double anchorX, double anchorY, double anchorZ, double radius) {
        if (!this.worldId.equals(world)) {
            return false;
        }
        double dx = this.x - anchorX;
        double dy = this.y - anchorY;
        double dz = this.z - anchorZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
