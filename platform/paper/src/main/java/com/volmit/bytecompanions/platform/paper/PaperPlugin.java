package com.volmit.bytecompanions.platform.paper;

import com.volmit.bytecompanions.platform.paper.presentation.ChatMenuStyle;
import com.volmit.bytecompanions.platform.paper.metrics.MetricsService;

import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.integration.ReloadPreparation;
import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import art.arcane.volmlib.util.update.BukkitUpdateService;
import art.arcane.volmlib.util.config.BukkitConfigEditor;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import com.volmit.bytecompanions.platform.paper.util.lifecycle.AsyncTeardown;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import com.volmit.bytecompanions.platform.paper.bootstrap.H2ResourceRelocator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.adapter_v26_1.V26_1CompanionEntityFactory;
import com.volmit.bytecompanions.adapter_v26_3.V26_3CompanionEntityFactory;
import com.volmit.bytecompanions.adapter_spigot26_1.Spigot26_1CompanionEntityFactory;
import com.volmit.bytecompanions.adapter_spigot26_3.Spigot26_3CompanionEntityFactory;
import com.volmit.bytecompanions.adapter_spigot26_1.Spigot26_1InteractionListener;
import com.volmit.bytecompanions.platform.paper.compatibility.RuntimeCompatibility;
import com.volmit.bytecompanions.api.ConfigurationSettings;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.access.ByteCompanionsProvider;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.core.factory.UserCompanionFactory;
import com.volmit.bytecompanions.core.loader.DefaultUserLoader;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.core.manager.DefaultUserManager;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.platform.paper.command.CompanionBukkitCommand;
import com.volmit.bytecompanions.platform.paper.configuration.ConfigEditorLayout;
import com.volmit.bytecompanions.platform.paper.integration.CompanionIntegrationService;
import com.volmit.bytecompanions.platform.paper.presentation.SplashScreen;
import com.volmit.bytecompanions.platform.paper.companion.DefaultCompanionFactory;
import com.volmit.bytecompanions.platform.paper.companion.animations.BuiltinAnimationRegistry;
import com.volmit.bytecompanions.platform.paper.component.adapter.ComponentSerializerAdapter;
import com.volmit.bytecompanions.platform.paper.component.adapter.ComponentSerializerFactory;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.configuration.ConfigService;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight;
import com.volmit.bytecompanions.platform.paper.configuration.PreparedRuntimeConfiguration;
import com.volmit.bytecompanions.platform.paper.configuration.StarterCollection;
import com.volmit.bytecompanions.platform.paper.studio.CompanionDraft;
import com.volmit.bytecompanions.platform.paper.studio.CompanionPreviewDefinition;
import com.volmit.bytecompanions.platform.paper.studio.CompanionPreviewService;
import com.volmit.bytecompanions.platform.paper.collection.CollectionService;
import com.volmit.bytecompanions.platform.paper.collection.VariantCatalog;
import com.volmit.bytecompanions.platform.paper.integration.ServerPolicy;
import com.volmit.bytecompanions.platform.paper.integration.RuntimeServerRules;
import com.volmit.bytecompanions.platform.paper.integration.IntegrationProbe;
import com.volmit.bytecompanions.platform.paper.integration.worldguard.WorldGuardRegions;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehaviorPolicy;
import com.volmit.bytecompanions.platform.paper.ui.dashboard.CompanionStudio;
import com.volmit.bytecompanions.platform.paper.ui.dashboard.CompanionMenus;
import com.volmit.bytecompanions.platform.paper.ui.dashboard.CompanionBonusService;
import com.volmit.bytecompanions.platform.paper.diagnostics.CompanionDebugReport;
import com.volmit.bytecompanions.platform.paper.integration.placeholderapi.CompanionPlaceholders;
import com.volmit.bytecompanions.platform.paper.integration.placeholderapi.PlaceholderApiInstaller;
import com.volmit.bytecompanions.platform.paper.listener.PlayerGameModeChangeListener;
import com.volmit.bytecompanions.platform.paper.listener.PlayerJoinListener;
import com.volmit.bytecompanions.platform.paper.listener.PlayerQuitListener;
import com.volmit.bytecompanions.platform.paper.listener.PlayerUseUnknownEntityListener;
import com.volmit.bytecompanions.platform.paper.listener.ProgressionGameplayListener;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.listener.sync.PlayerChangedWorldListener;
import com.volmit.bytecompanions.platform.paper.listener.sync.PlayerDeathListener;
import com.volmit.bytecompanions.platform.paper.listener.sync.PlayerPostRespawnListener;
import com.volmit.bytecompanions.platform.paper.listener.sync.PlayerTeleportListener;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;
import com.volmit.bytecompanions.platform.paper.manager.CompanionPlayerManager;
import com.volmit.bytecompanions.platform.paper.messenger.MessageDispatcher;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import com.volmit.bytecompanions.platform.paper.storage.RuntimeStorage;
import com.volmit.bytecompanions.platform.paper.storage.RuntimeStorageFactory;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionsTickScheduler;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionViewerService;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.service.CompanionPlayerLifecyclePlatformService;
import com.volmit.bytecompanions.platform.paper.service.CompanionsSyncService;
import com.volmit.bytecompanions.spi.companions.NmsCompanionEntityFactory;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;
import com.volmit.bytecompanions.features.animations.AnimationsFeature;
import com.volmit.bytecompanions.features.attributes.AttributesFeature;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.decorations.DecorationsFeature;
import com.volmit.bytecompanions.features.effects.EffectsFeature;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public final class PaperPlugin extends JavaPlugin implements ByteCompanions, ReloadAware {

    private MetricsService metricsService;
    private BukkitUpdateService updateService;
    private CompanionIntegrationService integrationService;
    private PluginCommand rootCommand;
    private long enableStartedNanos;
    private boolean splashPrinted;
    private CompletableFuture<Void> stopCompletion;

    private ProgressionService progressionService;
    private ProgressionGameplayListener progressionGameplayListener;

    private AnimationRegistry animationRegistry;
    private volatile ContentSnapshot contentSnapshot;
    private final DefaultCompanionTypeRegistry companionTypeRegistry = new DefaultCompanionTypeRegistry();

    private AsyncExecutor asyncExecutor;

    private ComponentSerializerAdapter componentSerializerAdapter;

    private Configuration configuration;
    private CompanionMenus companionMenus;
    private CompanionStudio companionStudio;
    private CompanionPreviewService companionPreviews;
    private CollectionService collectionService;
    private VariantCatalog collectionVariants;
    private MessageDispatcher messageDispatcher;
    private ConfigService configService;
    private LanguageService languageService;
    private BukkitConfigEditor configEditor;
    private BukkitLanguageSwitcher languageSwitcher;
    private ExecutorService configurationExecutor;
    private final Set<CompletableFuture<Void>> pendingInstallations = ConcurrentHashMap.newKeySet();
    private volatile boolean closing;

    private DefaultCompanionFactory companionFactory;
    private DefaultCompanionManager companionManager;
    private CompanionsSyncService companionsSyncService;
    private CompanionPlayerLifecyclePlatformService lifecyclePlatformService;
    private CompanionPlayerManager companionPlayerManager;
    private UserCompanionFactory userCompanionFactory;

    private RuntimeStorage storage;

    private CompanionPlayerCache playerCache;
    private DefaultUserLoader userLoader;
    private DefaultUserManager userManager;
    private NmsCompanionEntityFactory nmsCompanionEntityFactory;
    private RuntimeCompatibility.Selection runtimeCompatibility;
    private Runnable stopInteractionListener;
    private CompanionEntityCache companionEntityCache;
    private CompanionEventBus companionEventBus;
    private AttributesFeature attributesFeature;
    private BuffsFeature buffsFeature;
    private DecorationsFeature decorationsFeature;
    private EffectsFeature effectsFeature;
    private ParticlesFeature particlesFeature;
    private CompanionPlaceholders placeholders;
    private PlaceholderApiInstaller placeholderInstaller;
    private CompanionsTickScheduler tickScheduler;
    private CompanionViewerService viewerService;
    private ServerPolicy serverPolicy;
    private RuntimeServerRules runtimeServerRules;
    private Function<Player, ServerPolicy.Decision> regionRules = player -> ServerPolicy.Decision.ALLOW;
    private volatile CompletableFuture<Void> runtimeStop;
    private volatile CompletableFuture<Void> runtimeOwnerStop;
    private final Map<String, BukkitCommand> featureCommands = new LinkedHashMap<>();
    private final List<Listener> runtimeListeners = new ArrayList<>();
    private volatile boolean runtimeActive;
    private volatile long runtimeGeneration;
    private BukkitDebugDump debugDump;

    // Menu

    // Item
    private ItemBlueprintRegistry itemBlueprintRegistry;

    public PaperPlugin() {
        RuntimeCompatibility.requireSupported(this.getServer().getBukkitVersion(), Runtime.version().feature());
        this.getLogger().info("Loading libraries...");
        H2ResourceRelocator.configure(new SpigotApplicationBuilder(this),
                this.getDataFolder().toPath().resolve(".libs")).build();
        this.getLogger().info("Libraries loaded.");
    }

    @Override
    public void onLoad() {
        if (this.getServer().getPluginManager().getPlugin("WorldGuard") != null
                && IntegrationProbe.classAvailable("com.sk89q.worldguard.WorldGuard")) {
            try {
                this.regionRules = WorldGuardRegions.register(this.getLogger());
            } catch (RuntimeException | LinkageError failure) {
                this.regionRules = player -> ServerPolicy.Decision.DENY;
                this.getLogger().log(Level.SEVERE, "Could not register WorldGuard companion flags; enabled region restrictions will suspend visuals and bonuses", failure);
            }
        }
    }

    @Override
    public void onEnable() {
        this.enableStartedNanos = System.nanoTime();
        try {
            this.rootCommand = Objects.requireNonNull(this.getCommand(CompanionBukkitCommand.ROOT), "Missing bytecompanions command in plugin.yml");
            this.runtimeCompatibility = RuntimeCompatibility.require(this.getServer());
            List<World> worlds = this.getServer().getWorlds();
            if (worlds.isEmpty()) {
                throw new IllegalStateException("ByteCompanions requires a loaded world to validate its native renderer.");
            }
            this.closing = false;
            Location validationLocation = worlds.getFirst().getSpawnLocation();
            CompletableFuture<Void> validation = BukkitOwnerTasks.region(this, validationLocation, () -> {
                this.createNmsCompanionEntityFactory().validateCompatibility(validationLocation.getWorld());
                ParticlesFeature.validateCompatibility();
            });
            CompletableFuture<Void> bootstrap = activateRuntimeWhenReady(validation,
                    () -> !this.closing && this.isEnabled(), () -> {
                        try {
                            this.initializePlugin();
                        } catch (Exception exception) {
                            throw new CompletionException(exception);
                        }
                    }, this::executeGlobal);
            this.pendingInstallations.add(bootstrap);
            bootstrap.whenComplete((ignored, failure) -> {
                this.pendingInstallations.remove(bootstrap);
                if (failure != null) {
                    this.failStartup(failure);
                }
            });
        } catch (Exception | LinkageError exception) {
            this.getLogger().log(Level.SEVERE, "Could not start ByteCompanions", exception);
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializePlugin() throws Exception {
        this.metricsService = new MetricsService(this);
        this.getLogger().info("Native adapter " + this.runtimeCompatibility.companionAdapter()
                + " selected for Minecraft " + this.runtimeCompatibility.minecraftVersion() + "; construction checks passed.");
        this.configurationExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.languageService = new LanguageService(this.getDataFolder(), this.getLogger());
        this.configService = new ConfigService(this.getDataFolder(), this::prepareConfiguration);
        ConfigService.PreparedConfig prepared = this.configService.prepareFromDisk();
        this.installConfiguredLanguage(prepared.configuration().general().language());
        this.configService.install(prepared);
        this.languageService.initializeSelections(this.configService::language,
                (locale, snapshot) -> this.configService.updateLanguage(locale));
        this.configEditor = BukkitConfigEditor.register(this, this.configService.editorOptions(
                new BukkitConfigEditor.Presentation(FeaturePermission.Command.CONFIG, ChatMenuStyle.theme(),
                        this.languageService.directorResolver())));
        this.languageSwitcher = BukkitLanguageSwitcher.register(this, this.languageService.selections(),
                new BukkitLanguageSwitcher.Options(CompanionBukkitCommand.ROOT, FeaturePermission.Command.CONFIG,
                        ChatMenuStyle.theme(), this.languageService.directorResolver(), this.languageService.editorOptions()));
        this.configEditor.configureLayout(ConfigEditorLayout.create(
                player -> this.languageSwitcher.command(player, new String[]{"server"}),
                player -> this.languageSwitcher.openEditor(player, returned -> this.configEditor.open(returned))));
        this.updateService = BukkitUpdateService.register(this, new BukkitUpdateService.Options(
                "VolmitSoftware", "ByteCompanions", FeaturePermission.Command.UPDATE,
                () -> this.configService.current().general().updateNotifications()));
        this.integrationService = new CompanionIntegrationService(this, this::integrationSnapshot);
        this.integrationService.register();
        this.configService.onFileChange(this::announceFileChange);
        this.contentSnapshot = ContentSnapshot.capture(this);
        ContentPreflight.validate(this, prepared.configuration(), this.contentSnapshot);
        this.configService.installContent(this.contentSnapshot);
        this.startRuntime(prepared.configuration()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.failStartup(failure);
                return;
            }
            try {
                this.configService.startWatching(exception -> this.getLogger().log(Level.SEVERE,
                        "Could not reload ByteCompanions configuration", exception));
                this.languageService.startWatching(() -> this.configService.current().hotReload().enabled());
                this.getLogger().info("Starter companions: use /bytecompanions setup in-game to choose cosmetic or survival bonuses.");
            } catch (Exception | LinkageError exception) {
                this.failStartup(exception);
            }
        });
    }

    private void installConfiguredLanguage(String locale) throws IOException {
        try {
            this.languageService.install(this.languageService.prepare(locale));
        } catch (IOException exception) {
            if (locale.equals("en_US")) {
                throw exception;
            }
            this.getLogger().log(Level.WARNING, "Configured language " + locale
                    + " could not be loaded; ByteCompanions is continuing with English", exception);
            this.languageService.install(this.languageService.prepare("en_US"));
        }
    }

    private void failStartup(Throwable failure) {
        this.getLogger().log(Level.SEVERE, "Could not start ByteCompanions", failure);
        this.printSplash();
        if (this.isEnabled() && !FoliaScheduler.runGlobal(this,
                () -> this.getServer().getPluginManager().disablePlugin(this))) {
            this.getLogger().severe("Could not schedule plugin disable after startup failure");
        }
    }

    private CompletableFuture<Void> startRuntime(Configuration configuration) {
        this.getLogger().info("Enabling plugin...");
        this.runtimeGeneration++;
        this.animationRegistry = BuiltinAnimationRegistry.createDefaults();
        this.getLogger().info("Minecraft Version: " + this.runtimeCompatibility().minecraftVersion());
        Function<Player, ServerPolicy.Decision> regionDecision = !configuration.general().enabled() ? player -> ServerPolicy.Decision.DENY
                : configuration.serverRules().worldguard() ? this.regionRules : player -> ServerPolicy.Decision.ALLOW;
        this.serverPolicy = new ServerPolicy(Set.copyOf(configuration.serverRules().hiddenWorlds()),
                Set.copyOf(configuration.serverRules().bonusDisabledWorlds()), regionDecision);
        AtomicReference<RuntimeServerRules> audienceRules = new AtomicReference<>();
        this.viewerService = new CompanionViewerService(this, configuration.performance().viewerRefreshDelay(), (viewer, owner) -> {
            RuntimeServerRules rules = audienceRules.get();
            return rules == null ? new CompanionViewerService.Audience(false, 0) : rules.audience(viewer, owner);
        });
        this.nmsCompanionEntityFactory = this.createNmsCompanionEntityFactory();

        this.configuration = configuration;

        final String serializerType = this.configuration.presentation().serializer();
        this.componentSerializerAdapter = ComponentSerializerFactory.createAdapter(serializerType);

        this.asyncExecutor = new VirtualThreadAsyncExecutor();
        this.messageDispatcher = new MessageDispatcher(this.languageService, () -> this.configService.current().presentation().commandSounds());

        final TextSerializer textSerializer = TextSerializerFactory.create(serializerType);
        this.itemBlueprintRegistry = ItemBlueprintRegistry.create(textSerializer);

        this.initializeStorage();

        this.companionEventBus = new BukkitCompanionEventBus(this);
        this.companionEntityCache = new CompanionEntityCache();
        this.storage.connect();

        CompletableFuture<Void> progressionReady = CompletableFuture.completedFuture(null);
        if (configuration.features().enabled("progression") || configuration.features().enabled("boosters")) {
            try {
                ProgressionDefinitions definitions = ProgressionDefinitions.parse(
                        this.contentSnapshot.source("features/progression/config.toml").getBytes(StandardCharsets.UTF_8));
                this.progressionService = new ProgressionService(
                        this, this.storage::progression, this.asyncExecutor.executor(), definitions,
                        configuration.features().enabled("progression"), configuration.features().enabled("boosters"),
                        this.captureRuntimeGuard());
                ProgressionService current = this.progressionService;
                current.targetEnabled(target -> configuration.features().enabled(target.startsWith("attribute:") ? "attributes"
                        : target.startsWith("effect:") ? "effects" : "buffs"));
                current.onChanged(playerId -> {
                    Player player = this.getServer().getPlayer(playerId);
                    if (player != null) {
                        FoliaScheduler.runEntity(this, player, () -> {
                            if (this.progressionService == current && this.runtimeActive && player.isOnline()) {
                                if (this.attributesFeature != null) this.attributesFeature.refresh(player);
                                if (this.effectsFeature != null) this.effectsFeature.refresh(player);
                                if (this.buffsFeature != null) this.buffsFeature.refresh(player);
                            }
                        });
                    }
                });
                progressionReady = current.initializeAsync();
            } catch (IOException failure) {
                throw new IllegalStateException("Could not prepare companion progression", failure);
            }
        }

        this.playerCache = new CompanionPlayerCache();
        this.userCompanionFactory = new UserCompanionFactory();
        this.userLoader = new DefaultUserLoader(this.playerCache, this.storage.users(), this.userCompanionFactory, this.companionTypeRegistry);
        this.userManager = new DefaultUserManager(this.playerCache, this.storage.users());

        this.companionFactory = new DefaultCompanionFactory(
                this.componentSerializerAdapter,
                this.nmsCompanionEntityFactory,
                this.animationRegistry,
                this.configuration
        );

        this.companionManager = new DefaultCompanionManager(
                this.companionTypeRegistry,
                this.companionFactory,
                this.playerCache,
                this.companionEventBus,
                this.companionEntityCache
        );

        this.companionPlayerManager = new CompanionPlayerManager(
                this,
                this.userManager,
                this.companionManager,
                this.messageDispatcher,
                this.companionTypeRegistry,
                this.storage.users(),
                this.configuration.general().companionLimits()
        );

        ByteCompanionsProvider.setInstance(this);
        new AnimationsFeature(this).enable();
        this.startPlayerFeatures(audienceRules);
        this.collectionVariants = new VariantCatalog(this);
        this.collectionService = new CollectionService(this, this.storage.collections(),
                this.asyncExecutor.executor(), this.storage.users(), this.companionManager,
                this.configuration.general().companionLimits(), this.particlesFeature,
                this.configuration.collections().maximumOutfits(), this.collectionVariants);
        this.registerListeners(this.collectionService);
        CompletableFuture<Void> collectionsReady = this.collectionService.initialize();
        CollectionService collections = this.collectionService;
        AttributesFeature attributes = this.attributesFeature;
        EffectsFeature effects = this.effectsFeature;
        this.runtimeServerRules = new RuntimeServerRules(this.serverPolicy,
                configuration.performance().maximumParticleIntensity(), collections::viewerPreferences, player -> {
                    if (attributes != null) attributes.refresh(player);
                    if (effects != null) effects.refresh(player);
                });
        RuntimeServerRules rules = this.runtimeServerRules;
        audienceRules.set(rules);
        this.viewerService.ownerRefresh(rules::refresh);
        this.registerListeners(rules, this.viewerService);
        for (Player player : this.getServer().getOnlinePlayers()) this.viewerService.start(player);
        if (this.progressionService != null && configuration.features().enabled("progression")) {
            this.progressionGameplayListener = new ProgressionGameplayListener(
                    this, this, this.progressionService);
            this.progressionGameplayListener.bonusesAllowed(this.serverPolicy::bonusesAllowed);
            this.registerListeners(this.progressionGameplayListener);
        }
        this.companionMenus = new CompanionMenus(this, this.particlesFeature,
                new CompanionBonusService(this.attributesFeature, this.effectsFeature, this.buffsFeature));
        this.companionPreviews = new CompanionPreviewService(this, this.companionFactory,
                (player, type) -> this.getCompanionPlayer(player)
                        .map(state -> state.companions().containsKey(type.id())).orElse(true));
        this.companionStudio = new CompanionStudio(this, this.companionMenus, this.companionPreviews);
        this.registerListeners(this.companionPreviews, this.companionStudio);

        if (this.configuration.general().autoLoadCompanions()) {
            new CompanionConfigurationLoader(this, this.companionTypeRegistry, this.itemBlueprintRegistry).load();
            VariantCatalog.validateDefinitions(this.companionTypeRegistry);
            this.getLogger().info("Miniature types loaded successfully.");
        }

        CompletableFuture<Void> decorationsReady = CompletableFuture.completedFuture(null);
        if (this.configuration.features().enabled("decorations")) {
            this.decorationsFeature = new DecorationsFeature(this);
            decorationsReady = this.decorationsFeature.enableAsync();
        }
        long generation = this.runtimeGeneration;
        CompletableFuture<Void> startup = activateRuntimeWhenReady(CompletableFuture.allOf(decorationsReady, progressionReady, collectionsReady),
                () -> !this.closing && this.isEnabled() && generation == this.runtimeGeneration,
                this::activateRuntime, this::executeGlobal);
        this.pendingInstallations.add(startup);
        startup.whenComplete((ignored, failure) -> this.pendingInstallations.remove(startup));
        return startup;
    }

    private void executeGlobal(Runnable action) {
        if (!FoliaScheduler.isFoliaThreading(this.getServer()) && FoliaScheduler.isPrimaryThread()) {
            action.run();
            return;
        }
        if (!FoliaScheduler.runGlobal(this, action)) {
            throw new RejectedExecutionException("Could not schedule runtime activation");
        }
    }

    static CompletableFuture<Void> activateRuntimeWhenReady(CompletableFuture<Void> readiness,
                                                           BooleanSupplier current, Runnable activation,
                                                           Executor owner) {
        CompletableFuture<Void> startup = new CompletableFuture<>();
        readiness.whenComplete((ignored, failure) -> {
            if (failure != null) {
                startup.completeExceptionally(failure);
                return;
            }
            try {
                owner.execute(() -> {
                    if (startup.isDone()) {
                        return;
                    }
                    try {
                        if (!current.getAsBoolean()) {
                            throw new IllegalStateException("Runtime startup was superseded or stopped");
                        }
                        activation.run();
                        startup.complete(null);
                    } catch (Exception | LinkageError exception) {
                        startup.completeExceptionally(exception);
                    }
                });
            } catch (Exception | LinkageError exception) {
                startup.completeExceptionally(exception);
            }
        });
        return startup;
    }

    private void activateRuntime() {
        this.getLogger().info("Enabled companion services: " + String.join(", ",
                List.of("particles", "attributes", "effects", "buffs", "decorations").stream()
                        .filter(this::isFeatureEnabled).toList()));

        this.tickScheduler = new CompanionsTickScheduler(this, this.playerCache, this.companionManager,
                this.configuration.performance().updateInterval());
        this.lifecyclePlatformService = new CompanionPlayerLifecyclePlatformService(
                this,
                this.playerCache,
                this.companionEntityCache,
                this.companionEventBus,
                this.userLoader,
                this.companionManager,
                this.tickScheduler
        );

        this.companionsSyncService = new CompanionsSyncService(this, this.companionManager);

        this.placeholders = new CompanionPlaceholders(this, this.companionTypeRegistry, this.playerCache);
        this.placeholderInstaller = new PlaceholderApiInstaller(this);
        this.registerListeners(this.placeholders, this.placeholderInstaller);
        this.placeholders.start();
        this.placeholderInstaller.attach(this.placeholders);

        CompanionBukkitCommand command = new CompanionBukkitCommand(this, this.messageDispatcher,
                this.companionPlayerManager, this.companionTypeRegistry);
        this.rootCommand.setExecutor(command);
        this.rootCommand.setTabCompleter(command);
        if (this.configuration.performance().dispatchInteractionEvent() || this.configuration.openDashboardOnInteract()) {
            if (this.runtimeCompatibility().companionAdapter() == RuntimeCompatibility.CompanionAdapter.SPIGOT_26_1
                    || this.runtimeCompatibility().companionAdapter() == RuntimeCompatibility.CompanionAdapter.SPIGOT_26_3) {
                Spigot26_1InteractionListener listener = new Spigot26_1InteractionListener(this, this.companionEntityCache, this.companionEventBus);
                this.stopInteractionListener = listener::close;
            } else {
                this.registerListeners(new PlayerUseUnknownEntityListener(this, this.companionEntityCache,
                        this.companionEventBus, this.captureRuntimeGuard()));
            }
        }

        this.registerListeners(
                // Sync listeners
                new PlayerDeathListener(this.companionsSyncService),
                new PlayerTeleportListener(this.companionsSyncService),
                new PlayerChangedWorldListener(this.companionsSyncService),
                new PlayerPostRespawnListener(this.companionsSyncService),
                new PlayerGameModeChangeListener(this.companionManager),
                new PlayerQuitListener(this.lifecyclePlatformService),
                new PlayerJoinListener(this, this.lifecyclePlatformService)
        );

        this.getLogger().info("Listeners registered successfully.");

        this.runtimeActive = true;


        this.loadAllOnlinePlayers();

        this.debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(
                () -> this.configService.current().debug().uploadEnabled(), this::captureDebugReport, new BukkitDebugDump.Presentation(
                        "/" + CompanionBukkitCommand.ROOT + " debug dump", "/" + CompanionBukkitCommand.ROOT + " debug", ChatMenuStyle.theme(),
                        (key, arguments) -> this.languageService.render(null, key, arguments)),
                FeaturePermission.Command.DEBUG));
        this.configurationInstalled();
        if (!this.splashPrinted) {
            this.printSplash();
            long elapsed = (System.nanoTime() - this.enableStartedNanos) / 1_000_000L;
            this.getLogger().info("ByteCompanions ready in " + elapsed + " ms with " + schedulerName()
                    + " scheduling and " + this.companionTypeRegistry.totalRegistered() + " companion definitions.");
        }
    }

    private void configurationInstalled() {
        Configuration current = this.configService.current();
        this.metricsService.reconfigure(current.metrics().enabled());
        this.updateService.reconfigure();
        this.configService.updateTiming(current.hotReload().pollIntervalMillis(), current.hotReload().cooldownMillis());
        this.languageService.updateTiming(current.hotReload().pollIntervalMillis(), current.hotReload().cooldownMillis());
    }

    private void printSplash() {
        if (this.splashPrinted || this.configService == null) {
            return;
        }
        this.splashPrinted = true;
        Configuration current = this.configService.current();
        if (current.presentation().splashScreen()) {
            SplashScreen.print(this, current.presentation().alternateSplashes());
        }
    }

    private String schedulerName() {
        return FoliaScheduler.isFoliaThreading(this.getServer()) ? "Folia region" : "Bukkit main-thread";
    }

    private void announceFileChange(boolean applied) {
        if (applied) {
            this.getLogger().info("Applied ByteCompanions configuration file changes.");
        } else {
            this.getLogger().severe("Rejected ByteCompanions file changes; the last known good settings remain active.");
        }
        if (!this.configService.current().hotReload().notifyOperators()) {
            return;
        }
        String key = applied ? "runtime.hot_reload.success" : "runtime.hot_reload.failed";
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (!player.hasPermission(FeaturePermission.Command.CONFIG)) {
                continue;
            }
            FoliaScheduler.runEntity(this, player, () -> ComponentMessenger.sendActionBarMarkup(player,
                    this.languageService.render(player, com.volmit.bytecompanions.platform.paper.localization.CompanionMessages.key(key)).miniMessage()));
        }
    }

    public List<CompanionBukkitCommand.StatusEntry> statusEntries() {
        Configuration current = this.configService.current();
        List<String> features = new ArrayList<>(5);
        for (String feature : List.of("attributes", "buffs", "decorations", "effects", "particles", "progression", "boosters")) {
            if (current.features().enabled(feature)) {
                features.add(feature);
            }
        }
        List<String> integrations = new ArrayList<>(5);
        integrations.add("PlaceholderAPI " + (this.placeholderInstaller == null ? "absent" : this.placeholderInstaller.state()));
        integrations.add("WorldGuard " + (this.getServer().getPluginManager().isPluginEnabled("WorldGuard") ? "ready" : "absent"));
        integrations.add("Vault " + (this.getServer().getPluginManager().isPluginEnabled("Vault") ? "ready" : "absent"));
        integrations.add("Nexo " + (this.getServer().getPluginManager().isPluginEnabled("Nexo") ? "ready" : "absent"));
        integrations.add("ItemsAdder " + (this.getServer().getPluginManager().isPluginEnabled("ItemsAdder") ? "ready" : "absent"));
        int equipped = 0;
        if (this.playerCache != null) {
            for (DefaultCompanionPlayer state : this.playerCache.all()) {
                equipped += state.companions().size();
            }
        }
        String enabled = "<green>enabled</green>";
        String disabled = "<red>disabled</red>";
        return List.of(
                new CompanionBukkitCommand.StatusEntry("command.status.configuration", MessageArgs.builder()
                        .trusted("enabled", current.general().enabled() ? enabled : disabled)
                        .untrusted("language", current.general().language())
                        .trusted("hot_reload", current.hotReload().enabled() ? enabled : disabled).build()),
                new CompanionBukkitCommand.StatusEntry("command.status.runtime", MessageArgs.builder()
                        .untrusted("count", Integer.toString(this.companionTypeRegistry.totalRegistered()))
                        .untrusted("players", Integer.toString(this.playerCache == null ? 0 : this.playerCache.all().size()))
                        .untrusted("companions", Integer.toString(equipped)).build()),
                new CompanionBukkitCommand.StatusEntry("command.status.features", MessageArgs.builder()
                        .untrusted("features", features.isEmpty() ? "none" : String.join(", ", features))
                        .untrusted("value", current.storage().backend()).build()),
                new CompanionBukkitCommand.StatusEntry("command.status.integrations", MessageArgs.builder()
                        .untrusted("integrations", String.join(", ", integrations)).build()),
                new CompanionBukkitCommand.StatusEntry("command.status.compatibility", MessageArgs.builder()
                        .untrusted("java", Integer.toString(Runtime.version().feature()))
                        .untrusted("range", com.volmit.bytecompanions.platform.paper.compatibility.BuildInfo.minecraftRange())
                        .untrusted("scheduler", schedulerName())
                        .untrusted("value", this.runtimeCompatibility == null ? "none" : this.runtimeCompatibility.companionAdapter().name()).build()));
    }

    private CompanionIntegrationService.Snapshot integrationSnapshot() {
        if (!this.runtimeActive || this.playerCache == null) {
            return CompanionIntegrationService.Snapshot.INACTIVE;
        }
        int equipped = 0;
        for (DefaultCompanionPlayer state : this.playerCache.all()) {
            equipped += state.companions().size();
        }
        CompanionsTickScheduler.Snapshot ticks = this.tickScheduler == null ? null : this.tickScheduler.snapshot();
        return new CompanionIntegrationService.Snapshot(true, this.companionTypeRegistry.totalRegistered(), this.playerCache.all().size(),
                equipped, this.decorationsFeature == null ? 0 : this.decorationsFeature.registeredCompanions().size(),
                ticks == null ? 0 : ticks.failures(), this.lifecyclePlatformService == null ? 0 : this.lifecyclePlatformService.loadFailures(),
                com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation.trafficSnapshot().packetDeliveries());
    }

    @Override
    public CompletionStage<ReloadPreparation> prepareReload(PreUnloadReason reason) {
        return CompletableFuture.completedFuture(this.pendingInstallations.isEmpty()
                ? ReloadPreparation.readyToUnload()
                : ReloadPreparation.refuse("A ByteCompanions configuration replacement is still being applied."));
    }

    @Override
    public CompletionStage<Void> commitReload(PreUnloadReason reason) {
        return this.stop();
    }

    @Override
    public void onDisable() {
        CompletableFuture<Void> stopped = this.stop();
        try {
            if (!awaitRuntimeStorage(this.runtimeOwnerStop == null ? CompletableFuture.completedFuture(null) : this.runtimeOwnerStop,
                    stopped, 30, TimeUnit.SECONDS)) {
                this.getLogger().severe("Runtime owner cleanup is still pending; shutdown cannot wait on owner tasks. "
                        + "Cleanup will continue asynchronously, but full shutdown completion is not confirmed.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            this.getLogger().log(Level.SEVERE, "Interrupted while waiting for runtime storage shutdown", failure);
        } catch (TimeoutException failure) {
            this.getLogger().log(Level.SEVERE, "Runtime storage shutdown did not finish within 30 seconds", failure);
        } catch (ExecutionException ignored) {
        }
    }

    private synchronized CompletableFuture<Void> stop() {
        if (this.stopCompletion != null) {
            return this.stopCompletion;
        }
        this.closing = true;
        if (this.metricsService != null) {
            this.cleanup("bStats metrics", this.metricsService::close);
        }
        this.pendingInstallations.forEach(future -> future.completeExceptionally(
                new IllegalStateException("ByteCompanions is stopping")));
        if (this.configService != null) {
            this.cleanup("configuration watcher", this.configService::close);
        }
        if (this.configEditor != null) {
            this.cleanup("configuration editor", this.configEditor::close);
        }
        if (this.languageSwitcher != null) {
            this.cleanup("language editor", this.languageSwitcher::close);
        }
        if (this.languageService != null) {
            this.cleanup("language selections", this.languageService::close);
        }
        if (this.updateService != null) {
            this.cleanup("update notifier", this.updateService::close);
        }
        if (this.integrationService != null) {
            this.cleanup("integration contract", this.integrationService::unregister);
        }
        CompletableFuture<Void> stopped = this.stopRuntimeAsync();
        this.stopCompletion = stopped.whenComplete((ignored, failure) -> {
            if (this.configurationExecutor != null) {
                this.configurationExecutor.shutdown();
            }
            this.cleanup("listeners", () -> HandlerList.unregisterAll(this));
            this.cleanup("scheduled tasks", () -> SchedulerUtils.cancelPluginTasks(this));
            if (failure != null) {
                this.getLogger().log(Level.SEVERE, "Could not complete runtime shutdown", failure);
            }
        });
        return this.stopCompletion;
    }

    private CompletableFuture<Void> stopRuntimeAsync() {
        if (this.runtimeStop != null && !this.runtimeStop.isDone()) {
            return this.runtimeStop;
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        this.runtimeStop = completion;
        if (this.debugDump != null) {
            this.cleanup("diagnostics", this.debugDump::close);
            this.debugDump = null;
        }
        this.runtimeActive = false;
        this.runtimeGeneration++;
        if (this.progressionGameplayListener != null) {
            this.cleanup("progression events", this.progressionGameplayListener::close);
            this.progressionGameplayListener = null;
        }
        if (this.stopInteractionListener != null) {
            this.cleanup("native interactions", this.stopInteractionListener);
            this.stopInteractionListener = null;
        }
        if (this.lifecyclePlatformService != null) {
            this.cleanup("player sessions", this.lifecyclePlatformService::close);
        }
        if (this.tickScheduler != null) {
            this.cleanup("companion ticks", this.tickScheduler::close);
            this.tickScheduler = null;
        }
        this.cleanup("listeners", () -> this.runtimeListeners.forEach(HandlerList::unregisterAll));
        this.runtimeListeners.clear();
        if (this.placeholderInstaller != null) {
            this.cleanup("PlaceholderAPI", this.placeholderInstaller::detach);
            this.placeholderInstaller = null;
        }
        if (this.placeholders != null) {
            this.cleanup("placeholder snapshots", this.placeholders::close);
            this.placeholders = null;
        }
        this.cleanup("commands", () -> {
            if (this.rootCommand != null) {
                this.rootCommand.setExecutor((sender, command, label, arguments) -> {
                    this.languageService.send(sender, "command.feedback.inactive");
                    return true;
                });
                this.rootCommand.setTabCompleter((sender, command, alias, arguments) -> List.of());
            }
        });
        this.featureCommands.clear();
        CompletableFuture<Void> menuCleanup = this.beginCleanup(this::closeRuntimeMenus);
        CompletableFuture<Void> studioCleanup = this.beginCleanup(() -> this.companionStudio == null
                ? CompletableFuture.completedFuture(null) : this.companionStudio.closeAsync());
        CompletableFuture<Void> previewCleanup = this.beginCleanup(() -> this.companionPreviews == null
                ? CompletableFuture.completedFuture(null) : this.companionPreviews.closeAsync());
        CompletableFuture<Void> featureCleanup = this.beginCleanup(this::stopFeaturesAsync);
        CompletableFuture<Void> companionCleanup = this.beginCleanup(this::stopCompanionsAsync);
        CompanionViewerService viewers = this.viewerService;
        if (this.runtimeServerRules != null) this.runtimeServerRules.close();
        this.runtimeOwnerStop = AsyncTeardown.run(List.of(
                () -> menuCleanup,
                () -> studioCleanup,
                () -> previewCleanup,
                () -> featureCleanup,
                () -> companionCleanup,
                () -> this.progressionService == null ? CompletableFuture.completedFuture(null) : this.progressionService.stopOwnerTasks(),
                () -> viewers == null ? CompletableFuture.completedFuture(null) : viewers.closeAsync().toCompletableFuture()));
        AsyncTeardown.run(List.of(
                () -> this.runtimeOwnerStop,
                () -> this.progressionService == null ? CompletableFuture.completedFuture(null) : this.progressionService.closeAsync(),
                () -> this.collectionService == null ? CompletableFuture.completedFuture(null) : this.collectionService.closeAsync(),
                this::releaseRuntimeAsync)).whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        completion.complete(null);
                    } else {
                        completion.completeExceptionally(failure);
                    }
                });
        return completion;
    }

    private CompletableFuture<Void> closeRuntimeMenus() {
        return this.companionMenus == null ? CompletableFuture.completedFuture(null) : this.companionMenus.closeAsync();
    }

    private CompletableFuture<Void> stopCompanionsAsync() {
        if (this.playerCache == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        CompanionEventBus events = this.companionEventBus;
        for (DefaultCompanionPlayer state : this.playerCache.all()) {
            Player player = this.getServer().getPlayer(state.playerId());
            if (player == null) {
                continue;
            }
            tasks.add(BukkitOwnerTasks.entity(this, player, () -> {
                if (this.collectionService != null) {
                    this.collectionService.unload(player.getUniqueId());
                }
                for (Companion companion : List.copyOf(state.companions().values())) {
                    events.callDespawn(companion);
                }
                state.unequip();
            }));
        }
        return this.awaitCleanup(tasks);
    }

    private CompletableFuture<Void> releaseRuntimeAsync() {
        Executor executor = this.configurationExecutor == null ? Runnable::run : this.configurationExecutor;
        return releaseRuntimeResources(this::closeRuntimeStorage, this::releaseRuntimeReferences, executor);
    }

    static boolean awaitRuntimeStorage(CompletableFuture<Void> ownerCleanup, CompletableFuture<Void> completion,
                                       long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (!ownerCleanup.isDone()) {
            return false;
        }
        completion.get(timeout, unit);
        return true;
    }

    static CompletableFuture<Void> releaseRuntimeResources(Runnable closeStorage, Runnable releaseReferences,
                                                           Executor executor) {
        return AsyncTeardown.run(List.of(
                () -> CompletableFuture.runAsync(closeStorage, executor),
                () -> {
                    releaseReferences.run();
                    return CompletableFuture.completedFuture(null);
                }));
    }

    private void closeRuntimeStorage() {
        if (this.particlesFeature != null) {
            this.cleanup("particle resources", this.particlesFeature::closeResources);
        }
        if (this.storage != null) {
            this.cleanup("accepted storage operations", () -> this.storage.closeQueues().join());
        }
        if (this.asyncExecutor != null) {
            this.cleanup("storage executor", this.asyncExecutor::shutdown);
        }
        if (this.storage != null) {
            this.cleanup("storage connection", this.storage::disconnect);
        }
    }

    private void releaseRuntimeReferences() {
        this.decorationsFeature = null;
        this.particlesFeature = null;
        this.buffsFeature = null;
        this.effectsFeature = null;
        this.attributesFeature = null;
        this.companionMenus = null;
        this.companionStudio = null;
        this.companionPreviews = null;
        this.collectionService = null;
        this.collectionVariants = null;
        this.progressionService = null;
        this.viewerService = null;
        this.runtimeServerRules = null;
        this.serverPolicy = null;
        if (this.itemBlueprintRegistry != null) {
            this.cleanup("item registry", this.itemBlueprintRegistry::clear);
        }
        this.companionTypeRegistry.clear();
        this.itemBlueprintRegistry = null;
        this.userLoader = null;
        this.userManager = null;
        this.lifecyclePlatformService = null;
        this.companionsSyncService = null;
        this.playerCache = null;
        this.companionPlayerManager = null;
        this.companionManager = null;
        this.companionFactory = null;
        this.messageDispatcher = null;
        this.configuration = null;
        this.storage = null;
        this.asyncExecutor = null;
        this.nmsCompanionEntityFactory = null;
        this.companionEntityCache = null;
        this.companionEventBus = null;
        this.animationRegistry = null;
        ByteCompanionsProvider.resetInstance();
        this.getLogger().info("Plugin runtime stopped.");
    }

    private void startPlayerFeatures(AtomicReference<RuntimeServerRules> audienceRules) {
        if (this.configuration.features().enabled("particles")) {
            this.particlesFeature = new ParticlesFeature(this, this, this.storage.particles());
            this.particlesFeature.enable();
            this.featureCommands.put("particles", this.particlesFeature.command());
        }
        if (this.configuration.features().enabled("attributes")) {
            this.attributesFeature = new AttributesFeature(this, this, this.progressionService);
            this.attributesFeature.bonusesAllowed(this.serverPolicy::bonusesAllowed);
            this.attributesFeature.enable();
        }
        if (this.configuration.features().enabled("effects")) {
            this.effectsFeature = new EffectsFeature(this, this, this.progressionService);
            this.effectsFeature.bonusesAllowed(this.serverPolicy::bonusesAllowed);
            this.effectsFeature.enable();
        }
        if (this.configuration.features().enabled("buffs") || this.configuration.features().enabled("boosters")) {
            this.buffsFeature = new BuffsFeature(this, this, this.progressionService);
            ServerPolicy policy = this.serverPolicy;
            this.buffsFeature.bonusesAllowed(player -> {
                if (FoliaScheduler.isOwnedByCurrentRegion(player)) return policy.bonusesAllowed(player);
                RuntimeServerRules rules = audienceRules.get();
                return rules != null && rules.bonusesAllowed(player.getUniqueId());
            });
            this.buffsFeature.enable(this.configuration.features().enabled("buffs"));
        }
    }

    private CompletableFuture<Void> stopFeaturesAsync() {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        if (this.decorationsFeature != null) {
            tasks.add(this.beginCleanup(this.decorationsFeature::stopAsync));
        }
        if (this.particlesFeature != null) {
            tasks.add(this.beginCleanup(this.particlesFeature::stopAsync));
        }
        if (this.buffsFeature != null) {
            tasks.add(this.beginCleanup(this.buffsFeature::stopAsync));
        }
        if (this.effectsFeature != null) {
            tasks.add(this.beginCleanup(this.effectsFeature::stopAsync));
        }
        if (this.attributesFeature != null) {
            tasks.add(this.beginCleanup(this.attributesFeature::stopAsync));
        }
        return this.awaitCleanup(tasks);
    }

    private CompletableFuture<Void> awaitCleanup(List<CompletableFuture<Void>> tasks) {
        return AsyncTeardown.run(tasks.stream()
                .<Supplier<CompletableFuture<Void>>>map(task -> () -> task).toList());
    }

    private CompletableFuture<Void> beginCleanup(Supplier<CompletableFuture<Void>> action) {
        try {
            return action.get();
        } catch (Exception | LinkageError failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void cleanup(String component, Runnable action) {
        try {
            action.run();
        } catch (Exception | LinkageError exception) {
            this.getLogger().log(Level.SEVERE, "Could not stop " + component, exception);
        }
    }

    public String setupSource() {
        return this.configService.source();
    }

    public void requestStarterSetup(Player player, StarterCollection.Profile profile, String expectedSource) {
        if (!player.hasPermission(FeaturePermission.Command.SETUP)) {
            this.languageService.send(player, "errors.no-permission");
            return;
        }
        if (!Objects.equals(expectedSource, this.configService.source())) {
            this.languageService.send(player, "setup.stale");
            return;
        }
        if (this.closing || this.configurationExecutor == null) {
            return;
        }
        BooleanSupplier acceptedRuntime = this.captureRuntimeGuard();
        this.languageService.send(player, "setup.applying");
        CompletableFuture.runAsync(() -> {
            try {
                if (!acceptedRuntime.getAsBoolean()) {
                    throw new IllegalStateException("Runtime changed before starter setup could be applied");
                }
                this.configService.applyStarterProfile(profile, expectedSource);
            } catch (Exception exception) {
                this.getLogger().log(Level.SEVERE, "Could not apply starter companion setup", exception);
                throw new CompletionException(exception);
            }
        }, this.configurationExecutor).whenComplete((ignored, failure) -> {
            if (!this.closing) {
                FoliaScheduler.runEntity(this, player, () -> {
                    if (player.isOnline()) {
                        this.languageService.send(player, failure != null ? "setup.failed" : "setup.applied");
                    }
                });
            }
        });
    }

    private ConfigService.Installation prepareConfiguration(Configuration candidate, Configuration previous) throws Exception {
        return this.prepareConfiguration(candidate, previous, ContentSnapshot.read(this.getDataFolder().toPath()));
    }

    private ConfigService.Installation prepareConfiguration(Configuration candidate, Configuration previous, ContentSnapshot candidateContent) throws Exception {
        ContentSnapshot previousContent = this.contentSnapshot;
        this.onGlobal(() -> ContentPreflight.validate(this, candidate, candidateContent));
        return new PreparedRuntimeConfiguration(candidate, previous, this.languageService,
                (configuration, language, restoring) -> this.installConfiguration(configuration, language,
                        restoring, restoring ? previousContent : candidateContent));
    }

    public ContentSnapshot contentSnapshot() {
        return this.contentSnapshot;
    }

    public CompanionStudio companionStudio() {
        return this.companionStudio;
    }

    public CompletableFuture<CompanionType> prepareStudioPreview(CompanionDraft draft) {
        if (this.closing || this.configurationExecutor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ByteCompanions is stopping"));
        }
        BooleanSupplier accepted = this.captureRuntimeGuard();
        return CompletableFuture.supplyAsync(() -> {
            AtomicReference<CompanionType> type = new AtomicReference<>();
            try {
                this.onGlobal(() -> {
                    if (!accepted.getAsBoolean()) {
                        throw new IllegalStateException("Runtime changed while preparing the companion preview");
                    }
                    type.set(CompanionPreviewDefinition.parse(this, this.configuration, draft));
                });
                return type.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, this.configurationExecutor);
    }

    public CompletableFuture<Boolean> saveStudioDraft(Player player, CompanionDraft draft) {
        if (!player.hasPermission(FeaturePermission.Command.EDIT) || this.closing || this.configurationExecutor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Companion editor is unavailable"));
        }
        BooleanSupplier accepted = this.captureRuntimeGuard();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!accepted.getAsBoolean()) {
                    throw new IllegalStateException("Runtime changed before the companion could be saved");
                }
                this.configService.saveCompanion(draft, () -> ContentSnapshot.read(this.getDataFolder().toPath()),
                        (configuration, content) -> this.prepareConfiguration(configuration, configuration, content));
            } catch (Exception exception) {
                this.getLogger().log(Level.SEVERE, "Could not save companion " + draft.id(), exception);
                throw new CompletionException(exception);
            }
            return true;
        }, this.configurationExecutor);
    }

    private void onGlobal(CheckedAction action) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        this.pendingInstallations.add(completion);
        try {
            if (this.closing || !FoliaScheduler.runGlobal(this, () -> {
                if (this.closing || completion.isDone()) {
                    completion.completeExceptionally(new IllegalStateException("ByteCompanions is stopping"));
                    return;
                }
                try {
                    action.run();
                    completion.complete(null);
                } catch (Exception | LinkageError exception) {
                    completion.completeExceptionally(exception);
                }
            })) {
                throw new IllegalStateException("Could not schedule content preparation");
            }
            completion.get();
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Content preparation rejected", exception.getCause());
        } finally {
            this.pendingInstallations.remove(completion);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
    private void installConfiguration(Configuration candidate, LanguageService.PreparedLanguage language, boolean restoring, ContentSnapshot content) throws Exception {
        CompletableFuture<Void> installation = new CompletableFuture<>();
        this.pendingInstallations.add(installation);
        try {
            if (this.closing || !FoliaScheduler.runGlobal(this, () -> {
                if (this.closing || installation.isDone()) {
                    installation.completeExceptionally(new IllegalStateException("ByteCompanions is stopping"));
                    return;
                }
                try {
                    this.stopRuntimeAsync().whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            installation.completeExceptionally(failure);
                            return;
                        }
                        if (this.closing || !FoliaScheduler.runGlobal(this, () -> {
                            if (this.closing || installation.isDone()) {
                                installation.completeExceptionally(new IllegalStateException("ByteCompanions is stopping"));
                                return;
                            }
                            try {
                                this.contentSnapshot = content;
                                if (restoring) {
                                    this.languageService.restore(language);
                                } else {
                                    this.languageService.install(language);
                                }
                                this.startRuntime(candidate).whenComplete((started, startupFailure) -> {
                                    if (startupFailure == null) {
                                        installation.complete(null);
                                    } else {
                                        installation.completeExceptionally(startupFailure);
                                    }
                                });
                            } catch (Exception | LinkageError exception) {
                                installation.completeExceptionally(exception);
                            }
                        })) {
                            installation.completeExceptionally(new IllegalStateException("Could not schedule runtime replacement"));
                        }
                    });
                } catch (Exception | LinkageError exception) {
                    installation.completeExceptionally(exception);
                }
            })) {
                throw new IllegalStateException("Could not schedule configuration installation");
            }
            installation.get();
            this.languageService.refreshSelections();
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Could not install configuration", exception.getCause());
        } finally {
            this.pendingInstallations.remove(installation);
        }
    }

    public LanguageService languageService() {
        return this.languageService;
    }

    public BukkitLanguageSwitcher languageSwitcher() {
        return this.languageSwitcher;
    }

    public void openConfiguration(Player player) {
        this.configEditor.open(player);
    }

    public Map<String, BukkitCommand> featureCommands() {
        return Map.copyOf(this.featureCommands);
    }

    public BukkitDebugDump debugDump() {
        return this.debugDump;
    }

    private CompanionDebugReport captureDebugReport() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String feature : List.of("attributes", "buffs", "decorations", "effects", "particles")) {
            features.put(feature, this.isFeatureEnabled(feature));
        }
        Map<String, art.arcane.volmlib.util.scheduling.KeyedTaskQueue.Snapshot> storageQueues =
                this.storage == null ? Map.of() : this.storage.queueSnapshots();
        CompanionDebugReport.RuntimeSnapshot runtime = new CompanionDebugReport.RuntimeSnapshot(
                this.tickScheduler == null ? null : this.tickScheduler.snapshot(),
                this.lifecyclePlatformService == null ? 0 : this.lifecyclePlatformService.loadFailures(), storageQueues,
                com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation.trafficSnapshot(),
                this.decorationsFeature == null ? 0 : this.decorationsFeature.registeredCompanions().size(),
                this.configuration.general().language(), "config.toml (active runtime snapshot)");
        return new CompanionDebugReport(this.runtimeActive, this.runtimeGeneration,
                this.nmsCompanionEntityFactory.getClass().getSimpleName(), this.configuration.storage().backend(),
                this.companionTypeRegistry.totalRegistered(), this.playerCache.all().size(), features,
                this.configuration.performance().updateInterval(), this.configuration.performance().viewerRefreshDelay(),
                this.configuration.performance().viewDistance(), runtime);
    }

    public boolean isFeatureEnabled(String feature) {
        return switch (feature) {
            case "particles" -> this.particlesFeature != null;
            case "attributes" -> this.attributesFeature != null;
            case "effects" -> this.effectsFeature != null;
            case "buffs" -> this.buffsFeature != null && this.configuration.features().enabled("buffs");
            case "decorations" -> this.decorationsFeature != null;
            default -> false;
        };
    }

    private void loadAllOnlinePlayers() {
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.lifecyclePlatformService.onJoin(player);
        }
    }

    private void initializeStorage() {
        this.storage = RuntimeStorageFactory.create(this, this.configuration.storage(), this.asyncExecutor);
    }

    private @NotNull NmsCompanionEntityFactory createNmsCompanionEntityFactory() {
        CompanionViewerEnvironment viewers = this.viewerService == null
                ? CompanionViewerEnvironment.validationOnly() : this.viewerService;
        return switch (this.runtimeCompatibility().companionAdapter()) {
            case V26_1 -> new V26_1CompanionEntityFactory(viewers);
            case V26_3 -> new V26_3CompanionEntityFactory(viewers);
            case SPIGOT_26_1 -> new Spigot26_1CompanionEntityFactory(viewers);
            case SPIGOT_26_3 -> new Spigot26_3CompanionEntityFactory(viewers);
        };
    }

    public CompanionViewerEnvironment viewerEnvironment() {
        return Objects.requireNonNull(this.viewerService, "Viewer service is not running");
    }

    public BooleanSupplier captureRuntimeGuard() {
        long generation = this.runtimeGeneration;
        return () -> this.runtimeActive && !this.closing && this.runtimeGeneration == generation;
    }

    public RuntimeCompatibility.Selection runtimeCompatibility() {
        return Objects.requireNonNull(this.runtimeCompatibility, "Runtime compatibility has not been checked");
    }

    void registerListeners(final @NotNull Listener @NotNull... listeners) {
        for (final Listener listener : listeners) {
            this.getServer().getPluginManager().registerEvents(listener, this);
            this.runtimeListeners.add(listener);
        }
    }

    public CompanionMenus companionMenus() {
        return this.companionMenus;
    }

    public ProgressionService progressionService() {
        return this.progressionService;
    }

    public CompanionPlayerManager companionPlayerManager() {
        return this.companionPlayerManager;
    }

    public UserCompanionFactory userCompanionFactory() {
        return this.userCompanionFactory;
    }

    public DefaultCompanionFactory companionFactory() {
        return this.companionFactory;
    }

    public CollectionService collectionService() {
        return this.collectionService;
    }

    public VariantCatalog collectionVariants() {
        return this.collectionVariants;
    }

    public CompanionPreviewService companionPreviews() {
        return this.companionPreviews;
    }

    public CompanionBehaviorPolicy personalityPolicy() {
        return new CompanionBehaviorPolicy(this.configuration.collections().personalityEnabled(),
                this.configuration.collections().idleReactions(), Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofSeconds(30), 16);
    }

    public Configuration configuration() {
        return this.configuration;
    }

    public MessageDispatcher messageDispatcher() {
        return this.messageDispatcher;
    }

    public ComponentSerializerAdapter componentSerializerAdapter() {
        return this.componentSerializerAdapter;
    }

    public ItemBlueprintRegistry itemBlueprintRegistry() {
        return this.itemBlueprintRegistry;
    }

    @Override
    public @NotNull ConfigurationSettings configurationSettings() {
        return this.configuration;
    }

    @Override
    public @NotNull CompanionTypeRegistry typeRegistry() {
        return this.companionTypeRegistry;
    }

    @Override
    public @NotNull CompanionManager companionManager() {
        return this.companionManager;
    }

    @Override
    public @NotNull AnimationRegistry animationRegistry() {
        return this.animationRegistry;
    }

    @Override
    public @NotNull Optional<CompanionType> getCompanionType(final @Nullable String typeId) {
        return Optional.ofNullable(this.getCompanionTypeOrNull(typeId));
    }

    @Override
    public @Nullable CompanionType getCompanionTypeOrNull(final @Nullable String typeId) {
        return typeId != null ? this.companionTypeRegistry.getOrNull(typeId) : null;
    }

    @Override
    public @NotNull Optional<CompanionPlayer> getCompanionPlayer(final @NotNull Player player) {
        return Optional.ofNullable(this.getCompanionPlayerOrNull(player));
    }

    @Override
    public @Nullable CompanionPlayer getCompanionPlayerOrNull(final @NotNull Player player) {
        return this.playerCache.getByEntityId(player.getEntityId());
    }

    @Override
    public @NotNull Optional<CompanionPlayer> getCompanionPlayer(final @NotNull UUID playerId) {
        return Optional.ofNullable(this.getCompanionPlayerOrNull(playerId));
    }

    @Override
    public @Nullable CompanionPlayer getCompanionPlayerOrNull(final @NotNull UUID playerId) {
        return this.playerCache.getByUniqueId(playerId);
    }

}
