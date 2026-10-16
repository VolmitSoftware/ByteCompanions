package com.volmit.bytecompanions.spi.companions;

public interface NmsCompanionHandle {

    void spawn();

    void setPose(float headYaw, float bodyYaw);

    void lookAt(final double x, final double y, final double z);

    void destroy();

}
