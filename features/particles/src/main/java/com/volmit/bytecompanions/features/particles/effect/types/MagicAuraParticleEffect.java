package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class MagicAuraParticleEffect implements ParticleEffect {

    private boolean tick = false;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.tick = !this.tick;
        return this.tick;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final int particleCount = 5;
        final float spread = 0.3f;
        ParticleBatch result = ParticlesFeature.getNMSAdapter().createBatch(1);
        result.set(0, ParticleKind.WITCH, x, y + 0.5, z, spread, 0.4f, spread, 0.01f, particleCount);
        return result;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
