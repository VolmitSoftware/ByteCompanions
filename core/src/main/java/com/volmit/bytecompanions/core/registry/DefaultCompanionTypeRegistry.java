package com.volmit.bytecompanions.core.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.CompanionType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultCompanionTypeRegistry implements CompanionTypeRegistry {

    private volatile Map<String, CompanionType> typeMap = Map.of();

    public Set<String> registeredIds() {
        return this.typeMap.keySet();
    }

    public int totalRegistered() {
        return this.typeMap.size();
    }

    @Override
    public @Nullable CompanionType getOrNull(final @NotNull String id) {
        return this.typeMap.get(id);
    }

    @Override
    public @NotNull Optional<CompanionType> get(final @NotNull String id) {
        return Optional.ofNullable(this.getOrNull(id));
    }

    @Override
    public synchronized void register(final @NotNull String id, @NotNull CompanionType companionType) {
        Map<String, CompanionType> updated = new HashMap<>(this.typeMap);
        updated.put(id, companionType);
        this.typeMap = Map.copyOf(updated);
    }

    @Override
    public synchronized void clear() {
        this.typeMap = Map.of();
    }

    @Override
    public @NotNull Collection<CompanionType> all() {
        return this.typeMap.values();
    }

}
