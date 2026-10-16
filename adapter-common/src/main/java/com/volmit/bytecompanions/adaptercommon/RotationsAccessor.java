package com.volmit.bytecompanions.adaptercommon;

import net.minecraft.core.Rotations;
import org.jetbrains.annotations.NotNull;

public interface RotationsAccessor {

    float rotX(final @NotNull Rotations rotations);

    float rotY(final @NotNull Rotations rotations);

    float rotZ(final @NotNull Rotations rotations);

}
