package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionDraftTest {
    @Test
    void editingPreservesLiteralKeysNestedTableArraysAndExactLargeIntegers() throws Exception {
        Map<String, String> source = sources();
        source.put("companions/scout.toml", """
                model = "FULL_BODY"
                [appearance]
                scale = 0.8
                [extensions]
                "owner.key" = 9007199254740993
                "effect:minecraft:speed" = 2
                [[animations.movement]]
                id = "follow_formation"
                [[animations.movement.points]]
                "offset.x" = 0.25
                [[animations.movement.points]]
                "offset.x" = 1.25
                """);
        ContentSnapshot accepted = new ContentSnapshot(source);
        CompanionDraft edited = CompanionDraft.open(accepted, "scout", "config")
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5);
        ContentSnapshot saved = edited.prepare(accepted).snapshot();
        var node = saved.node("companions/scout.toml", org.spongepowered.configurate.ConfigurationOptions.defaults());
        assertEquals(1.5, node.node("appearance", "scale").getDouble());
        assertEquals(9007199254740993L, node.node("extensions", "owner.key").raw());
        assertEquals(2L, node.node("extensions", "effect:minecraft:speed").raw());
        assertEquals(2, node.node("animations", "movement", 0, "points").childrenList().size());
        assertEquals(0.25, node.node("animations", "movement", 0, "points", 0, "offset.x").getDouble());
        assertEquals(1.25, node.node("animations", "movement", 0, "points", 1, "offset.x").getDouble());
        assertEquals(accepted.source("companions/scout.toml"), source.get("companions/scout.toml"));
    }

    @Test
    void draftAndReturnedNodesCannotMutateAcceptedContent() throws Exception {
        ContentSnapshot accepted = content();
        CompanionDraft original = CompanionDraft.open(accepted, "scout", "config");
        original.node(CompanionDraft.Section.COMPANION).node("model").raw("HEAD_ONLY");
        CompanionDraft edited = original.withValue(CompanionDraft.Section.COMPANION,
                List.of("appearance", "scale"), 1.25);
        assertFalse(original.changed());
        assertTrue(edited.changed());
        assertEquals(0.8, original.node(CompanionDraft.Section.COMPANION).node("appearance", "scale").getDouble());
        assertEquals(1.25, edited.node(CompanionDraft.Section.COMPANION).node("appearance", "scale").getDouble());
        assertEquals("FULL_BODY", original.node(CompanionDraft.Section.COMPANION).node("model").getString());
        assertEquals(accepted.source("companions/scout.toml"), edited.reset().snapshot().source("companions/scout.toml"));
    }

    @Test
    void sharedFeatureChangesRebaseWithoutReplacingOtherCompanions() throws Exception {
        CompanionDraft draft = CompanionDraft.open(content(), "scout", "config")
                .withValue(CompanionDraft.Section.BUFFS, List.of("default-priority"), 25);
        Map<String, String> latest = sources();
        latest.put("features/buffs/config.toml", "[companions.scout]\ndefault-priority = 10\n\n[companions.bee]\ndefault-priority = 99\n");
        CompanionDraft.PreparedEdit edit = draft.prepare(new ContentSnapshot(latest));
        assertEquals(1, edit.writes().size());
        assertEquals(99, edit.snapshot().bukkit("features/buffs/config.toml").getInt("companions.bee.default-priority"));
        assertEquals(25, edit.snapshot().bukkit("features/buffs/config.toml").getInt("companions.scout.default-priority"));
    }

    @Test
    void concurrentChangesToTheSameCompanionAreRejected() throws Exception {
        CompanionDraft draft = CompanionDraft.open(content(), "scout", "config")
                .withValue(CompanionDraft.Section.BUFFS, List.of("default-priority"), 25);
        Map<String, String> latest = sources();
        latest.put("features/buffs/config.toml", "[companions.scout]\ndefault-priority = 30\n");
        ContentSnapshot changedBuffs = new ContentSnapshot(latest);
        assertThrows(IOException.class, () -> draft.prepare(changedBuffs));
        CompanionDraft visual = CompanionDraft.open(content(), "scout", "config")
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.25);
        latest = sources();
        latest.put("companions/scout.toml", "model = \"HEAD_ONLY\"\n");
        ContentSnapshot changed = new ContentSnapshot(latest);
        assertThrows(IOException.class, () -> visual.prepare(changed));
    }

    @Test
    void rejectsUnsafeIdentifiersAndNonFiniteDraftNumbers() throws Exception {
        ContentSnapshot content = content();
        for (String id : List.of("../scout", "scout/other", "scout\\other", "C:scout", "..", "")) {
            assertThrows(IllegalArgumentException.class, () -> CompanionDraft.open(content, id, "config"));
        }
        CompanionDraft draft = CompanionDraft.open(content, "scout", "config");
        assertThrows(IOException.class, () -> draft.withValue(CompanionDraft.Section.COMPANION,
                List.of("appearance", "scale"), Double.NaN));
    }

    @Test
    void serializationOnlyChangesDoNotProduceDirtyDraftsOrWrites() throws Exception {
        ContentSnapshot content = content();
        CompanionDraft draft = CompanionDraft.open(content, "scout", "config")
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 0.8);
        assertFalse(draft.changed());
        assertTrue(draft.changes().isEmpty());
        assertTrue(draft.prepare(content).writes().isEmpty());
        CompanionDraft reverted = draft.withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5)
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 0.8);
        assertFalse(reverted.changed());
    }

    @Test
    void reviewDescribesOnlyOwnedChangedPathsAndValues() throws Exception {
        CompanionDraft draft = CompanionDraft.open(content(), "scout", "config")
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5)
                .withValue(CompanionDraft.Section.BUFFS, List.of("default-priority"), 25);
        assertEquals(List.of("Companion › Appearance › Scale: 0.8 → 1.5", "Buffs › Default priority: 10 → 25"), draft.changes());
        assertThrows(UnsupportedOperationException.class, () -> draft.changes().clear());
        assertTrue(draft.reset().changes().isEmpty());
    }

    @Test
    void duplicationCopiesEditedContentWithoutChangingSourceOrProgression() throws Exception {
        Map<String, String> sources = sources();
        sources.put("features/progression/config.toml", "[companions.scout.level-rewards.2]\n\"buff:experience_block\" = 1\n");
        ContentSnapshot original = new ContentSnapshot(sources);
        CompanionDraft duplicate = CompanionDraft.open(original, "scout", "config")
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5)
                .withValue(CompanionDraft.Section.BUFFS, List.of("default-priority"), 45)
                .duplicate("scout_copy");
        assertTrue(duplicate.creation());
        assertTrue(duplicate.changed());
        assertFalse(duplicate.changes().isEmpty());
        assertEquals(1.5, duplicate.node(CompanionDraft.Section.COMPANION).node("appearance", "scale").getDouble());
        assertEquals(original.source("companions/scout.toml"), duplicate.snapshot().source("companions/scout.toml"));
        assertEquals(10, duplicate.snapshot().bukkit("features/buffs/config.toml").getInt("companions.scout.default-priority"));
        assertEquals(45, duplicate.snapshot().bukkit("features/buffs/config.toml").getInt("companions.scout_copy.default-priority"));
        assertEquals(original.source("features/progression/config.toml"), duplicate.snapshot().source("features/progression/config.toml"));
        assertFalse(duplicate.prepare(original).writes().containsKey("companions/scout.toml"));
        CompanionDraft reset = duplicate.withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 2.0).reset();
        assertTrue(reset.creation());
        assertTrue(reset.changed());
        assertEquals(1.5, reset.node(CompanionDraft.Section.COMPANION).node("appearance", "scale").getDouble());
    }

    @Test
    void duplicateRejectsUnsafeExistingAndOrphanIdentifiers() throws Exception {
        CompanionDraft draft = CompanionDraft.open(content(), "scout", "config");
        for (String id : List.of("../new", "bad/name", "Uppercase", "", "x".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> draft.duplicate(id));
        }
        assertThrows(IOException.class, () -> draft.duplicate("scout"));
        assertThrows(IOException.class, () -> draft.duplicate("bee"));
        Map<String, String> sources = sources();
        sources.put("features/progression/config.toml", "[companions.newid.level-rewards]\n");
        CompanionDraft progression = CompanionDraft.open(new ContentSnapshot(sources), "scout", "config");
        assertThrows(IOException.class, () -> progression.duplicate("newid"));
        CompanionDraft duplicate = draft.duplicate("newid");
        sources = sources();
        sources.put("companions/newid.toml", "model = \"HEAD\"\n");
        ContentSnapshot concurrent = new ContentSnapshot(sources);
        assertThrows(IOException.class, () -> duplicate.prepare(concurrent));
    }

    static ContentSnapshot content() throws IOException {
        return new ContentSnapshot(sources());
    }

    static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("companions/scout.toml", "model = \"FULL_BODY\"\n\n[appearance]\nscale = 0.8\n");
        sources.put("features/attributes/config.toml", "[companions]\n");
        sources.put("features/buffs/config.toml", "[companions.scout]\ndefault-priority = 10\n\n[companions.bee]\ndefault-priority = 20\n");
        sources.put("features/effects/config.toml", "[companions]\n");
        return sources;
    }
}
