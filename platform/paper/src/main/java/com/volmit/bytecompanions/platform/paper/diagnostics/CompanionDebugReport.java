package com.volmit.bytecompanions.platform.paper.diagnostics;

import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionsTickScheduler;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;

import java.util.Map;
import java.util.TreeMap;

public record CompanionDebugReport(boolean active, long generation, String adapter, String storageType,
                                   int companionDefinitions, int loadedPlayers, Map<String, Boolean> features,
                                   int updateInterval, int viewerRefreshDelay, int viewDistance,
                                   RuntimeSnapshot runtime)
        implements DebugDumpContributor.Report {
    public CompanionDebugReport {
        features = Map.copyOf(features);
    }

    @Override
    public String render() {
        StringBuilder report = new StringBuilder("ByteCompanions runtime\n");
        report.append("Active: ").append(active).append('\n');
        report.append("Generation: ").append(generation).append('\n');
        report.append("Native adapter: ").append(adapter).append('\n');
        report.append("Storage type: ").append(storageType).append('\n');
        report.append("Companion definitions: ").append(companionDefinitions).append('\n');
        report.append("Loaded players: ").append(loadedPlayers).append('\n');
        report.append("Update interval (ticks): ").append(updateInterval).append('\n');
        report.append("Viewer refresh delay (ticks): ").append(viewerRefreshDelay).append('\n');
        report.append("View distance (blocks): ").append(viewDistance).append('\n');
        report.append("Configuration source: ").append(runtime.configurationSource()).append('\n');
        report.append("Default language: ").append(runtime.defaultLanguage()).append('\n');
        report.append("Active decorations: ").append(runtime.activeDecorations()).append('\n');
        report.append("Player load failures: ").append(runtime.loadFailures()).append('\n');
        if (runtime.ticks() == null) {
            report.append("Player tick scheduler: unavailable\n");
        } else {
            report.append("Player tick tasks: ").append(runtime.ticks().playerTasks()).append('\n');
            report.append("Active player companions (last tick): ").append(runtime.ticks().activeCompanions()).append('\n');
            report.append("Completed player ticks: ").append(runtime.ticks().completedTicks()).append('\n');
            report.append("Player tick failures: ").append(runtime.ticks().failures()).append('\n');
        }
        report.append("Distinct active viewers: unavailable\n");
        for (Map.Entry<String, KeyedTaskQueue.Snapshot> queue : new TreeMap<>(runtime.storageQueues()).entrySet()) {
            String prefix = "Storage queue " + queue.getKey();
            report.append(prefix).append(" active players: ").append(queue.getValue().activeKeys()).append('\n');
            report.append(prefix).append(" pending operations: ").append(queue.getValue().pendingTasks()).append('\n');
            report.append(prefix).append(" failures: ").append(queue.getValue().failures()).append('\n');
            report.append(prefix).append(" closed: ").append(queue.getValue().closed()).append('\n');
        }
        report.append("Particle traffic scope: plugin classloader lifetime; successful batch sends\n");
        report.append("Particle batches: ").append(runtime.particles().batches()).append('\n');
        report.append("Particle packet entries: ").append(runtime.particles().packetEntries()).append('\n');
        report.append("Particle viewer deliveries (not distinct viewers): ").append(runtime.particles().viewerDeliveries()).append('\n');
        report.append("Particle packet deliveries: ").append(runtime.particles().packetDeliveries()).append('\n');
        report.append("Particle budget: not configured\n");
        for (Map.Entry<String, Boolean> feature : new TreeMap<>(features).entrySet()) {
            report.append("Feature ").append(feature.getKey()).append(": ").append(feature.getValue()).append('\n');
        }
        return report.toString();
    }

    public record RuntimeSnapshot(CompanionsTickScheduler.Snapshot ticks, long loadFailures,
                                  Map<String, KeyedTaskQueue.Snapshot> storageQueues,
                                  EffectTrackingAnimation.TrafficSnapshot particles, int activeDecorations,
                                  String defaultLanguage, String configurationSource) {
        public RuntimeSnapshot {
            storageQueues = Map.copyOf(storageQueues);
        }
    }
}
