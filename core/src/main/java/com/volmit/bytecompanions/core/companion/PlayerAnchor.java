package com.volmit.bytecompanions.core.companion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;

import java.util.Objects;

/**
 * A {@link CompanionAnchor} that delegates all spatial queries to a live Bukkit {@link Player}.
 *
 * <p>All values are read in real-time on every call — the anchor always reflects
 * the player's current position, rotation, and world.
 */
public abstract class PlayerAnchor implements CompanionAnchor {

    private final Player player;
    private final Location location = new Location(null, 0, 0, 0);

    protected PlayerAnchor(@NotNull Player player) {
        this.player = Objects.requireNonNull(player);
    }

    public final @NotNull Player player() {
        return this.player;
    }

    private Location currentLocation() {
        return this.player.getLocation(this.location);
    }

    @Override
    public double x() {
        return this.currentLocation().getX();
    }

    @Override
    public double y() {
        return this.currentLocation().getY();
    }

    @Override
    public double z() {
        return this.currentLocation().getZ();
    }

    @Override
    public float yaw() {
        return this.currentLocation().getYaw();
    }

    @Override
    public float pitch() {
        return this.currentLocation().getPitch();
    }

    @Override
    public abstract float bodyYaw();

    @Override
    public double eyeHeight() {
        return this.player.getEyeHeight();
    }

    @Override
    public double height() {
        return this.player.getHeight();
    }

    @Override
    public @NotNull World world() {
        return this.player.getWorld();
    }

}
