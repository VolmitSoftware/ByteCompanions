package com.volmit.bytecompanions.platform.paper.progression;

import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class ProgressionServiceTest {
    private final UUID playerId = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private HikariDataSource pool;
    private SQLStorageConnection connection;
    private ProgressionService service;
    private Plugin plugin;
    private Player player;

    @BeforeEach
    void open() {
        pool = new HikariDataSource();
        pool.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        pool.setMaximumPoolSize(2);
        connection = new SQLStorageConnection(pool, SqlDialect.H2, Logger.getAnonymousLogger());
        connection.connect();
        plugin = proxy(Plugin.class, (name, args) -> switch (name) {
            case "getLogger" -> Logger.getAnonymousLogger();
            case "getName" -> "ByteCompanions";
            default -> null;
        });
        PlayerInventory inventory = proxy(PlayerInventory.class, (name, args) -> name.equals("getStorageContents") ? new ItemStack[36] : null);
        player = proxy(Player.class, (name, args) -> switch (name) {
            case "getUniqueId" -> playerId;
            case "isOnline", "hasPermission" -> true;
            case "getInventory" -> inventory;
            default -> null;
        });
        service = create();
        service.load(playerId).join();
    }
    @AfterEach
    void close() {
        if (service != null) service.closeAsync().join();
        pool.close();
    }
    private ProgressionService create() {
        ProgressionService created = new ProgressionService(plugin, scope -> new SqlProgressionStore(connection, scope), Runnable::run, definitions(), true, true,
                () -> true, clock::get, ignored -> true, false);
        created.initializeAsync().join();
        return created;
    }
    @Test
    void ownerShutdownBarrierRetainsAcceptedPurchaseAndRejectsNewPayment() throws Exception {
        service.closeAsync().join();
        ArrayDeque<Runnable> accepted = new ArrayDeque<>();
        AtomicBoolean defer = new AtomicBoolean();
        service = new ProgressionService(plugin, scope -> new SqlProgressionStore(connection, scope), action -> {
            if (defer.get()) accepted.add(action); else action.run();
        }, definitions(), true, true, () -> true, clock::get, ignored -> true, false);
        service.initializeAsync().join();
        service.load(playerId).join();
        service.grantXp(playerId, "bee", 100).join();
        long revision = service.snapshot(playerId, "bee").revision();
        defer.set(true);
        CompletableFuture<ProgressionService.MutationResult> purchase = service.purchase(player, "bee", "speed", 0, revision);
        CompletableFuture<Void> owners = service.stopOwnerTasks();
        assertFalse(owners.isDone());
        assertEquals(ProgressionService.Status.NOT_READY, service.purchase(player, "bee", "speed", 0, revision).join().status());
        accepted.remove().run();
        assertEquals(ProgressionService.Status.PAYMENT_REJECTED, purchase.join().status());
        assertTrue(owners.isDone());
        assertEquals(0, service.snapshot(playerId, "bee").tracks().getOrDefault("speed", 0));
        ProgressionStore store = new SqlProgressionStore(connection, "test");
        assertEquals("CANCELLED", store.transaction(transaction -> transaction.journal(purchase.join().transactionId())).status());
        CompletableFuture<Void> closed = service.closeAsync();
        assertFalse(closed.isDone());
        accepted.remove().run();
        closed.join();
    }
    @Test
    void stalePurchaseCannotGrantAnotherTierAndPermanentBenefitsSurviveServiceReplacement() {
        service.grantXp(playerId, "bee", 100).join();
        long revision = service.snapshot(playerId, "bee").revision();
        assertTrue(service.purchase(player, "bee", "speed", 0, revision).join().success());
        assertEquals(ProgressionService.Status.STALE, service.purchase(player, "bee", "speed", 0, revision).join().status());
        assertEquals(1, service.snapshot(playerId, "bee").tracks().get("speed"));
        assertEquals(3, service.bonusAddition(playerId, "bee", "effect:minecraft:speed"));
        service.closeAsync().join();
        service = create();
        service.load(playerId).join();
        assertEquals(100, service.snapshot(playerId, "bee").totalXp());
        assertEquals(1, service.snapshot(playerId, "bee").tracks().get("speed"));
        assertEquals(3, service.bonusAddition(playerId, "bee", "effect:minecraft:speed"));
    }
    @Test
    void purchaseStoppedBeforePaymentIsCancelledAndDoesNotBlockTheNextPurchase() throws Exception {
        service.grantXp(playerId, "bee", 100).join();
        long revision = service.snapshot(playerId, "bee").revision();
        Player leaving = proxy(Player.class, (name, args) -> switch (name) {
            case "getUniqueId" -> playerId;
            case "isOnline" -> throw new IllegalStateException("player left during the owner hop");
            default -> null;
        });
        ProgressionService.MutationResult stopped = service.purchase(leaving, "bee", "speed", 0, revision).join();
        assertEquals(ProgressionService.Status.PAYMENT_REJECTED, stopped.status());
        ProgressionStore store = new SqlProgressionStore(connection, "test");
        assertEquals("CANCELLED", store.transaction(transaction -> transaction.journal(stopped.transactionId())).status());
        assertFalse(service.snapshot(playerId, "bee").busy());
        assertTrue(service.purchase(player, "bee", "speed", 0, revision).join().success());
    }
    @Test
    void journalLeftBeforePaymentIsCancelledOnStartup() throws Exception {
        ProgressionStore store = new SqlProgressionStore(connection, "test");
        String id = UUID.randomUUID().toString();
        store.transaction(transaction -> {
            transaction.create(new ProgressionService.PurchaseJournal(id, playerId, "UPGRADE", "bee", "speed", 0,
                    new ProgressionDefinitions.Cost(10, Map.of()), "PREPARED", "", clock.get(), ""));
            return null;
        });
        service.closeAsync().join();
        service = create(); service.load(playerId).join();
        assertFalse(service.snapshot(playerId, "bee").busy());
        assertEquals("CANCELLED", store.transaction(transaction -> transaction.journal(id)).status());
        assertTrue(service.unresolvedPurchases().join().isEmpty());
    }
    @Test
    void ambiguousCrashJournalBlocksRepurchaseAndManualGrantIsIdempotent() throws Exception {
        ProgressionStore store = new SqlProgressionStore(connection, "test");
        String id = UUID.randomUUID().toString();
        ProgressionService.PurchaseJournal journal = new ProgressionService.PurchaseJournal(id, playerId, "UPGRADE", "bee", "speed", 0,
                new ProgressionDefinitions.Cost(10, Map.of()), "ATTEMPTING", "test/provider", clock.get(), "");
        store.transaction(transaction -> { transaction.create(journal); return null; });
        service.closeAsync().join();
        service = create(); service.load(playerId).join();
        assertTrue(service.snapshot(playerId, "bee").busy());
        assertEquals(ProgressionService.Status.BUSY, service.purchase(player, "bee", "speed", 0, 0).join().status());
        assertEquals("test/provider", service.unresolvedPurchases().join().getFirst().provider());
        assertTrue(service.resolvePurchase(id, true).join().success());
        assertTrue(service.resolvePurchase(id, true).join().success());
        assertEquals(1, service.snapshot(playerId, "bee").tracks().get("speed"));
        assertFalse(service.snapshot(playerId, "bee").busy());
        assertTrue(service.unresolvedPurchases().join().isEmpty());
    }
    @Test
    void personalAndServerBoostersAddWhileDuplicateActivationUsesRevisionAndQueues() {
        service.grantCredits(playerId, "personal", 2).join();
        long revision = service.snapshot(playerId, "bee").revision();
        assertTrue(service.activate(player, "personal", revision).join().success());
        assertEquals(ProgressionService.Status.STALE, service.activate(player, "personal", revision).join().status());
        assertEquals(1, service.snapshot(playerId, "bee").credits().get("personal"));
        assertTrue(service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join().success());
        assertEquals(1, service.snapshot(playerId, "bee").personalBoosters().stream().filter(ProgressionService.ActiveBooster::queued).count());
        assertTrue(service.startGlobal("server").join().success());
        assertEquals(75, service.boosterPercent(playerId, "companion_xp"));
        assertEquals(0, service.boosterPercent(UUID.randomUUID(), "companion_xp"));
        service.award(playerId, "bee", 4).join();
        assertEquals(7, service.snapshot(playerId, "bee").totalXp());
        clock.addAndGet(10_001);
        assertEquals(25, service.boosterPercent(playerId, "companion_xp"));
    }
    @Test
    void personalTimePausesOfflineWhileGlobalTimeContinuesAndOfflineCancellationWorks() {
        service.grantCredits(playerId, "personal", 1).join();
        service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join();
        service.startGlobal("server").join();
        clock.addAndGet(2_000); service.unload(playerId).join();
        clock.addAndGet(60_000); service.load(playerId).join();
        assertEquals(25, service.boosterPercent(playerId, "companion_xp"));
        assertEquals(8_000, service.snapshot(playerId, "bee").personalBoosters().getFirst().remainingMillis());
        String activation = service.snapshot(playerId, "bee").personalBoosters().getFirst().activationId();
        service.unload(playerId).join();
        assertTrue(service.cancelBooster(activation).join().success());
        service.load(playerId).join();
        assertTrue(service.snapshot(playerId, "bee").personalBoosters().isEmpty());
    }
    @Test
    void shutdownFreezesPersonalDurationAndDisabledTargetDoesNotCreatePurchaseJournal() {
        service.grantXp(playerId, "bee", 100).join();
        service.targetEnabled(target -> false);
        assertEquals(ProgressionService.Status.LOCKED, service.purchase(player, "bee", "speed", 0, service.snapshot(playerId, "bee").revision()).join().status());
        assertTrue(service.unresolvedPurchases().join().isEmpty());
        service.grantCredits(playerId, "personal", 1).join();
        service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join();
        clock.addAndGet(3_000); service.closeAsync().join();
        assertEquals(0, service.bonusAddition(playerId, "bee", "effect:minecraft:speed"));
        clock.addAndGet(50_000); service = create(); service.load(playerId).join();
        assertEquals(7_000, service.snapshot(playerId, "bee").personalBoosters().getFirst().remainingMillis());
    }
    @Test
    void globalActivationIdCanBeCancelledAndCreditsRoundTripThroughSql() {
        service.grantCredits(playerId, "personal", 7).join();
        ProgressionService.MutationResult started = service.startGlobal("server").join();
        assertFalse(started.transactionId().isBlank());
        assertTrue(service.cancelBooster(started.transactionId()).join().success());
        assertTrue(service.globalBoosters().isEmpty());
        service.closeAsync().join(); service = create(); service.load(playerId).join();
        assertEquals(7, service.snapshot(playerId, "bee").credits().get("personal"));
    }
    @Test
    void fractionalXpSurvivesRestartAndAdminGrantsWithoutRefreshingUnchangedBonuses() {
        AtomicInteger changes = new AtomicInteger();
        service.onChanged(ignored -> changes.incrementAndGet());
        service.grantCredits(playerId, "personal", 1).join();
        assertEquals(0, changes.get());
        service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join();
        changes.set(0);
        service.award(playerId, "bee", 1).join();
        service.award(playerId, "bee", 1).join();
        assertEquals(2, service.snapshot(playerId, "bee").totalXp());
        assertEquals(0, changes.get());
        service.closeAsync().join(); service = create(); service.load(playerId).join();
        service.grantXp(playerId, "bee", 10).join();
        service.award(playerId, "bee", 1).join();
        service.award(playerId, "bee", 1).join();
        assertEquals(15, service.snapshot(playerId, "bee").totalXp());
        service.onChanged(ignored -> changes.incrementAndGet());
        service.grantXp(playerId, "bee", 85).join();
        assertEquals(1, changes.get());
    }
    @Test
    void failedInitializationCanStillDrainAndClose() {
        service.closeAsync().join();
        pool.close();
        service = new ProgressionService(plugin, scope -> new SqlProgressionStore(connection, scope), Runnable::run, definitions(), true, true,
                () -> true, clock::get, ignored -> true, false);
        assertThrows(RuntimeException.class, () -> service.initializeAsync().join());
        service.closeAsync().join();
        assertFalse(service.active());
    }
    @Test
    void sqlRollbackDoesNotPublishPartialXpOrTierChanges() throws Exception {
        ProgressionStore store = new SqlProgressionStore(connection, "test");
        assertThrows(Exception.class, () -> store.transaction(transaction -> {
            ProgressionService.PlayerState previous = transaction.player(playerId);
            transaction.player(playerId, new ProgressionService.PlayerState(10,
                    Map.of("bee", new ProgressionService.Progress(100, Map.of("speed", 1), 250_000)),
                    previous.credits(), previous.boosters(), previous.checkpoint(), previous.online()));
            throw new IllegalStateException("Intentional transaction failure");
        }));
        assertTrue(store.transaction(transaction -> transaction.player(playerId)).companions().isEmpty());
        assertEquals(0, service.snapshot(playerId, "bee").totalXp());
    }
    @Test
    void checkpointNotifiesOnlyOnQueueBoundariesAndExpiry() {
        service.grantCredits(playerId, "personal", 2).join();
        service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join();
        service.activate(player, "personal", service.snapshot(playerId, "bee").revision()).join();
        AtomicInteger changes = new AtomicInteger();
        service.onChanged(ignored -> changes.incrementAndGet());
        clock.addAndGet(1_000); service.checkpointAsync().join();
        assertEquals(0, changes.get());
        clock.addAndGet(9_000); service.checkpointAsync().join();
        assertEquals(1, changes.get());
        assertEquals(25, service.boosterPercent(playerId, "companion_xp"));
        assertEquals(1, service.snapshot(playerId, "bee").personalBoosters().size());
        clock.addAndGet(10_000); service.checkpointAsync().join();
        assertEquals(2, changes.get());
        assertEquals(0, service.boosterPercent(playerId, "companion_xp"));
        assertTrue(service.snapshot(playerId, "bee").personalBoosters().isEmpty());
        clock.addAndGet(1_000); service.checkpointAsync().join();
        assertEquals(2, changes.get());
    }
    private static ProgressionDefinitions definitions() {
        ProgressionDefinitions.Cost free = new ProgressionDefinitions.Cost(0, Map.of());
        ProgressionDefinitions.TierDefinition first = new ProgressionDefinitions.TierDefinition(2, free, Map.of("effect:minecraft:speed", 2D));
        ProgressionDefinitions.TierDefinition second = new ProgressionDefinitions.TierDefinition(2, free, Map.of("effect:minecraft:speed", 1D));
        return new ProgressionDefinitions("test", true, 100, 10, 1000, Map.of("block_break", 1L), Set.of(), List.of(0L, 100L),
                Map.of("bee", new ProgressionDefinitions.CompanionDefinition(Map.of("speed", new ProgressionDefinitions.TrackDefinition("Speed", List.of(first, second))), Map.of(2, Map.of("effect:minecraft:speed", 1D)))),
                Map.of("personal", new ProgressionDefinitions.BoosterDefinition("Personal", ProgressionDefinitions.Scope.PERSONAL, Map.of("companion_xp", 25D), 10_000, ProgressionDefinitions.Stacking.QUEUE, free),
                        "server", new ProgressionDefinitions.BoosterDefinition("Server", ProgressionDefinitions.Scope.SERVER, Map.of("companion_xp", 50D), 10_000, ProgressionDefinitions.Stacking.QUEUE, free)));
    }
    private interface Call { Object invoke(String name, Object[] arguments); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return type.getSimpleName();
            return call.invoke(method.getName(), args);
        }));
    }
}
