package com.volmit.bytecompanions.features.decorations.data;

public record CompanionData(
        String id,
        String companionTypeId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float bodyYaw,
        double eyeHeight,
        double height,
        String worldName
) {
}
