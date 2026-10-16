package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;

import java.util.Objects;
import java.util.UUID;

public final class CompanionPlayerInteractEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Actor actor;
    private final Companion companion;
    private final int entityId;

    private final EquipmentSlot hand;
    private final boolean attack;

    public CompanionPlayerInteractEvent(
            final @NotNull Player player,
            final @NotNull Actor actor,
            final @NotNull Companion companion,
            final int entityId,
            final @NotNull EquipmentSlot hand,
            final boolean attack
    ) {
        this.player = player;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.companion = companion;
        this.entityId = entityId;
        this.hand = hand;
        this.attack = attack;
    }

    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull Actor actor() {
        return this.actor;
    }

    public @NotNull Companion companion() {
        return this.companion;
    }

    public int entityId() {
        return this.entityId;
    }

    public EquipmentSlot hand() {
        return this.hand;
    }

    public boolean attack() {
        return this.attack;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public record Actor(UUID playerId, String playerName, UUID worldId, double x, double y, double z) {
        public Actor {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(worldId, "worldId");
        }

        public static Actor capture(Player player) {
            Location location = player.getLocation();
            return new Actor(player.getUniqueId(), player.getName(), location.getWorld().getUID(),
                    location.getX(), location.getY(), location.getZ());
        }
    }
}
