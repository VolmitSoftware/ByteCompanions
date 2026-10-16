package com.volmit.bytecompanions.features.particles.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprint;

import java.util.function.Supplier;

public record ParticleEffectEntry(
        @NotNull String id,
        @Nullable ItemBlueprint itemBlueprint,
        @NotNull Supplier<ParticleEffect> factory
) {
}