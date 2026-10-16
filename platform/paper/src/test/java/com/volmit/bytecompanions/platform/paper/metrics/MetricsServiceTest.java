package com.volmit.bytecompanions.platform.paper.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MetricsServiceTest {
    @TempDir
    Path directory;

    @Test
    void pluginOptOutDoesNotConstructMetrics() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class);
             MetricsService service = new MetricsService(plugin)) {
            service.reconfigure(false);
            service.reconfigure(false);

            assertFalse(service.initialized());
            assertTrue(construction.constructed().isEmpty());
            verifyNoInteractions(plugin);
        }
    }

    @Test
    void enablingUsesRegisteredServiceIdAndRetainsOneInstanceAcrossReconfiguration() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class,
                (metrics, context) -> assertEquals(List.of(plugin, 34587), context.arguments()));
             MetricsService service = new MetricsService(plugin)) {
            service.reconfigure(true);
            service.reconfigure(true);
            service.reconfigure(true);

            assertTrue(service.initialized());
            assertEquals(1, construction.constructed().size());
            verify(construction.constructed().getFirst(), never()).shutdown();
        }
    }

    @Test
    void disablingStopsActiveInstanceAndReenablingCreatesOneReplacement() {
        try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class);
             MetricsService service = new MetricsService(mock(JavaPlugin.class))) {
            service.reconfigure(true);
            Metrics first = construction.constructed().getFirst();

            service.reconfigure(false);
            service.reconfigure(false);

            assertFalse(service.initialized());
            verify(first).shutdown();
            service.reconfigure(true);
            assertTrue(service.initialized());
            assertEquals(2, construction.constructed().size());
            verify(construction.constructed().get(1), never()).shutdown();
        }
    }

    @Test
    void closingStopsOnceAndCannotBeReenabled() {
        try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class)) {
            MetricsService service = new MetricsService(mock(JavaPlugin.class));
            service.reconfigure(true);

            service.close();
            service.close();
            service.reconfigure(true);

            assertFalse(service.initialized());
            assertEquals(1, construction.constructed().size());
            verify(construction.constructed().getFirst()).shutdown();
            verifyNoMoreInteractions(construction.constructed().getFirst());
        }
    }

    @Test
    void constructorFailureIsLoggedAndLaterReconfigurationCanRecover() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (MetricsService service = new MetricsService(plugin)) {
            try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class,
                    (metrics, context) -> { throw new IllegalStateException("Metrics initialization failed"); })) {
                assertDoesNotThrow(() -> service.reconfigure(true));
                assertFalse(service.initialized());
                assertTrue(construction.constructed().isEmpty());
                verify(logger).log(eq(Level.WARNING), eq("Unable to start ByteCompanions bStats metrics"),
                        any(RuntimeException.class));
            }
            try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class)) {
                service.reconfigure(true);

                assertTrue(service.initialized());
                assertEquals(1, construction.constructed().size());
                service.reconfigure(false);
                verify(construction.constructed().getFirst()).shutdown();
            }
        }
    }

    @Test
    void shutdownFailureIsLoggedWithoutPreventingDisableOrFutureEnable() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        IllegalStateException failure = new IllegalStateException("Metrics shutdown failed");
        try (MockedConstruction<Metrics> construction = mockConstruction(Metrics.class);
             MetricsService service = new MetricsService(plugin)) {
            service.reconfigure(true);
            doThrow(failure).when(construction.constructed().getFirst()).shutdown();

            assertDoesNotThrow(() -> service.reconfigure(false));

            assertFalse(service.initialized());
            verify(logger).log(Level.WARNING, "Unable to stop ByteCompanions bStats metrics", failure);
            service.reconfigure(true);
            assertTrue(service.initialized());
            assertEquals(2, construction.constructed().size());
        }
    }

    @Test
    void globalBstatsOptOutPreventsSchedulingEvenWhenPluginMetricsAreEnabled() throws Exception {
        Path bstats = Files.createDirectories(directory.resolve("bStats"));
        String globalConfiguration = "enabled: false\nserverUuid: 5c1de4f7-6670-4890-bda3-5d9a8cbbca01\n";
        Files.writeString(bstats.resolve("config.yml"), globalConfiguration);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.resolve("ByteCompanions").toFile());

        try (MockedConstruction<ScheduledThreadPoolExecutor> construction = mockConstruction(ScheduledThreadPoolExecutor.class)) {
            MetricsService service = new MetricsService(plugin);
            try {
                service.reconfigure(true);

                assertTrue(service.initialized());
                assertEquals(1, construction.constructed().size());
                ScheduledThreadPoolExecutor scheduler = construction.constructed().getFirst();
                verify(scheduler).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
                verifyNoMoreInteractions(scheduler);
                assertEquals(globalConfiguration, Files.readString(bstats.resolve("config.yml")));
            } finally {
                service.close();
            }
            ScheduledThreadPoolExecutor scheduler = construction.constructed().getFirst();
            verify(scheduler).shutdown();
            verifyNoMoreInteractions(scheduler);
        }
    }
}
