package com.volmit.bytecompanions.features.particles.adapternms.v26_1;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.NMSAdapter;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class V26_1NMSAdapter implements NMSAdapter {
    private static final int STYLE_CACHE_LIMIT = 128;
    private final Map<DustStyle, DustParticleOptions> styles = new LinkedHashMap<>();
    private final ParticleBatch empty = new Batch(this, 0);

    @Override
    public ParticleBatch createBatch(int size) {
        return size == 0 ? empty : new Batch(this, size);
    }

    private synchronized DustParticleOptions resolve(DustStyle style) {
        DustParticleOptions particle = styles.get(style);
        if (particle != null) {
            return particle;
        }
        particle = Batch.dust(style.red(), style.green(), style.blue(), style.scale());
        if (styles.size() == STYLE_CACHE_LIMIT) {
            styles.remove(styles.keySet().iterator().next());
        }
        styles.put(style, particle);
        return particle;
    }

    private static final class Batch implements ParticleBatch {
        private final ClientboundLevelParticlesPacket[] packets;
        private final V26_1NMSAdapter adapter;
        private DustStyle lastStyle;
        private DustParticleOptions lastParticle;
        private ClientboundBundlePacket bundle;

        private Batch(V26_1NMSAdapter adapter, int size) {
            this.adapter = adapter;
            packets = new ClientboundLevelParticlesPacket[size];
        }

        @Override
        public int size() {
            return packets.length;
        }

        @Override
        public void set(int index, ParticleKind kind, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            ParticleOptions particle = switch (kind) {
                case CLOUD -> ParticleTypes.CLOUD;
                case RAIN -> ParticleTypes.RAIN;
                case ENCHANT -> ParticleTypes.ENCHANT;
                case HEART -> ParticleTypes.HEART;
                case WITCH -> ParticleTypes.WITCH;
                case NOTE -> ParticleTypes.NOTE;
            };
            setPacket(index, particle, x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void set(int index, DustStyle style, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            if (!style.equals(lastStyle)) {
                lastStyle = style;
                lastParticle = adapter.resolve(style);
            }
            setPacket(index, lastParticle, x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void setDust(int index, Vector3f color, float scale, double x, double y, double z,
                            float offsetX, float offsetY, float offsetZ, float speed, int count) {
            setPacket(index, dust(color.x(), color.y(), color.z(), scale), x, y, z,
                    offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void setColored(int index, float red, float green, float blue, double x, double y, double z,
                               float offsetX, float offsetY, float offsetZ, float speed, int count) {
            setPacket(index, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, red, green, blue),
                    x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        private static DustParticleOptions dust(float red, float green, float blue, float scale) {
            int rgb = (Math.round(red * 255.0f) << 16) | (Math.round(green * 255.0f) << 8) | Math.round(blue * 255.0f);
            return new DustParticleOptions(rgb, scale);
        }

        private void setPacket(int index, ParticleOptions particle, double x, double y, double z,
                               float offsetX, float offsetY, float offsetZ, float speed, int count) {
            packets[index] = new ClientboundLevelParticlesPacket(particle, true, true,
                    x, y, z, offsetX, offsetY, offsetZ, speed, count);
            bundle = null;
        }

        @Override
        public void copyTo(int sourceIndex, ParticleBatch target, int targetIndex, int count) {
            if (!(target instanceof Batch destination)) {
                throw new IllegalArgumentException("Particle batches must use the same native adapter");
            }
            System.arraycopy(packets, sourceIndex, destination.packets, targetIndex, count);
            destination.bundle = null;
        }

        @Override
        public ParticleBatch slice(int size) {
            if (size < 0 || size > packets.length) {
                throw new IndexOutOfBoundsException(size);
            }
            if (size == packets.length) {
                return this;
            }
            Batch result = new Batch(adapter, size);
            copyTo(0, result, 0, size);
            return result;
        }

        @Override
        public void clear() {
            Arrays.fill(packets, null);
            lastStyle = null;
            lastParticle = null;
            bundle = null;
        }

        @Override
        public Consumer<Player> snapshotDelivery() {
            if (packets.length == 0) {
                return viewer -> {};
            }
            if (bundle == null) {
                bundle = new ClientboundBundlePacket(List.of(packets));
            }
            ClientboundBundlePacket snapshot = bundle;
            return viewer -> ((CraftPlayer) viewer).getHandle().connection.send(snapshot);
        }

        @Override
        public void send(Iterable<? extends Player> viewers) {
            Consumer<Player> delivery = this.snapshotDelivery();
            for (Player viewer : viewers) {
                delivery.accept(viewer);
            }
        }
    }
}
