package com.volmit.bytecompanions.core.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;

public final class CompanionEntityCache {

    private final Int2ObjectMap<Companion> entities = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    public void put(final @NotNull Companion companion) {
        this.entities.put(companion.entityId(), companion);
    }

    public Companion get(final int entityId) {
        return this.entities.get(entityId);
    }

    public Companion remove(final int entityId) {
        return this.entities.remove(entityId);
    }

}
