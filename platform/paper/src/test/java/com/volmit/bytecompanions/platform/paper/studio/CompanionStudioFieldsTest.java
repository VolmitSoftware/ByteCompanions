package com.volmit.bytecompanions.platform.paper.studio;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.platform.paper.configuration.ProgressionContentPreflight;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionStudioFieldsTest {
    @Test
    void missingAppearanceAndEquipmentFieldsRemainEditableWithoutWritingDefaults() throws Exception {
        CompanionDraft draft = draft();
        List<CompanionStudioFields.Field> appearance = CompanionStudioFields.fields(draft, CompanionStudioFields.Category.APPEARANCE);
        List<CompanionStudioFields.Field> equipment = CompanionStudioFields.fields(draft, CompanionStudioFields.Category.EQUIPMENT);
        assertTrue(appearance.stream().anyMatch(field -> field.path().equals(List.of("appearance", "custom-name"))));
        assertEquals(12, equipment.size());
        assertFalse(draft.changed());
        assertTrue(draft.node(CompanionDraft.Section.COMPANION).node("equipment").virtual());
    }

    @Test
    void animationTogglePreservesUnrelatedEntriesAndTheirOrder() throws Exception {
        CompanionDraft draft = draft().withValue(CompanionDraft.Section.COMPANION, List.of("animations", "movement"),
                List.of(Map.of("id", "custom-animation", "speed", 0.9), Map.of("id", "follow_owner", "desired-distance", 2.0)));
        CompanionDraft added = CompanionStudioFields.preset(draft, CompanionStudioFields.Category.MOVEMENT, "hover");
        assertEquals(3, added.node(CompanionDraft.Section.COMPANION).node("animations", "movement").childrenList().size());
        CompanionDraft removed = CompanionStudioFields.preset(added, CompanionStudioFields.Category.MOVEMENT, "hover");
        assertEquals(draft.node(CompanionDraft.Section.COMPANION).node("animations", "movement").raw(),
                removed.node(CompanionDraft.Section.COMPANION).node("animations", "movement").raw());
        assertTrue(CompanionStudioFields.fields(added, CompanionStudioFields.Category.MOVEMENT).stream()
                .anyMatch(field -> field.path().equals(List.of("animations", "movement", 0, "speed"))));
        assertFalse(CompanionStudioFields.fields(added, CompanionStudioFields.Category.MOVEMENT).stream()
                .anyMatch(field -> field.path().getLast().equals("id")));
    }

    @Test
    void particleToggleDoesNotReplaceMovementAnimations() throws Exception {
        CompanionDraft draft = CompanionStudioFields.preset(draft(), CompanionStudioFields.Category.MOVEMENT, "hover");
        CompanionDraft added = CompanionStudioFields.preset(draft, CompanionStudioFields.Category.PARTICLES, "aura_effect");
        assertEquals("aura_effect", added.node(CompanionDraft.Section.COMPANION).node("animations", "post-tracking", 0, "id").getString());
        assertEquals(draft.node(CompanionDraft.Section.COMPANION).node("animations", "movement").raw(),
                added.node(CompanionDraft.Section.COMPANION).node("animations", "movement").raw());
        CompanionDraft removed = CompanionStudioFields.preset(added, CompanionStudioFields.Category.PARTICLES, "aura_effect");
        assertTrue(removed.node(CompanionDraft.Section.COMPANION).node("animations", "post-tracking").childrenList().isEmpty());
    }

    @Test
    void firstBuffPresetDoesNotEnableUnrelatedBuffs() throws Exception {
        CompanionDraft draft = draft().withValue(CompanionDraft.Section.BUFFS, List.of(), null);
        CompanionDraft edited = CompanionStudioFields.preset(draft, CompanionStudioFields.Category.BUFFS, "experience-block");
        assertTrue(edited.node(CompanionDraft.Section.BUFFS).node("vanilla", "experience-block", "enabled").getBoolean());
        assertFalse(edited.node(CompanionDraft.Section.BUFFS).node("vanilla", "experience", "enabled").getBoolean(true));
        assertFalse(edited.node(CompanionDraft.Section.BUFFS).node("vanilla", "mob-loot", "enabled").getBoolean(true));
    }

    @Test
    void potionAndAttributeTogglesUseCurrentSchemasAndPreserveOtherRows() throws Exception {
        CompanionDraft potion = CompanionStudioFields.preset(draft(), CompanionStudioFields.Category.EFFECTS, "SPEED");
        potion = CompanionStudioFields.preset(potion, CompanionStudioFields.Category.EFFECTS, "NIGHT_VISION");
        potion = CompanionStudioFields.preset(potion, CompanionStudioFields.Category.EFFECTS, "SPEED");
        assertEquals(1, potion.node(CompanionDraft.Section.EFFECTS).childrenList().size());
        assertEquals("NIGHT_VISION", potion.node(CompanionDraft.Section.EFFECTS).node(0, "effect").getString());
        CompanionDraft attribute = CompanionStudioFields.preset(draft(), CompanionStudioFields.Category.ATTRIBUTES, "armor");
        assertEquals("ADD_NUMBER", attribute.node(CompanionDraft.Section.ATTRIBUTES).node(0, "operation").getString());
        assertTrue(CompanionStudioFields.removeEntry(attribute, CompanionStudioFields.Category.ATTRIBUTES, 0)
                .node(CompanionDraft.Section.ATTRIBUTES).childrenList().isEmpty());
    }

    @Test
    void fieldInputEnforcesFiniteNumbersIntegerBoundsChoicesAndRgb() {
        CompanionStudioFields.Field scale = field(List.of("appearance", "scale"), 1.0);
        assertThrows(IllegalArgumentException.class, () -> scale.parse("NaN"));
        assertThrows(IllegalArgumentException.class, () -> scale.parse("0"));
        assertEquals(-1.0, scale.parse("-1"));
        CompanionStudioFields.Field integer = field(List.of("amplifier"), 0);
        assertEquals(2, integer.parse("2"));
        assertThrows(IllegalArgumentException.class, () -> integer.parse("256"));
        assertThrows(IllegalArgumentException.class, () -> integer.parse("0.5"));
        CompanionStudioFields.Field color = field(List.of("equipment", "HEAD", "rgb-color"), "0,0,0");
        assertEquals("255,20,0", color.parse("255, 20, 0"));
        assertThrows(IllegalArgumentException.class, () -> color.parse("256,0,0"));
        assertThrows(IllegalArgumentException.class, () -> field(List.of("visible"), true).parse("yes"));
        CompanionStudioFields.Field choice = new CompanionStudioFields.Field(CompanionDraft.Section.COMPANION,
                List.of("model"), "FULL_BODY", "Model", List.of("FULL_BODY", "HEAD"));
        assertEquals("HEAD", choice.parse("head"));
        assertThrows(IllegalArgumentException.class, () -> choice.parse("HEAD_ONLY"));
    }

    @Test
    void presetSelectionMatchesToggleStateWithoutChangingTheDraft() throws Exception {
        for (CompanionStudioFields.Category category : List.of(CompanionStudioFields.Category.MOVEMENT,
                CompanionStudioFields.Category.PARTICLES, CompanionStudioFields.Category.ATTRIBUTES,
                CompanionStudioFields.Category.EFFECTS)) {
            String choice = CompanionStudioFields.choices(category).getFirst();
            CompanionDraft original = draft();
            assertFalse(CompanionStudioFields.selected(original, category, choice));
            assertFalse(original.changed());
            CompanionDraft enabled = CompanionStudioFields.preset(original, category, choice);
            assertTrue(CompanionStudioFields.selected(enabled, category, choice));
            CompanionDraft disabled = CompanionStudioFields.preset(enabled, category, choice);
            assertFalse(CompanionStudioFields.selected(disabled, category, choice));
        }
        CompanionDraft draft = draft().withValue(CompanionDraft.Section.BUFFS, List.of(), null);
        assertFalse(CompanionStudioFields.selected(draft, CompanionStudioFields.Category.BUFFS, "experience-block"));
        CompanionDraft enabled = CompanionStudioFields.preset(draft, CompanionStudioFields.Category.BUFFS, "experience-block");
        assertTrue(CompanionStudioFields.selected(enabled, CompanionStudioFields.Category.BUFFS, "experience-block"));
        assertFalse(CompanionStudioFields.selected(enabled, CompanionStudioFields.Category.BUFFS, "not-a-buff"));
    }

    @Test
    void removingBonusesReferencedByProgressionIsRejectedBeforeSaving() throws Exception {
        for (Map.Entry<CompanionStudioFields.Category, String> preset : Map.of(
                CompanionStudioFields.Category.ATTRIBUTES, "armor", CompanionStudioFields.Category.EFFECTS, "SPEED").entrySet()) {
            CompanionDraft enabled = CompanionStudioFields.preset(draft(), preset.getKey(), preset.getValue());
            String target = preset.getKey() == CompanionStudioFields.Category.ATTRIBUTES
                    ? "attribute:minecraft:armor" : "effect:minecraft:speed";
            ProgressionDefinitions definitions = ProgressionDefinitions.parse(("[companions.scout.level-rewards.2]\n\""
                    + target + "\" = 1\n").getBytes(StandardCharsets.UTF_8));
            assertDoesNotThrow(() -> ProgressionContentPreflight.validate(definitions, enabled.snapshot(), true, false));
            CompanionDraft removed = CompanionStudioFields.preset(enabled, preset.getKey(), preset.getValue());
            assertThrows(IllegalArgumentException.class,
                    () -> ProgressionContentPreflight.validate(definitions, removed.snapshot(), true, false));
        }
    }

    @Test
    void decimalFieldsAcceptFractionsWhenTomlStartedWithIntegers() throws Exception {
        CompanionDraft draft = draft()
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1)
                .withValue(CompanionDraft.Section.COMPANION, List.of("animations", "movement"), List.of(Map.of("id", "hover", "height", 0)))
                .withValue(CompanionDraft.Section.ATTRIBUTES, List.of(), List.of(Map.of("attribute", "armor", "amount", 2, "operation", "ADD_NUMBER")))
                .withValue(CompanionDraft.Section.BUFFS, List.of("vanilla", "experience-block", "value"), 10);
        Map<CompanionStudioFields.Category, List<Object>> fields = Map.of(
                CompanionStudioFields.Category.APPEARANCE, List.of("appearance", "scale"),
                CompanionStudioFields.Category.MOVEMENT, List.of("animations", "movement", 0, "height"),
                CompanionStudioFields.Category.ATTRIBUTES, List.of(0, "amount"),
                CompanionStudioFields.Category.BUFFS, List.of("vanilla", "experience-block", "value"));
        for (Map.Entry<CompanionStudioFields.Category, List<Object>> entry : fields.entrySet()) {
            CompanionStudioFields.Field field = CompanionStudioFields.fields(draft, entry.getKey()).stream()
                    .filter(candidate -> candidate.path().equals(entry.getValue())).findFirst().orElseThrow();
            assertTrue(field.value() instanceof Double);
            assertEquals(1.5, field.parse("1.5"));
        }
    }

    @Test
    void integerFieldsStayIntegralAndFormationChoicesExposeTheirRealDefaults() throws Exception {
        CompanionDraft draft = CompanionStudioFields.preset(draft(), CompanionStudioFields.Category.MOVEMENT, "follow_formation");
        List<CompanionStudioFields.Field> fields = CompanionStudioFields.fields(draft, CompanionStudioFields.Category.MOVEMENT);
        CompanionStudioFields.Field type = fields.stream().filter(field -> field.path().getLast().equals("type")).findFirst().orElseThrow();
        assertEquals(List.of("grid", "line", "orbit"), type.choices());
        CompanionStudioFields.Field columns = fields.stream().filter(field -> field.path().getLast().equals("columns")).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> columns.parse("2.5"));
        assertThrows(IllegalArgumentException.class, () -> columns.parse("0"));
        assertEquals(4, columns.parse("4"));
        CompanionDraft orbit = CompanionStudioFields.apply(draft, type, "orbit");
        assertTrue(CompanionStudioFields.fields(orbit, CompanionStudioFields.Category.MOVEMENT).stream()
                .anyMatch(field -> field.path().getLast().equals("radius") && field.value().equals(1.0)));
        CompanionStudioFields.Field amplifier = new CompanionStudioFields.Field(CompanionDraft.Section.EFFECTS,
                List.of(0, "amplifier"), 1.0, "Amplifier", List.of());
        assertTrue(amplifier.value() instanceof Integer);
        assertThrows(IllegalArgumentException.class, () -> amplifier.parse("1.5"));
    }

    private static CompanionStudioFields.Field field(List<Object> path, Object value) {
        return new CompanionStudioFields.Field(CompanionDraft.Section.COMPANION, path, value, "Field", List.of());
    }

    private static CompanionDraft draft() throws Exception {
        return CompanionDraft.open(CompanionDraftTest.content(), "scout", "config");
    }
}
