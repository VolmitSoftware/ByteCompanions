package com.volmit.bytecompanions.features.particles.effect;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.features.particles.configuration.MovementConfiguration;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import java.util.concurrent.atomic.LongAdder;


public final class EffectTrackingAnimation implements Animation<Void> {
    private static final LongAdder BATCHES = new LongAdder();
    private static final LongAdder PACKET_ENTRIES = new LongAdder();
    private static final LongAdder VIEWER_DELIVERIES = new LongAdder();
    private static final LongAdder PACKET_DELIVERIES = new LongAdder();

    private final ParticleEffect particleEffect;
    private final CompanionViewerEnvironment viewers;
    private final BooleanSupplier runtimeActive;
    private volatile long generation;
    private long frameSequence;
    private final double movementEpsilonSquared;
    private final double settleDelaySeconds;

    private boolean wasMoving = false;
    private boolean settling = false;
    private double stillTime = 0.0;
    private CachedFrame cachedFrame;

    public EffectTrackingAnimation(
            final @NotNull ParticleEffect particleEffect,
            final @NotNull MovementConfiguration movementConfiguration,
            final @NotNull CompanionViewerEnvironment viewers,
            final @NotNull BooleanSupplier runtimeActive
    ) {
        this.particleEffect = particleEffect;
        this.viewers = viewers;
        this.runtimeActive = runtimeActive;
        this.movementEpsilonSquared = movementConfiguration.movementEpsilonSquared();
        this.settleDelaySeconds = movementConfiguration.settleDelaySeconds();
    }

    @Override
    public @NotNull AnimationChannel channel() {
        return AnimationChannel.POST_TRACKING;
    }

    @Override
    public @Nullable Void tick(final @NotNull AnimationContext context) {
        if (!this.runtimeActive.getAsBoolean()) {
            return null;
        }
        final double dx = context.anchor().x() - context.previousAnchorX();
        final double dy = context.anchor().y() - context.previousAnchorY();
        final double dz = context.anchor().z() - context.previousAnchorZ();
        final boolean movingNow = (dx * dx + dy * dy + dz * dz) > this.movementEpsilonSquared;

        if (movingNow) {
            this.stillTime = 0.0;
            if (!this.wasMoving) {
                this.settling = false;
                this.cachedFrame = null;
                this.particleEffect.onMovementStart(context);
            }
        } else if (this.wasMoving || this.settling) {
            this.settling = true;
            this.stillTime += context.deltaTime();
            if (this.stillTime >= this.settleDelaySeconds) {
                this.settling = false;
                this.cachedFrame = null;
                this.particleEffect.onMovementStop(context);
            }
        }

        this.wasMoving = movingNow;
        final boolean shouldSuppress = this.particleEffect.hideWhileMoving() && (movingNow || this.settling);
        if (shouldSuppress) {
            return null;
        }

        final double effectY = this.particleEffect.isFixed()
                ? context.anchor().y() + this.particleEffect.fixedYOffset()
                : context.currentY();

        this.particleEffect.updateAnchor(context.currentX(), effectY, context.currentZ(), context.bodyYaw());

        if (!this.particleEffect.advance(
                context.currentX(),
                effectY,
                context.currentZ(),
                context.bodyYaw(),
                context.headPitch(),
                context.bodyYaw()
        )) {
            return null;
        }

        Consumer<Player> delivery = null;
        Player owner = context.anchor() instanceof PlayerAnchor playerAnchor ? playerAnchor.player() : null;
        long particleFrame = this.frameSequence++;
        UUID worldId = context.anchor().world().getUID();
        long expectedGeneration = this.generation;
        int packetCount = 0;
        for (Player player : context.viewers()) {
            ViewerSnapshot viewer = this.viewers.snapshot(player);
            int interval = viewer == null ? 0 : this.viewers.particleInterval(viewer, owner);
            if (viewer == null || !worldId.equals(viewer.worldId()) || interval == 0 || particleFrame % interval != 0) {
                continue;
            }
            if (delivery == null) {
                if (this.cachedFrame != null && this.cachedFrame.matches(context, effectY, worldId)) {
                    delivery = this.cachedFrame.delivery();
                    packetCount = this.cachedFrame.packetCount();
                } else {
                    ParticleBatch packets = this.particleEffect.particles(context.currentX(), effectY,
                            context.currentZ(), context.bodyYaw(), context.headPitch(), context.bodyYaw());
                    packetCount = packets.size();
                    if (packetCount == 0) {
                        return null;
                    }
                    delivery = packets.snapshotDelivery();
                    if (this.particleEffect.reusableFrame()) {
                        this.cachedFrame = new CachedFrame(context.currentX(), effectY, context.currentZ(),
                                context.bodyYaw(), context.headPitch(), worldId, delivery, packetCount);
                    }
                    BATCHES.increment();
                    PACKET_ENTRIES.add(packetCount);
                }
            }
            this.queueDelivery(viewer, owner, worldId, particleFrame, expectedGeneration, delivery, packetCount);
        }

        return null;
    }

    @Override
    public void onStop(final @NotNull AnimationContext context) {
        this.generation++;
        this.wasMoving = false;
        this.settling = false;
        this.stillTime = 0.0;
        this.cachedFrame = null;
        this.particleEffect.reset();
    }

    public static TrafficSnapshot trafficSnapshot() {
        return new TrafficSnapshot(BATCHES.sum(), PACKET_ENTRIES.sum(), VIEWER_DELIVERIES.sum(), PACKET_DELIVERIES.sum());
    }

    private void queueDelivery(ViewerSnapshot viewer, Player owner, UUID worldId, long particleFrame, long expectedGeneration,
                               Consumer<Player> delivery, int packetCount) {
        this.viewers.deliverVisual(viewer, owner, worldId, particleFrame,
                () -> this.runtimeActive.getAsBoolean() && this.generation == expectedGeneration,
                () -> {
                    delivery.accept(viewer.player());
                    VIEWER_DELIVERIES.increment();
                    PACKET_DELIVERIES.add(packetCount);
                }, () -> {});
    }

    public record TrafficSnapshot(long batches, long packetEntries, long viewerDeliveries, long packetDeliveries) {}

    private record CachedFrame(double x, double y, double z, float yaw, float pitch, UUID worldId,
                               Consumer<Player> delivery, int packetCount) {
        boolean matches(AnimationContext context, double effectY, UUID currentWorldId) {
            return this.worldId.equals(currentWorldId)
                    && Double.doubleToRawLongBits(this.x) == Double.doubleToRawLongBits(context.currentX())
                    && Double.doubleToRawLongBits(this.y) == Double.doubleToRawLongBits(effectY)
                    && Double.doubleToRawLongBits(this.z) == Double.doubleToRawLongBits(context.currentZ())
                    && Float.floatToRawIntBits(this.yaw) == Float.floatToRawIntBits(context.bodyYaw())
                    && Float.floatToRawIntBits(this.pitch) == Float.floatToRawIntBits(context.headPitch());
        }
    }
}
