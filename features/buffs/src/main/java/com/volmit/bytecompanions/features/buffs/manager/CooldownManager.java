package com.volmit.bytecompanions.features.buffs.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.EnumMap;

public final class CooldownManager {

    private final Int2ObjectOpenHashMap<EnumMap<BuffKey, Long>> cooldowns = new Int2ObjectOpenHashMap<>();

    public boolean tryUse(final @NotNull Player player, final @NotNull BuffKey buffKey, final long cooldownTicks) {
        return this.tryUse(player.getEntityId(), buffKey, cooldownTicks);
    }

    public synchronized boolean tryUse(
            final int entityId,
            final @NotNull BuffKey buffKey,
            final long cooldownTicks
    ) {
        if (cooldownTicks <= 0) {
            return true;
        }

        EnumMap<BuffKey, Long> playerCooldowns = this.cooldowns.get(entityId);
        if (playerCooldowns == null) {
            playerCooldowns = new EnumMap<>(BuffKey.class);
            this.cooldowns.put(entityId, playerCooldowns);
        }

        final long cooldownMillis = cooldownTicks * 50L;
        final long now = System.currentTimeMillis();
        final long last = playerCooldowns.getOrDefault(buffKey, 0L);

        if (now - last < cooldownMillis) {
            return false;
        }

        playerCooldowns.put(buffKey, now);
        return true;
    }

    public synchronized void remove(final int entityId) {
        this.cooldowns.remove(entityId);
    }

    public synchronized long remainingMillis(int entityId, BuffKey buffKey, long cooldownTicks) {
        EnumMap<BuffKey, Long> playerCooldowns = this.cooldowns.get(entityId);
        if (cooldownTicks <= 0 || playerCooldowns == null || !playerCooldowns.containsKey(buffKey)) {
            return 0;
        }
        return Math.max(0, cooldownTicks * 50L - (System.currentTimeMillis() - playerCooldowns.get(buffKey)));
    }
}
