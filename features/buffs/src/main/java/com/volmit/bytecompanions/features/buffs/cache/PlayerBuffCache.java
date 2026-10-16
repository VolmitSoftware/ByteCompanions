package com.volmit.bytecompanions.features.buffs.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class PlayerBuffCache {

    private final Int2ObjectOpenHashMap<EnumMap<BuffKey, ResolvedBuff>> activeBuffs = new Int2ObjectOpenHashMap<>();

    public synchronized void refresh(final @NotNull Player player, final @NotNull Map<BuffKey, ResolvedBuff> buffs) {
        this.activeBuffs.put(player.getEntityId(), new EnumMap<>(buffs));
    }

    public synchronized @NotNull Optional<ResolvedBuff> get(final @NotNull Player player, final @NotNull BuffKey buffType) {
        final EnumMap<BuffKey, ResolvedBuff> active = this.activeBuffs.get(player.getEntityId());
        if (active == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(active.get(buffType));
    }

    public synchronized void clear(final @NotNull Player player) {
        this.activeBuffs.remove(player.getEntityId());
    }

    public synchronized void clear() {
        this.activeBuffs.clear();
    }

}
