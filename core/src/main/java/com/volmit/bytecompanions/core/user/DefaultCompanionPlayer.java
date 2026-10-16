package com.volmit.bytecompanions.core.user;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.entity.CompanionEntity;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.data.OwnerData;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCompanionPlayer implements CompanionPlayer {

    private final Map<String, Companion> companions = new ConcurrentHashMap<>();
    private volatile List<String> equippedIds = List.of();
    private Map<String, Integer> formationOrder = Map.of();

    private final OwnerData ownerData;

    public DefaultCompanionPlayer(
            final @NotNull UUID playerId,
            final @NotNull String playerName
    ) {
        this(new OwnerData(playerId, playerName));
    }

    public DefaultCompanionPlayer(final @NotNull OwnerData ownerData) {
        this(ownerData, Collections.emptyMap());
    }

    public DefaultCompanionPlayer(
            final @NotNull OwnerData ownerData,
            final @NotNull Map<String, Companion> companions
    ) {
        this.ownerData = ownerData;
        this.companions.putAll(companions);
        this.publishEquippedIds();
    }

    @Override
    public @NotNull OwnerData ownerData() {
        return this.ownerData;
    }

    @Override
    public @NotNull UUID playerId() {
        return this.ownerData.uniqueId();
    }

    @Override
    public @NotNull String playerName() {
        return this.ownerData.name();
    }

    @Override
    public @NotNull Map<String, Companion> companions() {
        return this.companions;
    }

    public List<String> equippedIds() {
        return this.equippedIds;
    }

    public void formationOrder(List<String> companionIds) {
        Objects.requireNonNull(companionIds, "companionIds");
        Map<String, Integer> order = new HashMap<>();
        for (String companionId : companionIds) {
            if (companionId == null || companionId.isBlank() || order.putIfAbsent(companionId, order.size()) != null) {
                throw new IllegalArgumentException("Formation order requires unique companion IDs");
            }
        }
        this.formationOrder = Map.copyOf(order);
        this.reindexFormationSlots();
    }

    private void publishEquippedIds() {
        this.equippedIds = this.companions.keySet().stream().sorted().toList();
    }

    @Override
    public int companionsActive() {
        return Math.toIntExact(this.companions.values()
                .stream()
                .filter(Objects::nonNull)
                .count());
    }

    @Override
    public int companionsActive(final @NotNull CompanionModel companionModel) {
        return Math.toIntExact(this.companions.values()
                .stream()
                .filter(companion -> companion.type().model() == companionModel)
                .count());
    }

    @Override
    public boolean hasAnyExclusive() {
        return this.companions.values()
                .stream()
                .anyMatch(companion -> companion.type().exclusive());
    }

    @Override
    public boolean hasCompanion() {
        return !this.companions.isEmpty();
    }

    @Override
    public boolean hasCompanion(final @NotNull String companionTypeId) {
        return this.companions.containsKey(companionTypeId);
    }

    @Override
    public void equip(final @NotNull Companion companion) {
        final String companionTypeId = companion.type().id();
        final Companion companionEntry = this.companions.get(companionTypeId);
        if (companionEntry != null) {
            companionEntry.destroy();
        }

        this.companions.put(companionTypeId, companion);
        this.publishEquippedIds();
        this.reindexFormationSlots();
    }

    @Override
    public void unequip(final @NotNull String companionTypeId) {
        final Companion companion = this.companions.remove(companionTypeId);
        this.publishEquippedIds();
        if (companion != null) {
            companion.destroy();
        }

        this.reindexFormationSlots();
    }

    @Override
    public void unequip() {
        this.companions.values()
                .stream()
                .forEach(Companion::destroy);

        this.companions.clear();
        this.publishEquippedIds();
    }

    public void equipAll(final @NotNull Collection<Companion> companionsToEquip) {
        for (final Companion companion : companionsToEquip) {
            final String companionTypeId = companion.type().id();
            final Companion existing = this.companions.get(companionTypeId);
            if (existing != null) {
                existing.destroy();
            }

            this.companions.put(companionTypeId, companion);
        }

        this.publishEquippedIds();
        this.reindexFormationSlots();
    }

    /**
     * Recalculates and pushes the contiguous formation index for every
     * currently active companion of this player. Called only on equip/unequip,
     * never per-tick — cheap because it's O(n) on a small collection and rare.
     */
    private void reindexFormationSlots() {
        final List<Companion> active = this.companions.values().stream()
                .filter(CompanionEntity::isSpawned)
                .sorted(Comparator.comparingInt((Companion companion) ->
                                this.formationOrder.getOrDefault(companion.type().id(), Integer.MAX_VALUE))
                        .thenComparing(companion -> companion.type().id()))
                .toList();

        final int count = active.size();
        for (int i = 0; i < count; i++) {
            active.get(i).formationIndex(i, count);
        }
    }

}
