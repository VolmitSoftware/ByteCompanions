package com.volmit.bytecompanions.internal.itemblueprint.component;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemComponentRegistry {

    private final @NotNull Map<String, ItemComponent> components = new HashMap<>();

    public void clear() {
        this.components.clear();
    }

    public void register(final @NotNull String id, final @NotNull ItemComponent component) {
        final String key = normalize(id);
        if (this.components.containsKey(key)) {
            throw new IllegalStateException("Item component '" + key + "' is already registered.");
        }

        this.components.put(key, component);
    }

    public void unregister(final @NotNull String id) {
        final String normalizedId = normalize(id);
        this.components.remove(normalizedId);
    }

    public boolean isRegistered(final @NotNull String id) {
        final String normalizedId = normalize(id);
        return this.components.containsKey(normalizedId);
    }

    public @NotNull @Unmodifiable Set<String> registeredIds() {
        return Set.copyOf(this.components.keySet());
    }

    private @Nullable ItemComponent find(final @NotNull String id) {
        final String normalizedId = normalize(id);
        return this.components.get(normalizedId);
    }

    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull String id, final @NotNull ConfigurationNode node) {
        final ItemComponent component = this.find(id);
        if (component == null) {
            return BlueprintResponse.error(BlueprintResult.UNKNOWN_TYPE, id, "Unknown item component '" + id + "'.");
        }

        return component.compile(node);
    }

    private static @NotNull String normalize(final @NotNull String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
