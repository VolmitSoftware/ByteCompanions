package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSpawnEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.manager.PlayerEffectManager;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

public final class CollectionService implements Listener {
    public enum Status { SUCCESS, INVALID, NOT_READY, BUSY, CANCELLED, FAILED }
    public record Result(Status status, CollectionFailure failure) {
        public boolean success() { return status == Status.SUCCESS; }
    }
    public interface StyleAdapter {
        void validate(Player player, String companionId, CompanionStyle style);
        void apply(Player player, Companion companion, CompanionStyle style);
        void order(Player player, List<String> companionIds);
    }

    private final PaperPlugin plugin;
    private final CollectionStore store;
    private final SQLUserStorage selections;
    private final DefaultCompanionManager manager;
    private final Configuration.CompanionLimits limits;
    private final ParticlesFeature particles;
    private final StyleAdapter adapter;
    private final int maximumOutfits;
    private final BooleanSupplier runtime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> ready;
    private volatile boolean closed;

    public CollectionService(PaperPlugin plugin, SQLStorageConnection connection, Executor executor,
                             UserStorage selections, DefaultCompanionManager manager,
                             Configuration.CompanionLimits limits, ParticlesFeature particles,
                             int maximumOutfits, StyleAdapter adapter) {
        this.plugin = plugin;
        this.store = new CollectionStore(connection);
        if (!(selections instanceof SQLUserStorage sql)) throw new IllegalArgumentException("Collections require SQL user storage");
        this.selections = sql;
        this.manager = manager;
        this.limits = limits;
        this.particles = particles;
        this.adapter = adapter;
        if (maximumOutfits < 1 || maximumOutfits > 100) throw new IllegalArgumentException("Outfit limit must be 1–100");
        this.maximumOutfits = maximumOutfits;
        this.runtime = plugin.captureRuntimeGuard();
        this.ready = track(CompletableFuture.runAsync(() -> {
            try { store.initialize(); } catch (SQLException failure) { throw new CompletionException(failure); }
        }, executor));
        if (particles != null) {
            particles.playerEffectManager().selectionHandler(new PlayerEffectManager.SelectionHandler() {
                @Override public boolean managed(UUID playerId, String companionId) {
                    return profile(playerId).map(value -> value.styles().containsKey(companionId)).orElse(false);
                }
                @Override public void select(Player player, String companionId, String effectId) {
                    String previous = particles.playerEffectManager().selected(player.getUniqueId(), companionId);
                    particleFeedback(player, updateParticle(player, companionId, effectId), effectId, previous);
                }
                @Override public void clear(Player player, List<String> companionIds) {
                    List<String> ids = List.copyOf(companionIds);
                    String previous = ids.stream().map(id -> particles.playerEffectManager().selected(player.getUniqueId(), id))
                            .filter(value -> value != null).distinct().sorted().reduce((first, second) -> first + ", " + second).orElse("");
                    CompletableFuture<Result> operation = edit(player, "bytecompanions.command.particles.unequip", profile -> {
                        CompanionPlayer state = requirePlayer(player);
                        Map<String, CompanionStyle> styles = new HashMap<>(profile.styles());
                        for (String id : ids) {
                            if (!state.hasCompanion(id)) throw CollectionFailure.of(CollectionFailure.Reason.EQUIP_FIRST);
                            CompanionStyle style = currentStyle(player, profile, id).withParticle(null);
                            validateParticle(player, id, style.particleId());
                            styles.put(id, style);
                        }
                        return profile.next(profile.favorites(), profile.outfits(), styles, profile.formationOrder());
                    }, true, null);
                    particleFeedback(player, operation, null, previous);
                }
            });
        }
    }

    public CompletableFuture<Void> initialize() { return ready.copy(); }

    private void particleFeedback(Player player, CompletableFuture<Result> operation, String selected, String previous) {
        UUID playerId = player.getUniqueId();
        Session session = sessions.get(playerId);
        operation.thenAccept(result -> BukkitOwnerTasks.entity(plugin, player, () -> {
            if (!active(player) || sessions.get(playerId) != session) return;
            if (!result.success()) {
                plugin.languageService().send(player, "gui.collection-operation-failed", "reason", result.failure().render(plugin.languageService(), player));
            } else if (selected != null) {
                particles.messageSender().send(player, "particle-effects.equipped", selected);
                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
            } else if (previous != null) {
                particles.messageSender().send(player, "particle-effects.unequipped", previous);
            }
        }));
    }

    public Optional<CollectionProfile> profile(UUID playerId) {
        Session session = sessions.get(playerId);
        return session == null ? Optional.empty() : Optional.ofNullable(session.profile);
    }

    public ViewerPreferences viewerPreferences(UUID playerId) {
        return profile(playerId).map(CollectionProfile::viewerPreferences).orElseGet(ViewerPreferences::loading);
    }

    public CompletableFuture<Result> updateViewerPreferences(Player player, UnaryOperator<ViewerPreferences> mutation) {
        return edit(player, "bytecompanions.command.settings",
                previous -> previous.withViewerPreferences(mutation.apply(previous.viewerPreferences())), false, null);
    }

    public CompanionStyle style(Player player, String companionId) {
        CollectionProfile profile = profile(player.getUniqueId()).orElseThrow(() -> CollectionFailure.of(CollectionFailure.Reason.LOADING));
        return currentStyle(player, profile, companionId);
    }

    public CompletableFuture<CollectionProfile> load(UUID playerId) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Collections are stopping"));
        Session session = new Session();
        Session existing = sessions.putIfAbsent(playerId, session);
        if (existing != null) return existing.loaded.copy();
        track(ready.thenCompose(ignored -> selections.submit(playerId, () -> {
            try { return store.load(playerId); } catch (SQLException failure) { throw new CompletionException(failure); }
        }))).whenComplete((profile, failure) -> {
            if (failure != null) session.loaded.completeExceptionally(failure);
            else if (!closed && sessions.get(playerId) == session) {
                session.profile = profile;
                session.loaded.complete(profile);
            } else session.loaded.completeExceptionally(new IllegalStateException("Player session ended"));
        });
        return session.loaded.copy();
    }

    public void unload(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null && session.change != null) session.change.close();
    }

    public CompletableFuture<Result> toggleFavorite(Player player, String companionId) {
        return edit(player, "bytecompanions.command.menu", previous -> {
            type(companionId);
            Set<String> favorites = new HashSet<>(previous.favorites());
            if (!favorites.remove(companionId)) favorites.add(companionId);
            return previous.next(favorites, previous.outfits(), previous.styles(), previous.formationOrder());
        }, false, null);
    }

    public CompletableFuture<Result> saveOutfit(Player player, String name) {
        return edit(player, "bytecompanions.command.outfits", previous -> {
            requireParticlesReady(player);
            String key = Outfit.key(name);
            if (!previous.outfits().containsKey(key) && previous.outfits().size() >= maximumOutfits) {
                throw new CollectionFailure(CollectionFailure.Reason.OUTFIT_LIMIT, Map.of("limit", Integer.toString(maximumOutfits)));
            }
            CompanionPlayer state = requirePlayer(player);
            List<String> ids = ordered(state.companions().keySet(), previous.formationOrder());
            Map<String, CompanionStyle> styles = new HashMap<>();
            for (String id : ids) styles.put(id, currentStyle(player, previous, id));
            Map<String, Outfit> outfits = new HashMap<>(previous.outfits());
            outfits.put(key, new Outfit(key, ids, styles, ids));
            return previous.next(previous.favorites(), outfits, previous.styles(), previous.formationOrder());
        }, false, null);
    }

    public CompletableFuture<Result> deleteOutfit(Player player, String name) {
        return edit(player, "bytecompanions.command.outfits", previous -> {
            Map<String, Outfit> outfits = new HashMap<>(previous.outfits());
            if (outfits.remove(Outfit.key(name)) == null) throw CollectionFailure.of(CollectionFailure.Reason.OUTFIT_UNAVAILABLE);
            return previous.next(previous.favorites(), outfits, previous.styles(), previous.formationOrder());
        }, false, null);
    }

    public CompletableFuture<Result> updateStyle(Player player, String companionId, CompanionStyle style) {
        return updateStyle(player, companionId, style, "bytecompanions.command.personalize");
    }

    private CompletableFuture<Result> updateParticle(Player player, String companionId, String effectId) {
        CollectionProfile profile = profile(player.getUniqueId()).orElse(null);
        if (profile == null) return result(Status.NOT_READY, CollectionFailure.of(CollectionFailure.Reason.LOADING));
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        if (state == null || !state.hasCompanion(companionId)) return result(Status.INVALID, CollectionFailure.of(CollectionFailure.Reason.EQUIP_FIRST));
        CompanionStyle next = currentStyle(player, profile, companionId).withParticle(effectId);
        return edit(player, "bytecompanions.command.particles." + (effectId == null ? "unequip" : "equip"), previous -> {
            validateParticle(player, companionId, effectId);
            Map<String, CompanionStyle> styles = new HashMap<>(previous.styles());
            styles.put(companionId, next);
            return previous.next(previous.favorites(), previous.outfits(), styles, previous.formationOrder());
        }, true, null);
    }

    private CompletableFuture<Result> updateStyle(Player player, String companionId, CompanionStyle style, String permission) {
        return edit(player, permission, previous -> {
            validateStyle(player, companionId, style);
            Map<String, CompanionStyle> styles = new HashMap<>(previous.styles());
            styles.put(companionId, style);
            return previous.next(previous.favorites(), previous.outfits(), styles, previous.formationOrder());
        }, true, null);
    }

    public CompletableFuture<Result> updateFormationOrder(Player player, List<String> order) {
        List<String> requested = List.copyOf(order);
        return edit(player, "bytecompanions.command.personalize", previous -> {
            if (!requirePlayer(player).companions().keySet().containsAll(requested)) {
                throw CollectionFailure.of(CollectionFailure.Reason.FORMATION_ORDER);
            }
            return previous.next(previous.favorites(), previous.outfits(), previous.styles(), requested);
        }, true, null);
    }

    public CompletableFuture<Result> applyOutfit(Player player, String name) {
        String key;
        try { key = Outfit.key(name); } catch (CollectionFailure failure) { return result(Status.INVALID, failure); }
        CollectionProfile current = profile(player.getUniqueId()).orElse(null);
        Outfit outfit = current == null ? null : current.outfits().get(key);
        if (outfit == null) return result(current == null ? Status.NOT_READY : Status.INVALID, CollectionFailure.of(CollectionFailure.Reason.OUTFIT_UNAVAILABLE));
        return edit(player, "bytecompanions.command.outfits", previous -> {
            validateOutfit(player, outfit);
            Map<String, CompanionStyle> styles = new HashMap<>(previous.styles());
            for (String id : outfit.companionIds()) styles.put(id, outfit.styles().getOrDefault(id, CompanionStyle.defaults()));
            return previous.next(previous.favorites(), previous.outfits(), styles, outfit.formationOrder());
        }, true, outfit);
    }

    private CompletableFuture<Result> edit(Player player, String permission, UnaryOperator<CollectionProfile> edit,
                                           boolean apply, Outfit outfit) {
        UUID playerId = player.getUniqueId();
        Session session = sessions.get(playerId);
        if (!active(player) || session == null || session.profile == null) return result(Status.NOT_READY, CollectionFailure.of(CollectionFailure.Reason.LOADING));
        if ((!permission.startsWith("bytecompanions.command.particles.") && !permission.equals("bytecompanions.command.settings")
                && !player.hasPermission("bytecompanions.command.menu"))
                || !player.hasPermission(permission)) return result(Status.INVALID, CollectionFailure.of(CollectionFailure.Reason.NO_PERMISSION));
        synchronized (session) {
            if (session.busy) return result(Status.BUSY, CollectionFailure.of(CollectionFailure.Reason.BUSY));
            session.busy = true;
        }
        CollectionProfile previous = session.profile;
        CollectionProfile restoreProfile = previous;
        CollectionProfile next;
        UserEntry selection = null;
        boolean runtimeTouched = false;
        boolean orderTouched = false;
        boolean particlesOnly = permission.startsWith("bytecompanions.command.particles.");
        Set<String> touchedStyles = new HashSet<>();
        try {
            if (apply) requireParticlesReady(player);
            if (apply) {
                Map<String, CompanionStyle> restoredStyles = new HashMap<>(previous.styles());
                for (String id : requirePlayer(player).companions().keySet()) restoredStyles.put(id, currentStyle(player, previous, id));
                restoreProfile = new CollectionProfile(previous.revision(), previous.favorites(), previous.outfits(), restoredStyles,
                        previous.formationOrder(), previous.viewerPreferences());
            }
            next = edit.apply(previous);
            if (apply) session.appliedProfile = next;
            if (outfit != null) {
                List<CompanionType> types = outfit.companionIds().stream().map(this::type).toList();
                session.change = manager.prepareSelection(player, types);
                runtimeTouched = true;
                touchedStyles.addAll(outfit.companionIds());
                orderTouched = true;
                session.change.apply();
                selection = new UserEntry(playerId, player.getName(), session.change.selectedIds());
            }
            if (apply) {
                for (Companion companion : requirePlayer(player).companions().values()) {
                    String id = companion.type().id();
                    CompanionStyle style = currentStyle(player, next, id);
                    if (outfit != null || !Objects.equals(style, currentStyle(player, restoreProfile, id))) {
                        runtimeTouched = true;
                        touchedStyles.add(id);
                        if (particlesOnly) particles.playerEffectManager().applySelection(player, companion, style.particleId());
                        else applyCompanion(player, companion, style, false);
                    }
                }
                if (outfit != null || !previous.formationOrder().equals(next.formationOrder())) {
                    runtimeTouched = true;
                    orderTouched = true;
                    adapter.order(player, next.formationOrder());
                }
            }
        } catch (RuntimeException failure) {
            if (runtimeTouched) restore(player, session, restoreProfile, touchedStyles, orderTouched, particlesOnly, failure);
            finish(session);
            if (failure instanceof CollectionFailure rejected) return result(Status.INVALID, rejected);
            if (failure instanceof DefaultCompanionManager.SelectionCancelledException) {
                return result(Status.CANCELLED, CollectionFailure.of(CollectionFailure.Reason.CANCELLED));
            }
            plugin.getLogger().log(Level.SEVERE, "Could not update companion collection for " + playerId, failure);
            return result(Status.FAILED, CollectionFailure.of(CollectionFailure.Reason.FAILED));
        }
        UserEntry capturedSelection = selection;
        CollectionProfile capturedRestore = restoreProfile;
        Set<String> capturedStyles = Set.copyOf(touchedStyles);
        boolean capturedOrder = orderTouched;
        CompletableFuture<Result> result = new CompletableFuture<>();
        track(selections.submit(playerId, () -> {
            try { store.save(playerId, previous, next, selections, capturedSelection); return next; }
            catch (SQLException failure) { throw new CompletionException(failure); }
        })).whenComplete((saved, failure) -> {
            if (failure == null) session.profile = saved;
            else plugin.getLogger().log(Level.SEVERE, "Could not save collection for " + playerId, failure);
            if (closed || !runtime.getAsBoolean() || sessions.get(playerId) != session) {
                result.complete(new Result(failure == null ? Status.SUCCESS : Status.FAILED,
                        failure == null ? null : CollectionFailure.of(CollectionFailure.Reason.SAVE_FAILED)));
                return;
            }
            BukkitOwnerTasks.entity(plugin, player, () -> {
                if (!active(player) || sessions.get(playerId) != session) return;
                if (failure != null) {
                    restore(player, session, capturedRestore, capturedStyles, capturedOrder, particlesOnly, failure);
                }
                finish(session);
                result.complete(new Result(failure == null ? Status.SUCCESS : Status.FAILED,
                        failure == null ? null : CollectionFailure.of(CollectionFailure.Reason.SAVE_FAILED)));
            }).whenComplete((ignored, ownerFailure) -> {
                if (!result.isDone()) result.complete(new Result(failure == null ? Status.SUCCESS : Status.FAILED,
                        failure == null ? null : CollectionFailure.of(CollectionFailure.Reason.SAVE_FAILED)));
            });
        });
        return result;
    }

    private void restore(Player player, Session session, CollectionProfile previous, Set<String> touchedStyles,
                         boolean orderTouched, boolean particlesOnly, Throwable failure) {
        try {
            session.appliedProfile = previous;
            if (session.change != null) session.change.restore();
            CompanionPlayer current = plugin.getCompanionPlayerOrNull(player);
            if (current != null) {
                for (String id : touchedStyles) {
                    Companion companion = current.companions().get(id);
                    if (companion != null) {
                        CompanionStyle style = currentStyle(player, previous, id);
                        if (particlesOnly) particles.playerEffectManager().applySelection(player, companion, style.particleId());
                        else applyCompanion(player, companion, style, true);
                    }
                }
            }
            if (orderTouched) adapter.order(player, previous.formationOrder());
        } catch (RuntimeException restoreFailure) {
            failure.addSuppressed(restoreFailure);
            plugin.getLogger().log(Level.SEVERE, "Could not fully restore companion visuals for " + player.getUniqueId(), failure);
            CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
            if (state != null) track(selections.async().save(new UserEntry(player.getUniqueId(), player.getName(), state.companions().keySet())));
        }
    }

    private void finish(Session session) {
        try {
            if (session.change != null) session.change.close();
        } finally {
            session.change = null;
            session.appliedProfile = null;
            session.busy = false;
        }
    }

    private void validateOutfit(Player player, Outfit outfit) {
        CompanionPlayer state = requirePlayer(player);
        if (!player.hasPermission("bytecompanions.command.spawn") && !state.companions().keySet().containsAll(outfit.companionIds())) {
            throw CollectionFailure.of(CollectionFailure.Reason.NO_PERMISSION);
        }
        if (!player.hasPermission("bytecompanions.command.despawn") && !outfit.companionIds().containsAll(state.companions().keySet())) {
            throw CollectionFailure.of(CollectionFailure.Reason.NO_PERMISSION);
        }
        if (limits.maxTotalPerPlayer() > 0 && outfit.companionIds().size() > limits.maxTotalPerPlayer()) {
            throw CollectionFailure.of(CollectionFailure.Reason.COMPANION_LIMIT);
        }
        Map<CompanionModel, Integer> counts = new HashMap<>();
        for (String id : outfit.companionIds()) {
            CompanionType type = type(id);
            if (type.exclusive() && outfit.companionIds().size() > 1) throw CollectionFailure.of(CollectionFailure.Reason.EXCLUSIVE);
            if (type.animations().isEmpty()) throw CollectionFailure.of(CollectionFailure.Reason.NO_ANIMATIONS);
            counts.merge(type.model(), 1, Integer::sum);
            validateStyle(player, id, outfit.styles().getOrDefault(id, CompanionStyle.defaults()));
        }
        if (limits.perModel().fullBody() > 0 && counts.getOrDefault(CompanionModel.FULL_BODY, 0) > limits.perModel().fullBody()
                || limits.perModel().headOnly() > 0 && counts.getOrDefault(CompanionModel.HEAD, 0) > limits.perModel().headOnly()) {
            throw CollectionFailure.of(CollectionFailure.Reason.MODEL_LIMIT);
        }
    }

    private void validateStyle(Player player, String companionId, CompanionStyle style) {
        type(companionId);
        if (!player.hasPermission("bytecompanions.companion." + companionId)) throw CollectionFailure.of(CollectionFailure.Reason.COMPANION_LOCKED);
        if ((style.nickname() != null || style.variantId() != null || style.formationId() != null)
                && !player.hasPermission("bytecompanions.command.personalize")) throw CollectionFailure.of(CollectionFailure.Reason.PERSONALIZATION_LOCKED);
        adapter.validate(player, companionId, style);
        validateParticle(player, companionId, style.particleId());
    }

    private void validateParticle(Player player, String companionId, String particleId) {
        String previous = particles == null ? null : particles.playerEffectManager().selected(player.getUniqueId(), companionId);
        if (particleId != null) {
            if (particles == null || particles.particleEffectRegistry().get(particleId) == null) throw CollectionFailure.of(CollectionFailure.Reason.PARTICLE_UNAVAILABLE);
            if (!player.hasPermission("bytecompanions.particle." + particleId)
                    || !player.hasPermission("bytecompanions.command.particles.equip")) throw CollectionFailure.of(CollectionFailure.Reason.PARTICLE_LOCKED);
        } else if (previous != null && !player.hasPermission("bytecompanions.command.particles.unequip")) {
            throw CollectionFailure.of(CollectionFailure.Reason.PARTICLE_REMOVE_DENIED);
        }
    }

    public void applyLoaded(Player player) {
        profile(player.getUniqueId()).ifPresent(value -> applyProfile(player, value, true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(CompanionSpawnEvent event) {
        Companion companion = event.companion();
        if (companion.ownerData() == null || closed) return;
        Player player = plugin.getServer().getPlayer(companion.ownerData().uniqueId());
        if (player == null) return;
        Session session = sessions.get(player.getUniqueId());
        CollectionProfile profile = session == null ? null : session.appliedProfile == null ? session.profile : session.appliedProfile;
        if (profile != null) {
            applyCompanion(player, companion, currentStyle(player, profile, companion.type().id()), true);
            adapter.order(player, profile.formationOrder());
        }
    }

    private void applyProfile(Player player, CollectionProfile profile, boolean tolerant) {
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        if (state == null) return;
        for (Companion companion : state.companions().values()) {
            applyCompanion(player, companion, currentStyle(player, profile, companion.type().id()), tolerant);
        }
        adapter.order(player, profile.formationOrder());
    }

    private void applyCompanion(Player player, Companion companion, CompanionStyle style, boolean tolerant) {
        if (tolerant && !player.hasPermission("bytecompanions.command.personalize")) {
            style = new CompanionStyle(null, null, style.particleId(), null);
        }
        try {
            adapter.validate(player, companion.type().id(), style);
            adapter.apply(player, companion, style);
        } catch (IllegalArgumentException failure) {
            if (!tolerant) throw failure;
            adapter.apply(player, companion, CompanionStyle.defaults());
        }
        if (particles != null) {
            String particle = style.particleId();
            if (tolerant && particle != null && (particles.particleEffectRegistry().get(particle) == null
                    || !player.hasPermission("bytecompanions.particle." + particle)
                    || !player.hasPermission("bytecompanions.command.particles.equip"))) particle = null;
            particles.playerEffectManager().applySelection(player, companion, particle);
        }
    }

    private CompanionStyle currentStyle(Player player, CollectionProfile profile, String companionId) {
        CompanionStyle style = profile.styles().get(companionId);
        if (style != null) return style;
        String particle = particles == null ? null : particles.playerEffectManager().selected(player.getUniqueId(), companionId);
        return CompanionStyle.defaults().withParticle(particle);
    }

    private CompanionType type(String companionId) {
        CompanionType type = plugin.typeRegistry().getOrNull(companionId);
        if (type == null) throw new CollectionFailure(CollectionFailure.Reason.COMPANION_UNAVAILABLE, Map.of("companion", companionId));
        return type;
    }

    private CompanionPlayer requirePlayer(Player player) {
        CompanionPlayer state = plugin.getCompanionPlayerOrNull(player);
        if (state == null) throw CollectionFailure.of(CollectionFailure.Reason.LOADING);
        return state;
    }

    private void requireParticlesReady(Player player) {
        if (particles != null && particles.playerEffectManager().loading(player.getUniqueId())) {
            throw CollectionFailure.of(CollectionFailure.Reason.LOADING);
        }
    }

    private static List<String> ordered(Set<String> ids, List<String> preference) {
        List<String> result = new ArrayList<>(preference.stream().filter(ids::contains).toList());
        ids.stream().sorted().filter(id -> !result.contains(id)).forEach(result::add);
        return List.copyOf(result);
    }

    private boolean active(Player player) { return !closed && runtime.getAsBoolean() && player.isOnline(); }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        pending.add(future);
        future.whenComplete((ignored, failure) -> pending.remove(future));
        return future;
    }

    private static CompletableFuture<Result> result(Status status, CollectionFailure failure) {
        return CompletableFuture.completedFuture(new Result(status, failure));
    }

    public CompletableFuture<Void> closeAsync() {
        closed = true;
        sessions.clear();
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    private static final class Session {
        private final CompletableFuture<CollectionProfile> loaded = new CompletableFuture<>();
        private volatile CollectionProfile profile;
        private CollectionProfile appliedProfile;
        private boolean busy;
        private DefaultCompanionManager.SelectionChange change;
    }
}
