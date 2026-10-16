package com.volmit.bytecompanions.core.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public final class CompanionPlayerCache {

    private final Int2ObjectMap<DefaultCompanionPlayer> idCache = new Int2ObjectOpenHashMap<>();
    private final Map<UUID, DefaultCompanionPlayer> uuidCache = new HashMap<>();

    public synchronized void put(
            final @NotNull Player player,
            final @NotNull DefaultCompanionPlayer companionPlayer
    ) {
        this.idCache.put(player.getEntityId(), companionPlayer);
        this.uuidCache.put(player.getUniqueId(), companionPlayer);
    }

    public synchronized @Nullable DefaultCompanionPlayer getByEntityId(final int entityId) {
        return this.idCache.get(entityId);
    }

    public synchronized @Nullable DefaultCompanionPlayer getByUniqueId(final @NotNull UUID playerId) {
        return this.uuidCache.get(playerId);
    }

    public synchronized @Nullable DefaultCompanionPlayer remove(final @NotNull Player player) {
        this.idCache.remove(player.getEntityId());
        return this.uuidCache.remove(player.getUniqueId());
    }

    public synchronized @NotNull Collection<DefaultCompanionPlayer> all() {
        return List.copyOf(this.uuidCache.values());
    }

    public synchronized void clear() {
        this.idCache.clear();
        this.uuidCache.clear();
    }

}
