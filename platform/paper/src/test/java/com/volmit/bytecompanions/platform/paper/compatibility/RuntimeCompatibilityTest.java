package com.volmit.bytecompanions.platform.paper.compatibility;

import com.volmit.bytecompanions.platform.paper.compatibility.RuntimeCompatibility.CompanionAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCompatibilityTest {
    private static final List<String> SUPPORTED = List.of("26.1", "26.1-R0.1-SNAPSHOT", "26.1.1.build.29-alpha",
            "26.1.2-R0.1-SNAPSHOT", "26.1.2.build.74-stable", "26.1.2.build.876-recommended", "26.2-R0.1-SNAPSHOT",
            "26.2.build.134-stable", "26.2.1", "26.3-R0.1-SNAPSHOT", "26.3.build.190-stable", "26.3.build.157-beta",
            "26.3.build.2647-experimental", "26.3.1");

    @Test
    void acceptsEveryReleaseInTheSupportedRangeOnJava25() {
        for (String version : SUPPORTED) {
            assertDoesNotThrow(() -> RuntimeCompatibility.requireSupported(version, 25), version);
        }
    }

    @Test
    void rejectsVersionsOutsideTheSupportedRange() {
        for (String version : List.of("1.20.1-R0.1-SNAPSHOT", "1.21.11", "1.21.11-R0.2-SNAPSHOT", "26.0", "26.4",
                "26.4.build.1-stable", "27.1", "26.30", "26.3-pre1", "26.1.2-pre1-R0.1-SNAPSHOT", "26.1.2.build.3-ALPHA",
                "26w01a", "unknown", "")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> RuntimeCompatibility.requireSupported(version, 25), version);
            assertTrue(failure.getMessage().contains(version), version);
            assertTrue(failure.getMessage().contains(BuildInfo.minecraftRange()), version);
        }
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.requireSupported(null, 25));
    }

    @Test
    void requiresJava25Exactly() {
        for (int javaVersion : List.of(17, 21, 24, 26)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> RuntimeCompatibility.requireSupported("26.3.build.190-stable", javaVersion));
            assertTrue(failure.getMessage().contains("Java " + javaVersion));
        }
    }

    @Test
    void extractsTheMinecraftReleaseFromEveryBukkitForm() {
        assertEquals("26.1", RuntimeCompatibility.minecraftVersion("26.1-R0.1-SNAPSHOT"));
        assertEquals("26.1.2", RuntimeCompatibility.minecraftVersion("26.1.2-R0.1-SNAPSHOT"));
        assertEquals("26.1.2", RuntimeCompatibility.minecraftVersion("26.1.2.build.74-stable"));
        assertEquals("26.3", RuntimeCompatibility.minecraftVersion("26.3.build.190-stable"));
        assertEquals("26.3.1", RuntimeCompatibility.minecraftVersion("26.3.1"));
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.minecraftVersion("26.1.2-R0.1"));
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.minecraftVersion(null));
    }

    @Test
    void paperReleasesUseTheirFamilyAdapter() {
        for (String version : List.of("26.1-R0.1-SNAPSHOT", "26.1.2.build.74-stable", "26.2.build.134-stable")) {
            assertEquals(CompanionAdapter.V26_1, RuntimeCompatibility.select(version, 25, true, false).companionAdapter(), version);
            assertEquals(CompanionAdapter.V26_1, RuntimeCompatibility.select(version, 25, true, true).companionAdapter(), version);
        }
        for (String version : List.of("26.3-R0.1-SNAPSHOT", "26.3.build.190-stable", "26.3.1")) {
            assertEquals(CompanionAdapter.V26_3, RuntimeCompatibility.select(version, 25, true, false).companionAdapter(), version);
            assertEquals(CompanionAdapter.V26_3, RuntimeCompatibility.select(version, 25, true, true).companionAdapter(), version);
        }
    }

    @Test
    void spigotReleasesUseTheirFamilyAdapter() {
        for (String version : List.of("26.1-R0.1-SNAPSHOT", "26.1.2-R0.1-SNAPSHOT", "26.2-R0.1-SNAPSHOT")) {
            assertEquals(CompanionAdapter.SPIGOT_26_1, RuntimeCompatibility.select(version, 25, false, false).companionAdapter(), version);
        }
        assertEquals(CompanionAdapter.SPIGOT_26_3, RuntimeCompatibility.select("26.3-R0.1-SNAPSHOT", 25, false, false).companionAdapter());
    }

    @Test
    void selectionCarriesTheMinecraftRelease() {
        RuntimeCompatibility.Selection selection = RuntimeCompatibility.select("26.2.build.134-stable", 25, true, false);
        assertEquals("26.2", selection.minecraftVersion());
    }

    @Test
    void regionizedRuntimesRequireThePaperApi() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RuntimeCompatibility.select("26.2-R0.1-SNAPSHOT", 25, false, true));
        assertTrue(failure.getMessage().contains("Paper API"));
    }

    @Test
    void selectionRejectsUnsupportedRuntimesBeforeChoosingAnAdapter() {
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.select("1.21.11-R0.2-SNAPSHOT", 25, true, false));
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.select("26.2-R0.1-SNAPSHOT", 21, true, false));
        assertThrows(IllegalStateException.class, () -> RuntimeCompatibility.select(null, 25, true, false));
    }
}
