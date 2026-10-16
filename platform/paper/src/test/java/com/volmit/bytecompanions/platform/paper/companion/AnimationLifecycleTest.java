package com.volmit.bytecompanions.platform.paper.companion;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.core.MiniatureAnimator;
import com.volmit.bytecompanions.core.companion.AnimationsSet;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLifecycleTest {
    private final AnimationContext context = (AnimationContext) Proxy.newProxyInstance(AnimationContext.class.getClassLoader(),
            new Class<?>[]{AnimationContext.class}, (proxy, method, arguments) -> { throw new AssertionError(method); });

    @Test
    void animationsRegisteredBeforeFirstTickStartOnceAndStopOnce() {
        AnimationsSet animations = new AnimationsSet();
        TrackedAnimation animation = new TrackedAnimation(AnimationChannel.MOVEMENT);
        animations.add("move", animation);
        assertEquals(0, animation.starts);
        animations.updateContext(this.context);
        animations.updateContext(this.context);
        assertEquals(1, animation.starts);
        animations.clear();
        animations.clear();
        assertEquals(1, animation.stops);
    }

    @Test
    void replacingSingleChannelStopsPreviousAndStartsReplacement() {
        AnimationsSet animations = new AnimationsSet();
        TrackedAnimation old = new TrackedAnimation(AnimationChannel.FULL_BODY);
        TrackedAnimation replacement = new TrackedAnimation(AnimationChannel.FULL_BODY);
        animations.add("old", old);
        animations.updateContext(this.context);
        animations.add("replacement", replacement);
        assertEquals(1, old.stops);
        assertEquals(1, replacement.starts);
        assertEquals(1, animations.size());
    }

    @Test
    void removingUninitializedOrEmptyChannelIsSafe() {
        AnimationsSet animations = new AnimationsSet();
        assertFalse(animations.remove(AnimationChannel.HEAD_ONLY));
        TrackedAnimation animation = new TrackedAnimation(AnimationChannel.HEAD_ONLY);
        animations.add("head", animation);
        assertTrue(animations.remove(AnimationChannel.HEAD_ONLY));
        assertTrue(animations.isEmpty());
        assertEquals(0, animation.starts);
        assertEquals(0, animation.stops);
    }

    @Test
    void clearedSetWaitsForNewContextBeforeStartingAgain() {
        AnimationsSet animations = new AnimationsSet();
        animations.updateContext(this.context);
        animations.clear();
        TrackedAnimation animation = new TrackedAnimation(AnimationChannel.MOVEMENT);
        animations.add("new", animation);
        assertEquals(0, animation.starts);
        animations.updateContext(this.context);
        assertEquals(1, animation.starts);
    }

    @Test
    void inactiveExclusiveChannelsNeverTick() {
        MiniatureAnimator animator = new MiniatureAnimator();
        TrackedAnimation fullBody = new TrackedAnimation(AnimationChannel.FULL_BODY);
        TrackedAnimation head = new TrackedAnimation(AnimationChannel.HEAD_ONLY);
        animator.animationSet().add("full", fullBody);
        animator.animationSet().add("head", head);
        animator.tick(this.context);
        assertEquals(0, fullBody.ticks);
        assertEquals(0, head.ticks);
    }

    @Test
    void destroyingCompanionStopsAnimationsBeforeDestroyingNativeVisuals() {
        AnimationsSet animations = new AnimationsSet();
        TrackedAnimation animation = new TrackedAnimation(AnimationChannel.MOVEMENT);
        animations.add("move", animation);
        animations.updateContext(this.context);
        AtomicInteger destroyed = new AtomicInteger();
        NmsCapableCompanionEntity entity = (NmsCapableCompanionEntity) Proxy.newProxyInstance(
                NmsCapableCompanionEntity.class.getClassLoader(), new Class<?>[]{NmsCapableCompanionEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "animations" -> animations;
                    case "destroy" -> {
                        assertEquals(1, animation.stops);
                        assertTrue(animations.isEmpty());
                        destroyed.incrementAndGet();
                        yield null;
                    }
                    default -> throw new AssertionError(method);
                });
        DefaultCompanion companion = new DefaultCompanion(UUID.randomUUID(), "test", null, null, entity);
        companion.destroy();
        companion.destroy();
        assertEquals(1, animation.stops);
        assertEquals(2, destroyed.get());
    }

    private static final class TrackedAnimation implements Animation<Object> {
        private final AnimationChannel channel;
        private int starts;
        private int stops;
        private int ticks;

        private TrackedAnimation(AnimationChannel channel) { this.channel = channel; }
        @Override public AnimationChannel channel() { return this.channel; }
        @Override public void onStart(AnimationContext context) { this.starts++; }
        @Override public void onStop(AnimationContext context) { this.stops++; }
        @Override public boolean isActive() { return false; }
        @Override public Object tick(AnimationContext context) { this.ticks++; return null; }
    }
}
