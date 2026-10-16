package com.volmit.bytecompanions.features.decorations;

import org.slf4j.LoggerFactory;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.spi.companions.CompanionLocalSettings;
import com.volmit.bytecompanions.features.decorations.data.CompanionData;
import com.volmit.bytecompanions.features.decorations.data.ConfigurationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class DecorationsFeature {

    private final PaperPlugin plugin;
    private final List<DecorationRuntime> decorations = new ArrayList<>();
    private ConfigurationData configurationData;
    private CompletableFuture<Void> stopped;

    public DecorationsFeature(PaperPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized CompletableFuture<Void> enableAsync() {
        this.configurationData = this.createConfigurationData();
        this.loadDecorations();
        List<CompletableFuture<Void>> starts = new ArrayList<>();
        for (DecorationRuntime decoration : this.decorations) {
            starts.add(decoration.startAsync());
        }
        return CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new));
    }

    public synchronized CompletableFuture<Void> stopAsync() {
        if (this.stopped != null) {
            return this.stopped;
        }
        List<CompletableFuture<Void>> cleanups = new ArrayList<>();
        for (DecorationRuntime decoration : this.decorations) {
            cleanups.add(decoration.closeAsync());
        }
        this.stopped = CompletableFuture.allOf(cleanups.toArray(CompletableFuture[]::new));
        return this.stopped;
    }

    private void loadDecorations() {
        for (Map.Entry<String, CompanionData> entry : this.configurationData.companionDataMap().entrySet()) {
            final String id = entry.getKey();
            final CompanionData data = entry.getValue();

            final String companionId = data.companionTypeId();
            final World world = this.plugin.getServer().getWorld(data.worldName());

            if (world == null) {
                LoggerFactory.getLogger(this.plugin.getName()).info("World '{}' not found for companion '{}'. Skipping.", data.worldName(), id);
                continue;
            }

            final CompanionType companionType = this.plugin.getCompanionTypeOrNull(companionId);
            if (companionType == null) {
                LoggerFactory.getLogger(this.plugin.getName()).info("Companion type with ID '{}' not found for companion '{}'. Skipping.", companionId, id);
                continue;
            }

            final StaticAnchor staticAnchor = new StaticAnchor(
                    data.x(),
                    data.y(),
                    data.z(),
                    data.yaw(),
                    data.pitch(),
                    data.bodyYaw(),
                    data.eyeHeight(),
                    data.height(),
                    world
            );

            Location location = new Location(world, data.x(), data.y(), data.z());
            ConfigurationData settings = this.configurationData;
            this.decorations.add(new DecorationRuntime(
                    () -> this.plugin.companionFactory().create(staticAnchor, companionType,
                            new CompanionLocalSettings(settings.viewDistance(), settings.viewerRefreshDelay())),
                    action -> BukkitOwnerTasks.region(this.plugin, location, action),
                    action -> FoliaScheduler.runRegion(this.plugin, location, action, settings.updateIntervalTicks()),
                    failure -> this.plugin.getLogger().log(Level.SEVERE,
                            "Decoration tick failed for " + id, failure)));
        }

    }

    private @NotNull ConfigurationData createConfigurationData() {
        MemoryConfiguration configuration = ((PaperPlugin) this.plugin).contentSnapshot().bukkit("features/decorations/config.toml");
        final Map<String, CompanionData> companionDataMap = new HashMap<>();

        final int updateIntervalTicks = configuration.getInt("settings.update-interval", 3);
        final int viewDistance = configuration.getInt("settings.view-distance", 8);
        final int viewerRefreshDelay = configuration.getInt("settings.view-refresh-delay", 3);
        if (updateIntervalTicks < 1 || viewDistance < 0 || viewerRefreshDelay < 0) {
            throw new IllegalArgumentException("Decoration update interval must be positive; distances and refresh delay cannot be negative");
        }

        final ConfigurationSection decorations = configuration.getConfigurationSection("decorations");
        if (decorations != null) {
            for (final String id : decorations.getKeys(false)) {
                final ConfigurationSection section = decorations.getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", true)) {
                    continue;
                }

                companionDataMap.put(id, new CompanionData(
                        id,
                        section.getString("companion"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        (float) section.getDouble("yaw", 0.0),
                        (float) section.getDouble("pitch", 0.0),
                        (float) section.getDouble("body-yaw", 0.0),
                        section.getDouble("eye-height", 1.4),
                        section.getDouble("height", 1.8),
                        section.getString("world", "world")
                ));
            }
        }

        return new ConfigurationData(updateIntervalTicks, viewDistance, viewerRefreshDelay, companionDataMap);
    }

    public synchronized List<Companion> registeredCompanions() {
        List<Companion> companions = new ArrayList<>();
        for (DecorationRuntime decoration : this.decorations) {
            Companion companion = decoration.companion();
            if (companion != null) {
                companions.add(companion);
            }
        }
        return List.copyOf(companions);
    }

}
