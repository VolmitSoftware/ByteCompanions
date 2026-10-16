package com.volmit.bytecompanions.features.attributes;

import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.bonus.BonusValues;

import org.slf4j.LoggerFactory;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.features.attributes.data.AttributeModifierData;
import com.volmit.bytecompanions.features.attributes.data.ConfigurationData;
import com.volmit.bytecompanions.features.attributes.listener.MechanicListeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class AttributesFeature {

    private final ProgressionService progression;
    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private volatile ConfigurationData configurationData;
    private MechanicListeners listener;
    private volatile boolean active;
    private CompletableFuture<Void> stopped;
    private volatile Predicate<Player> bonusesAllowed = player -> true;

    public AttributesFeature(JavaPlugin plugin, ByteCompanions companions, ProgressionService progression) {
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
        MechanicListeners current = this.listener;
        CompletableFuture<Void> cleanup = this.configurationData == null
                ? CompletableFuture.completedFuture(null) : this.clearPlayers();
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

    public ConfigurationData configurationData() {
        return this.configurationData;
    }

    public List<AttributeModifierData> effectiveAttributes(Player player, String companionId) {
        List<AttributeModifierData> configured = this.configurationData.getAttribute(companionId);
        if (configured == null) {
            return List.of();
        }
        if (this.progression == null) {
            return configured;
        }
        return configured.stream().map(entry -> new AttributeModifierData(entry.attribute(), entry.key(),
                BonusValues.add(entry.amount(), this.progression.bonusAddition(player.getUniqueId(), companionId,
                        "attribute:" + entry.attribute().getKey())), entry.operation())).toList();
    }

    public void refresh(Player player) {
        if (this.active) {
            this.companions.getCompanionPlayer(player).ifPresent(state -> {
                if (this.bonusesAllowed(player)) {
                    state.companions().keySet().forEach(id -> this.applyAttributes(player, id));
                } else {
                    state.companions().keySet().forEach(id -> this.removeAttributes(player, id));
                }
            });
        }
    }

    public void bonusesAllowed(Predicate<Player> allowed) {
        this.bonusesAllowed = Objects.requireNonNull(allowed);
    }

    public boolean bonusesAllowed(Player player) {
        return this.bonusesAllowed.test(player);
    }

    public void applyAttributes(final @NotNull Player player, final @NotNull String typeId) {
        if (!this.active || !this.bonusesAllowed(player)) {
            return;
        }
        final List<AttributeModifierData> attributeModifierData = this.effectiveAttributes(player, typeId);
        if (attributeModifierData == null || attributeModifierData.isEmpty()) {
            return;
        }

        for (final AttributeModifierData attData : attributeModifierData) {
            final AttributeInstance attributeInstance = player.getAttribute(attData.attribute());
            if (attributeInstance != null) {
                removeModifier(attributeInstance, attData.key());
                attributeInstance.addModifier(attData.createBukkitInstance());
            }
        }
    }

    public void removeAttributes(final @NotNull Player player, final @NotNull String typeId) {
        final List<AttributeModifierData> attributeModifierData = this.configurationData.getAttribute(typeId);
        this.removeModifiers(player, attributeModifierData);
    }

    private void removeModifiers(Player player, List<AttributeModifierData> attributeModifierData) {
        if (attributeModifierData == null || attributeModifierData.isEmpty()) {
            return;
        }

        for (final AttributeModifierData attData : attributeModifierData) {
            final AttributeInstance attributeInstance = player.getAttribute(attData.attribute());
            if (attributeInstance != null) {
                removeModifier(attributeInstance, attData.key());
            }
        }
    }

    static void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getKey().equals(key)) {
                instance.removeModifier(modifier);
            }
        }
    }

    private CompletableFuture<Void> clearPlayers() {
        List<CompletableFuture<Void>> cleanupTasks = new ArrayList<>();
        ConfigurationData current = this.configurationData;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            Runnable cleanup = () -> this.companions.getCompanionPlayer(player).ifPresent(companionPlayer ->
                    companionPlayer.companions().values().forEach(companion ->
                            this.removeModifiers(player, current.getAttribute(companion.type().id()))));
            cleanupTasks.add(BukkitOwnerTasks.entity(this.plugin, player, cleanup));
        }
        return CompletableFuture.allOf(cleanupTasks.toArray(CompletableFuture[]::new));
    }

    private void reapplyPlayers() {
        ConfigurationData current = this.configurationData;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(this.plugin, player, () -> {
                if (!this.active || this.configurationData != current || !player.isOnline()) {
                    return;
                }
                this.companions.getCompanionPlayer(player).ifPresent(companionPlayer ->
                    companionPlayer.companions().values().forEach(companion ->
                            this.applyAttributes(player, companion.type().id())));
            });
        }
    }

    @Contract(" -> new")
    private @NotNull ConfigurationData createConfigurationData() {
        MemoryConfiguration configuration = ((PaperPlugin) this.plugin).contentSnapshot().bukkit("features/attributes/config.toml");
        ContentPreflight.validateAttributes(configuration);
        final Map<String, List<AttributeModifierData>> companionEffects = new HashMap<>();

        final ConfigurationSection companions = configuration.getConfigurationSection("companions");
        if (companions != null) {
            for (final String companionId : companions.getKeys(false)) {
                final List<Map<?, ?>> attributesList = configuration.getMapList("companions." + companionId);
                final List<AttributeModifierData> attributeModifierData = new ArrayList<>();

                for (final Map<?, ?> entry : attributesList) {
                    final String attributeKey = (String) entry.get("attribute");
                    final String attributeModifierId = companionId + "_" + attributeKey;
                    final double amount = entry.get("amount") instanceof Number number ? number.doubleValue() : 0.0;
                    final String operation = entry.get("operation") instanceof String s ? s : "";
                    final AttributeModifier.Operation operationEnum;

                    try {
                        operationEnum = AttributeModifier.Operation.valueOf(operation.toUpperCase(Locale.ROOT));
                    } catch (final IllegalArgumentException e) {
                        LoggerFactory.getLogger(this.plugin.getName()).info("Invalid operation '{}' for companion '{}'. Skipping entry.", operation, companionId);
                        continue;
                    }

                    final String normalizedAttributeKey = attributeKey.toLowerCase(Locale.ROOT);
                    final NamespacedKey attributeNamespacedKey = NamespacedKey.minecraft(normalizedAttributeKey);
                    final Attribute attribute = Registry.ATTRIBUTE.get(attributeNamespacedKey);
                    if (attribute == null) {
                        LoggerFactory.getLogger(this.plugin.getName()).error("Attribute '{}' not found for companion '{}'. Skipping entry.", attributeNamespacedKey, companionId);
                        continue;
                    }

                    final NamespacedKey attributeModifierKey = new NamespacedKey(this.plugin, attributeModifierId);
                    attributeModifierData.add(new AttributeModifierData(attribute, attributeModifierKey, amount, operationEnum));
                }

                companionEffects.put(companionId, List.copyOf(attributeModifierData));
            }
        }

        return new ConfigurationData(companionEffects);
    }
}
