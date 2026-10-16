package com.volmit.bytecompanions.internal.itemblueprint.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.context.BlueprintContext;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;

import java.util.List;
import java.util.Locale;

public final class ConfigNodes {

    private ConfigNodes() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull BlueprintResponse<String> requiredString(final @NotNull ConfigurationNode node, final @NotNull String field) {
        final String value = node.node(field).getString();
        if (value == null || value.isBlank()) {
            return BlueprintResponse.error(BlueprintResult.MISSING_FIELD, field, "Missing required field '" + field + "'.");
        }

        return BlueprintResponse.success(value);
    }

    public static @Nullable String string(final @NotNull ConfigurationNode node, final @NotNull String field) {
        return node.node(field).getString();
    }

    public static @NotNull String optionalString(
            final @NotNull ConfigurationNode node,
            final @NotNull String field,
            final @NotNull String fallback
    ) {
        return node.node(field).getString(fallback);
    }

    public static int optionalInt(final @NotNull ConfigurationNode node, final @NotNull String field, final int fallback) {
        return node.node(field).getInt(fallback);
    }

    public static boolean optionalBoolean(final @NotNull ConfigurationNode node, final @NotNull String field, final boolean fallback) {
        return node.node(field).getBoolean(fallback);
    }

    public static @Nullable <E extends Enum<E>> E kebabToEnum(final @Nullable String raw, final @NotNull Class<E> enumType) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        final String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(enumType, normalized);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    public static @Nullable Material materialByName(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        final String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Material.getMaterial(normalized, false);
    }

    public static <T extends Keyed> @Nullable T registryValue(final @NotNull Registry<T> registry, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        final NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT).replace('-', '_'));
        return key != null ? registry.get(key) : null;
    }

    public static @Nullable Color parseHexColor(final @NotNull String raw) {
        final String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) {
            return null;
        }

        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    public static @NotNull Component resolveText(
            final @NotNull String raw,
            final @NotNull BlueprintContext context,
            final @NotNull TextSerializer serializer
    ) {
        return serializer.serialize(context.resolve(raw));
    }

    public static @NotNull List<? extends ConfigurationNode> childrenList(final @NotNull ConfigurationNode node) {
        return node.isList() ? node.childrenList() : List.of();
    }

    public static @NotNull @Unmodifiable List<String> stringList(final @NotNull ConfigurationNode node) {
        return childrenList(node).stream().map(child -> child.getString("")).toList();
    }
}
