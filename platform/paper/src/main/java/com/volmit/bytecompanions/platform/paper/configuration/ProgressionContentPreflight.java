package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.features.buffs.BuffPlatformSupport;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProgressionContentPreflight {
    private ProgressionContentPreflight() {
    }

    public static void validate(ProgressionDefinitions definitions, ContentSnapshot content, boolean progressionEnabled, boolean boostersEnabled) throws IOException {
        if (progressionEnabled) {
            validateTargets(definitions, content);
        }
        Set<String> buffChannels = ProgressionDefinitions.CHANNELS;
        for (ProgressionDefinitions.BoosterDefinition booster : definitions.boosters().values()) {
            for (String channel : booster.percentages().keySet()) {
                if (!channel.equals("companion_xp") && !buffChannels.contains(channel)) {
                    throw new IllegalArgumentException("Unknown booster channel: " + channel);
                }
                if (boostersEnabled && channel.equals("experience") && !BuffPlatformSupport.supportsPickupExperience()) {
                    throw new IllegalArgumentException("General pickup experience boosters require Paper; use source-specific XP channels on Spigot");
                }
            }
        }
    }

    private static void validateTargets(ProgressionDefinitions definitions, ContentSnapshot content) throws IOException {
        ConfigurationNode attributes = content.node("features/attributes/config.toml", ConfigurationOptions.defaults());
        ConfigurationNode effects = content.node("features/effects/config.toml", ConfigurationOptions.defaults());
        ConfigurationNode buffs = content.node("features/buffs/config.toml", ConfigurationOptions.defaults());
        for (Map.Entry<String, ProgressionDefinitions.CompanionDefinition> companion : definitions.companions().entrySet()) {
            Map<String, List<Double>> bases = new LinkedHashMap<>();
            collect(bases, attributes.node("companions", companion.getKey()), "attribute", "attribute", "amount");
            collect(bases, effects.node("companions", companion.getKey()), "effect", "effect", "amplifier");
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : buffs.node("companions", companion.getKey(), "vanilla").childrenMap().entrySet()) {
                ConfigurationNode node = entry.getValue();
                if (node.node("enabled").getBoolean(true)) {
                    String target = "buff:" + entry.getKey().toString().replace('-', '_');
                    bases.put(target, List.of(node.node("value").getDouble(0)));
                }
            }
            Map<String, Double> minimum = new LinkedHashMap<>();
            Map<String, Double> maximum = new LinkedHashMap<>();
            for (Map<String, Double> reward : companion.getValue().levelRewards().values()) {
                accumulate(companion.getKey(), bases, minimum, maximum, reward);
            }
            for (ProgressionDefinitions.TrackDefinition track : companion.getValue().tracks().values()) {
                for (ProgressionDefinitions.TierDefinition tier : track.tiers()) {
                    accumulate(companion.getKey(), bases, minimum, maximum, tier.additions());
                }
            }
            for (String target : maximum.keySet()) {
                for (double base : bases.get(target)) {
                    double lower = base + minimum.get(target);
                    double upper = base + maximum.get(target);
                    if (!Double.isFinite(lower) || !Double.isFinite(upper)) {
                        throw new IllegalArgumentException("Progression bonuses must remain finite: " + companion.getKey() + " / " + target);
                    }
                    if (target.startsWith("effect:") && (lower < 0 || upper > 255)) {
                        throw new IllegalArgumentException("Progression potion amplifier exceeds 0 through 255: " + companion.getKey() + " / " + target);
                    }
                }
            }
        }
    }

    private static void collect(Map<String, List<Double>> bases, ConfigurationNode entries, String prefix,
                                String identifier, String amount) {
        for (ConfigurationNode entry : entries.childrenList()) {
            String id = entry.node(identifier).getString("").toLowerCase(Locale.ROOT);
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            bases.computeIfAbsent(prefix + ":" + id, ignored -> new ArrayList<>()).add(entry.node(amount).getDouble(prefix.equals("effect") ? 1 : 0));
        }
    }

    private static void accumulate(String companionId, Map<String, List<Double>> bases, Map<String, Double> minimum,
                                   Map<String, Double> maximum, Map<String, Double> additions) {
        for (Map.Entry<String, Double> addition : additions.entrySet()) {
            String target = addition.getKey();
            double value = addition.getValue();
            if (!bases.containsKey(target)) {
                throw new IllegalArgumentException("Progression target requires a configured enabled bonus: " + companionId + " / " + target);
            }
            if (bases.get(target).size() != 1) {
                throw new IllegalArgumentException("Progression target must identify exactly one base bonus: " + companionId + " / " + target);
            }
            if (!Double.isFinite(value) || target.startsWith("effect:") && (value != Math.rint(value) || value < 0)) {
                throw new IllegalArgumentException("Invalid progression bonus addition: " + companionId + " / " + target);
            }
            minimum.merge(target, Math.min(0, value), Double::sum);
            maximum.merge(target, Math.max(0, value), Double::sum);
        }
    }
}
