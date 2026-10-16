package com.volmit.bytecompanions.features.effects.data;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class ConfigurationData {

    private final Map<String, List<PotionEffectData>> companionEffects;

    public ConfigurationData(final @NotNull Map<String, List<PotionEffectData>> companionEffects) {
        this.companionEffects = Map.copyOf(companionEffects);
    }

    public @NotNull List<PotionEffectData> effects(final @NotNull String companionId) {
        return this.companionEffects.getOrDefault(companionId, List.of());
    }

}