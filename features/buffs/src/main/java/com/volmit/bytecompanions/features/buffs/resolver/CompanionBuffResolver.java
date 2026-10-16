package com.volmit.bytecompanions.features.buffs.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Vanilla;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public final class CompanionBuffResolver {

    private static final Map<BuffKey, Function<Vanilla, Buff>> EXTRACTORS = new EnumMap<>(BuffKey.class);

    static {
        EXTRACTORS.put(BuffKey.EXPERIENCE, Vanilla::experience);
        EXTRACTORS.put(BuffKey.EXPERIENCE_MOB, Vanilla::experienceMob);
        EXTRACTORS.put(BuffKey.EXPERIENCE_BLOCK, Vanilla::experienceBlock);
        EXTRACTORS.put(BuffKey.EXPERIENCE_FURNACE, Vanilla::experienceFurnace);
        EXTRACTORS.put(BuffKey.EXPERIENCE_FISHING, Vanilla::experienceFishing);
        EXTRACTORS.put(BuffKey.MOB_LOOT, Vanilla::mobLoot);
        EXTRACTORS.put(BuffKey.BLOCK_DROPS, Vanilla::blockDrops);
        EXTRACTORS.put(BuffKey.FISHING_LOOT, Vanilla::fishingLoot);
    }

    private final BuffsFeature feature;
    private final Logger logger;
    public CompanionBuffResolver(final @NotNull BuffsFeature feature, Logger logger) {
        this.feature = feature;
        this.logger = logger;
    }

    public @NotNull Map<BuffKey, ResolvedBuff> resolve(final @NotNull Collection<String> equippedTypeIds) {
        final Map<BuffKey, ResolvedBuff> winners = new EnumMap<>(BuffKey.class);

        for (final String typeId : equippedTypeIds) {
            final Companions config = this.feature.companions(typeId);
            if (config == null) {
                continue;
            }

            final Vanilla vanilla = config.vanilla();
            for (final Map.Entry<BuffKey, Function<Vanilla, Buff>> entry : EXTRACTORS.entrySet()) {
                this.tryClaim(winners, entry.getKey(), entry.getValue().apply(vanilla), typeId, config.defaultPriority());
            }
        }

        return winners;
    }

    private void tryClaim(
            final @NotNull Map<BuffKey, ResolvedBuff> winners,
            final @NotNull BuffKey type,
            final @Nullable Buff buff,
            final @NotNull String typeId,
            final int defaultPriority
    ) {
        if (buff == null || !buff.enabled()) {
            return;
        }

        final Integer override = buff.rawPriority();
        final int priority = override != null ? override : defaultPriority;
        final ResolvedBuff current = winners.get(type);
        if (current == null || priority > current.priority()) {
            winners.put(type, new ResolvedBuff(typeId, priority, buff));
            return;
        }

        if (priority == current.priority()) {
            this.logger.warn(
                    "Buff conflict for '{}': tie between '{}' and '{}' (priority {}). Keeping '{}'.",
                    type,
                    current.companionTypeId(),
                    typeId,
                    priority,
                    current.companionTypeId()
            );
        }
    }
}
