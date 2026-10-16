package com.volmit.bytecompanions.features.effects.data;

import com.volmit.bytecompanions.features.effects.StackMode;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public record EffectResolution(ResolvedEffect effect, List<PotionEffectData> contributors) {
    public EffectResolution {
        contributors = List.copyOf(contributors);
    }

    public static EffectResolution resolve(PotionEffectType type, List<PotionEffectData> entries) {
        PotionEffectData metadataSource = null;
        PotionEffectData highestWinner = null;
        long summedAmplifier = 0;
        List<PotionEffectData> contributors = new ArrayList<>();
        for (PotionEffectData entry : entries) {
            if (metadataSource == null || entry.priority() > metadataSource.priority()) {
                metadataSource = entry;
            }
            if (entry.stackMode() == StackMode.SUM) {
                summedAmplifier += entry.amplifier();
                contributors.add(entry);
            } else if (highestWinner == null || entry.priority() > highestWinner.priority()) {
                highestWinner = entry;
            }
        }
        if (metadataSource == null) {
            throw new IllegalArgumentException("Effect resolution requires at least one contribution");
        }
        if (highestWinner != null) {
            contributors.add(highestWinner);
        }
        int baseAmplifier = highestWinner == null ? 0 : highestWinner.amplifier();
        return new EffectResolution(new ResolvedEffect(type, (int) Math.min(255L, baseAmplifier + summedAmplifier),
                metadataSource.ambient(), metadataSource.particles(), metadataSource.icon()), contributors);
    }

    public boolean includes(PotionEffectData entry) {
        return this.contributors.stream().anyMatch(contributor -> contributor == entry);
    }
}
