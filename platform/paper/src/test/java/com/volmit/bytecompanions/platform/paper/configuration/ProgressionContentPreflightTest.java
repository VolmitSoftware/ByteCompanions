package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressionContentPreflightTest {
    @Test
    void disabledProgressionDoesNotRequireBundledTargetsOnCustomServers() throws Exception {
        ContentSnapshot content = content("[companions]\n", 1);
        ProgressionDefinitions definitions = definitions("attribute:minecraft:armor", 1);
        assertDoesNotThrow(() -> ProgressionContentPreflight.validate(definitions, content, false, false));
        assertDoesNotThrow(() -> ProgressionContentPreflight.validate(definitions, content, false, true));
        assertThrows(IllegalArgumentException.class, () -> ProgressionContentPreflight.validate(definitions, content, true, false));
    }

    @Test
    void upgradesMustReferenceEnabledConfiguredBonuses() throws Exception {
        ContentSnapshot content = content("[companions.bee.vanilla.experience-block]\nenabled = false\nvalue = 2\n", 1);
        for (String target : new String[]{"buff:experience_block", "buff:experience", "attribute:minecraft:armor", "effect:minecraft:strength"}) {
            ProgressionDefinitions definitions = definitions(target, 1);
            assertThrows(IllegalArgumentException.class, () -> ProgressionContentPreflight.validate(definitions, content, true, false));
        }
    }

    @Test
    void knownPotionAndEnabledBuffTargetsPassWithoutChangingTheirValues() throws Exception {
        ContentSnapshot content = content("[companions.bee.vanilla.experience-block]\nenabled = true\nvalue = 2\n", 1);
        for (String target : new String[]{"buff:experience_block", "effect:minecraft:speed"}) {
            ProgressionDefinitions definitions = definitions(target, 1);
            assertDoesNotThrow(() -> ProgressionContentPreflight.validate(definitions, content, true, false));
        }
    }

    @Test
    void cumulativePotionRewardsCannotSellLevelsBeyondTheAmplifierCap() throws Exception {
        ContentSnapshot content = content("[companions]\n", 254);
        ProgressionDefinitions definitions = definitions("effect:minecraft:speed", 2);
        assertThrows(IllegalArgumentException.class, () -> ProgressionContentPreflight.validate(definitions, content, true, false));
    }

    @Test
    void duplicateBaseEntriesCannotMakeOneUpgradeTargetAmbiguous() throws Exception {
        ContentSnapshot content = new ContentSnapshot(Map.of("features/attributes/config.toml", "[companions]\n",
                "features/effects/config.toml", "[[companions.bee]]\neffect = \"SPEED\"\namplifier = 1\n\n[[companions.bee]]\neffect = \"SPEED\"\namplifier = 2\n",
                "features/buffs/config.toml", "[companions]\n"));
        ProgressionDefinitions definitions = definitions("effect:minecraft:speed", 1);
        assertThrows(IllegalArgumentException.class, () -> ProgressionContentPreflight.validate(definitions, content, true, false));
    }

    private static ProgressionDefinitions definitions(String target, int value) throws Exception {
        return ProgressionDefinitions.parse(("[companions.bee.level-rewards.2]\n\"" + target + "\" = " + value).getBytes(StandardCharsets.UTF_8));
    }

    private static ContentSnapshot content(String buffs, int amplifier) throws Exception {
        return new ContentSnapshot(Map.of("features/attributes/config.toml", "[companions]\n",
                "features/effects/config.toml", "[[companions.bee]]\neffect = \"SPEED\"\namplifier = " + amplifier,
                "features/buffs/config.toml", buffs));
    }
}
