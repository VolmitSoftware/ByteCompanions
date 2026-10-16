package com.volmit.bytecompanions.features.effects;

import com.volmit.bytecompanions.features.effects.data.EffectResolution;
import com.volmit.bytecompanions.features.effects.data.PotionEffectData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectResolutionTest {
    @Test
    void highestUsesPriorityRatherThanAmplifierAndSumEntriesStillContribute() {
        PotionEffectData strong = entry(9, 1, StackMode.HIGHEST);
        PotionEffectData preferred = entry(2, 10, StackMode.HIGHEST);
        PotionEffectData additive = entry(3, 0, StackMode.SUM);
        EffectResolution resolution = EffectResolution.resolve(null, List.of(strong, preferred, additive));
        assertEquals(5, resolution.effect().amplifier());
        assertFalse(resolution.includes(strong));
        assertTrue(resolution.includes(preferred));
        assertTrue(resolution.includes(additive));
    }

    @Test
    void equalPriorityRetainsFirstEntryAndDoesNotMistakeEqualRecordsForContributors() {
        PotionEffectData first = entry(2, 5, StackMode.HIGHEST);
        PotionEffectData equal = entry(2, 5, StackMode.HIGHEST);
        EffectResolution resolution = EffectResolution.resolve(null, List.of(first, equal));
        assertTrue(resolution.includes(first));
        assertFalse(resolution.includes(equal));
        assertEquals(1, resolution.contributors().size());
    }

    @Test
    void sumCapsAt255AndMetadataStillComesFromHighestPriorityEntry() {
        PotionEffectData base = entry(200, 1, StackMode.HIGHEST);
        PotionEffectData metadata = new PotionEffectData(null, 100, 10, false, false, false, StackMode.SUM);
        EffectResolution resolution = EffectResolution.resolve(null, List.of(base, metadata));
        assertEquals(255, resolution.effect().amplifier());
        assertFalse(resolution.effect().ambient());
        assertFalse(resolution.effect().particles());
        assertFalse(resolution.effect().icon());
    }

    @Test
    void sumUsesRawAmplifiersIncludingZeroLevelContributions() {
        EffectResolution resolution = EffectResolution.resolve(null,
                List.of(entry(0, 0, StackMode.SUM), entry(0, 0, StackMode.SUM)));
        assertEquals(0, resolution.effect().amplifier());
        assertEquals(2, resolution.contributors().size());
    }

    private static PotionEffectData entry(int amplifier, int priority, StackMode mode) {
        return new PotionEffectData(null, amplifier, priority, true, true, true, mode);
    }
}
