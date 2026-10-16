package com.volmit.bytecompanions.platform.paper.progression;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressionDefinitionsTest {
    @Test
    void rejectsWrongShapesScalarCoercionAndUnknownSettings() {
        for (String toml : new String[]{"maximum-booster-percent = \"20\"\n", "source-cooldown-millis = 2.5\n", "pause-personal-offline = \"yesplease\"\n",
                "companions = []\n", "level-thresholds = [0, '100']", "[xp-sources]\nplaytime = 3\n", "unknown = true\n", "allowed-worlds = \"world\"\n"}) {
            assertThrows(Exception.class, () -> parse(toml), toml);
        }
    }
    @Test
    void rejectsEmptyTiersRewardsAndNonIncreasingLevels() {
        for (String toml : new String[]{"level-thresholds = [0, 0]\n", "[companions.bee.tracks.speed]\ntiers = []\n",
                "[[companions.bee.tracks.speed.tiers]]\n\n[companions.bee.tracks.speed.tiers.additions]\n", "[boosters.xp]\nduration-seconds = 1\n\n[boosters.xp.percentages]\nunknown = 10\n"}) {
            assertThrows(Exception.class, () -> parse(toml), toml);
        }
    }
    @Test
    void minimalDefinitionsUseSafeDefaultsAndNestedRewardsAreImmutable() throws Exception {
        ProgressionDefinitions definitions = parse("[companions.bee.level-rewards.2]\n\"effect:minecraft:speed\" = 1\n");
        assertEquals("local", definitions.serverId());
        assertEquals(1000, definitions.sourceCooldownMillis());
        assertEquals(Map.of("block_break", 1L, "mob_kill", 5L, "fishing", 3L), definitions.xpSources());
        assertThrows(UnsupportedOperationException.class, () -> definitions.companions().get("bee").levelRewards().get(2).put("effect:minecraft:speed", 2D));
    }
    private static ProgressionDefinitions parse(String toml) throws Exception { return ProgressionDefinitions.parse(toml.getBytes(StandardCharsets.UTF_8)); }
}
