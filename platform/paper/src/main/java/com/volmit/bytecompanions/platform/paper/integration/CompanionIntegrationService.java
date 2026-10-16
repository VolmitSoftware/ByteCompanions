package com.volmit.bytecompanions.platform.paper.integration;

import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class CompanionIntegrationService implements IntegrationServiceContract {
    private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
            new IntegrationProtocolVersion(1, 0),
            new IntegrationProtocolVersion(1, 1));
    private static final Set<String> CAPABILITIES = Set.of("handshake", "heartbeat", "metrics");

    private final Plugin plugin;
    private final Supplier<Snapshot> snapshots;
    private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

    public CompanionIntegrationService(Plugin plugin, Supplier<Snapshot> snapshots) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    public void register() {
        plugin.getServer().getServicesManager().register(IntegrationServiceContract.class, this, plugin, ServicePriority.Normal);
    }

    public void unregister() {
        plugin.getServer().getServicesManager().unregister(IntegrationServiceContract.class, this);
    }

    @Override
    public String pluginId() {
        return "bytecompanions";
    }

    @Override
    public String pluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public Set<IntegrationProtocolVersion> supportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

    @Override
    public Set<String> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public Set<IntegrationMetricDescriptor> metricDescriptors() {
        return IntegrationMetricSchema.descriptors().stream()
                .filter(descriptor -> descriptor.key().startsWith("bytecompanions."))
                .collect(Collectors.toSet());
    }

    @Override
    public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
        long now = System.currentTimeMillis();
        if (request == null) {
            return new IntegrationHandshakeResponse(pluginId(), pluginVersion(), false, null,
                    SUPPORTED_PROTOCOLS, CAPABILITIES, "missing request", now);
        }
        Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
                SUPPORTED_PROTOCOLS, request.supportedProtocols());
        if (negotiated.isEmpty()) {
            return new IntegrationHandshakeResponse(pluginId(), pluginVersion(), false, null,
                    SUPPORTED_PROTOCOLS, CAPABILITIES, "no-common-protocol", now);
        }
        negotiatedProtocol = negotiated.get();
        return new IntegrationHandshakeResponse(pluginId(), pluginVersion(), true, negotiatedProtocol,
                SUPPORTED_PROTOCOLS, CAPABILITIES, "ok", now);
    }

    @Override
    public IntegrationHeartbeat heartbeat() {
        Snapshot snapshot = snapshots.get();
        return new IntegrationHeartbeat(negotiatedProtocol, snapshot.active(), System.currentTimeMillis(),
                snapshot.active() ? "ok" : "runtime inactive");
    }

    @Override
    public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
        Set<String> requested = metricKeys == null || metricKeys.isEmpty()
                ? IntegrationMetricSchema.byteCompanionsKeys() : metricKeys;
        Snapshot snapshot = snapshots.get();
        long now = System.currentTimeMillis();
        Map<String, IntegrationMetricSample> samples = new HashMap<>(requested.size());
        for (String key : requested) {
            switch (key) {
                case IntegrationMetricSchema.BYTECOMPANIONS_COMPANION_DEFINITIONS -> samples.put(key, sample(key, snapshot.definitions(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_LOADED_PLAYERS -> samples.put(key, sample(key, snapshot.loadedPlayers(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_ACTIVE_COMPANIONS -> samples.put(key, sample(key, snapshot.activeCompanions(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_ACTIVE_DECORATIONS -> samples.put(key, sample(key, snapshot.activeDecorations(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_PLAYER_TICK_FAILURES_TOTAL -> samples.put(key, sample(key, snapshot.tickFailures(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_PLAYER_LOAD_FAILURES_TOTAL -> samples.put(key, sample(key, snapshot.loadFailures(), now));
                case IntegrationMetricSchema.BYTECOMPANIONS_PARTICLE_PACKETS_TOTAL -> samples.put(key, sample(key, snapshot.particlePackets(), now));
                default -> samples.put(key, IntegrationMetricSample.unavailable(IntegrationMetricSchema.descriptor(key), "unknown metric", now));
            }
        }
        return samples;
    }

    private static IntegrationMetricSample sample(String key, double value, long now) {
        return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, now);
    }

    public record Snapshot(boolean active, int definitions, int loadedPlayers, int activeCompanions, int activeDecorations,
                           long tickFailures, long loadFailures, long particlePackets) {
        public static final Snapshot INACTIVE = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0);
    }
}
