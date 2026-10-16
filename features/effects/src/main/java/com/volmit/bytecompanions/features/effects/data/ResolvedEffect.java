package com.volmit.bytecompanions.features.effects.data;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public record ResolvedEffect(
        @NotNull PotionEffectType effectType,
        int amplifier,
        boolean ambient,
        boolean particles,
        boolean icon
) {

}
