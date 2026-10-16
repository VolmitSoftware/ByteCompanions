package com.volmit.bytecompanions.core.companion;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.util.Rotations;

public final class AnimationFrame {

    public double deltaX;
    public double deltaY;
    public double deltaZ;

    public Double absoluteX;
    public Double absoluteY;
    public Double absoluteZ;

    public Float absoluteBodyYaw;
    public Float absoluteHeadPitch;

    public float deltaBodyYaw;
    public float deltaHeadPitch;

    public Rotations leftArm;
    public Rotations rightArm;
    public Rotations leftLeg;
    public Rotations rightLeg;

    public static @NotNull AnimationFrame empty() {
        return new AnimationFrame();
    }
}
