package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.cache.PlayerBuffCache;
import com.volmit.bytecompanions.features.buffs.configuration.Configuration;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.manager.CooldownManager;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;
import com.volmit.bytecompanions.features.buffs.util.Operation;
import com.volmit.bytecompanions.features.buffs.service.BuffService;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.progression.SqlProgressionStore;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionBonusServiceTest {
    @Test
    void readsCachedWinnerInsteadOfGuessingTieOrder() throws Exception {
        Fixture fixture = new Fixture();
        fixture.winner(BuffKey.EXPERIENCE_BLOCK, "owl", 5);
        CompanionBonusService.BuffBonus bonus = fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK);
        assertEquals(CompanionBonusService.Status.OVERRIDDEN, bonus.status());
        assertEquals("owl", bonus.winnerId());
        assertEquals(5, bonus.priority());
        assertEquals(5, bonus.winnerPriority());
        assertEquals(Operation.MULTIPLY, bonus.operation());
        assertEquals(1.5, bonus.value());
        assertEquals(2.5, bonus.winnerValue());
    }

    @Test
    void openingDashboardNeitherConsumesNorResetsCooldown() throws Exception {
        Fixture fixture = new Fixture();
        fixture.winner(BuffKey.EXPERIENCE_BLOCK, "bee", 5);
        assertEquals(CompanionBonusService.Status.ACTIVE, fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK).status());
        assertTrue(fixture.cooldowns.tryUse(42, BuffKey.EXPERIENCE_BLOCK, 1200));
        CompanionBonusService.BuffBonus bonus = fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK);
        assertEquals(CompanionBonusService.Status.COOLDOWN, bonus.status());
        assertTrue(bonus.remainingCooldownMillis() > 0 && bonus.remainingCooldownMillis() <= 60_000);
        fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK);
        assertFalse(fixture.cooldowns.tryUse(42, BuffKey.EXPERIENCE_BLOCK, 1200));
        assertTrue(fixture.cooldowns.remainingMillis(42, BuffKey.EXPERIENCE_BLOCK, 1200) <= bonus.remainingCooldownMillis());
    }

    @Test
    void pickupFallbackExcludesSpecificSourcesEvenWhileTheirCooldownIsActive() throws Exception {
        Fixture fixture = new Fixture();
        fixture.winner(BuffKey.EXPERIENCE, "bee", 5);
        fixture.winner(BuffKey.EXPERIENCE_BLOCK, "owl", 5);
        fixture.cooldowns.tryUse(42, BuffKey.EXPERIENCE_BLOCK, 1200);
        assertEquals(Set.of(BuffKey.EXPERIENCE_BLOCK), fixture.bonus("bee", BuffKey.EXPERIENCE).excludedSources());
    }

    @Test
    void pickupExclusionsFollowBoosterOnlySourcesAndTheirCancellation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.winner(BuffKey.EXPERIENCE, "bee", 5);
        ProgressionDefinitions definitions = new ProgressionDefinitions("test", true, 300, 10, 1000,
                Map.of(), Set.of(), List.of(0L), Map.of(), Map.of("mining",
                new ProgressionDefinitions.BoosterDefinition("Mining", ProgressionDefinitions.Scope.SERVER,
                        Map.of("experience_block", 25D), 60_000, ProgressionDefinitions.Stacking.QUEUE,
                        new ProgressionDefinitions.Cost(0, Map.of()))));
        Plugin plugin = proxy(Plugin.class, method -> method.equals("getLogger") ? Logger.getAnonymousLogger() : null);
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
            pool.setMaximumPoolSize(2);
            SQLStorageConnection connection = new SQLStorageConnection(pool, SqlDialect.H2, Logger.getAnonymousLogger());
            connection.connect();
            ProgressionService progression = new ProgressionService(plugin, scope -> new SqlProgressionStore(connection, scope), Runnable::run, definitions,
                    false, true, () -> true);
            try {
                progression.initializeAsync().join();
                progression.load(fixture.id).join();
                set(fixture.feature, "buffService", new BuffService(fixture.cache, fixture.cooldowns, progression));
                assertTrue(fixture.bonus("bee", BuffKey.EXPERIENCE).excludedSources().isEmpty());
                ProgressionService.MutationResult activation = progression.startGlobal("mining").join();
                assertTrue(fixture.cache.get(fixture.player, BuffKey.EXPERIENCE_BLOCK).isEmpty());
                assertEquals(Set.of(BuffKey.EXPERIENCE_BLOCK), fixture.bonus("bee", BuffKey.EXPERIENCE).excludedSources());
                progression.cancelBooster(activation.transactionId()).join();
                assertTrue(fixture.bonus("bee", BuffKey.EXPERIENCE).excludedSources().isEmpty());
            } finally {
                progression.closeAsync().join();
            }
        }
    }

    @Test
    void disabledDefinitionsAndUnequippedSelectionsAreNeverReportedActive() throws Exception {
        Fixture fixture = new Fixture();
        assertEquals(CompanionBonusService.Status.DISABLED, fixture.bonus("bee", BuffKey.MOB_LOOT).status());
        fixture.equipped.remove("bee");
        assertEquals(CompanionBonusService.Status.NOT_EQUIPPED, fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK).status());
        CompanionBonusService.Snapshot snapshot = new CompanionBonusService(null, null, null, player -> true)
                .snapshot(fixture.player, fixture.state, "bee");
        assertTrue(snapshot.enabledFeatures().isEmpty());
        assertTrue(snapshot.buffs().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.buffs().add(null));
    }

    @Test
    void rejectsForeignOwnerBeforeReadingPlayerState() {
        CompanionBonusService service = new CompanionBonusService(null, null, null, player -> false);
        assertThrows(IllegalStateException.class, () -> service.snapshot(null, null, "bee"));
    }

    @Test
    void restrictionsKeepSavedBuffDefinitionsVisibleButInactive() throws Exception {
        Fixture fixture = new Fixture();
        fixture.winner(BuffKey.EXPERIENCE_BLOCK, "bee", 5);
        fixture.feature.buffService().bonusesAllowed(player -> false);
        CompanionBonusService.BuffBonus bonus = fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK);
        assertEquals(CompanionBonusService.Status.INACTIVE, bonus.status());
        assertEquals("bee", bonus.winnerId());
        assertEquals(1.5, bonus.value());
        assertTrue(fixture.equipped.containsKey("bee"));
        assertEquals("bee", fixture.cache.get(fixture.player, BuffKey.EXPERIENCE_BLOCK).orElseThrow().companionTypeId());
        fixture.feature.buffService().bonusesAllowed(player -> true);
        assertEquals(CompanionBonusService.Status.ACTIVE, fixture.bonus("bee", BuffKey.EXPERIENCE_BLOCK).status());
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName())));
    }

    private interface Invocation {
        Object call(String method);
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final Player player = proxy(Player.class, method -> switch (method) {
            case "getUniqueId" -> this.id;
            case "getEntityId" -> 42;
            default -> throw new AssertionError(method);
        });
        private final Map<String, Companion> equipped = new LinkedHashMap<>();
        private final CompanionPlayer state = proxy(CompanionPlayer.class, method -> switch (method) {
            case "playerId" -> this.id;
            case "companions" -> this.equipped;
            default -> throw new AssertionError(method);
        });
        private final BuffsFeature feature = new BuffsFeature(null, null, null);
        private final PlayerBuffCache cache = new PlayerBuffCache();
        private final CooldownManager cooldowns = new CooldownManager();
        private final Map<BuffKey, ResolvedBuff> winners = new EnumMap<>(BuffKey.class);
        private final CompanionBonusService service = new CompanionBonusService(null, null, this.feature, player -> true);

        private Fixture() throws Exception {
            ConfigurationNode root = BasicConfigurationNode.root();
            for (String companionId : List.of("bee", "owl")) {
                this.equipped.put(companionId, proxy(Companion.class, method -> { throw new AssertionError(method); }));
                root.node("companions", companionId, "default-priority").set(5);
                ConfigurationNode block = root.node("companions", companionId, "vanilla", "experience-block");
                block.node("operation").set("MULTIPLY");
                block.node("value").set(companionId.equals("bee") ? 1.5 : 2.5);
                block.node("cooldown").set(1200);
                root.node("companions", companionId, "vanilla", "mob-loot", "enabled").set(false);
            }
            set(this.feature, "configuration", root.get(Configuration.class));
            set(this.feature, "active", true);
            set(this.feature, "playerBuffCache", this.cache);
            set(this.feature, "cooldownManager", this.cooldowns);
            set(this.feature, "buffService", new com.volmit.bytecompanions.features.buffs.service.BuffService(this.cache, this.cooldowns, null));
        }

        private void winner(BuffKey key, String companionId, int priority) {
            Buff buff = key == BuffKey.EXPERIENCE ? this.feature.companions(companionId).vanilla().experience()
                    : this.feature.companions(companionId).vanilla().experienceBlock();
            this.winners.put(key, new ResolvedBuff(companionId, priority, buff));
            this.cache.refresh(this.player, this.winners);
        }

        private CompanionBonusService.BuffBonus bonus(String companionId, BuffKey key) {
            return this.service.snapshot(this.player, this.state, companionId).buffs().stream()
                    .filter(bonus -> bonus.key() == key).findFirst().orElseThrow();
        }
    }
}
