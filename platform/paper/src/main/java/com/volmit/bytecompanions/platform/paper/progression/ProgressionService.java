package com.volmit.bytecompanions.platform.paper.progression;

import art.arcane.volmlib.integration.VaultEconomy;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

import static com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions.Scope;

public final class ProgressionService {
    public enum Status { SUCCESS, NOT_READY, BUSY, STALE, LOCKED, MAX_LEVEL, INSUFFICIENT_ITEMS,
        ECONOMY_UNAVAILABLE, INSUFFICIENT_FUNDS, PAYMENT_REJECTED, PAYMENT_UNKNOWN, PERSISTENCE_FAILED,
        UNKNOWN_DEFINITION, NO_CREDIT, DISABLED, INVALID_REQUEST }
    public record ActiveBooster(String activationId, String boosterId, Scope scope, Map<String, Double> percentages,
            long startsAt, long expiresAt, long remainingMillis, boolean queued) {
        public ActiveBooster { percentages = Map.copyOf(percentages); }
    }
    public record Snapshot(boolean ready, boolean busy, long revision, String companionId, long totalXp,
            int level, long nextThreshold, Map<String, Integer> tracks, Map<String, Integer> credits,
            List<ActiveBooster> personalBoosters, List<ActiveBooster> serverBoosters) {
        public Snapshot { tracks = Map.copyOf(tracks); credits = Map.copyOf(credits); personalBoosters = List.copyOf(personalBoosters); serverBoosters = List.copyOf(serverBoosters); }
    }
    public record MutationResult(Status status, String transactionId, String message, Snapshot snapshot) {
        public boolean success() { return status == Status.SUCCESS; }
    }
    public record PurchaseJournal(String transactionId, UUID playerId, String kind, String companionId,
            String definitionId, int expectedTier, ProgressionDefinitions.Cost cost, String status,
            String provider, long createdAt, String error) {
        PurchaseJournal state(String status, String provider, String error) {
            return new PurchaseJournal(transactionId, playerId, kind, companionId, definitionId, expectedTier,
                    cost, status, provider, createdAt, error);
        }
    }
    record Progress(long xp, Map<String, Integer> tiers, long xpRemainderMicros) {
        Progress {
            tiers = Map.copyOf(tiers);
            if (xp < 0 || xpRemainderMicros < 0 || xpRemainderMicros >= 1_000_000) throw new IllegalArgumentException("Invalid saved XP");
        }
        static Progress empty() { return new Progress(0, Map.of(), 0); }
    }
    record Booster(String activationId, String boosterId, Scope scope, Map<String, Double> percentages,
            long startsAt, long expiresAt) {
        Booster { percentages = Map.copyOf(percentages); }
        Booster shift(long delta) { return new Booster(activationId, boosterId, scope, percentages, Math.addExact(startsAt, delta), Math.addExact(expiresAt, delta)); }
    }
    record PlayerState(long revision, Map<String, Progress> companions, Map<String, Integer> credits,
            List<Booster> boosters, long checkpoint, boolean online) {
        PlayerState { companions = Map.copyOf(companions); credits = Map.copyOf(credits); boosters = List.copyOf(boosters); }
        static PlayerState empty() { return new PlayerState(0, Map.of(), Map.of(), List.of(), 0, false); }
    }
    record GlobalState(List<Booster> boosters) { GlobalState { boosters = List.copyOf(boosters); } }
    private final Plugin plugin;
    private final SQLStorageConnection connection;
    private final ProgressionDefinitions definitions;
    private final boolean progressionEnabled;
    private final boolean boostersEnabled;
    private final BooleanSupplier runtimeCurrent;
    private final LongSupplier clock;
    private final Predicate<Player> ownerCheck;
    private final boolean runTimer;
    private final KeyedTaskQueue<String> operations;
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<MutationResult>> purchases = new ConcurrentHashMap<>();
    private final Set<UUID> unresolved = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean checkpointPending = new AtomicBoolean();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ByteCompanions-booster-clock"); thread.setDaemon(true); return thread;
    });
    private volatile GlobalState global = new GlobalState(List.of());
    private volatile ProgressionStore store;
    private volatile Consumer<UUID> observer = ignored -> { };
    private volatile Predicate<String> targetEnabled = ignored -> true;
    private volatile long lastCheckpoint;
    private volatile boolean initialized;
    private volatile boolean closing;
    private CompletableFuture<Void> stopped;
    private CompletableFuture<Void> ownerTasksStopped;

    public ProgressionService(Plugin plugin, SQLStorageConnection connection, Executor executor,
            ProgressionDefinitions definitions, boolean progressionEnabled, boolean boostersEnabled, BooleanSupplier runtimeCurrent) {
        this(plugin, connection, executor, definitions, progressionEnabled, boostersEnabled, runtimeCurrent,
                System::currentTimeMillis, FoliaScheduler::isOwnedByCurrentRegion, true);
    }
    ProgressionService(Plugin plugin, SQLStorageConnection connection, Executor executor,
            ProgressionDefinitions definitions, boolean progressionEnabled, boolean boostersEnabled, BooleanSupplier runtimeCurrent,
            LongSupplier clock, Predicate<Player> ownerCheck, boolean runTimer) {
        this.plugin = plugin; this.connection = connection; this.definitions = definitions;
        this.progressionEnabled = progressionEnabled; this.boostersEnabled = boostersEnabled; this.runtimeCurrent = runtimeCurrent;
        this.clock = clock; this.ownerCheck = ownerCheck; this.runTimer = runTimer; this.lastCheckpoint = clock.getAsLong();
        operations = new KeyedTaskQueue<>(executor, (key, failure) -> plugin.getLogger().log(Level.SEVERE, "Progression persistence failed", failure));
    }
    public ProgressionDefinitions definitions() { return definitions; }
    public boolean progressionEnabled() { return progressionEnabled; }
    public boolean boostersEnabled() { return boostersEnabled; }
    public boolean active() { return initialized && !closing && runtimeCurrent.getAsBoolean(); }
    public boolean isLoaded(UUID player) { return players.containsKey(player); }
    public void onChanged(Consumer<UUID> observer) { this.observer = observer; }
    public void targetEnabled(Predicate<String> targetEnabled) { this.targetEnabled = targetEnabled; }
    public CompletableFuture<Void> initializeAsync() {
        return operations.execute("state", () -> {
            try {
                String scope = definitions.serverId();
                if (scope.equals("local")) {
                    Path path = plugin.getDataFolder().toPath().resolve("storage/progression-server-id.txt");
                    Files.createDirectories(path.getParent());
                    if (!Files.exists(path)) Files.writeString(path, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
                    scope = "local:" + UUID.fromString(Files.readString(path).strip());
                }
                store = new ProgressionStore(connection, scope);
                store.initialize();
                global = store.transaction(transaction -> transaction.global());
                for (PurchaseJournal journal : store.transaction(transaction -> transaction.unresolved())) unresolved.add(journal.playerId());
                initialized = true;
                if (runTimer && boostersEnabled) timer.scheduleAtFixedRate(this::checkpoint, 1, 1, TimeUnit.SECONDS);
            } catch (Exception failure) { throw new IllegalStateException("Could not initialize progression", failure); }
        });
    }
    public CompletableFuture<Snapshot> load(UUID player) {
        return submit(() -> {
            if (players.containsKey(player)) return snapshot(player, "");
            PlayerState state = store.transaction(transaction -> {
                PlayerState saved = transaction.player(player);
                long now = clock.getAsLong();
                long shift = definitions.pausePersonalOffline() && saved.checkpoint() > 0 ? Math.max(0, now - saved.checkpoint()) : 0;
                List<Booster> boosts = saved.boosters().stream().map(booster -> booster.shift(shift)).filter(booster -> booster.expiresAt() > now).toList();
                PlayerState loaded = new PlayerState(saved.revision(), saved.companions(), saved.credits(), boosts, now, true);
                transaction.player(player, loaded); return loaded;
            });
            if (store.transaction(transaction -> transaction.unresolved()).stream().noneMatch(journal -> journal.playerId().equals(player))) unresolved.remove(player);
            publish(player, state); return snapshot(player, "");
        });
    }
    public CompletableFuture<Void> unload(UUID player) {
        return submit(() -> { checkpointPlayer(player, false); players.remove(player); return null; });
    }
    public Snapshot snapshot(UUID player, String companionId) {
        PlayerState state = players.get(player);
        Progress progress = state == null ? Progress.empty() : state.companions().getOrDefault(companionId, Progress.empty());
        int level = level(progress.xp());
        return new Snapshot(state != null, purchases.containsKey(player) || unresolved.contains(player), state == null ? 0 : state.revision(),
                companionId, progress.xp(), level, level < definitions.levelThresholds().size() ? definitions.levelThresholds().get(level) : -1,
                progress.tiers(), state == null ? Map.of() : state.credits(), state == null ? List.of() : views(state.boosters()), globalBoosters());
    }
    public List<ActiveBooster> globalBoosters() { return boostersEnabled && active() ? views(global.boosters()) : List.of(); }
    public double bonusAddition(UUID player, String companion, String target) {
        if (!progressionEnabled || !active()) return 0;
        PlayerState state = players.get(player);
        ProgressionDefinitions.CompanionDefinition definition = definitions.companions().get(companion);
        if (state == null || definition == null) return 0;
        Progress progress = state.companions().getOrDefault(companion, Progress.empty());
        double total = 0;
        for (Map.Entry<Integer, Map<String, Double>> reward : definition.levelRewards().entrySet())
            if (level(progress.xp()) >= reward.getKey()) total += reward.getValue().getOrDefault(target, 0D);
        for (Map.Entry<String, Integer> track : progress.tiers().entrySet()) {
            ProgressionDefinitions.TrackDefinition configured = definition.tracks().get(track.getKey());
            if (configured != null) for (int i = 0; i < Math.min(track.getValue(), configured.tiers().size()); i++) total += configured.tiers().get(i).additions().getOrDefault(target, 0D);
        }
        return total;
    }
    public double boosterPercent(UUID player, String channel) {
        if (!boostersEnabled || !active() || !isLoaded(player)) return 0;
        PlayerState state = players.get(player);
        return Math.min(definitions.maximumBoosterPercent(), largest(global.boosters(), channel) + (state == null ? 0 : largest(state.boosters(), channel)));
    }
    public CompletableFuture<Snapshot> award(UUID player, String companion, long xp) {
        if (xp < 0) return CompletableFuture.failedFuture(new IllegalArgumentException("XP cannot be negative"));
        double multiplier = Math.min(definitions.maximumRewardMultiplier(), 1 + boosterPercent(player, "companion_xp") / 100);
        BigDecimal adjusted = BigDecimal.valueOf(xp).multiply(BigDecimal.valueOf(multiplier)).setScale(6, RoundingMode.DOWN);
        try {
            long whole = adjusted.setScale(0, RoundingMode.DOWN).longValueExact();
            long remainder = adjusted.subtract(BigDecimal.valueOf(whole)).movePointRight(6).longValueExact();
            return incrementXp(player, companion, whole, remainder);
        } catch (ArithmeticException overflow) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("XP overflow", overflow));
        }
    }
    public CompletableFuture<Snapshot> grantXp(UUID player, String companion, long xp) {
        return incrementXp(player, companion, xp, 0);
    }
    private CompletableFuture<Snapshot> incrementXp(UUID player, String companion, long xp, long remainder) {
        return submit(() -> {
            if (!progressionEnabled || xp < 0 || !definitions.companions().containsKey(companion)) throw new IllegalArgumentException("Progression is disabled or XP/companion is invalid");
            requireLoaded(player);
            int oldLevel = level(players.get(player).companions().getOrDefault(companion, Progress.empty()).xp());
            PlayerState changed = store.transaction(transaction -> {
                PlayerState state = transaction.player(player);
                Map<String, Progress> companions = new HashMap<>(state.companions());
                Progress old = companions.getOrDefault(companion, Progress.empty());
                long micros = old.xpRemainderMicros() + remainder;
                long whole = Math.addExact(xp, micros / 1_000_000);
                companions.put(companion, new Progress(Math.addExact(old.xp(), whole), old.tiers(), micros % 1_000_000));
                PlayerState next = new PlayerState(state.revision() + 1, companions, state.credits(), state.boosters(), state.checkpoint(), state.online());
                transaction.player(player, next); return next;
            });
            publish(player, changed, oldLevel != level(changed.companions().get(companion).xp())); return snapshot(player, companion);
        });
    }
    public CompletableFuture<Snapshot> grantCredits(UUID player, String booster, int count) {
        return submit(() -> {
            if (!boostersEnabled || count <= 0 || !definitions.boosters().containsKey(booster)) throw new IllegalArgumentException("Booster is disabled or grant is invalid");
            requireLoaded(player);
            PlayerState changed = store.transaction(transaction -> {
                PlayerState state = transaction.player(player); Map<String, Integer> credits = new HashMap<>(state.credits());
                credits.put(booster, Math.addExact(credits.getOrDefault(booster, 0), count));
                PlayerState next = new PlayerState(state.revision() + 1, state.companions(), credits, state.boosters(), state.checkpoint(), state.online());
                transaction.player(player, next); return next;
            });
            publish(player, changed, false); return snapshot(player, "");
        });
    }
    public CompletableFuture<MutationResult> purchase(Player player, String companion, String track, int expectedTier, long expectedRevision) {
        if (!progressionEnabled) return completed(Status.DISABLED, player.getUniqueId(), companion);
        ProgressionDefinitions.CompanionDefinition configured = definitions.companions().get(companion);
        ProgressionDefinitions.TrackDefinition definition = configured == null ? null : configured.tracks().get(track);
        if (definition == null) return completed(Status.UNKNOWN_DEFINITION, player.getUniqueId(), companion);
        if (expectedTier < 0 || expectedTier >= definition.tiers().size()) return completed(Status.MAX_LEVEL, player.getUniqueId(), companion);
        ProgressionDefinitions.TierDefinition tier = definition.tiers().get(expectedTier);
        return purchase(player, new PurchaseJournal(UUID.randomUUID().toString(), player.getUniqueId(), "UPGRADE", companion, track,
                expectedTier, tier.cost(), "PREPARED", "", clock.getAsLong(), ""), expectedRevision, tier.requiredLevel());
    }
    public CompletableFuture<MutationResult> purchaseBooster(Player player, String booster, long expectedRevision) {
        if (!boostersEnabled) return completed(Status.DISABLED, player.getUniqueId(), "");
        ProgressionDefinitions.BoosterDefinition definition = definitions.boosters().get(booster);
        if (definition == null) return completed(Status.UNKNOWN_DEFINITION, player.getUniqueId(), "");
        return purchase(player, new PurchaseJournal(UUID.randomUUID().toString(), player.getUniqueId(), "CREDIT", "", booster,
                0, definition.cost(), "PREPARED", "", clock.getAsLong(), ""), expectedRevision, 1);
    }
    private CompletableFuture<MutationResult> purchase(Player player, PurchaseJournal journal, long revision, int requiredLevel) {
        UUID id = journal.playerId();
        CompletableFuture<MutationResult> result = new CompletableFuture<>();
        synchronized (this) {
            if (!active() || !isLoaded(id)) return completed(Status.NOT_READY, id, journal.companionId());
            if (unresolved.contains(id) || purchases.putIfAbsent(id, result) != null) return completed(Status.BUSY, id, journal.companionId());
        }
        AtomicBoolean journalCreated = new AtomicBoolean();
        submit(() -> {
            Snapshot view = snapshot(id, journal.companionId());
            if (view.revision() != revision || (journal.kind().equals("UPGRADE") && view.tracks().getOrDefault(journal.definitionId(), 0) != journal.expectedTier())) return Status.STALE;
            if (view.level() < requiredLevel) return Status.LOCKED;
            if (!targetsEnabled(journal)) return Status.LOCKED;
            Status stored = store.transaction(transaction -> {
                PlayerState current = transaction.player(id);
                if (current.revision() != revision) return Status.STALE;
                if (transaction.unresolved().stream().anyMatch(pending -> pending.playerId().equals(id))) return Status.BUSY;
                transaction.create(journal); return Status.SUCCESS;
            });
            if (stored == Status.SUCCESS) journalCreated.set(true);
            return stored;
        }).thenCompose(status -> {
            if (status != Status.SUCCESS) return CompletableFuture.completedFuture(new MutationResult(status, "", "", snapshot(id, journal.companionId())));
            return onOwner(player, () -> preparePayment(player, journal)).thenCompose(selection -> {
                if (selection.status() != Status.SUCCESS) return settle(journal, new Payment(selection.status(), "", selection.message()));
                String provider = selection.provider() == null ? "" : selection.provider().identity();
                return submit(() -> { store.transaction(transaction -> { transaction.journal(journal.state("ATTEMPTING", provider, "")); return null; }); return true; })
                        .thenCompose(ignored -> onOwner(player, () -> charge(player, journal, selection)))
                        .thenCompose(payment -> settle(journal, payment));
            });
        }).whenComplete((value, failure) -> {
            if (failure != null) {
                if (journalCreated.get()) unresolved.add(id);
                plugin.getLogger().log(Level.SEVERE, "Purchase " + journal.transactionId()
                        + (journalCreated.get() ? " requires reconciliation; do not retry payment" : " failed before payment"), failure);
                value = new MutationResult(journalCreated.get() ? Status.PAYMENT_UNKNOWN : Status.PERSISTENCE_FAILED, journal.transactionId(), failure.toString(), snapshot(id, journal.companionId()));
            }
            purchases.remove(id, result);
            result.complete(new MutationResult(value.status(), value.transactionId(), value.message(), snapshot(id, journal.companionId())));
        });
        return result;
    }
    private PaymentSelection preparePayment(Player player, PurchaseJournal journal) {
        if (!active() || !player.isOnline() || !isLoaded(journal.playerId())) return new PaymentSelection(Status.PAYMENT_REJECTED, null, "Player/runtime changed");
        if (!permitted(player, journal.kind().equals("UPGRADE"), journal.kind().equals("UPGRADE") ? journal.companionId() : journal.definitionId()) || !targetsEnabled(journal)) return new PaymentSelection(Status.LOCKED, null, "Purchase permission or feature changed");
        if (!hasItems(player, journal.cost().items())) return new PaymentSelection(Status.INSUFFICIENT_ITEMS, null, "");
        if (journal.cost().money() == 0) return new PaymentSelection(Status.SUCCESS, null, "");
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return new PaymentSelection(Status.ECONOMY_UNAVAILABLE, null, "Vault is unavailable");
        VaultEconomy.TransferProvider provider = new VaultEconomy(plugin).transferProvider().orElse(null);
        return new PaymentSelection(provider == null ? Status.ECONOMY_UNAVAILABLE : Status.SUCCESS, provider, "");
    }
    private Payment charge(Player player, PurchaseJournal journal, PaymentSelection selection) {
        if (!active() || !player.isOnline() || !isLoaded(journal.playerId())) return new Payment(Status.PAYMENT_REJECTED, "", "Player/runtime changed");
        if (!permitted(player, journal.kind().equals("UPGRADE"), journal.kind().equals("UPGRADE") ? journal.companionId() : journal.definitionId()) || !targetsEnabled(journal)) return new Payment(Status.LOCKED, "", "Purchase permission or feature changed");
        ProgressionDefinitions.Cost cost = journal.cost();
        if (!hasItems(player, cost.items())) return new Payment(Status.INSUFFICIENT_ITEMS, "", "");
        String provider = "";
        if (cost.money() > 0) {
            VaultEconomy.TransferProvider selected = selection.provider();
            provider = selected.identity();
            VaultEconomy.TransferResult transfer = selected.transfer(player, new VaultEconomy.TransferRequest(VaultEconomy.TransferDirection.WITHDRAW, cost.money(), journal.transactionId()));
            if (transfer.status() == VaultEconomy.TransferStatus.UNKNOWN) return new Payment(Status.PAYMENT_UNKNOWN, provider, "Economy outcome unknown; item costs were not removed. " + transfer.error());
            if (transfer.status() == VaultEconomy.TransferStatus.REJECTED) return new Payment(Status.PAYMENT_REJECTED, provider, transfer.error());
        }
        removeItems(player, cost.items());
        return new Payment(Status.SUCCESS, provider, "");
    }
    private boolean targetsEnabled(PurchaseJournal journal) {
        if (!journal.kind().equals("UPGRADE")) return true;
        ProgressionDefinitions.CompanionDefinition companion = definitions.companions().get(journal.companionId());
        ProgressionDefinitions.TrackDefinition track = companion == null ? null : companion.tracks().get(journal.definitionId());
        return track != null && journal.expectedTier() < track.tiers().size()
                && track.tiers().get(journal.expectedTier()).additions().keySet().stream().allMatch(targetEnabled);
    }
    private boolean permitted(Player player, boolean upgrade, String id) {
        return player.hasPermission("bytecompanions.command.menu")
                && player.hasPermission("bytecompanions.command." + (upgrade ? "upgrades" : "boosts"))
                && player.hasPermission("bytecompanions." + (upgrade ? "companion." : "booster.") + id);
    }
    private CompletableFuture<MutationResult> settle(PurchaseJournal journal, Payment payment) {
        return submitInternal(() -> {
            PlayerState updated = store.transaction(transaction -> {
                if (payment.status() == Status.SUCCESS) {
                    PlayerState state = applyGrant(transaction.player(journal.playerId()), journal);
                    transaction.player(journal.playerId(), state);
                    transaction.journal(journal.state("GRANTED", payment.provider(), "")); return state;
                }
                transaction.journal(journal.state(payment.status() == Status.PAYMENT_UNKNOWN ? "UNKNOWN" : "CANCELLED", payment.provider(), payment.message()));
                return null;
            });
            if (updated != null && isLoaded(journal.playerId())) publish(journal.playerId(), updated, journal.kind().equals("UPGRADE"));
            if (payment.status() == Status.PAYMENT_UNKNOWN) unresolved.add(journal.playerId());
            return new MutationResult(payment.status(), journal.transactionId(), payment.message(), snapshot(journal.playerId(), journal.companionId()));
        });
    }
    private PlayerState applyGrant(PlayerState state, PurchaseJournal journal) {
        Map<String, Progress> companions = new HashMap<>(state.companions());
        Map<String, Integer> credits = new HashMap<>(state.credits());
        if (journal.kind().equals("UPGRADE")) {
            Progress old = companions.getOrDefault(journal.companionId(), Progress.empty()); Map<String, Integer> tiers = new HashMap<>(old.tiers());
            if (tiers.getOrDefault(journal.definitionId(), 0) != journal.expectedTier()) throw new IllegalStateException("Purchase tier changed; manual reconciliation required");
            tiers.put(journal.definitionId(), journal.expectedTier() + 1); companions.put(journal.companionId(), new Progress(old.xp(), tiers, old.xpRemainderMicros()));
        } else credits.put(journal.definitionId(), Math.addExact(credits.getOrDefault(journal.definitionId(), 0), 1));
        return new PlayerState(state.revision() + 1, companions, credits, state.boosters(), state.checkpoint(), state.online());
    }
    public CompletableFuture<MutationResult> activate(Player player, String boosterId, long expectedRevision) {
        UUID id = player.getUniqueId();
        return onOwner(player, () -> active() && player.isOnline() && permitted(player, false, boosterId)).thenCompose(allowed -> {
            if (!allowed) return completed(Status.LOCKED, id, "");
            return submit(() -> {
            if (!boostersEnabled) return result(Status.DISABLED, id, "");
            if (!isLoaded(id)) return result(Status.NOT_READY, id, "");
            ProgressionDefinitions.BoosterDefinition definition = definitions.boosters().get(boosterId);
            if (definition == null) return result(Status.UNKNOWN_DEFINITION, id, "");
            Activation applied = store.transaction(transaction -> {
                PlayerState state = transaction.player(id);
                if (state.revision() != expectedRevision) return new Activation(null, null, Status.STALE, "");
                if (state.credits().getOrDefault(boosterId, 0) < 1) return new Activation(null, null, Status.NO_CREDIT, "");
                Map<String, Integer> credits = new HashMap<>(state.credits()); credits.put(boosterId, credits.get(boosterId) - 1);
                List<Booster> personal = state.boosters();
                GlobalState updatedGlobal = null;
                String activationId;
                if (definition.scope() == Scope.SERVER) {
                    GlobalState current = transaction.global(); updatedGlobal = new GlobalState(append(current.boosters(), boosterId, definition)); transaction.global(updatedGlobal);
                    activationId = updatedGlobal.boosters().getLast().activationId();
                } else {
                    personal = append(personal, boosterId, definition); activationId = personal.getLast().activationId();
                }
                PlayerState next = new PlayerState(state.revision() + 1, state.companions(), credits, personal, clock.getAsLong(), state.online()); transaction.player(id, next); return new Activation(next, updatedGlobal, Status.SUCCESS, activationId);
            });
            if (applied.status() != Status.SUCCESS) return result(applied.status(), id, "");
            if (applied.global() != null) global = applied.global();
            publish(id, applied.player(), applied.global() == null);
            if (applied.global() != null) changedAll();
            return new MutationResult(Status.SUCCESS, applied.activationId(), "", snapshot(id, ""));
        });
        });
    }
    public CompletableFuture<MutationResult> startGlobal(String boosterId) {
        return submit(() -> {
            ProgressionDefinitions.BoosterDefinition definition = definitions.boosters().get(boosterId);
            if (!boostersEnabled) return result(Status.DISABLED, null, "");
            if (definition == null || definition.scope() != Scope.SERVER) return result(Status.UNKNOWN_DEFINITION, null, "");
            GlobalState updated = store.transaction(transaction -> { GlobalState next = new GlobalState(append(transaction.global().boosters(), boosterId, definition)); transaction.global(next); return next; });
            global = updated; changedAll(); return new MutationResult(Status.SUCCESS, updated.boosters().getLast().activationId(), "", null);
        });
    }
    public CompletableFuture<MutationResult> cancelBooster(String activationId) {
        return submit(() -> {
            boolean found = false;
            for (Map.Entry<UUID, PlayerState> entry : store.transaction(transaction -> transaction.players()).entrySet()) {
                UUID player = entry.getKey();
                PlayerState state = entry.getValue();
                if (state.boosters().stream().noneMatch(booster -> booster.activationId().equals(activationId))) continue;
                PlayerState updated = store.transaction(transaction -> {
                    PlayerState current = transaction.player(player);
                    PlayerState next = new PlayerState(current.revision() + 1, current.companions(), current.credits(), current.boosters().stream().filter(booster -> !booster.activationId().equals(activationId)).toList(), current.checkpoint(), current.online());
                    transaction.player(player, next); return next;
                }); if (isLoaded(player)) publish(player, updated); found = true;
            }
            if (global.boosters().stream().anyMatch(booster -> booster.activationId().equals(activationId))) {
                global = store.transaction(transaction -> { GlobalState next = new GlobalState(transaction.global().boosters().stream().filter(booster -> !booster.activationId().equals(activationId)).toList()); transaction.global(next); return next; });
                found = true; changedAll();
            }
            return result(found ? Status.SUCCESS : Status.UNKNOWN_DEFINITION, null, "");
        });
    }
    public CompletableFuture<List<PurchaseJournal>> unresolvedPurchases() { return submit(() -> store.transaction(transaction -> transaction.unresolved())); }
    public CompletableFuture<MutationResult> resolvePurchase(String id, boolean grant) {
        return submit(() -> {
            PurchaseJournal resolved = store.transaction(transaction -> {
                PurchaseJournal journal = transaction.journal(id);
                if (journal == null) return null;
                if (purchases.containsKey(journal.playerId())) throw new IllegalStateException("Purchase is still running");
                if (journal.status().equals("GRANTED") || journal.status().equals("CANCELLED")) return journal;
                if (grant) transaction.player(journal.playerId(), applyGrant(transaction.player(journal.playerId()), journal));
                transaction.journal(journal.state(grant ? "GRANTED" : "CANCELLED", journal.provider(), "Manually reconciled; no money or items transferred")); return journal;
            });
            if (resolved == null) return result(Status.STALE, null, "");
            unresolved.remove(resolved.playerId());
            for (PurchaseJournal journal : store.transaction(transaction -> transaction.unresolved())) unresolved.add(journal.playerId());
            if (isLoaded(resolved.playerId())) publish(resolved.playerId(), store.transaction(transaction -> transaction.player(resolved.playerId())), grant && resolved.kind().equals("UPGRADE"));
            return result(Status.SUCCESS, resolved.playerId(), resolved.companionId());
        });
    }
    public synchronized CompletableFuture<Void> stopOwnerTasks() {
        if (ownerTasksStopped == null) {
            closing = true;
            timer.shutdownNow();
            ownerTasksStopped = CompletableFuture.allOf(purchases.values().toArray(CompletableFuture[]::new));
        }
        return ownerTasksStopped.copy();
    }
    public synchronized CompletableFuture<Void> closeAsync() {
        if (stopped != null) return stopped;
        CompletableFuture<Void> checkpointed = stopOwnerTasks()
                .thenCompose(ignored -> submitInternal(() -> {
                    try {
                        for (UUID id : List.copyOf(players.keySet())) checkpointPlayer(id, false);
                    } finally {
                        players.clear();
                    }
                    return null;
                }));
        stopped = checkpointed.handle((ignored, failure) -> operations.closeAsync().handle((drained, drainFailure) -> {
            if (failure != null) {
                if (drainFailure != null) failure.addSuppressed(drainFailure);
                throw new CompletionException(failure);
            }
            if (drainFailure != null) throw new CompletionException(drainFailure);
            return (Void) null;
        })).thenCompose(future -> future);
        return stopped.copy();
    }
    private void checkpoint() {
        if (checkpointPending.compareAndSet(false, true)) checkpointAsync().whenComplete((ignored, failure) -> checkpointPending.set(false));
    }
    CompletableFuture<Void> checkpointAsync() {
        if (closing || !initialized) return CompletableFuture.completedFuture(null);
        return submit(() -> {
            long now = clock.getAsLong();
            long previous = lastCheckpoint;
            lastCheckpoint = now;
            for (UUID id : List.copyOf(players.keySet())) {
                PlayerState state = players.get(id);
                boolean boundary = state.boosters().stream().anyMatch(booster -> booster.startsAt() > previous && booster.startsAt() <= now || booster.expiresAt() > previous && booster.expiresAt() <= now);
                if (!state.boosters().isEmpty() && (definitions.pausePersonalOffline() || boundary)) checkpointPlayer(id, true);
                if (boundary) changed(id);
            }
            boolean globalBoundary = global.boosters().stream().anyMatch(booster -> booster.startsAt() > previous && booster.startsAt() <= now || booster.expiresAt() > previous && booster.expiresAt() <= now);
            if (global.boosters().stream().anyMatch(booster -> booster.expiresAt() <= now)) {
                global = store.transaction(transaction -> { GlobalState next = new GlobalState(transaction.global().boosters().stream().filter(booster -> booster.expiresAt() > now).toList()); transaction.global(next); return next; });
            }
            if (globalBoundary) changedAll(); return null;
        });
    }
    private void checkpointPlayer(UUID id, boolean online) throws Exception {
        PlayerState cached = players.get(id);
        if (cached == null || cached.boosters().isEmpty()) return;
        long now = clock.getAsLong();
        PlayerState updated = store.transaction(transaction -> {
            PlayerState state = transaction.player(id);
            PlayerState next = new PlayerState(state.revision(), state.companions(), state.credits(), state.boosters().stream().filter(booster -> booster.expiresAt() > now).toList(), now, online);
            transaction.player(id, next); return next;
        }); players.put(id, updated);
    }
    private List<Booster> append(List<Booster> current, String id, ProgressionDefinitions.BoosterDefinition definition) {
        long now = clock.getAsLong(); List<Booster> next = new ArrayList<>(current.stream().filter(booster -> booster.expiresAt() > now).toList());
        if (next.size() >= 1000) throw new IllegalStateException("Booster queue is full");
        long starts = now;
        if (definition.stacking() == ProgressionDefinitions.Stacking.QUEUE) for (Booster booster : next)
            if (booster.percentages().keySet().stream().anyMatch(definition.percentages()::containsKey)) starts = Math.max(starts, booster.expiresAt());
        next.add(new Booster(UUID.randomUUID().toString(), id, definition.scope(), definition.percentages(), starts, Math.addExact(starts, definition.durationMillis())));
        return List.copyOf(next);
    }
    private List<ActiveBooster> views(List<Booster> boosts) {
        long now = clock.getAsLong();
        return boosts.stream().filter(booster -> booster.expiresAt() > now).map(booster -> new ActiveBooster(booster.activationId(), booster.boosterId(), booster.scope(), booster.percentages(), booster.startsAt(), booster.expiresAt(), Math.max(0, booster.expiresAt() - Math.max(now, booster.startsAt())), now < booster.startsAt())).toList();
    }
    private double largest(List<Booster> boosts, String channel) {
        long now = clock.getAsLong();
        return boosts.stream().filter(booster -> booster.startsAt() <= now && booster.expiresAt() > now).mapToDouble(booster -> booster.percentages().getOrDefault(channel, 0D)).max().orElse(0);
    }
    private int level(long xp) { int level = 1; while (level < definitions.levelThresholds().size() && xp >= definitions.levelThresholds().get(level)) level++; return level; }
    private void requireLoaded(UUID id) { if (!players.containsKey(id)) throw new IllegalStateException("Progression player data is not loaded"); }
    private void publish(UUID id, PlayerState state) { publish(id, state, true); }
    private void publish(UUID id, PlayerState state, boolean notify) { players.put(id, state); if (notify) changed(id); }
    private void changed(UUID id) { try { observer.accept(id); } catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "Progression observer failed", failure); } }
    private void changedAll() { players.keySet().forEach(this::changed); }
    private MutationResult result(Status status, UUID player, String companion) { return new MutationResult(status, "", "", player == null ? null : snapshot(player, companion)); }
    private CompletableFuture<MutationResult> completed(Status status, UUID player, String companion) { return CompletableFuture.completedFuture(result(status, player, companion)); }
    private <T> CompletableFuture<T> submit(Work<T> work) {
        if (closing || !initialized) return CompletableFuture.failedFuture(new IllegalStateException("Progression is unavailable"));
        return submitInternal(work);
    }
    private <T> CompletableFuture<T> submitInternal(Work<T> work) {
        return operations.submit("state", () -> { try { return work.get(); } catch (Exception failure) { throw new IllegalStateException(failure); } });
    }
    private interface Work<T> { T get() throws Exception; }
    private record Payment(Status status, String provider, String message) { }
    private record PaymentSelection(Status status, VaultEconomy.TransferProvider provider, String message) { }
    private record Activation(PlayerState player, GlobalState global, Status status, String activationId) { }
    private <T> CompletableFuture<T> onOwner(Player player, Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable run = () -> { try { result.complete(operation.get()); } catch (Throwable failure) { result.completeExceptionally(failure); } };
        try {
            if (ownerCheck.test(player)) run.run();
            else if (!FoliaScheduler.runEntity(plugin, player, run, 0, () -> result.completeExceptionally(new IllegalStateException("Player retired before payment")))) result.completeExceptionally(new IllegalStateException("Player scheduler rejected payment"));
        } catch (Throwable failure) { result.completeExceptionally(failure); }
        return result;
    }
    private static boolean hasItems(Player player, Map<String, Integer> costs) {
        if (costs.isEmpty()) return true;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (Map.Entry<String, Integer> cost : costs.entrySet()) {
            ItemStack wanted = new ItemStack(Material.valueOf(cost.getKey())); int count = 0;
            for (ItemStack item : contents) if (item != null && item.isSimilar(wanted)) count += item.getAmount();
            if (count < cost.getValue()) return false;
        }
        return true;
    }
    private static void removeItems(Player player, Map<String, Integer> costs) {
        if (costs.isEmpty()) return;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (Map.Entry<String, Integer> cost : costs.entrySet()) {
            ItemStack wanted = new ItemStack(Material.valueOf(cost.getKey())); int remaining = cost.getValue();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i]; if (item == null || !item.isSimilar(wanted)) continue;
                int taken = Math.min(remaining, item.getAmount()); remaining -= taken;
                contents[i] = taken == item.getAmount() ? null : item.clone();
                if (contents[i] != null) contents[i].setAmount(item.getAmount() - taken);
            }
            if (remaining != 0) throw new IllegalStateException("Item cost changed during purchase");
        }
        player.getInventory().setStorageContents(contents);
    }
}
