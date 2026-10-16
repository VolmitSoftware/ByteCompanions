package com.volmit.bytecompanions.platform.paper.storage;

import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.access.ByteCompanionsProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.core.manager.DefaultUserManager;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import com.volmit.bytecompanions.platform.paper.manager.CompanionPlayerManager;
import com.volmit.bytecompanions.platform.paper.messenger.MessageDispatcher;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
@ResourceLock("bukkit-server")
class CompanionSelectionRecoveryTest {
    @TempDir Path directory;
    private final UUID playerId = UUID.randomUUID();
    private final Set<String> cancelledRemovals = new HashSet<>();
    private final List<UserEntry> submitted = new ArrayList<>();
    private final CompanionPlayerCache players = new CompanionPlayerCache();
    private final DefaultCompanionPlayer selection = new DefaultCompanionPlayer(playerId, "Tester");
    private FaultSource source;
    private VirtualThreadAsyncExecutor executor;
    private SQLUserStorage storage;
    private CompanionPlayerManager manager;
    private Player player;
    private CompletableFuture<?> lastWrite;
    private int writes;
    private boolean cancelEquip;
    private Field serverField;
    private Object previousServer;

    @BeforeEach
    void open() throws Exception {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        UnsafeValues unsafe = proxy(UnsafeValues.class, (method, arguments) -> null);
        Server server = proxy(Server.class, (method, arguments) -> switch (method) {
            case "getLogger" -> logger;
            case "getUnsafe" -> unsafe;
            default -> throw new AssertionError(method);
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        JavaPlugin plugin = new DisabledPlugin(server, directory.toFile());
        assertFalse(plugin.isEnabled());

        source = new FaultSource();
        source.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        source.setMaximumPoolSize(2);
        executor = new VirtualThreadAsyncExecutor();
        SQLStorageConnection connection = new SQLStorageConnection(source, logger);
        connection.connect();
        storage = new SQLUserStorage(logger, connection, executor);
        UserStorage.Async delegate = storage.async();
        UserStorage.Async trackedAsync = UserStorage.Async.class.cast(Proxy.newProxyInstance(
                UserStorage.Async.class.getClassLoader(), new Class<?>[]{UserStorage.Async.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("save")) submitted.add((UserEntry) arguments[0]);
                    lastWrite = (CompletableFuture<?>) method.invoke(delegate, arguments);
                    writes++;
                    return lastWrite;
                }));
        UserStorage trackedStorage = proxy(UserStorage.class, (method, arguments) -> {
            if (method.equals("async")) return trackedAsync;
            throw new AssertionError(method);
        });
        Thread owner = Thread.currentThread();
        player = proxy(Player.class, (method, arguments) -> {
            assertSame(owner, Thread.currentThread(), "Player state must be captured on its owner");
            return switch (method) {
                case "getUniqueId" -> playerId;
                case "getEntityId" -> 100;
                case "getName" -> "Tester";
                case "hasPermission" -> true;
                case "spigot", "sendMessage", "sendRichMessage" -> null;
                default -> throw new AssertionError(method);
            };
        });
        players.put(player, selection);
        AtomicInteger ids = new AtomicInteger();
        CompanionFactory factory = proxy(CompanionFactory.class, (method, arguments) -> {
            CompanionType type = (CompanionType) arguments[1];
            int entityId = ids.incrementAndGet();
            return proxy(Companion.class, (call, values) -> switch (call) {
                case "type" -> type;
                case "isSpawned" -> true;
                case "entityId" -> entityId;
                case "formationIndex", "destroy" -> null;
                default -> throw new AssertionError(call);
            });
        });
        CompanionEventBus events = proxy(CompanionEventBus.class, (method, arguments) -> switch (method) {
            case "callEquip" -> !cancelEquip;
            case "callUnequip" -> !cancelledRemovals.contains(((Companion) arguments[1]).type().id());
            case "callSpawn", "callDespawn" -> null;
            default -> throw new AssertionError(method);
        });
        DefaultCompanionManager companions = new DefaultCompanionManager(null, factory, players, events,
                new CompanionEntityCache());
        manager = new CompanionPlayerManager(plugin, new DefaultUserManager(players, null), companions,
                new MessageDispatcher(new LanguageService(directory.toFile(), logger), () -> false), null,
                trackedStorage, new Configuration.CompanionLimits());
        ByteCompanionsProvider.setInstance(proxy(ByteCompanions.class, (method, arguments) -> {
            if (method.equals("getCompanionPlayerOrNull")) return players.getByUniqueId((UUID) arguments[0]);
            throw new AssertionError(method);
        }));
    }

    @AfterEach
    void close() throws Exception {
        try {
            if (source != null) source.release.countDown();
            if (storage != null) storage.closeAsync().get(5, TimeUnit.SECONDS);
        } finally {
            if (executor != null) executor.shutdown();
            if (source != null) source.close();
            ByteCompanionsProvider.resetInstance();
            if (serverField != null) serverField.set(null, previousServer);
        }
    }

    @Test
    void failedRemovalThenReequipDoesNotDuplicateTheStoredCompanion() throws Exception {
        equip("bee");
        failWrite(() -> manager.despawn(player, "bee"));
        assertEquals(List.of("bee"), stored());
        equip("bee");
        assertEquals(List.of("bee"), stored());
    }

    @Test
    void failedRemovalThenDifferentEquipRemovesTheStaleStoredCompanion() throws Exception {
        equip("bee");
        failWrite(() -> manager.despawn(player, "bee"));
        equip("fox");
        assertEquals(List.of("fox"), stored());
    }

    @Test
    void laterEquipAlsoPersistsTheSelectionWhoseEarlierWriteFailed() throws Exception {
        failWrite(() -> manager.spawn(player, type("bee")));
        assertEquals(List.of(), stored());
        equip("fox");
        assertEquals(List.of("bee", "fox"), stored());
    }

    @Test
    void successfulSingleRemovalAlsoPersistsTheOtherUnsavedSelection() throws Exception {
        equip("bee");
        failWrite(() -> manager.spawn(player, type("fox")));
        manager.despawn(player, "bee");
        awaitWrite();
        assertEquals(List.of("fox"), stored());
    }

    @Test
    void partialCancellationKeepsTheCancelledSelectionAndAllRemovalSavesEmptyState() throws Exception {
        equip("bee");
        equip("fox");
        cancelledRemovals.add("bee");
        manager.despawn(player);
        awaitWrite();
        assertEquals(Set.of("bee"), selection.companions().keySet());
        assertEquals(List.of("bee"), stored());
        cancelledRemovals.clear();
        manager.despawn(player);
        awaitWrite();
        assertTrue(selection.companions().isEmpty());
        assertEquals(List.of(), stored());
    }

    @Test
    void successfulBulkRemovalAlsoErasesRowsLeftByAnEarlierFailedRemoval() throws Exception {
        equip("bee");
        equip("fox");
        failWrite(() -> manager.despawn(player, "bee"));
        manager.despawn(player);
        awaitWrite();
        assertEquals(List.of(), stored());
    }

    @Test
    void cancelledAndUnloadedMutationsNeverSubmitAWrite() throws Exception {
        equip("bee");
        int before = writes;
        cancelEquip = true;
        manager.spawn(player, type("fox"));
        cancelledRemovals.add("bee");
        manager.despawn(player, "bee");
        manager.despawn(player);
        players.remove(player);
        manager.spawn(player, type("fox"));
        manager.despawn(player, "bee");
        manager.despawn(player);
        assertEquals(before, writes);
        assertEquals(List.of("bee"), stored());
    }

    @Test
    void queuedSavesKeepTheirOwnerCapturedSelectionWhenThePlayerChangesAgain() throws Exception {
        source.blockNext.set(true);
        manager.spawn(player, type("bee"));
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        manager.spawn(player, type("fox"));
        assertEquals(List.of("bee"), List.copyOf(submitted.getFirst().companionTypeIds()));
        assertEquals(Set.of("bee", "fox"), Set.copyOf(submitted.getLast().companionTypeIds()));
        assertFalse(lastWrite.isDone());
        source.release.countDown();
        awaitWrite();
        assertEquals(List.of("bee", "fox"), stored());
    }

    private void equip(String id) throws Exception {
        manager.spawn(player, type(id));
        awaitWrite();
    }

    private void awaitWrite() throws Exception {
        lastWrite.get(5, TimeUnit.SECONDS);
    }

    private void failWrite(Runnable mutation) {
        source.fail.set(true);
        try {
            mutation.run();
            assertThrows(ExecutionException.class, () -> lastWrite.get(5, TimeUnit.SECONDS));
        } finally {
            source.fail.set(false);
        }
    }

    private List<String> stored() {
        var view = storage.fetch(playerId);
        return view == null ? List.of() : view.companionTypeIds().stream().sorted().toList();
    }

    private static CompanionType type(String id) {
        return proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "id" -> id;
            case "model" -> id.equals("bee") ? CompanionModel.HEAD : CompanionModel.FULL_BODY;
            case "exclusive" -> false;
            case "animations" -> Map.of(AnimationChannel.TRACKING, Map.of());
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class DisabledPlugin extends JavaPlugin {
        private DisabledPlugin(Server server, File directory) {
            super(new JavaPluginLoader(server), new PluginDescriptionFile("SelectionRecoveryTest", "1", "test.Plugin"),
                    directory, new File(directory, "test.jar"));
        }
    }

    private static final class FaultSource extends HikariDataSource {
        private final AtomicBoolean fail = new AtomicBoolean();
        private final AtomicBoolean blockNext = new AtomicBoolean();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Connection getConnection() throws SQLException {
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("Test connection was not released");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(failure);
                }
            }
            if (fail.get()) throw new SQLException("Database unavailable");
            return super.getConnection();
        }
    }
}
