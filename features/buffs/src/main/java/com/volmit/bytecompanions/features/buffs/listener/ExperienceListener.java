package com.volmit.bytecompanions.features.buffs.listener;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ExperienceListener implements Listener {

    private static final Map<ExperienceOrb.SpawnReason, BuffKey> REASON_TO_SPECIFIC_TYPE = new EnumMap<>(ExperienceOrb.SpawnReason.class) {{
        put(ExperienceOrb.SpawnReason.BLOCK_BREAK, BuffKey.EXPERIENCE_BLOCK);
        put(ExperienceOrb.SpawnReason.FURNACE, BuffKey.EXPERIENCE_FURNACE);
        put(ExperienceOrb.SpawnReason.FISHING, BuffKey.EXPERIENCE_FISHING);
        put(ExperienceOrb.SpawnReason.ENTITY_DEATH, BuffKey.EXPERIENCE_MOB);
    }};

    private static final Set<ExperienceOrb.SpawnReason> EXCLUDED_REASONS = EnumSet.of(
            ExperienceOrb.SpawnReason.PLAYER_DEATH,
            ExperienceOrb.SpawnReason.EXP_BOTTLE
    );

    private final BuffsFeature feature;
    public ExperienceListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupExperienceEvent(final @NotNull PlayerPickupExperienceEvent experienceEvent) {
        if (!this.feature.active()) {
            return;
        }
        final Player player = experienceEvent.getPlayer();
        final ExperienceOrb experienceOrb = experienceEvent.getExperienceOrb();
        final ExperienceOrb.SpawnReason spawnReason = experienceOrb.getSpawnReason();

        if (EXCLUDED_REASONS.contains(spawnReason)) {
            return;
        }

        final BuffKey specificKey = REASON_TO_SPECIFIC_TYPE.get(spawnReason);
        if (specificKey != null && this.feature.buffService().isActive(player, specificKey)) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.EXPERIENCE)
                .ifPresent(buff -> {
                    final int base = experienceOrb.getExperience();
                    experienceOrb.setExperience(this.feature.buffService().apply(player, BuffKey.EXPERIENCE, buff, base));
                    this.feature.notifyPlayer(player, buff, BuffKey.EXPERIENCE);
                });
    }

}
