package com.volmit.bytecompanions.platform.paper.collection;

import java.util.Objects;
import java.util.UUID;

public record ViewerPreferences(Visibility visibility, ParticleIntensity particleIntensity) {
    public ViewerPreferences {
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(particleIntensity, "particleIntensity");
    }

    public static ViewerPreferences defaults() { return new ViewerPreferences(Visibility.ALL, ParticleIntensity.FULL); }

    public static ViewerPreferences loading() { return new ViewerPreferences(Visibility.NONE, ParticleIntensity.OFF); }

    public enum Visibility {
        ALL, OWN, NONE;

        public boolean allows(UUID viewerId, UUID ownerId) {
            return this == ALL || this == OWN && viewerId != null && viewerId.equals(ownerId);
        }
    }

    public enum ParticleIntensity {
        FULL, REDUCED, OFF;

        public ParticleIntensity limitedBy(ParticleIntensity maximum) {
            return ordinal() >= maximum.ordinal() ? this : maximum;
        }

        public boolean acceptsFrame(long sequence) {
            return this == FULL || this == REDUCED && (sequence & 1L) == 0L;
        }
    }
}
