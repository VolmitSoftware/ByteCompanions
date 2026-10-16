package com.volmit.bytecompanions.platform.paper.configuration;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        files.put("features/particles/config.toml", "value = 6\n");
        ContentSnapshot snapshot = new ContentSnapshot(files);
        files.put("features/particles/config.toml", "value = 0\n");
        snapshot.node("features/particles/config.toml", ConfigurationOptions.defaults()).node("value").set(3);
        assertEquals(6, snapshot.node("features/particles/config.toml", ConfigurationOptions.defaults()).node("value").getInt());
    }

    @Test
    void malformedCompanionPreventsAnEntireCandidateFromBeingCaptured() {
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of(
                "features/particles/config.toml", "value = 6\n", "companions/bee.toml", "model = [")));
    }

    @Test
    void invalidRootAndNonFiniteNumbersAreRejected() {
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("features/particles/config.toml", "[unterminated")));
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("companions/bee.toml", "[appearance]\nscale = nan")));
        assertThrows(IOException.class, () -> new ContentSnapshot(Map.of("features/attributes/config.toml", "[[companions.bee]]\namount = inf")));
    }

    @Test
    void commentOnlyFeatureFilesRemainValid() throws Exception {
        ContentSnapshot snapshot = new ContentSnapshot(Map.of("features/attributes/config.toml", "# No attributes configured\n"));
        assertEquals(0, snapshot.bukkit("features/attributes/config.toml").getKeys(false).size());
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
    }}
