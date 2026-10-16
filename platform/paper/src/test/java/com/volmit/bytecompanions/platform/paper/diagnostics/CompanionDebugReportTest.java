package com.volmit.bytecompanions.platform.paper.diagnostics;

import org.junit.jupiter.api.Test;
import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionsTickScheduler;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompanionDebugReportTest {
    @Test
    void asynchronousRenderingUsesCapturedFeatureState() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("particles", true);
        Map<String, KeyedTaskQueue.Snapshot> queues = new HashMap<>();
        queues.put("companions", new KeyedTaskQueue.Snapshot(1, 2, 3, false));
        CompanionDebugReport report = new CompanionDebugReport(true, 3, "V26_1CompanionEntityFactory", "H2",
                5, 1, features, 3, 20, 32, new CompanionDebugReport.RuntimeSnapshot(
                new CompanionsTickScheduler.Snapshot(1, 2, 100, 4), 5, queues,
                new EffectTrackingAnimation.TrafficSnapshot(10, 80, 20, 160), 1, "en_US", "config.toml (active runtime snapshot)"));
        String captured = report.render();
        features.put("particles", false);
        features.put("effects", false);
        queues.clear();

        assertEquals(captured, report.render());
        assertTrue(captured.contains("Feature particles: true"));
        assertTrue(captured.contains("Companion definitions: 5"));
        assertTrue(captured.contains("Active player companions (last tick): 2"));
        assertTrue(captured.contains("Player load failures: 5"));
        assertTrue(captured.contains("Storage queue companions failures: 3"));
        assertTrue(captured.contains("Particle viewer deliveries (not distinct viewers): 20"));
        assertTrue(captured.contains("Particle packet deliveries: 160"));
        assertTrue(captured.contains("Particle budget: not configured"));
        assertTrue(captured.contains("Default language: en_US"));
        assertFalse(captured.contains("jdbc:"));
        assertFalse(captured.contains("password"));
    }
}
