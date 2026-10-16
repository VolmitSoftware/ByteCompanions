package com.volmit.bytecompanions.features.buffs;

import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;

import org.slf4j.LoggerFactory;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.bytecompanions.platform.paper.compatibility.SoundPlayback;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import java.io.IOException;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.features.buffs.cache.PlayerBuffCache;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Configuration;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.configuration.feedback.FeedBack;
import com.volmit.bytecompanions.features.buffs.listener.BuffCacheListener;
import com.volmit.bytecompanions.features.buffs.manager.CooldownManager;
import com.volmit.bytecompanions.features.buffs.resolver.CompanionBuffResolver;
import com.volmit.bytecompanions.features.buffs.service.BuffService;
import com.volmit.bytecompanions.features.buffs.util.ComponentUtil;

import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import java.util.Objects;
import java.util.function.Predicate;

public final class BuffsFeature {

    private final ProgressionService progression;
    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private Configuration configuration;
    private volatile boolean active;
    private CompletableFuture<Void> stopped;
    private volatile Predicate<Player> bonusesAllowed = player -> true;

    private PlayerBuffCache playerBuffCache;
    private CooldownManager cooldownManager;

    private BuffService buffService;
    private CompanionBuffResolver companionBuffResolver;

    private ListenerRegistry listenerRegistry;
    private volatile BuffCacheListener cacheListener;

    public BuffsFeature(JavaPlugin plugin, ByteCompanions companions, ProgressionService progression) {
        this.progression = progression;
        this.plugin = plugin;
        this.companions = companions;
    }

    public void enable(boolean baseBonusesEnabled) {
        this.enable(baseBonusesEnabled ? this.loadConfiguration() : new Configuration());
    }

    public void bonusesAllowed(Predicate<Player> allowed) {
        this.bonusesAllowed = Objects.requireNonNull(allowed);
    }

    private void enable(Configuration configuration) {
        BuffPlatformSupport.validate(configuration, BuffPlatformSupport.supportsPickupExperience());
        this.configuration = configuration;
        this.active = true;
        this.cooldownManager = new CooldownManager();

        this.playerBuffCache = new PlayerBuffCache();
        this.buffService = new BuffService(this.playerBuffCache, this.cooldownManager, this.progression);
        this.buffService.bonusesAllowed(player -> this.bonusesAllowed.test(player));
        this.companionBuffResolver = new CompanionBuffResolver(this, LoggerFactory.getLogger(this.plugin.getName()));

        this.listenerRegistry = new ListenerRegistry(this.plugin, this, this.configuration);
        this.cacheListener = new BuffCacheListener(this.playerBuffCache, this.companionBuffResolver, this.companions);
        this.plugin.getServer().getPluginManager().registerEvents(this.cacheListener, this.plugin);
        this.listenerRegistry.registerAll();
        BuffCacheListener current = this.cacheListener;
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            FoliaScheduler.runEntity(this.plugin, player, () -> {
                if (this.cacheListener == current && player.isOnline()) {
                    current.refresh(player);
                }
            });
        }
    }

    public synchronized CompletableFuture<Void> stopAsync() {
        if (this.stopped != null) {
            return this.stopped;
        }
        this.active = false;
        if (this.cacheListener != null) {
            HandlerList.unregisterAll(this.cacheListener);
            this.cacheListener = null;
        }
        if (this.listenerRegistry != null) {
            this.listenerRegistry.unregisterAll();
        }
        List<CompletableFuture<Void>> cleanups = new ArrayList<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            cleanups.add(BukkitOwnerTasks.entity(this.plugin, player, () -> {
                if (this.playerBuffCache != null) {
                    this.playerBuffCache.clear(player);
                }
                if (this.cooldownManager != null) {
                    this.cooldownManager.remove(player.getEntityId());
                }
            }));
        }
        this.stopped = CompletableFuture.allOf(cleanups.toArray(CompletableFuture[]::new));
        return this.stopped;
    }

    public boolean active() {
        return this.active;
    }

    public void notifyPlayer(final @NotNull Player player, ResolvedBuff selected, BuffKey key) {
        if (selected.companionTypeId() == null) {
            return;
        }
        Buff buff = selected.buff();
        double value = this.buffService.value(player, selected.companionTypeId(), key, buff);
        FoliaScheduler.runEntity(this.plugin, player, () -> {
            if (this.active && player.isOnline()) {
                this.sendFeedback(player, buff, value);
            }
        });
    }

    private void sendFeedback(Player player, Buff buff, double value) {
        final FeedBack feedBack = buff.feedBack();

        if (feedBack.actionBar().enabled()) {
            final String message = feedBack.actionBar().message()
                    .replace("{value}", Double.toString(value))
                    .replace("{operation}", buff.operation().name());

            ComponentMessenger.sendActionBar(player, ComponentText.component(ComponentUtil.asComponent(message)));
        }

        if (feedBack.sound().enabled()) {
            SoundPlayback.play(player, feedBack.sound().adventureSound());
        }
    }

    public boolean hasBoosterChannel(BuffKey key) {
        if (this.progression == null || !this.progression.boostersEnabled()) {
            return false;
        }
        String channel = key.name().toLowerCase(java.util.Locale.ROOT);
        return this.progression.definitions().boosters().values().stream()
                .anyMatch(definition -> definition.percentages().containsKey(channel));
    }

    public void refresh(Player player) {
        if (this.active && this.cacheListener != null) {
            this.cacheListener.refresh(player);
        }
    }

    public PlayerBuffCache playerBuffCache() {
        return this.playerBuffCache;
    }

    public BuffService buffService() {
        return this.buffService;
    }

    public CooldownManager cooldownManager() {
        return this.cooldownManager;
    }

    public Companions companions(final @NotNull String companionId) {
        return this.configuration().companions().get(companionId);
    }

    public Configuration configuration() {
        return this.configuration;
    }

    private Configuration loadConfiguration() {
        try {
            final ConfigurationNode configurationNode = ((PaperPlugin) this.plugin).contentSnapshot()
                    .node("features/buffs/config.toml", ConfigurationOptions.defaults());
            Configuration loaded = Objects.requireNonNull(configurationNode.get(Configuration.class), "Buffs configuration is empty");
            BuffPlatformSupport.validate(loaded, BuffPlatformSupport.supportsPickupExperience());
            return loaded;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
