package com.volmit.bytecompanions.features.decorations.data;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class ConfigurationData {

    private final Map<String, CompanionData> companionDataMap;
    private final int updateIntervalTicks;
    private final int viewDistance;
    private final int viewerRefreshDelay;

    public ConfigurationData(
            final int updateIntervalTicks,
            final int viewDistance,
            final int viewerRefreshDelay,
            final @NotNull Map<String, CompanionData> companionDataMap
    ) {
        this.updateIntervalTicks = updateIntervalTicks;
        this.viewDistance = viewDistance;
        this.viewerRefreshDelay = viewerRefreshDelay;
        this.companionDataMap = Map.copyOf(companionDataMap);
    }

    public int updateIntervalTicks() {
        return this.updateIntervalTicks;
    }

    public int viewDistance() {
        return this.viewDistance;
    }

    public int viewerRefreshDelay() {
        return this.viewerRefreshDelay;
    }

    public @NotNull Map<String, CompanionData> companionDataMap() {
        return this.companionDataMap;
    }

    public @NotNull Optional<CompanionData> get(final @NotNull String id) {
        return Optional.ofNullable(this.companionDataMap.get(id));
    }

}