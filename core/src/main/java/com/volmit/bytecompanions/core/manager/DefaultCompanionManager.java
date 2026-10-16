package com.volmit.bytecompanions.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.result.EquipCompanionResult;
import com.volmit.bytecompanions.api.result.UnequipCompanionResult;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.api.util.response.Response;
import com.volmit.bytecompanions.api.util.response.context.ResponseContext;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCompanionManager implements CompanionManager {
    private final Set<UUID> selectionChanges = ConcurrentHashMap.newKeySet();

    private final CompanionTypeRegistry companionTypeRegistry;
    private final CompanionFactory companionFactory;
    private final CompanionPlayerCache playerCache;
    private final CompanionEventBus companionEventBus;
    private final CompanionEntityCache companionEntityCache;

    public DefaultCompanionManager(
            final @NotNull CompanionTypeRegistry companionTypeRegistry,
            final @NotNull CompanionFactory companionFactory,
            final @NotNull CompanionPlayerCache playerCache,
            final @NotNull CompanionEventBus companionEventBus,
            final @NotNull CompanionEntityCache companionEntityCache
    ) {
        this.companionTypeRegistry = companionTypeRegistry;
        this.companionFactory = companionFactory;
        this.playerCache = playerCache;
        this.companionEventBus = companionEventBus;
        this.companionEntityCache = companionEntityCache;
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Override
    public @NotNull Response<EquipCompanionResult> equip(
            final @NotNull UUID playerId,
            final @NotNull String companionTypeId
    ) {
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(playerId);
        if (companionPlayer == null) {
            return Response.failure(EquipCompanionResult.PLAYER_NOT_LOADED);
        }

        final CompanionType companionType = this.companionTypeRegistry.getOrNull(companionTypeId);
        if (companionType == null) {
            return Response.failure(EquipCompanionResult.COMPANION_TYPE_NOT_FOUND);
        }

        // Bukkit player needed — resolve via online players
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Response.failure(EquipCompanionResult.PLAYER_NOT_LOADED);
        }

        return this.equip(player, companionPlayer, companionType);
    }

    @Override
    public void forceEquip(
            final @NotNull Player player,
            final @NotNull Companion companion
    ) {
        if (this.selectionChanges.contains(player.getUniqueId())) return;
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer != null) {
            companionPlayer.equip(companion);
            this.companionEntityCache.put(companion);
        }
    }

    @Override
    public @NotNull Response<UnequipCompanionResult> unequip(final @NotNull UUID playerId) {
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(playerId);
        if (companionPlayer == null) {
            return Response.failure(UnequipCompanionResult.PLAYER_NOT_LOADED);
        }

        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Response.failure(UnequipCompanionResult.BUKKIT_PLAYER_NOT_FOUND);
        }

        final Response<UnequipCompanionResult> response = this.unequip(player, companionPlayer);
        if (!response.success()) {
            return Response.failure(response.result());
        }

        return Response.success(UnequipCompanionResult.SUCCESS);
    }

    @Override
    public @NotNull Response<EquipCompanionResult> equip(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {
        CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return Response.failure(EquipCompanionResult.PLAYER_NOT_LOADED);
        }
        return this.equip(player, companionPlayer, companionType);
    }

    @Override
    public @NotNull ResponseContext<Companion, EquipCompanionResult> equip(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer,
            final @NotNull CompanionType companionType
    ) {

        if (this.selectionChanges.contains(player.getUniqueId())) {
            return ResponseContext.failure(EquipCompanionResult.CANCELLED);
        }

        if (!companionPlayer.playerId().equals(player.getUniqueId())) {
            return ResponseContext.failure(EquipCompanionResult.COMPANION_PLAYER_IS_NOT_SAME);
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        if (!companionMap.isEmpty()) {
            if (companionPlayer.hasCompanion(companionType.id())) {
                return ResponseContext.failure(EquipCompanionResult.ALREADY_EQUIPPED);
            }
        }

        if (companionPlayer.hasAnyExclusive() || companionType.exclusive() && !companionMap.isEmpty()) {
            return ResponseContext.failure(EquipCompanionResult.COMPANION_TYPE_IS_EXCLUSIVE);
        }

        if (!this.companionEventBus.callEquip(player, companionType)) {
            return ResponseContext.failure(EquipCompanionResult.CANCELLED);
        }

        final Companion companion = this.companionFactory.create(player, companionType);
        companionPlayer.equip(companion);
        this.companionEntityCache.put(companion);

        this.companionEventBus.callSpawn(companion);
        return ResponseContext.success(companion, EquipCompanionResult.SUCCESS);

    }

    @Override
    public void equipVisual(final @NotNull Player player) {
        final DefaultCompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return;
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        final List<Companion> collection = companionMap.values()
                .stream()
                .filter(companion -> !companion.isSpawned())
                .toList();

        final List<Companion> spawnedCompanions = new ArrayList<>(collection.size());

        collection.forEach(companion -> {
            final CompanionType companionType = companion.type();
            final int entityId = companion.entityId();

            final Companion newCompanion = this.companionFactory.create(player, companionType, entityId);
            this.companionEntityCache.put(newCompanion);

            spawnedCompanions.add(newCompanion);
        });

        companionPlayer.equipAll(spawnedCompanions);

        for (final Companion newCompanion : spawnedCompanions) {
            this.companionEventBus.callSpawn(newCompanion);
        }
    }

    @Override
    public void equipVisual(
            final @NotNull Player player,
            final @NotNull String companionTypeId
    ) {
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return;
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        final Companion companionNull = companionMap.get(companionTypeId);

        if (companionNull != null && !companionNull.isSpawned()) {
            final CompanionType companionType = companionNull.type();
            final Companion companion = this.companionFactory.create(player, companionType, companionNull.entityId());
            companionPlayer.equip(companion);
            this.companionEntityCache.put(companion);
            this.companionEventBus.callSpawn(companion);
        }
    }

    @Override
    public @NotNull ResponseContext<Collection<String>, UnequipCompanionResult> unequip(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    ) {
        if (this.selectionChanges.contains(player.getUniqueId())) {
            return ResponseContext.failure(UnequipCompanionResult.CANCELLED);
        }
        final Map<String, Companion> companionMap = companionPlayer.companions();
        if (companionMap.isEmpty()) {
            return ResponseContext.failure(UnequipCompanionResult.NO_COMPANION_ACTIVE);
        }

        final List<String> removingCompanionIds = new ArrayList<>(companionMap.size());
        for (Map.Entry<String, Companion> entry : List.copyOf(companionMap.entrySet())) {
            final Companion companion = entry.getValue();

            if (!this.companionEventBus.callUnequip(player, companion)) {
                continue;
            }

            this.removeCompanion(companion);
            companionPlayer.unequip(entry.getKey());
            removingCompanionIds.add(entry.getKey());
        }

        if (removingCompanionIds.isEmpty()) {
            return ResponseContext.failure(UnequipCompanionResult.NONE_COMPANION_REMOVED);
        }

        return ResponseContext.success(removingCompanionIds, UnequipCompanionResult.SUCCESS);
    }

    @Override
    public @NotNull ResponseContext<CompanionPlayer, UnequipCompanionResult> unequip(
            final @NotNull Player player,
            final @NotNull String companionTypeId
    ) {
        if (this.selectionChanges.contains(player.getUniqueId())) {
            return ResponseContext.failure(UnequipCompanionResult.CANCELLED);
        }
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return ResponseContext.failure(UnequipCompanionResult.PLAYER_NOT_LOADED);
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        if (companionMap.isEmpty()) {
            return ResponseContext.failure(UnequipCompanionResult.NO_COMPANION_ACTIVE);
        }

        final Companion companion = companionMap.get(companionTypeId);
        if (companion == null) {
            return ResponseContext.failure(UnequipCompanionResult.NO_COMPANION_ACTIVE);
        }

        if (!this.companionEventBus.callUnequip(player, companion)) {
            return ResponseContext.failure(UnequipCompanionResult.CANCELLED);
        }

        this.removeCompanion(companion);
        companionPlayer.unequip(companionTypeId);

        return ResponseContext.success(companionPlayer, UnequipCompanionResult.SUCCESS);
    }

    @Override
    public @NotNull ResponseContext<CompanionPlayer, UnequipCompanionResult> unequip(final @NotNull Player player) {
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return ResponseContext.failure(UnequipCompanionResult.PLAYER_NOT_LOADED);
        }

        final Response<UnequipCompanionResult> response = this.unequip(player, companionPlayer);
        if (!response.success()) {
            return ResponseContext.failure(response.result());
        }

        return ResponseContext.success(companionPlayer, UnequipCompanionResult.SUCCESS);
    }

    private void removeCompanion(final @NotNull Companion companion) {
        this.companionEventBus.callDespawn(companion);
        this.companionEntityCache.remove(companion.entityId());
    }

    public SelectionChange prepareSelection(Player player, List<CompanionType> desired) {
        UUID playerId = player.getUniqueId();
        DefaultCompanionPlayer state = this.playerCache.getByUniqueId(playerId);
        if (state == null) throw new IllegalStateException("Companions are still loading");
        SelectionChange change = new SelectionChange(player, state, desired);
        if (!this.selectionChanges.add(playerId)) throw new IllegalStateException("A companion selection is already being saved");
        try {
            change.prepare();
            return change;
        } catch (RuntimeException | Error failure) {
            try { change.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public final class SelectionChange implements AutoCloseable {
        private final Player player;
        private final DefaultCompanionPlayer state;
        private final Map<String, Companion> before;
        private final Map<String, CompanionType> desired = new LinkedHashMap<>();
        private final Map<String, Companion> prepared = new LinkedHashMap<>();
        private boolean applied;
        private boolean finished;

        private SelectionChange(Player player, DefaultCompanionPlayer state, List<CompanionType> desired) {
            this.player = player;
            this.state = state;
            this.before = Map.copyOf(state.companions());
            for (CompanionType type : desired) {
                if (this.desired.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate companion");
            }
        }

        private void prepare() {
            if (desired.size() > 1 && desired.values().stream().anyMatch(CompanionType::exclusive)) {
                throw new IllegalArgumentException("Exclusive companions must be equipped alone");
            }
            for (Companion companion : before.values()) {
                if (!desired.containsKey(companion.type().id()) && !companionEventBus.callUnequip(player, companion, true)) {
                    throw new SelectionCancelledException();
                }
            }
            for (CompanionType type : desired.values()) {
                if (!before.containsKey(type.id()) && !companionEventBus.callEquip(player, type, true)) {
                    throw new SelectionCancelledException();
                }
            }
            if (!state.companions().equals(before)) throw new IllegalStateException("Companion selection changed during validation");
            for (CompanionType type : desired.values()) {
                if (!before.containsKey(type.id())) prepared.put(type.id(), companionFactory.create(player, type));
            }
        }

        public void apply() {
            if (finished || applied) throw new IllegalStateException("Selection change is no longer available");
            applied = true;
            for (Companion companion : before.values()) {
                if (!desired.containsKey(companion.type().id())) {
                    removeCompanion(companion);
                    state.unequip(companion.type().id());
                }
            }
            for (Companion companion : prepared.values()) {
                state.equip(companion);
                companionEntityCache.put(companion);
                companionEventBus.callSpawn(companion);
            }
            companionEventBus.callSelectionChanged(player, state, List.copyOf(before.keySet()));
        }

        public void restore() {
            if (!applied || finished) return;
            List<String> previousIds = List.copyOf(state.companions().keySet());
            RuntimeException failure = null;
            for (Companion companion : List.copyOf(state.companions().values())) {
                if (!before.containsKey(companion.type().id())) {
                    try {
                        removeCompanion(companion);
                        state.unequip(companion.type().id());
                    } catch (RuntimeException problem) {
                        if (failure == null) failure = problem; else failure.addSuppressed(problem);
                    }
                }
            }
            for (Companion previous : before.values()) {
                if (!state.hasCompanion(previous.type().id())) {
                    try {
                        Companion restored = companionFactory.create(player, previous.type());
                        state.equip(restored);
                        companionEntityCache.put(restored);
                        companionEventBus.callSpawn(restored);
                    } catch (RuntimeException problem) {
                        if (failure == null) failure = problem; else failure.addSuppressed(problem);
                    }
                }
            }
            companionEventBus.callSelectionChanged(player, state, previousIds);
            if (failure != null) throw failure;
        }

        public List<String> selectedIds() {
            return List.copyOf(state.companions().keySet());
        }

        @Override
        public void close() {
            if (finished) return;
            finished = true;
            selectionChanges.remove(player.getUniqueId());
            for (Companion companion : prepared.values()) {
                if (state.companions().get(companion.type().id()) != companion) companion.destroy();
            }
        }
    }

    public static final class SelectionCancelledException extends IllegalStateException {
        public SelectionCancelledException() {
            super("Another plugin cancelled the companion change");
        }
    }

    @Override
    public void unequipVisual(final @NotNull Player player) {
        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return;
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        if (companionMap.isEmpty()) {
            return;
        }

        final Set<Map.Entry<String, Companion>> entrySet = companionMap.entrySet();
        for (final Map.Entry<String, Companion> entry : entrySet) {
            final Companion companion = entry.getValue();
            if (companion != null) {
                companion.destroy();
            }
        }
    }

    @Override
    public void unequipVisual(
            final @NotNull Player player,
            final @NotNull String companionTypeId
    ) {

        final CompanionPlayer companionPlayer = this.playerCache.getByUniqueId(player.getUniqueId());
        if (companionPlayer == null) {
            return;
        }

        final Map<String, Companion> companionMap = companionPlayer.companions();
        if (companionMap.isEmpty()) {
            return;
        }

        final Companion companion = companionMap.get(companionTypeId);
        if (companion != null) {
            companion.destroy();
        }

    }

    @Override
    public @NotNull @Unmodifiable Collection<Companion> all() {
        return this.playerCache.all()
                .stream()
                .map(companionPlayer -> companionPlayer.companions().values())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .toList();
    }

}
