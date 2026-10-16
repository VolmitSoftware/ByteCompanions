package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class CloudParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(10);

        final double cloudY = y + 4.5;
        final double cloudRadius = 0.5;
        final int cloudCount = 2;

        packets.set(0, ParticleKind.CLOUD, x, cloudY, z, 0, 0, 0, 0, cloudCount);

        packets.set(1, ParticleKind.CLOUD, x + cloudRadius, cloudY, z, 0, 0, 0, 0, cloudCount);

        packets.set(2, ParticleKind.CLOUD, x - cloudRadius, cloudY, z, 0, 0, 0, 0, cloudCount);

        packets.set(3, ParticleKind.CLOUD, x, cloudY, z + cloudRadius, 0, 0, 0, 0, cloudCount);

        packets.set(4, ParticleKind.CLOUD, x, cloudY, z - cloudRadius, 0, 0, 0, 0, cloudCount);

        packets.set(5, ParticleKind.CLOUD, x + cloudRadius * 0.7, cloudY, z + cloudRadius * 0.7, 0, 0, 0, 0, cloudCount);

        packets.set(6, ParticleKind.CLOUD, x - cloudRadius * 0.7, cloudY, z - cloudRadius * 0.7, 0, 0, 0, 0, cloudCount);

        packets.set(7, ParticleKind.CLOUD, x + cloudRadius * 0.7, cloudY, z - cloudRadius * 0.7, 0, 0, 0, 0, cloudCount);

        packets.set(8, ParticleKind.CLOUD, x - cloudRadius * 0.7, cloudY, z + cloudRadius * 0.7, 0, 0, 0, 0, cloudCount);

        packets.set(9, ParticleKind.RAIN, x, cloudY, z, 0, 0, 0, 0, 5);

        return packets;
    }

}
