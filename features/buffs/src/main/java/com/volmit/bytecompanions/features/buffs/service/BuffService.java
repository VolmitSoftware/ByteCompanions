package com.volmit.bytecompanions.features.buffs.service;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.cache.PlayerBuffCache;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.manager.CooldownManager;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.Optional;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.bonus.BonusValues;

public final class BuffService {

    private final PlayerBuffCache playerBuffCache;
    private final CooldownManager cooldownManager;
    private final ProgressionService progression;
    private volatile Predicate<Player> bonusesAllowed = player -> true;

    public BuffService(
            final @NotNull PlayerBuffCache playerBuffCache,
            final @NotNull CooldownManager cooldownManager,
            ProgressionService progression
    ) {
        this.playerBuffCache = playerBuffCache;
        this.cooldownManager = cooldownManager;
        this.progression = progression;
    }

    public double value(Player player, String companionId, BuffKey key, Buff buff) {
        return this.progression == null || companionId == null ? buff.value() : BonusValues.add(buff.value(),
                this.progression.bonusAddition(player.getUniqueId(), companionId, "buff:" + key.name().toLowerCase(Locale.ROOT)));
    }

    public double boosterPercent(Player player, BuffKey key) {
        return !this.bonusesAllowed(player) || this.progression == null ? 0
                : this.progression.boosterPercent(player.getUniqueId(), key.name().toLowerCase(Locale.ROOT));
    }

    public void bonusesAllowed(Predicate<Player> allowed) {
        this.bonusesAllowed = Objects.requireNonNull(allowed);
    }

    public boolean bonusesAllowed(Player player) {
        return this.bonusesAllowed.test(player);
    }

    public int apply(Player player, BuffKey key, ResolvedBuff selected, int base) {
        if (!this.bonusesAllowed(player)) return base;
        Buff buff = selected.buff();
        double value = this.value(player, selected.companionTypeId(), key, buff);
        return BonusValues.apply(buff.operation(), base, value, this.boosterPercent(player, key),
                this.progression == null ? Double.MAX_VALUE : this.progression.definitions().maximumRewardMultiplier());
    }

    public @NotNull Optional<ResolvedBuff> peek(final @NotNull Player player, final @NotNull BuffKey buffKey) {
        if (!this.bonusesAllowed(player)) return Optional.empty();
        Optional<ResolvedBuff> selected = this.playerBuffCache.get(player, buffKey);
        return selected.isPresent() || this.boosterPercent(player, buffKey) <= 0
                ? selected : Optional.of(ResolvedBuff.booster(buffKey));
    }

    public boolean isActive(final @NotNull Player player, final @NotNull BuffKey buffKey) {
        return this.peek(player, buffKey).isPresent();
    }

    public @NotNull Optional<ResolvedBuff> resolve(
            final @NotNull Player player,
            final @NotNull BuffKey buffType
    ) {
        final ResolvedBuff resolvedBuff = this.peek(player, buffType)
                .orElse(null);

        if (resolvedBuff == null) {
            return Optional.empty();
        }

        if (!this.cooldownManager.tryUse(player, buffType, resolvedBuff.buff().cooldown())) {
            return Optional.empty();
        }

        return Optional.of(resolvedBuff);
    }

}
