package com.volmit.bytecompanions.platform.paper.configuration;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;
import com.volmit.bytecompanions.platform.paper.TextSerializerFactory;
import com.volmit.bytecompanions.features.buffs.BuffPlatformSupport;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.platform.paper.collection.VariantCatalog;

import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ContentPreflight {
    private ContentPreflight() {
    }

    public static void validate(PaperPlugin plugin, Configuration root, ContentSnapshot content) throws IOException {
        ConfigurationOptions options = ConfigurationOptions.defaults();
        for (String path : content.paths()) {
            validateMaterials(content.node(path, options));
        }
        ItemBlueprintRegistry items = ItemBlueprintRegistry.create(TextSerializerFactory.create(root.presentation().serializer()));
        DefaultCompanionTypeRegistry registry = new DefaultCompanionTypeRegistry();
        try {
            new CompanionConfigurationLoader(plugin, registry, items, content).load();
            VariantCatalog.validateDefinitions(registry);
        } finally {
            items.clear();
        }
        validateAttributes(content.bukkit("features/attributes/config.toml"));
        validateEffects(content.bukkit("features/effects/config.toml"));
        validateDecorations(content.node("features/decorations/config.toml", options));
        ConfigurationNode buffs = content.node("features/buffs/config.toml", options);
        BuffPlatformSupport.validateNode(buffs,
                !root.features().enabled("buffs") || BuffPlatformSupport.supportsPickupExperience());
        validateCooldowns(buffs);
        ParticleContentPreflight.validate(content);
        ProgressionDefinitions progression = ProgressionDefinitions.parse(
                content.source("features/progression/config.toml").getBytes(StandardCharsets.UTF_8));
        if (root.features().enabled("progression")) {
            progression.validateCompanions(registry.all().stream().map(CompanionType::id).collect(Collectors.toSet()));
        }
        ProgressionContentPreflight.validate(progression, content, root.features().enabled("progression"),
                root.features().enabled("boosters"));
    }

    public static void validateAttributes(MemoryConfiguration configuration) {
        for (Map<?, ?> entry : companionEntries(configuration)) {
            String attribute = requiredText(entry, "attribute");
            NamespacedKey key = NamespacedKey.minecraft(attribute.toLowerCase(Locale.ROOT));
            if (Registry.ATTRIBUTE.get(key) == null) {
                throw new IllegalArgumentException("Unknown attribute: " + attribute);
            }
            finite(entry.get("amount"), "attribute amount");
            AttributeModifier.Operation.valueOf(requiredText(entry, "operation").toUpperCase(Locale.ROOT));
        }
    }

    public static void validateEffects(MemoryConfiguration configuration) {
        for (Map<?, ?> entry : companionEntries(configuration)) {
            String effect = requiredText(entry, "effect");
            if (Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT))) == null) {
                throw new IllegalArgumentException("Unknown potion effect: " + effect);
            }
            if (!Set.of("SUM", "HIGHEST").contains(requiredText(entry, "stack-mode").toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unknown potion stack-mode");
            }
            boundedInteger(entry.get("amplifier"), 1, 0, 255, "potion amplifier");
            boundedInteger(entry.get("priority"), 0, Integer.MIN_VALUE, Integer.MAX_VALUE, "potion priority");
            for (String key : List.of("ambient", "particles", "icon")) {
                if (entry.containsKey(key) && !(entry.get(key) instanceof Boolean)) {
                    throw new IllegalArgumentException("Potion " + key + " must be a boolean");
                }
            }
        }
    }

    private static List<Map<?, ?>> companionEntries(MemoryConfiguration configuration) {
        ConfigurationSection companions = configuration.getConfigurationSection("companions");
        if (companions == null) {
            if (configuration.contains("companions")) {
                throw new IllegalArgumentException("companions must be a mapping");
            }
            return List.of();
        }
        List<Map<?, ?>> entries = new ArrayList<>();
        for (String id : companions.getKeys(false)) {
            Object value = companions.get(id);
            if (!(value instanceof List<?> list)) {
                throw new IllegalArgumentException("Companion " + id + " must contain a list");
            }
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Companion " + id + " entries must be mappings");
                }
                entries.add(map);
            }
        }
        return entries;
    }

    static void validateDecorations(ConfigurationNode node) {
        requireMapping(node.node("settings"), "decoration settings");
        requireMapping(node.node("decorations"), "decorations");
        integer(node.node("settings", "update-interval"), 3, 1, Integer.MAX_VALUE);
        integer(node.node("settings", "view-distance"), 8, 0, Integer.MAX_VALUE);
        integer(node.node("settings", "view-refresh-delay"), 3, 0, Integer.MAX_VALUE);
        for (ConfigurationNode decoration : node.node("decorations").childrenMap().values()) {
            requireMapping(decoration, "decoration entry");
            ConfigurationNode enabled = decoration.node("enabled");
            if (!enabled.virtual() && !(enabled.rawScalar() instanceof Boolean)) {
                throw new IllegalArgumentException("Decoration enabled must be a boolean");
            }
            if (!decoration.node("enabled").getBoolean(true)) {
                continue;
            }
            ConfigurationNode world = decoration.node("world");
            if (!world.virtual() && (!(world.rawScalar() instanceof String name) || name.isBlank())) {
                throw new IllegalArgumentException("Decoration world must be a nonempty string");
            }
            if (decoration.node("companion").getString("").isBlank()) {
                throw new IllegalArgumentException("Decoration requires a companion ID");
            }
            for (String key : List.of("x", "y", "z", "yaw", "pitch", "body-yaw", "eye-height", "height")) {
                if (!decoration.node(key).virtual()) {
                    double value = finite(decoration.node(key).rawScalar(), "decoration " + key);
                    if (key.equals("height") && value <= 0 || key.equals("eye-height") && value < 0) {
                        throw new IllegalArgumentException("Decoration height must be positive and eye-height nonnegative");
                    }
                }
            }
        }
    }

    private static void requireMapping(ConfigurationNode node, String field) {
        if (node.raw() != null && !node.isMap()) {
            throw new IllegalArgumentException(field + " must be a mapping");
        }
    }

    private static void validateCooldowns(ConfigurationNode node) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            if (entry.getKey().equals("cooldown")) {
                double value = finite(entry.getValue().rawScalar(), "buff cooldown");
                if (value < 0 || value != Math.rint(value) || value > Long.MAX_VALUE) {
                    throw new IllegalArgumentException("Buff cooldown must be a nonnegative integer");
                }
            } else {
                validateCooldowns(entry.getValue());
            }
        }
    }

    private static void validateMaterials(ConfigurationNode node) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            if (entry.getKey().equals("material")) {
                String material = entry.getValue().getString("");
                String lower = material.toLowerCase(Locale.ROOT);
                if (lower.startsWith("nexo:") || lower.startsWith("itemsadder:")) {
                    String plugin = lower.startsWith("nexo:") ? "Nexo" : "ItemsAdder";
                    if (!Bukkit.getPluginManager().isPluginEnabled(plugin)) {
                        throw new IllegalArgumentException("Material " + material + " requires " + plugin);
                    }
                } else if (Material.matchMaterial(material) == null) {
                    throw new IllegalArgumentException("Unknown material: " + material);
                }
            } else {
                validateMaterials(entry.getValue());
            }
        }
        for (ConfigurationNode child : node.childrenList()) {
            validateMaterials(child);
        }
    }

    static int integer(ConfigurationNode node, int fallback, int minimum, int maximum) {
        return boundedInteger(node.rawScalar(), fallback, minimum, maximum, node.path().toString());
    }

    private static int boundedInteger(Object value, int fallback, int minimum, int maximum, String field) {
        if (value == null) {
            return fallback;
        }
        double number = finite(value, field);
        if (number != Math.rint(number) || number < minimum || number > maximum) {
            throw new IllegalArgumentException(field + " must be an integer between " + minimum + " and " + maximum);
        }
        return (int) number;
    }

    static double finite(Object value, String field) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return number.doubleValue();
    }

    private static String requiredText(Map<?, ?> entry, String key) {
        if (!(entry.get(key) instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a nonempty string");
        }
        return text;
    }
}
