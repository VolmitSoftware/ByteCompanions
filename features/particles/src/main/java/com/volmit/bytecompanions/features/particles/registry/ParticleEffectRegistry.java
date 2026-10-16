package com.volmit.bytecompanions.features.particles.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

public final class ParticleEffectRegistry {

    private final Map<String, ParticleEffectEntry> entries = new LinkedHashMap<>();

    public void register(final @NotNull ParticleEffectEntry entry) {
        if (this.entries.containsKey(entry.id())) {
            throw new IllegalStateException("Particle effect '" + entry.id() + "' is already registered.");
        }

        this.entries.put(entry.id(), entry);
    }

    public int size() {
        return this.entries.size();
    }

    public void unregisterAll() {
        this.entries.clear();
    }

    public boolean contains(final @NotNull String id) {
        return this.entries.containsKey(id);
    }

    public @Nullable ParticleEffectEntry get(final @NotNull String id) {
        return this.entries.get(id);
    }

    public @NotNull Optional<ParticleEffectEntry> find(final @NotNull String id) {
        return Optional.ofNullable(this.entries.get(id));
    }

    public @NotNull @UnmodifiableView Collection<ParticleEffectEntry> entries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }
}