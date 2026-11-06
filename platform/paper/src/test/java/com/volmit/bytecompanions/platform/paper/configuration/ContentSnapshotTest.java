package com.volmit.bytecompanions.platform.paper.configuration;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentSnapshotTest {
    @Test
    void literalKeysAndLargeIntegersRemainExactInParsedContent() throws Exception {
        ContentSnapshot snapshot = new ContentSnapshot(Map.of("companions/scout.toml", """
                model = "HEAD"
                [extensions]
                "owner.key" = 9007199254740993
                "effect:minecraft:speed" = 2
                [[animations.movement]]
                id = "follow_formation"
                [[animations.movement.points]]
                "offset.x" = 0.25
                [[animations.movement.points]]
                "offset.x" = 1.25
                """));
        var root = snapshot.node("companions/scout.toml", ConfigurationOptions.defaults());
        assertEquals(9007199254740993L, root.node("extensions", "owner.key").raw());
        assertEquals(2L, root.node("extensions", "effect:minecraft:speed").raw());
        assertEquals(2, root.node("animations", "movement", 0, "points").childrenList().size());
        assertEquals(1.25, root.node("animations", "movement", 0, "points", 1, "offset.x").getDouble());
    }

    @Test
    void capturedSourcesAndParsedNodesCannotChangeAcceptedContent() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("features/particles.toml", "value = 6\n");
        ContentSnapshot snapshot = new ContentSnapshot(files);
        files.put("features/particles.toml", "value = 0\n");
        snapshot.node("features/particles.toml", ConfigurationOptions.defaults()).node("value").set(3);
        assertEquals(6, snapshot.node("features/particles.toml", ConfigurationOptions.defaults()).node("value").getInt());
    }

    @Test
    void malformedCompanionPreventsAnEntireCandidateFromBeingCaptured() {
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of(
                "features/particles.toml", "value = 6\n", "companions/bee.toml", "model = [")));
    }

    @Test
    void invalidRootAndNonFiniteNumbersAreRejected() {
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("features/particles.toml", "[unterminated")));
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("companions/bee.toml", "[appearance]\nscale = nan")));
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("features/attributes.toml", "[[companions.bee]]\namount = inf")));
    }

    @Test
    void commentOnlyFeatureFilesRemainValid() throws Exception {
        ContentSnapshot snapshot = new ContentSnapshot(Map.of("features/attributes.toml", "# No attributes configured\n"));
        assertEquals(0, snapshot.bukkit("features/attributes.toml").getKeys(false).size());
    }

    @Test
    void malformedDecorationSectionsCannotSilentlyRemoveWorkingDecorations() throws Exception {
        for (String source : new String[]{"decorations = \"invalid\"\n", "decorations = [\"invalid\"]\n",
                "[decorations]\nbee = \"invalid\"\n", "[decorations.bee]\nenabled = \"maybe\"\n",
                "[decorations.bee]\ncompanion = \"bee\"\nx = \"invalid\"\n",
                "[decorations.bee]\ncompanion = \"bee\"\nheight = -1\n"}) {
            ContentSnapshot snapshot = new ContentSnapshot(Map.of("decorations.toml", source));
            assertThrows(IllegalArgumentException.class, () ->
                    ContentPreflight.validateDecorations(snapshot.node("decorations.toml", ConfigurationOptions.defaults())));
        }
    }
    @Test
    void earlierFeatureFoldersMoveToOneFileEachAndKeepEdits() throws Exception {
        Path root = Files.createTempDirectory("bytecompanions-layout");
        try {
            Files.createDirectories(root.resolve("features/buffs"));
            Files.createDirectories(root.resolve("features/particles"));
            Files.writeString(root.resolve("features/buffs/config.toml"), "# edited\n[companions]\n");
            Files.writeString(root.resolve("features/particles/config.toml"), "[settings]\nserializer = \"LEGACY_AMPERSAND\"\n");
            Files.writeString(root.resolve("features/particles/icons.toml"), "[items.orb_effect]\nmaterial = \"STONE\"\n");
            Files.createDirectories(root.resolve("features/effects"));
            Files.writeString(root.resolve("features/effects.toml"), "[companions]\n");
            Files.writeString(root.resolve("features/effects/config.toml"), "# older copy\n");

            ContentSnapshot.migrateFeatureFolders(root);

            assertEquals("# edited\n[companions]\n", Files.readString(root.resolve("features/buffs.toml")));
            ContentSnapshot particles = new ContentSnapshot(Map.of("features/particles.toml", Files.readString(root.resolve("features/particles.toml"))));
            assertEquals("LEGACY_AMPERSAND", particles.node("features/particles.toml", ConfigurationOptions.defaults())
                    .node("settings", "serializer").getString());
            assertEquals("STONE", particles.node("features/particles.toml", ConfigurationOptions.defaults())
                    .node("items", "orb_effect", "material").getString());
            assertFalse(Files.exists(root.resolve("features/buffs")));
            assertFalse(Files.exists(root.resolve("features/particles")));
            assertEquals("[companions]\n", Files.readString(root.resolve("features/effects.toml")));
            assertTrue(Files.exists(root.resolve("features/effects/config.toml")));
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
