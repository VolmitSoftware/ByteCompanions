package com.volmit.bytecompanions.features.particles.manager;

import org.slf4j.LoggerFactory;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.features.particles.FeaturePermission;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectEntry;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectRegistry;
import com.volmit.bytecompanions.features.particles.storage.entry.UserEntry;
import com.volmit.bytecompanions.features.particles.storage.view.UserView;
import com.volmit.bytecompanions.features.particles.util.BukkitUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public final class PlayerEffectManager {

    // ── Cache ──
    // playerId -> (companionTypeId -> effectId).
    // Anidado en vez de Map<EffectKey, String> a propósito: hasEquipped/hasAnyEquipped
    // se llaman por slot/condición en cada apertura o refresco de menú, así que necesitan
    // resolver "¿este jugador tiene algo equipado?" en O(1) + iteración acotada a SUS
    // companions, sin recorrer el cache completo de todos los jugadores online.
    //
    // El mapa externo es ConcurrentHashMap porque computeIfAbsent/remove de distintos
    // jugadores puede solaparse entre el hilo principal y callbacks async de storage.
    // El mapa interno es un HashMap plano: equip/unequip/load corren siempre en el hilo
    // principal (ver load()), así que no hay escritura concurrente real sobre él y no
    // vale la pena pagar el overhead de sincronización ahí.
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pendingLoads = new ConcurrentHashMap<>();

    private final ParticlesFeature feature;
    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private volatile boolean active = true;
    private SelectionHandler selectionHandler;

    public interface SelectionHandler {
        boolean managed(UUID playerId, String companionId);
        void select(Player player, String companionId, String effectId);
        void clear(Player player, List<String> companionIds);
    }

    public void selectionHandler(SelectionHandler handler) {
        this.selectionHandler = handler;
    }

    public String selected(UUID playerId, String companionId) {
        Map<String, String> effects = this.cache.get(playerId);
        return effects == null ? null : effects.get(companionId);
    }

    public boolean loading(UUID playerId) {
        return this.pendingLoads.containsKey(playerId);
    }

    public void applySelection(Player player, Companion companion, String effectId) {
        this.detach(player, companion);
        if (effectId == null) return;
        ParticleEffectEntry entry = this.feature.particleEffectRegistry().get(effectId);
        if (entry == null || !player.hasPermission(FeaturePermission.resolveEffect(effectId))) {
            throw new IllegalArgumentException("Particle effect is unavailable: " + effectId);
        }
        companion.animations().remove(AnimationChannel.POST_TRACKING, effectId);
        this.equip(player, companion, entry, false, true);
        if (!effectId.equals(this.selected(player.getUniqueId(), companion.type().id()))) {
            throw new IllegalStateException("Particle effect could not be applied: " + effectId);
        }
    }

    public PlayerEffectManager(ParticlesFeature feature, JavaPlugin plugin, ByteCompanions companions) {
        this.feature = feature;
        this.plugin = plugin;
        this.companions = companions;
    }

    // ── Load / Unload ──

    public void load(final @NotNull Player player, final @NotNull CompanionPlayer companionPlayer) {
        UUID playerId = player.getUniqueId();
        Object session = new Object();
        this.pendingLoads.put(playerId, session);
        this.feature.userStorage().async().find(playerId)
                .thenAccept(userViews -> {
                    if (!this.active || this.pendingLoads.get(playerId) != session) {
                        return;
                    }

                    FoliaScheduler.runEntity(this.plugin, player, () -> {
                        if (!this.active || !player.isOnline() || !this.pendingLoads.remove(playerId, session)
                                || this.companions.getCompanionPlayerOrNull(player) != companionPlayer) {
                            return;
                        }

                        final ParticleEffectRegistry particleEffectRegistry = this.feature.particleEffectRegistry();

                        for (final UserView userView : userViews) {
                            if (this.selectionHandler != null && this.selectionHandler.managed(playerId, userView.companionTypeId())) continue;
                            final Companion companion = companionPlayer.companions().get(userView.companionTypeId());
                            if (companion == null) {
                                continue;
                            }

                            final ParticleEffectEntry particleEffectEntry = particleEffectRegistry.get(userView.effectId());
                            if (particleEffectEntry == null) {
                                continue;
                            }

                            this.equip(player, companion, particleEffectEntry, false, true);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    this.pendingLoads.remove(playerId, session);
                    LoggerFactory.getLogger(this.plugin.getName()).error("Could not load particle selections for {}", playerId, throwable);
                    return null;
                });
    }

    public void unload(final @NotNull Player player) {
        this.pendingLoads.remove(player.getUniqueId());
        this.cache.remove(player.getUniqueId());
    }

    public void onCompanionSpawn(Companion companion) {
        if (!this.active || companion.ownerData() == null) {
            return;
        }
        Map<String, String> equipped = this.cache.get(companion.ownerData().uniqueId());
        String effectId = equipped == null ? null : equipped.get(companion.type().id());
        if (effectId == null || companion.animations().contains(AnimationChannel.POST_TRACKING, effectId)) {
            return;
        }
        ParticleEffectEntry entry = this.feature.particleEffectRegistry().get(effectId);
        if (entry != null) {
            BukkitUtil.getPlayer(companion.ownerData().uniqueId())
                    .ifPresent(player -> this.equip(player, companion, entry, false, true));
        }
    }

    public boolean hasEquipped(final @NotNull Player player, final @NotNull String particleEffectId) {
        final Map<String, String> equipped = this.cache.get(player.getUniqueId());
        return equipped != null && equipped.containsValue(particleEffectId);
    }

    /**
     * Point-lookup por companion específico. Preferí este overload frente al de arriba
     * cuando el menú ya sepa sobre qué companion está preguntando (por ejemplo, un slot
     * dentro de la vista de un companion puntual) — evita el containsValue sobre todos
     * los companions equipados del jugador.
     */
    public boolean hasEquipped(final @NotNull Player player, final @NotNull String companionTypeId, final @NotNull String particleEffectId) {
        final Map<String, String> equipped = this.cache.get(player.getUniqueId());
        return equipped != null && particleEffectId.equals(equipped.get(companionTypeId));
    }

    public boolean hasAnyEquipped(final @NotNull Player player) {
        final Map<String, String> equipped = this.cache.get(player.getUniqueId());
        return equipped != null && !equipped.isEmpty();
    }

    public void equip(final @NotNull Player player, final @NotNull String companionTypeId, final @NotNull ParticleEffectEntry particleEffectEntry) {
        this.equip(player, companionTypeId, particleEffectEntry, true, false);
    }

    public void equip(
            final @NotNull Player player,
            final @NotNull String companionTypeId,
            final @NotNull ParticleEffectEntry particleEffectEntry,
            final boolean saveStorage,
            final boolean silent
    ) {
        final CompanionPlayer companionPlayer = this.companions.getCompanionPlayerOrNull(player);
        if (companionPlayer == null) {
            return;
        }

        final Companion companion = companionPlayer.companions().get(companionTypeId);
        this.equip(player, companion, particleEffectEntry, saveStorage, silent);
    }

    public void equip(
            final @NotNull Player player,
            final @Nullable Companion companion,
            final @NotNull ParticleEffectEntry particleEffectEntry,
            final boolean saveStorage,
            final boolean silent
    ) {
        if (!this.active) {
            return;
        }
        if (companion == null) {
            this.feature.messageSender().send(player, "companion.not-equipped");
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (saveStorage && this.selectionHandler != null) {
            this.selectionHandler.select(player, companion.type().id(), particleEffectEntry.id());
            return;
        }

        final String particleEffectId = particleEffectEntry.id();
        final String effectPermission = FeaturePermission.resolveEffect(particleEffectId);
        if (!player.hasPermission(effectPermission)) {
            this.feature.messageSender().send(player, "particle-effects.no-permission", particleEffectId);
            return;
        }

        if (companion.animations().contains(AnimationChannel.POST_TRACKING, particleEffectId)) {
            this.feature.messageSender().send(player, "particle-effects.already-equipped", particleEffectId);
            return;
        }

        this.detach(player, companion);
        if (saveStorage) {
            this.pendingLoads.remove(player.getUniqueId());
        }
        final ParticleEffect particleEffect = particleEffectEntry.factory().get();
        try {
            companion.animations().add(particleEffectId, new EffectTrackingAnimation(particleEffect, this.feature.particlesConfiguration().settings().movement(), this.feature.viewerEnvironment(), this.feature::active));
            this.cache.computeIfAbsent(
                            player.getUniqueId(), id -> new HashMap<>())
                    .put(companion.type().id(), particleEffectId);

            if (saveStorage) {
                this.reportPersistence(player,
                        this.feature.userStorage().async().save(UserEntry.fromPlayer(player, companion.type().id(), particleEffectId)),
                        () -> this.reportEquipped(player, particleEffectId, silent));
            } else {
                this.reportEquipped(player, particleEffectId, silent);
            }

        } catch (final IllegalStateException e) {
            // Siempre logueamos, aunque sea silent=true — si no, un fallo en load() (reconexión)
            // no deja ningún rastro y parece que "no pasa nada".
            LoggerFactory.getLogger(this.plugin.getName()).warn(
                    "Failed to equip particle effect '{}' on companion '{}' for {}: {}",
                    particleEffectId, companion.type().id(), player.getName(), e.getMessage(), e
            );

            if (!silent) {
                this.feature.messageSender().send(player, "particle-effects.already-equipped", particleEffectId);
                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }

    // ── Unequip ──

    public CompletableFuture<Void> detachAllAsync() {
        List<CompletableFuture<Void>> cleanupTasks = new ArrayList<>();
        this.active = false;
        this.pendingLoads.clear();
        for (final UUID playerId : this.cache.keySet()) {
            BukkitUtil.getPlayer(playerId).ifPresent(player -> {
                Runnable cleanup = () -> {
                final CompanionPlayer companionPlayer = this.companions.getCompanionPlayerOrNull(player);
                if (companionPlayer == null) {
                    return;
                }

                final Map<String, String> equipped = this.cache.get(playerId);
                if (equipped == null) {
                    return;
                }

                // Copia defensiva de las keys: unequip() muta `equipped` (el mapa interno)
                // mientras iteramos, así que no podemos recorrerlo en vivo.
                for (final String companionTypeId : new HashMap<>(equipped).keySet()) {
                    final Companion companion = companionPlayer.companions().get(companionTypeId);
                    if (companion == null) {
                        continue;
                    }

                    this.detach(player, companion);
                }
                };
                cleanupTasks.add(BukkitOwnerTasks.entity(this.plugin, player, cleanup));
            });
        }
        return CompletableFuture.allOf(cleanupTasks.toArray(CompletableFuture[]::new));
    }

    /**
     * Unequips the active particle effect from every companion the player owns.
     */
    public boolean unequip(final @NotNull Player player) {
        final CompanionPlayer companionPlayer = this.companions.getCompanionPlayerOrNull(player);
        if (companionPlayer == null) {
            return false;
        }

        if (this.selectionHandler != null) {
            List<String> selected = companionPlayer.companions().keySet().stream()
                    .filter(id -> this.selected(player.getUniqueId(), id) != null).toList();
            if (selected.isEmpty()) return false;
            this.selectionHandler.clear(player, selected);
            return true;
        }

        boolean unequippedAny = false;
        for (final Companion companion : companionPlayer.companions().values()) {
            if (this.unequip(player, companion, true)) {
                unequippedAny = true;
            }
        }

        return unequippedAny;
    }

    public boolean unequip(final @NotNull Player player, final @NotNull String companionTypeId) {
        final CompanionPlayer companionPlayer = this.companions.getCompanionPlayerOrNull(player);
        if (companionPlayer == null) {
            return false;
        }

        final Companion companion = companionPlayer.companions().get(companionTypeId);
        if (companion == null) {
            this.feature.messageSender().send(player, "companion.not-equipped");
            return false;
        }

        return this.unequip(player, companion, false);
    }

    public boolean unequip(
            final @NotNull Player player,
            final @NotNull Companion companion,
            final boolean silent
    ) {
        if (this.selectionHandler != null) {
            this.selectionHandler.select(player, companion.type().id(), null);
            return true;
        }
        this.pendingLoads.remove(player.getUniqueId());
        String previous = this.detach(player, companion);
        if (previous == null) {
            return false;
        }
        this.reportPersistence(player, this.feature.userStorage().async().delete(UserEntry.fromPlayer(player, companion.type().id())),
                () -> {
                    if (!silent) {
                        this.feature.messageSender().send(player, "particle-effects.unequipped", previous);
                    }
                });
        return true;
    }

    private void reportEquipped(Player player, String effectId, boolean silent) {
        if (!silent) {
            this.feature.messageSender().send(player, "particle-effects.equipped", effectId);
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    private void reportPersistence(Player player, CompletableFuture<?> operation, Runnable success) {
        CompanionPlayer session = this.companions.getCompanionPlayerOrNull(player);
        operation.whenComplete((ignored, failure) -> {
            if (session == null || !this.active || !this.plugin.isEnabled()) {
                return;
            }
            FoliaScheduler.runEntity(this.plugin, player, () -> {
                if (!this.active || !player.isOnline() || this.companions.getCompanionPlayerOrNull(player) != session) {
                    return;
                }
                if (failure == null) {
                    success.run();
                } else {
                    this.feature.messageSender().send(player, "general.persistence-failed");
                }
            });
        });
    }

    private String detach(Player player, Companion companion) {
        Map<String, String> equipped = this.cache.get(player.getUniqueId());
        String previous = equipped == null ? null : equipped.remove(companion.type().id());
        if (previous == null) {
            return null;
        }
        if (equipped.isEmpty()) {
            this.cache.remove(player.getUniqueId());
        }
        if (companion.animations().contains(AnimationChannel.POST_TRACKING, previous)) {
            companion.animations().remove(AnimationChannel.POST_TRACKING, previous);
        }
        return previous;
    }

}
