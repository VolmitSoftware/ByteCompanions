package com.volmit.bytecompanions.platform.paper.features;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.v26_1.V26_1NMSAdapter;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.features.particles.effect.types.DoubleHelixParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.PulseParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.RotatingRingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.ScannerParticleEffect;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBatchTest {
    @Test
    void queuedDeliveryRetainsItsImmutableBundleAfterBatchReuse() throws ReflectiveOperationException {
        ParticleBatch batch = new V26_1NMSAdapter().createBatch(1);
        batch.set(0, new DustStyle(1, 0, 0, 1), 1, 2, 3, 0, 0, 0, 0, 1);
        Object delivery = batch.snapshotDelivery();
        Field bundleField = Arrays.stream(delivery.getClass().getDeclaredFields())
                .filter(field -> field.getType() == ClientboundBundlePacket.class)
                .findFirst().orElseThrow();
        bundleField.setAccessible(true);
        Object capturedBundle = bundleField.get(delivery);
        batch.clear();
        batch.set(0, new DustStyle(0, 1, 0, 1), 40, 50, 60, 0, 0, 0, 0, 1);
        Object nextDelivery = batch.snapshotDelivery();
        assertSame(capturedBundle, bundleField.get(delivery));
        assertNotSame(capturedBundle, bundleField.get(nextDelivery));
        ClientboundBundlePacket snapshot = (ClientboundBundlePacket) capturedBundle;
        List<ClientboundLevelParticlesPacket> members = new ArrayList<>();
        snapshot.subPackets().forEach(packet -> members.add((ClientboundLevelParticlesPacket) packet));
        assertEquals(1, members.size());
        assertEquals(1.0, members.getFirst().getX());
        assertEquals(2.0, members.getFirst().getY());
        assertEquals(3.0, members.getFirst().getZ());
        assertEquals(1, members.getFirst().getCount());
    }

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void animatedDustColorsChangeBetweenFrames() throws ReflectiveOperationException {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, new V26_1NMSAdapter());
        try {
            for (ParticleEffect effect : List.of(new DoubleHelixParticleEffect(), new PulseParticleEffect(),
                    new RotatingRingParticleEffect(), new ScannerParticleEffect())) {
                Set<DustStyle> colors = new HashSet<>();
                for (int tick = 0; tick < 150; tick++) {
                    if (!effect.advance(10, 64, 10, 45, 0, 45)) {
                        continue;
                    }
                    ParticleBatch batch = effect.particles(10, 64, 10, 45, 0, 45);
                    if (batch.size() > 0) {
                        Field lastStyle = batch.getClass().getDeclaredField("lastStyle");
                        lastStyle.setAccessible(true);
                        colors.add((DustStyle) lastStyle.get(batch));
                    }
                }
                assertTrue(colors.size() > 1, effect.getClass().getSimpleName() + " retained one dust color");
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    @Test
    void identicalStylesReuseNativeOptionsAcrossBatchesAndCacheStaysBounded() throws ReflectiveOperationException {
        V26_1NMSAdapter adapter = new V26_1NMSAdapter();
        ParticleBatch first = adapter.createBatch(1);
        ParticleBatch second = adapter.createBatch(1);
        first.set(0, new DustStyle(1, 1, 1, 0.3f), 1, 2, 3, 0, 0, 0, 0, 1);
        second.set(0, new DustStyle(1, 1, 1, 0.3f), 4, 5, 6, 0, 0, 0, 0, 1);
        Field lastParticle = first.getClass().getDeclaredField("lastParticle");
        lastParticle.setAccessible(true);
        assertSame(lastParticle.get(first), lastParticle.get(second));

        for (int index = 0; index < 512; index++) {
            ParticleBatch batch = adapter.createBatch(1);
            batch.set(0, new DustStyle((index % 256) / 255.0f, (index / 256) / 255.0f, 0, 0.3f),
                    1, 2, 3, 0, 0, 0, 0, 1);
        }
        Field styles = V26_1NMSAdapter.class.getDeclaredField("styles");
        styles.setAccessible(true);
        assertEquals(128, ((Map<?, ?>) styles.get(adapter)).size());
        first.send(List.of());
        second.send(List.of());
    }

    @Test
    void everyEffectProducesCompleteBatchesAcrossAnimationCycles() throws ReflectiveOperationException {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, new V26_1NMSAdapter());
        try {
            for (Map.Entry<String, Supplier<ParticleEffect>> entry : ParticleEffectCatalog.factories().entrySet()) {
                ParticleEffect effect = entry.getValue().get();
                for (int tick = 0; tick < 150; tick++) {
                    if (!effect.advance(10, 64, 10, 45, 0, 45)) {
                        continue;
                    }
                    ParticleBatch batch = effect.particles(10, 64, 10, 45, 0, 45);
                    assertNotNull(batch, entry.getKey());
                    batch.send(List.of());
                }
                effect.reset();
                if (effect.advance(11, 64, 10, 90, 0, 90)) {
                    effect.particles(11, 64, 10, 90, 0, 90).send(List.of());
                }
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    @Test
    void slicingAndCopyingKeepPacketsIndependentOfLaterSourceChanges() {
        V26_1NMSAdapter adapter = new V26_1NMSAdapter();
        ParticleBatch source = adapter.createBatch(2);
        DustStyle white = new DustStyle(1, 1, 1, 0.3f);
        source.set(0, white, 1, 2, 3, 0, 0, 0, 0, 1);
        source.set(1, white, 4, 5, 6, 0, 0, 0, 0, 1);
        ParticleBatch first = source.slice(1);
        ParticleBatch copy = adapter.createBatch(2);
        source.copyTo(0, copy, 0, 2);
        source.clear();

        assertEquals(1, first.size());
        first.send(List.of());
        copy.send(List.of());
        assertThrows(NullPointerException.class, () -> source.send(List.of()));
        assertThrows(IndexOutOfBoundsException.class, () -> copy.slice(3));
    }
}
