package com.volmit.bytecompanions.features.particles.adapternms;

import org.joml.Vector3f;

public record DustStyle(float red, float green, float blue, float scale) {
    public DustStyle(Vector3f color, float scale) {
        this(color.x(), color.y(), color.z(), scale);
    }
}
