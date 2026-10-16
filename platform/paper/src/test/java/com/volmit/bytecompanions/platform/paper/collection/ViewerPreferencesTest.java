package com.volmit.bytecompanions.platform.paper.collection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewerPreferencesTest {
    @Test
    void ownVisibilityExcludesOtherOwnersAndDecorations() {
        UUID viewer = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(ViewerPreferences.Visibility.OWN.allows(viewer, viewer));
        assertFalse(ViewerPreferences.Visibility.OWN.allows(viewer, other));
        assertFalse(ViewerPreferences.Visibility.OWN.allows(viewer, null));
        assertFalse(ViewerPreferences.Visibility.NONE.allows(viewer, viewer));
        assertTrue(ViewerPreferences.Visibility.ALL.allows(viewer, other));
        assertTrue(ViewerPreferences.Visibility.ALL.allows(viewer, null));
    }

    @Test
    void serverLimitCanOnlyReducePersonalParticleIntensity() {
        for (ViewerPreferences.ParticleIntensity personal : ViewerPreferences.ParticleIntensity.values()) {
            for (ViewerPreferences.ParticleIntensity maximum : ViewerPreferences.ParticleIntensity.values()) {
                var effective = personal.limitedBy(maximum);
                assertTrue(effective.ordinal() >= personal.ordinal());
                assertTrue(effective.ordinal() >= maximum.ordinal());
            }
        }
    }

    @Test
    void reductionOmitsAlternateCompleteFrames() {
        for (long frame = 0; frame < 16; frame++) {
            assertTrue(ViewerPreferences.ParticleIntensity.FULL.acceptsFrame(frame));
            assertFalse(ViewerPreferences.ParticleIntensity.OFF.acceptsFrame(frame));
            assertEquals(frame % 2 == 0, ViewerPreferences.ParticleIntensity.REDUCED.acceptsFrame(frame));
        }
    }
}
