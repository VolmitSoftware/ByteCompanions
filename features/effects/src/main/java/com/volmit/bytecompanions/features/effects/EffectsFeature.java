package com.volmit.bytecompanions.features.effects;

import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.bonus.BonusValues;

import org.slf4j.LoggerFactory;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.features.effects.data.ConfigurationData;
import com.volmit.bytecompanions.features.effects.data.PotionEffectData;
import com.volmit.bytecompanions.features.effects.listener.MechanicListeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EffectsFeature {

    private final ProgressionService progression;
    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private volatile long generation;
    private volatile ConfigurationData configurationData;
    private volatile MechanicListeners listener;
    private volatile boolean active;
    private CompletableFuture<Void> stopped;
    private volatile Predicate<Player> bonusesAllowed = player -> true;

    public EffectsFeature(JavaPlugin plugin, ByteCompanions companions, ProgressionService progression) {
        this.progression = progression;
        this.plugin = plugin;
        this.companions = companions;
    }

    public void enable() {
        this.configurationData = this.createConfigurationData();
        this.active = true;
        this.listener = new MechanicListeners(this, this.companions);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.reapplyPlayers();
    }

    public synchronized CompletableFuture<Void> stopAsync() {
        if (this.stopped != null) {
            return this.stopped;
        }
        this.active = false;
        this.cancelPendingTasks();
        MechanicListeners current = this.listener;
        CompletableFuture<Void> cleanup = current == null ? CompletableFuture.completedFuture(null) : this.clearPlayers();
        this.stopped = cleanup.whenComplete((ignored, failure) -> {
            if (current != null) {
                HandlerList.unregisterAll(current);
            }
        });
        return this.stopped;
    }

    public boolean active() {
        return this.active;
    }

    private CompletableFuture<Void> clearPlayers() {
        MechanicListeners current = this.listener;
        List<CompletableFuture<Void>> cleanupTasks = new ArrayList<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            cleanupTasks.add(BukkitOwnerTasks.entity(this.plugin, player, () -> current.removeAllEffects(player)));
        }
        return CompletableFuture.allOf(cleanupTasks.toArray(CompletableFuture[]::new));
    }

    private void reapplyPlayers() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.runTaskInNextTick(player, () -> this.companions.getCompanionPlayer(player)
                    .ifPresent(companionPlayer -> this.listener.applyAllEffects(player, companionPlayer)));
        }
    }

    private void cancelPendingTasks() {
        this.generation++;
    }

    public void runTaskInNextTick(Player player, final @NotNull Runnable task) {
        long expected = this.generation;
        FoliaScheduler.runEntity(this.plugin, player, () -> {
            if (this.active && expected == this.generation && this.listener != null && player.isOnline() && !player.isDead()) {
                task.run();
            }
        }, 1);
    }

    @Contract(" -> new")
    private @NotNull ConfigurationData createConfigurationData() {
        MemoryConfiguration configuration = ((PaperPlugin) this.plugin).contentSnapshot().bukkit("features/effects/config.toml");
        ContentPreflight.validateEffects(configuration);
        final Map<String, List<PotionEffectData>> companionEffects = new HashMap<>();

        final ConfigurationSection companions = configuration.getConfigurationSection("companions");
        if (companions != null) {
            for (final String companionId : companions.getKeys(false)) {
                final List<Map<?, ?>> effectList = configuration.getMapList("companions." + companionId);
                final List<PotionEffectData> effects = new ArrayList<>();

                for (final Map<?, ?> entry : effectList) {
                    final String effectName = (String) entry.get("effect");
                    final int amplifier = entry.get("amplifier") instanceof Number value ? value.intValue() : 1;
                    final int priority = entry.get("priority") instanceof Number value ? value.intValue() : 0;
                    final boolean ambient = entry.get("ambient") instanceof Boolean b ? b : true;
                    final boolean particles = entry.get("particles") instanceof Boolean b ? b : true;
                    final boolean icon = entry.get("icon") instanceof Boolean b ? b : true;
                    final String stackModeString = entry.get("stack-mode") instanceof String ? (String) entry.get("stack-mode") : null;

                    final StackMode stackMode;
                    try {
                        if (stackModeString == null || stackModeString.isBlank()) {
                            LoggerFactory.getLogger(this.plugin.getName()).info("Invalid stack mode in {}", effectName);
                            continue;
                        }

                        stackMode = StackMode.valueOf(stackModeString.toUpperCase(Locale.ROOT));
                    } catch (final IllegalArgumentException e) {
                        LoggerFactory.getLogger(this.plugin.getName()).info("Invalid stack mode in {}", effectName);
                        continue;
                    }

                    final NamespacedKey namespacedKey = NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT));
                    final PotionEffectType potionEffectType = Registry.EFFECT.get(namespacedKey);
                    if (potionEffectType == null) {
                        continue;
                    }

                    effects.add(new PotionEffectData(potionEffectType, amplifier, priority, ambient, particles, icon, stackMode));
                }

                companionEffects.put(companionId, List.copyOf(effects));
            }
        }

        return new ConfigurationData(companionEffects);
    }

    public List<PotionEffectData> effectiveEffects(Player player, String companionId) {
        List<PotionEffectData> configured = this.configurationData.effects(companionId);
        if (this.progression == null) {
            return configured;
        }
        return configured.stream().map(entry -> new PotionEffectData(entry.effectType(),
                BonusValues.amplifier(entry.amplifier(), this.progression.bonusAddition(player.getUniqueId(), companionId,
                        "effect:" + entry.effectType().getKey())), entry.priority(), entry.ambient(), entry.particles(),
                entry.icon(), entry.stackMode())).toList();
    }

    public void refresh(Player player) {
        if (this.active && this.listener != null) {
            if (this.bonusesAllowed(player)) {
                this.companions.getCompanionPlayer(player).ifPresent(state -> this.listener.applyAllEffects(player, state));
            } else {
                this.listener.removeAllEffects(player);
            }
        }
    }

    public void bonusesAllowed(Predicate<Player> allowed) {
        this.bonusesAllowed = Objects.requireNonNull(allowed);
    }

    public boolean bonusesAllowed(Player player) {
        return this.bonusesAllowed.test(player);
    }

    public ConfigurationData configurationData() {
        return this.configurationData;
    }

    public boolean ownsEffect(Player player, org.bukkit.potion.PotionEffectType type) {
        return this.listener != null && this.listener.ownsEffect(player, type);
    }
}
