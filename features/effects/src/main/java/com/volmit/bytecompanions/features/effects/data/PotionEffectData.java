package com.volmit.bytecompanions.features.effects.data;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.effects.StackMode;

public record PotionEffectData(
        @NotNull PotionEffectType effectType,
        int amplifier,
        int priority,
        boolean ambient,
        boolean particles,
        boolean icon,
        StackMode stackMode
) {
}
