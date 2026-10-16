package com.volmit.bytecompanions.internal.itemblueprint.base;

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

public final class ItemBaseRegistry {

    private final @NotNull Map<String, ItemBase> bases = new HashMap<>();

    public void clear() {
        this.bases.clear();
    }

    public void register(final @NotNull String id, final @NotNull ItemBase base) {
        final String key = normalize(id);
        if (this.bases.containsKey(key)) {
            throw new IllegalStateException("Item base '" + key + "' is already registered.");
        }

        this.bases.put(key, base);
    }

    public void unregister(final @NotNull String id) {
        final String normalizedId = normalize(id);
        this.bases.remove(normalizedId);
    }

    public boolean isRegistered(final @NotNull String id) {
        final String normalizedId = normalize(id);
        return this.bases.containsKey(normalizedId);
    }

    public @NotNull @Unmodifiable Set<String> registeredIds() {
        return Set.copyOf(this.bases.keySet());
    }

    private @Nullable ItemBase find(final @NotNull String id) {
        final String normalizedId = normalize(id);
        return this.bases.get(normalizedId);
    }

    public @NotNull BlueprintResponse<CompiledBase> compile(final @NotNull ConfigurationNode itemNode) {
        final String type = itemNode.node("type").getString("material");
        final ItemBase base = this.find(type);
        if (base == null) {
            return BlueprintResponse.error(BlueprintResult.UNKNOWN_TYPE, "type", "Unknown item base '" + type + "'.");
        }

        return base.compile(itemNode);
    }

    private static @NotNull String normalize(final @NotNull String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
