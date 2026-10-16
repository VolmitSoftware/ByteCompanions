package com.volmit.bytecompanions.platform.paper.configuration.serializer;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import com.volmit.bytecompanions.core.companion.ArmorCycleEntry;
import com.volmit.bytecompanions.platform.paper.util.ColorUtil;

import java.lang.reflect.Type;
import java.util.Locale;

public final class ArmorCycleEntrySerializer implements TypeSerializer<ArmorCycleEntry> {

    public static final ArmorCycleEntrySerializer INSTANCE = new ArmorCycleEntrySerializer();

    @Override
    public @Nullable ArmorCycleEntry deserialize(
            final @NotNull Type type,
            final @NotNull ConfigurationNode node
    ) {
        if (node.virtual()) {
            return null;
        }

        final String rgbColorString = node.node("rgb-color").getString();
        final Color rgbColor = ColorUtil.parseColor(rgbColorString);

        final TrimPattern trimPattern = this.getTrimPattern(node);
        final TrimMaterial trimMaterial = this.getTrimMaterial(node);

        return new ArmorCycleEntry(rgbColor, trimPattern, trimMaterial);
    }

    @Override
    public void serialize(
            final @NotNull Type type,
            final @Nullable ArmorCycleEntry obj,
            final @NotNull ConfigurationNode node
    ) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }

        final Color rgbColor = obj.rgbColor();
        if (rgbColor != null) {
            node.node("rgb-color").set(rgbColor.getRed() + "," + rgbColor.getGreen() + "," + rgbColor.getBlue());
        }

        final TrimPattern trimPattern = obj.trimPattern();
        if (trimPattern != null) {
            node.node("trim-pattern").set(trimPattern.getKey().getKey());
        }

        final TrimMaterial trimMaterial = obj.trimMaterial();
        if (trimMaterial != null) {
            node.node("trim-material").set(trimMaterial.getKey().getKey());
        }
    }

    private @Nullable TrimPattern getTrimPattern(final @NotNull ConfigurationNode node) {
        final String trimPatternKey = node.node("trim-pattern").getString();
        if (trimPatternKey == null || trimPatternKey.isBlank()) {
            return null;
        }

        final String key = trimPatternKey.toLowerCase(Locale.ROOT);
        final Registry<TrimPattern> registry = Registry.TRIM_PATTERN;
        return registry.get(NamespacedKey.minecraft(key));
    }

    private @Nullable TrimMaterial getTrimMaterial(final @NotNull ConfigurationNode node) {
        final String trimMaterialKey = node.node("trim-material").getString();
        if (trimMaterialKey == null || trimMaterialKey.isBlank()) {
            return null;
        }

        final String key = trimMaterialKey.toLowerCase(Locale.ROOT);
        final Registry<TrimMaterial> registry = Registry.TRIM_MATERIAL;
        return registry.get(NamespacedKey.minecraft(key));
    }
}