package com.volmit.bytecompanions.features.effects.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerLoadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerUnloadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSelectionChangedEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.features.effects.EffectsFeature;
import com.volmit.bytecompanions.features.effects.OwnedPotionEffects;
import com.volmit.bytecompanions.features.effects.StackMode;
import com.volmit.bytecompanions.features.effects.data.PotionEffectData;
import com.volmit.bytecompanions.features.effects.data.ResolvedEffect;
import com.volmit.bytecompanions.features.effects.data.EffectResolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MechanicListeners implements Listener {

    private final EffectsFeature feature;
    private final ByteCompanions companions;
    private final OwnedPotionEffects appliedEffects;

    public MechanicListeners(final @NotNull EffectsFeature feature, ByteCompanions companions) {
        this.feature = feature;
        this.companions = companions;
        this.appliedEffects = new OwnedPotionEffects(Player::getTicksLived);
    }

    @EventHandler
    public void onCompanionPlayerLoadEvent(final @NotNull CompanionPlayerLoadEvent event) {
        if (!this.feature.active()) {
            return;
        }
        this.applyAllEffects(event.player(), event.companionPlayer());
    }

    @EventHandler
    public void onPlayerRespawnEvent(final @NotNull PlayerRespawnEvent event) {
        if (!this.feature.active()) {
            return;
        }
        final Player player = event.getPlayer();

        this.feature.runTaskInNextTick(player, () -> {
            if (player.isOnline()) {
                this.companions.getCompanionPlayer(player)
                        .ifPresent(companionPlayer -> this.applyAllEffects(player, companionPlayer));
            }
        });
    }

    @EventHandler
    public void onCompanionPlayerUnloadEvent(final @NotNull CompanionPlayerUnloadEvent event) {
        this.removeAllEffects(event.player());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuitEvent(final @NotNull PlayerQuitEvent event) {
        this.removeAllEffects(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionEquipEvent(final @NotNull CompanionEquipEvent equipEvent) {
        if (equipEvent.preflight()) return;
        if (!this.feature.active()) {
            return;
        }
        final Player player = equipEvent.player();
        final String companionId = equipEvent.companionType().id();

        this.companions.getCompanionPlayer(player)
                .ifPresent(companionPlayer -> {
                    final List<PotionEffectData> effects = this.feature.configurationData().effects(companionId);
                    final Map<PotionEffectType, ResolvedEffect> resolved = this.resolveAllEffects(player, companionPlayer, companionId, null);
                    final Set<PotionEffectType> affectedTypes = effects.stream()
                            .map(PotionEffectData::effectType)
                            .collect(Collectors.toSet());

                    for (final PotionEffectType effectType : affectedTypes) {
                        final ResolvedEffect resolvedEffect = resolved.get(effectType);
                        if (resolvedEffect != null) {
                            this.applyEffect(player, resolvedEffect);
                        }
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionUnequipEvent(final @NotNull CompanionUnequipEvent unequipEvent) {
        if (unequipEvent.preflight()) return;
        if (!this.feature.active()) {
            return;
        }
        final Player player = unequipEvent.player();
        final Companion removedCompanion = unequipEvent.companion();
        final String companionId = removedCompanion.type().id();

        this.companions.getCompanionPlayer(player)
                .ifPresent(companionPlayer -> {
                    final List<PotionEffectData> effects = this.feature.configurationData().effects(companionId);
                    final Map<PotionEffectType, ResolvedEffect> resolved = this.resolveAllEffects(player, companionPlayer, null, companionId);
                    final Set<PotionEffectType> affectedTypes = effects.stream()
                            .map(PotionEffectData::effectType)
                            .collect(Collectors.toSet());

                    for (final PotionEffectType effectType : affectedTypes) {
                        final ResolvedEffect resolvedEffect = resolved.get(effectType);

                        if (resolvedEffect == null) {
                            this.removeOwnedEffect(player, effectType);
                        } else {
                            this.applyEffect(player, resolvedEffect);
                        }
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPotionEffectEvent(final @NotNull EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || this.appliedEffects.changing(player)) {
            return;
        }
        if (event.getAction() == EntityPotionEffectEvent.Action.CHANGED) {
            if (this.appliedEffects.rememberDisplaced(player, event.getNewEffect()) || !event.isOverride()) {
                return;
            }
        }
        boolean displacedCompanion = this.appliedEffects.externalChange(player, event.getModifiedType());
        PotionEffect replacement = event.getNewEffect();
        if (displacedCompanion && replacement != null) {
            this.appliedEffects.trackReplacement(player, replacement);
        }
        if (!this.feature.active()) {
            return;
        }
        this.feature.runTaskInNextTick(player, () -> {
            if (displacedCompanion && replacement != null) {
                this.appliedEffects.discardHiddenEffect(player, replacement);
            }
            this.companions.getCompanionPlayer(player)
                    .ifPresent(companionPlayer -> this.applyAllEffects(player, companionPlayer));
        });
    }

    public void applyAllEffects(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    ) {
        if (!this.feature.active()) {
            return;
        }
        final Map<PotionEffectType, ResolvedEffect> resolved = this.resolveAllEffects(player, companionPlayer, null, null);
        for (final ResolvedEffect resolvedEffect : resolved.values()) {
            this.applyEffect(player, resolvedEffect);
        }
    }

    public void removeAllEffects(Player player) {
        this.appliedEffects.removeAll(player);
    }

    @EventHandler
    public void onSelectionChanged(CompanionSelectionChangedEvent event) {
        if (!this.feature.active()) return;
        this.removeAllEffects(event.player());
        this.applyAllEffects(event.player(), event.companionPlayer());
    }

    public boolean ownsEffect(Player player, PotionEffectType type) {
        return this.appliedEffects.owns(player, type);
    }

    private void removeOwnedEffect(Player player, PotionEffectType type) {
        this.appliedEffects.remove(player, type);
    }
    /**
     * Resolves the final {@link ResolvedEffect} for every effect type
     * contributed by the companions currently equipped by the player, in a
     * single pass - as opposed to resolving one effect type at a time, which
     * would re-scan every companion once per distinct effect type.
     * <p>
     * Entries with {@link StackMode#HIGHEST} compete against each other; only
     * the highest-priority one contributes its amplifier. Entries with
     * {@link StackMode#SUM} are additive and all contribute their amplifier.
     * The final amplifier is {@code highestWinnerAmplifier + sumOfSumEntries}.
     * Metadata ({@code ambient}/{@code particles}/{@code icon}) always comes
     * from the highest-priority contributing entry, regardless of its mode.
     *
     * @param includeCompanionId a companion id to consider as equipped even
     *                           though it is not yet present in {@code companionPlayer}
     *                           (used by the equip handler)
     * @param excludeCompanionId a companion id to ignore even though it is
     *                           still present in {@code companionPlayer}
     *                           (used by the unequip handler)
     */
    private @NotNull Map<PotionEffectType, ResolvedEffect> resolveAllEffects(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer,
            final @Nullable String includeCompanionId,
            final @Nullable String excludeCompanionId
    ) {
        final Map<PotionEffectType, List<PotionEffectData>> grouped = new HashMap<>();
        for (final Map.Entry<String, Companion> entry : companionPlayer.companions().entrySet()) {
            final String companionTypeId = entry.getKey();
            if (companionTypeId.equals(excludeCompanionId)) {
                continue;
            }

            for (final PotionEffectData effect : this.feature.effectiveEffects(player, companionTypeId)) {
                grouped.computeIfAbsent(effect.effectType(), key -> new ArrayList<>()).add(effect);
            }
        }

        if (includeCompanionId != null && !companionPlayer.companions().containsKey(includeCompanionId)) {
            for (final PotionEffectData effect : this.feature.effectiveEffects(player, includeCompanionId)) {
                grouped.computeIfAbsent(effect.effectType(), key -> new ArrayList<>()).add(effect);
            }
        }

        final Map<PotionEffectType, ResolvedEffect> resolved = new HashMap<>(grouped.size());
        for (final Map.Entry<PotionEffectType, List<PotionEffectData>> entry : grouped.entrySet()) {
            resolved.put(entry.getKey(), this.resolveFromContributing(entry.getKey(), entry.getValue()));
        }

        return resolved;
    }

    private @NotNull ResolvedEffect resolveFromContributing(
            final @NotNull PotionEffectType effectType,
            final @NotNull List<PotionEffectData> contributing
    ) {
        return EffectResolution.resolve(effectType, contributing).effect();
    }

    private void applyEffect(
            final @NotNull Player player,
            final @NotNull ResolvedEffect resolved
    ) {
        if (!this.feature.bonusesAllowed(player)) return;
        this.appliedEffects.apply(player, this.createPotionEffect(resolved));
    }
    private @NotNull PotionEffect createPotionEffect(final @NotNull ResolvedEffect resolved) {
        return new PotionEffect(
                resolved.effectType(),
                PotionEffect.INFINITE_DURATION,
                resolved.amplifier(),
                resolved.ambient(),
                resolved.particles(),
                resolved.icon()
        );
    }
}
