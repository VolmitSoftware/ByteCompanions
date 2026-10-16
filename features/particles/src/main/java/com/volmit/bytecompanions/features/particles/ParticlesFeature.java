package com.volmit.bytecompanions.features.particles;

import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import java.io.IOException;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.features.particles.adapternms.NMSAdapter;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.v26_1.V26_1NMSAdapter;
import com.volmit.bytecompanions.features.particles.adventure.ComponentSerializerAdapter;
import com.volmit.bytecompanions.features.particles.adventure.ComponentSerializerFactory;
import com.volmit.bytecompanions.features.particles.command.ParticlesCommand;
import com.volmit.bytecompanions.features.particles.configuration.ParticlesConfiguration;
import com.volmit.bytecompanions.features.particles.configuration.StorageConfiguration;
import com.volmit.bytecompanions.features.particles.configuration.ParticleIconsConfiguration;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.features.particles.listener.CompanionListener;
import com.volmit.bytecompanions.features.particles.manager.PlayerEffectManager;
import com.volmit.bytecompanions.features.particles.messages.MessageSender;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectEntry;
import com.volmit.bytecompanions.features.particles.registry.ParticleEffectRegistry;
import com.volmit.bytecompanions.features.particles.storage.StorageConnection;
import com.volmit.bytecompanions.features.particles.storage.UserStorage;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcCredentials;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcPoolConfig;
import com.volmit.bytecompanions.features.particles.storage.data.H2StorageConnectionData;
import com.volmit.bytecompanions.features.particles.storage.data.MariaDBStorageConnectionData;
import com.volmit.bytecompanions.features.particles.storage.data.MySQLStorageConnectionData;
import com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLStorageConnection;
import com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLUserStorage;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprint;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;

import java.io.File;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class ParticlesFeature {
    private volatile boolean active;
    private CompletableFuture<Void> stopped;

    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private CompanionListener listener;
    private BukkitCommand command;

    public ParticlesFeature(JavaPlugin plugin, ByteCompanions companions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.companions = Objects.requireNonNull(companions, "companions");
    }

    public BukkitCommand command() {
        return Objects.requireNonNull(this.command, "Particles feature is not enabled");
    }

    private static volatile NMSAdapter nmsAdapter = null;

    public static NMSAdapter getNMSAdapter() {
        return Objects.requireNonNull(nmsAdapter, "NMSAdapter is null");
    }

    private ExecutorService executorService;
    private ParticleEffectRegistry particleEffectRegistry;
    private ParticlesConfiguration particlesConfiguration;
    private ParticleIconsConfiguration iconsConfiguration;

    private PlayerEffectManager playerEffectManager;
    private StorageConnection storageConnection;
    private UserStorage userStorage;

    private ComponentSerializerAdapter serializerAdapter;
    private MessageSender messageSender;

    private ItemBlueprintRegistry itemBlueprintRegistry;

    public void enable() {
        nmsAdapter = this.createNMSAdapter();
        this.active = true;

        this.playerEffectManager = new PlayerEffectManager(this, this.plugin, this.companions);

        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.particlesConfiguration = this.loadConfiguration("config", ParticlesConfiguration.class);

        this.itemBlueprintRegistry = ItemBlueprintRegistry.create(TextSerializerFactory.create(this.particlesConfiguration.settings().serializer()));

        this.iconsConfiguration = this.loadConfiguration("icons", ParticleIconsConfiguration.class);

        this.serializerAdapter = ComponentSerializerFactory.create(this.particlesConfiguration.settings().serializer());
        this.messageSender = new MessageSender(((com.volmit.bytecompanions.platform.paper.PaperPlugin) plugin).languageService());

        this.particleEffectRegistry = new ParticleEffectRegistry();
        this.registerParticleEffects();
        this.registerAnimations();

        this.initializeStorage();
        this.listener = new CompanionListener(this);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.command = new ParticlesCommand(this, this.companions,
                ((com.volmit.bytecompanions.platform.paper.PaperPlugin) plugin).languageService());
    }

    public synchronized CompletableFuture<Void> stopAsync() {
        if (this.stopped != null) {
            return this.stopped;
        }
        this.active = false;
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
        this.stopped = this.playerEffectManager == null ? CompletableFuture.completedFuture(null)
                : this.playerEffectManager.detachAllAsync();
        return this.stopped;
    }

    public boolean active() {
        return this.active;
    }

    public CompanionViewerEnvironment viewerEnvironment() {
        return ((PaperPlugin) this.plugin).viewerEnvironment();
    }

    public void closeResources() {
        if (this.stopped == null || !this.stopped.isDone()) {
            throw new IllegalStateException("Particle owner cleanup must finish before closing resources");
        }
        if (this.particleEffectRegistry != null) {
            for (ParticleEffectEntry entry : this.particleEffectRegistry.entries()) {
                this.companions.animationRegistry().unregister(AnimationChannel.POST_TRACKING, entry.id());
            }
        }
        if (this.userStorage instanceof com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLUserStorage sqlStorage) {
            sqlStorage.closeAsync().join();
        }
        if (this.executorService != null) {
            this.executorService.close();
            this.executorService = null;
        }
        if (this.storageConnection != null) {
            this.storageConnection.disconnect();
            this.storageConnection = null;
        }
        if (this.itemBlueprintRegistry != null) {
            this.itemBlueprintRegistry.clear();
        }
        this.command = null;
        nmsAdapter = null;
    }

    @Contract(value = " -> new", pure = true)
    @NotNull NMSAdapter createNMSAdapter() {
        return new V26_1NMSAdapter();
    }

    public static void validateCompatibility() {
        NMSAdapter adapter = new V26_1NMSAdapter();
        ParticleKind[] kinds = ParticleKind.values();
        ParticleBatch batch = adapter.createBatch(kinds.length + 2);
        for (int index = 0; index < kinds.length; index++) {
            batch.set(index, kinds[index], 0, 0, 0, 0, 0, 0, 0, 1);
        }
        batch.set(kinds.length, new DustStyle(1, 0, 0, 1), 0, 0, 0, 0, 0, 0, 0, 1);
        batch.setColored(kinds.length + 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        batch.clear();
    }

    void initializeStorage() {
        final StorageConfiguration storageConfiguration = this.particlesConfiguration.storage();
        final JdbcPoolConfig jdbcPoolConfig = new JdbcPoolConfig(
                storageConfiguration.pool().connectionTimeout(),
                storageConfiguration.pool().maxLifetime(),
                storageConfiguration.pool().maxPoolSize(),
                storageConfiguration.pool().minIdle()
        );

        final JdbcCredentials jdbcCredentials = new JdbcCredentials(
                storageConfiguration.credentials().host(),
                storageConfiguration.credentials().port(),
                storageConfiguration.credentials().database(),
                storageConfiguration.credentials().username(),
                storageConfiguration.credentials().password(),
                storageConfiguration.credentials().useSSL()
        );

        final String type = storageConfiguration.type().toUpperCase(Locale.ROOT);
        final HikariDataSource hikariDataSource = switch (type) {
            case "H2" -> {
                final File file = this.plugin.getDataFolder().toPath().resolve("features/particles").resolve(storageConfiguration.h2().parentPath() + "/database").toFile();
                yield H2StorageConnectionData.builder()
                        .file(file)
                        .pool(jdbcPoolConfig)
                        .build();
            }
            case "MYSQL" -> MySQLStorageConnectionData.builder()
                    .credentials(jdbcCredentials)
                    .pool(jdbcPoolConfig)
                    .build();

            case "MARIADB" -> MariaDBStorageConnectionData.builder()
                    .credentials(jdbcCredentials)
                    .pool(jdbcPoolConfig)
                    .build();

            default -> throw new IllegalArgumentException("An invalid storage type was typed. ''" + type + "''");
        };

        LoggerFactory.getLogger(this.plugin.getName()).info("Initializing connection to database: {}", type);
        this.storageConnection = new AbstractSQLStorageConnection(LoggerFactory.getLogger(this.plugin.getName()), hikariDataSource);
        this.userStorage = new AbstractSQLUserStorage(LoggerFactory.getLogger(this.plugin.getName()), hikariDataSource, this.executorService);
        this.storageConnection.connect();
    }

    void registerParticleEffects() {
        ParticleEffectCatalog.factories().forEach(this::registerParticleEffectById);
    }

    void registerParticleEffectById(
            final @NotNull String id,
            final @NotNull Supplier<ParticleEffect> factory
    ) {
        final ParticleEffectEntry particleEffectEntry = this.buildEntry(id, factory);
        this.particleEffectRegistry.register(particleEffectEntry);
    }

    @NotNull ParticleEffectEntry buildEntry(
            final @NotNull String id,
            final @NotNull Supplier<ParticleEffect> factory
    ) {
        final ConfigurationNode configurationNode = this.iconsConfiguration.items().get(id);
        final ItemBlueprint itemBlueprint = this.itemBlueprintRegistry.compile(configurationNode).orElseThrow();
        return new ParticleEffectEntry(id, itemBlueprint, factory);
    }

    <T> T loadConfiguration(
            final @NotNull String fileName,
            final @NotNull Class<T> tClass
    ) {
        try {
            final ConfigurationNode configurationNode = ((PaperPlugin) this.plugin).contentSnapshot()
                    .node("features/particles/" + fileName + ".toml", ConfigurationOptions.defaults());
            return Objects.requireNonNull(configurationNode.get(tClass), "Failed to deserialize " + fileName + ".toml");
        } catch (IOException e) {
            throw new RuntimeException(new ConfigurateException("Failed to load configuration file.", e));
        }
    }

    void registerAnimations() {
        for (final ParticleEffectEntry particleEffectEntry : this.particleEffectRegistry.entries()) {
            this.registerAnimation(particleEffectEntry.id(), particleEffectEntry.factory());
        }
    }

    void registerAnimation(final @NotNull String animationId, final @NotNull Supplier<ParticleEffect> factory) {
        final AnimationRegistry animationRegistry = this.companions.animationRegistry();
        animationRegistry.register(AnimationChannel.POST_TRACKING, animationId, animationData ->
                new EffectTrackingAnimation(factory.get(), this.particlesConfiguration.settings().movement(), this.viewerEnvironment(), this::active)
        );
    }

    public ItemBlueprintRegistry itemBlueprintRegistry() {
        return this.itemBlueprintRegistry;
    }

    public StorageConnection storageConnection() {
        return this.storageConnection;
    }

    public UserStorage userStorage() {
        return this.userStorage;
    }

    public MessageSender messageSender() {
        return this.messageSender;
    }

    public ParticleEffectRegistry particleEffectRegistry() {
        return this.particleEffectRegistry;
    }

    public ParticlesConfiguration particlesConfiguration() {
        return this.particlesConfiguration;
    }

    public PlayerEffectManager playerEffectManager() {
        return this.playerEffectManager;
    }

    public ParticleIconsConfiguration iconsConfiguration() {
        return this.iconsConfiguration;
    }

    public ComponentSerializerAdapter serializerAdapter() {
        return this.serializerAdapter;
    }
}
