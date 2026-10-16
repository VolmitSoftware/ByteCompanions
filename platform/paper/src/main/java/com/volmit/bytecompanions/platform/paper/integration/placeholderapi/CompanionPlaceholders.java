package com.volmit.bytecompanions.platform.paper.integration.placeholderapi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class CompanionPlaceholders implements Listener {
    public static final String IDENTIFIER = "bytecompanions";

    private final Plugin plugin;
    private final CompanionPlayerCache players;
    private final DefaultCompanionTypeRegistry types;
    private final Predicate<Player> ownerThread;
    private final Scheduling scheduling;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final PlaceholderKeyRegistry registry;
    private volatile boolean closed;

    public CompanionPlaceholders(Plugin plugin, DefaultCompanionTypeRegistry types, CompanionPlayerCache players) {
        this(plugin, types, players, FoliaScheduler::isOwnedByCurrentRegion,
                (player, action, delay, retired) -> FoliaScheduler.runEntity(plugin, player, action, delay, retired));
    }

    CompanionPlaceholders(Plugin plugin, DefaultCompanionTypeRegistry types, CompanionPlayerCache players,
                          Predicate<Player> ownerThread, Scheduling scheduling) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.types = Objects.requireNonNull(types, "types");
        this.ownerThread = Objects.requireNonNull(ownerThread, "ownerThread");
        this.scheduling = Objects.requireNonNull(scheduling, "scheduling");
        this.registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.bool(!this.closed))
                .key("total", playerId -> this.closed ? null : PlaceholderValues.count(this.types.registeredIds().size()))
                .key("current", playerId -> this.closed ? null : String.join(",", this.equipped(playerId)))
                .key("player-total", playerId -> this.closed ? null
                        : PlaceholderValues.count(this.unlocked(playerId).stream().filter(this.types.registeredIds()::contains).count()))
                .group("has-equipped", (playerId, companion) -> this.closed ? null
                        : PlaceholderValues.bool(!companion.isEmpty() && this.equipped(playerId).contains(companion)))
                .group("has-unlocked", (playerId, companion) -> this.closed ? null
                        : PlaceholderValues.bool(this.types.registeredIds().contains(companion) && this.unlocked(playerId).contains(companion)))
                .build();
    }

    @FunctionalInterface
    interface Scheduling {
        boolean run(Player player, Runnable action, long delay, Runnable retired);
    }

    public PlaceholderKeyRegistry registry() {
        return this.registry;
    }

    public String resolve(UUID playerId, String path) {
        return this.registry.resolve(playerId, path);
    }

    public void start() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.track(player);
        }
    }

    public void close() {
        this.closed = true;
        this.sessions.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = this.sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.player == event.getPlayer()) {
            this.sessions.remove(session.id, session);
        }
    }

    void track(Player player) {
        if (this.closed) {
            return;
        }
        Session session = new Session(player);
        Session existing = this.sessions.putIfAbsent(session.id, session);
        if (existing == null && !this.scheduling.run(player, session::pulse, 1, session::retire)) {
            session.retire();
        }
    }

    private Session session(UUID playerId) {
        Session session = playerId == null ? null : this.sessions.get(playerId);
        if (session == null) {
            return null;
        }
        if (this.ownerThread.test(session.player)) {
            session.publishPermissions();
        }
        return session.current() ? session : null;
    }

    private List<String> equipped(UUID playerId) {
        Session session = this.session(playerId);
        DefaultCompanionPlayer state = session == null ? null : this.players.getByUniqueId(playerId);
        return state == null ? List.of() : state.equippedIds();
    }

    private Set<String> unlocked(UUID playerId) {
        Session session = this.session(playerId);
        return session == null ? Set.of() : session.unlocked;
    }

    private final class Session {
        private final Player player;
        private final UUID id;
        private volatile Set<String> unlocked = Set.of();

        private Session(Player player) {
            this.player = player;
            this.id = player.getUniqueId();
        }

        private boolean current() {
            return !closed && sessions.get(this.id) == this;
        }

        private void publishPermissions() {
            if (!this.current()) {
                return;
            }
            if (!this.player.isOnline()) {
                this.retire();
                return;
            }
            Set<String> allowed = new HashSet<>();
            for (String typeId : types.registeredIds()) {
                if (this.player.hasPermission(FeaturePermission.Companions.resolve(typeId))) {
                    allowed.add(typeId);
                }
            }
            this.unlocked = Set.copyOf(allowed);
        }

        private void pulse() {
            if (!this.current()) {
                return;
            }
            this.publishPermissions();
            if (this.current() && !scheduling.run(this.player, this::pulse, 20, this::retire)) {
                this.retire();
            }
        }

        private void retire() {
            this.unlocked = Set.of();
            sessions.remove(this.id, this);
        }
    }
}
