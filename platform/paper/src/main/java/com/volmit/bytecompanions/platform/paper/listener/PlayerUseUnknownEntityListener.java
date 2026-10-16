package com.volmit.bytecompanions.platform.paper.listener;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import com.volmit.bytecompanions.features.decorations.StaticAnchor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PlayerUseUnknownEntityListener implements Listener {

    private final CompanionEntityCache companionEntityCache;
    private final CompanionEventBus companionEventBus;
    private final BooleanSupplier current;
    private final OwnerDispatch ownerDispatch;
    private final Consumer<String> rejected;
    private final AtomicBoolean rejectionReported = new AtomicBoolean();

    public PlayerUseUnknownEntityListener(
            final @NotNull Plugin plugin,
            final @NotNull CompanionEntityCache companionEntityCache,
            final @NotNull CompanionEventBus companionEventBus,
            final @NotNull BooleanSupplier current
    ) {
        this(companionEntityCache, companionEventBus, current,
                (anchor, action) -> dispatch(plugin, anchor, action), plugin.getLogger()::warning);
    }

    PlayerUseUnknownEntityListener(CompanionEntityCache companionEntityCache, CompanionEventBus companionEventBus,
                                   BooleanSupplier current, OwnerDispatch ownerDispatch, Consumer<String> rejected) {
        this.companionEntityCache = companionEntityCache;
        this.companionEventBus = companionEventBus;
        this.current = current;
        this.ownerDispatch = ownerDispatch;
        this.rejected = rejected;
    }

    @EventHandler
    public void onPlayerUseUnknownEntityEvent(final @NotNull PlayerUseUnknownEntityEvent entityEvent) {
        if (!this.current.getAsBoolean()) {
            return;
        }
        final Player player = entityEvent.getPlayer();
        final int entityId = entityEvent.getEntityId();
        final Companion companion = this.companionEntityCache.get(entityId);
        if (companion == null) {
            return;
        }
        CompanionPlayerInteractEvent.Actor actor = CompanionPlayerInteractEvent.Actor.capture(player);
        EquipmentSlot hand = entityEvent.getHand();
        boolean attack = entityEvent.isAttack();
        boolean scheduled = this.ownerDispatch.run(companion.anchor(), () -> {
            if (this.current.getAsBoolean() && this.companionEntityCache.get(entityId) == companion) {
                this.companionEventBus.callPlayerInteract(player, actor, companion, entityId, hand, attack);
            }
        });
        if (!scheduled && this.rejectionReported.compareAndSet(false, true)) {
            this.rejected.accept("Companion interaction could not reach its owning region");
        }
    }

    private static boolean dispatch(Plugin plugin, CompanionAnchor anchor, Runnable action) {
        if (anchor instanceof PlayerAnchor playerAnchor) {
            return FoliaScheduler.runEntity(plugin, playerAnchor.player(), action);
        }
        if (anchor instanceof StaticAnchor) {
            return FoliaScheduler.runRegion(plugin, new Location(anchor.world(), anchor.x(), anchor.y(), anchor.z()), action);
        }
        return false;
    }

    @FunctionalInterface
    interface OwnerDispatch {
        boolean run(CompanionAnchor anchor, Runnable action);
    }
}
