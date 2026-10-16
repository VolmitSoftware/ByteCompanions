package com.volmit.bytecompanions.platform.paper.features;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.animation.output.HeadOnlyOutput;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationFactory;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.platform.paper.companion.animations.BuiltinAnimationRegistry;
import com.volmit.bytecompanions.features.animations.AnimationsFeature;
import com.volmit.bytecompanions.features.animations.animations.FollowOwnerAnimationPro;
import com.volmit.bytecompanions.features.animations.animations.HeadIdleSwayAnimation;
import com.volmit.bytecompanions.features.animations.animations.LookAtOwnerAnimationPro;
import com.volmit.bytecompanions.features.animations.animations.WalkSwingAnimationPro;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimationsFeatureTest {

    @Test
    void registersEveryAddonAnimationAlongsideCoreFactories() {
        AnimationRegistry registry = BuiltinAnimationRegistry.createDefaults();
        new AnimationsFeature(companions(registry)).enable();

        assertEquals(Set.of("follow_owner", "follow_formation", "hover", "follow_owner_pro"),
                registry.registeredIds(AnimationChannel.MOVEMENT));
        assertEquals(Set.of("look_at_owner", "look_at_owner_pro"), registry.registeredIds(AnimationChannel.ROTATION));
        assertEquals(Set.of("walk_swing", "walk_swing_pro"), registry.registeredIds(AnimationChannel.POSE));
        assertEquals(Set.of("head_idle_sway"), registry.registeredIds(AnimationChannel.HEAD_ONLY));

        assertFactory(registry, AnimationChannel.MOVEMENT, "follow_owner_pro", FollowOwnerAnimationPro.class);
        assertFactory(registry, AnimationChannel.ROTATION, "look_at_owner_pro", LookAtOwnerAnimationPro.class);
        assertFactory(registry, AnimationChannel.POSE, "walk_swing_pro", WalkSwingAnimationPro.class);
        assertFactory(registry, AnimationChannel.HEAD_ONLY, "head_idle_sway", HeadIdleSwayAnimation.class);
    }

    @Test
    void repeatedEnableKeepsCoreFactoriesAndDoesNotDuplicateRegistrations() {
        AnimationRegistry registry = BuiltinAnimationRegistry.createDefaults();
        AnimationFactory<?> coreFollow = registry.get(AnimationChannel.MOVEMENT, "follow_owner");
        AnimationsFeature feature = new AnimationsFeature(companions(registry));
        feature.enable();
        feature.enable();

        int registrations = 0;
        for (AnimationChannel channel : AnimationChannel.values()) {
            registrations += registry.registeredIds(channel).size();
        }
        assertEquals(9, registrations);
        assertSame(coreFollow, registry.get(AnimationChannel.MOVEMENT, "follow_owner"));
    }

    @Test
    void factoryCreatesIndependentAnimationStateForEachCompanion() {
        AnimationRegistry registry = BuiltinAnimationRegistry.createDefaults();
        new AnimationsFeature(companions(registry)).enable();
        AnimationFactory<?> factory = registry.get(AnimationChannel.HEAD_ONLY, "head_idle_sway");
        HeadIdleSwayAnimation first = assertInstanceOf(HeadIdleSwayAnimation.class,
                factory.create(data(Map.of())));
        HeadIdleSwayAnimation second = assertInstanceOf(HeadIdleSwayAnimation.class,
                factory.create(data(Map.of())));
        AnimationContext context = context(0.15);

        HeadOnlyOutput firstFrame = first.tick(context);
        HeadOnlyOutput laterFrame = first.tick(context);
        HeadOnlyOutput secondFirstFrame = second.tick(context);

        assertNotSame(first, second);
        assertNotEquals(firstFrame.headYaw, laterFrame.headYaw);
        assertEquals(firstFrame.headYaw, secondFirstFrame.headYaw);
        assertEquals(firstFrame.headPitch, secondFirstFrame.headPitch);
    }

    @Test
    void registeredFactoryReadsCompanionAnimationParameters() {
        AnimationRegistry registry = BuiltinAnimationRegistry.createDefaults();
        new AnimationsFeature(companions(registry)).enable();
        AnimationFactory<?> factory = registry.get(AnimationChannel.HEAD_ONLY, "head_idle_sway");
        HeadIdleSwayAnimation animation = assertInstanceOf(HeadIdleSwayAnimation.class,
                factory.create(data(Map.of("yaw-amplitude", 0.0f, "pitch-amplitude", 0.0f))));

        HeadOnlyOutput frame = animation.tick(context(0.15));

        assertEquals(0.0f, frame.headYaw.floatValue());
        assertEquals(0.0f, frame.headPitch.floatValue());
    }

    private static void assertFactory(AnimationRegistry registry, AnimationChannel channel, String id, Class<?> type) {
        Animation<?> animation = registry.get(channel, id).create(data(Map.of()));
        assertInstanceOf(type, animation);
        assertEquals(channel, animation.channel());
    }

    private static ByteCompanions companions(AnimationRegistry registry) {
        return ByteCompanions.class.cast(Proxy.newProxyInstance(ByteCompanions.class.getClassLoader(),
                new Class<?>[]{ByteCompanions.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("animationRegistry")) {
                        return registry;
                    }
                    throw new AssertionError("Unexpected service call: " + method.getName());
                }));
    }

    private static AnimationData data(Map<String, Number> values) {
        return AnimationData.class.cast(Proxy.newProxyInstance(AnimationData.class.getClassLoader(),
                new Class<?>[]{AnimationData.class}, (proxy, method, arguments) -> {
                    String path = (String) arguments[0];
                    Number value = values.get(path);
                    return switch (method.getName()) {
                        case "getDoubleOr" -> value == null ? arguments[1] : value.doubleValue();
                        case "getFloatOr" -> value == null ? arguments[1] : value.floatValue();
                        default -> throw new AssertionError("Unexpected configuration call: " + method.getName());
                    };
                }));
    }

    private static AnimationContext context(double deltaTime) {
        return AnimationContext.class.cast(Proxy.newProxyInstance(AnimationContext.class.getClassLoader(),
                new Class<?>[]{AnimationContext.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("deltaTime")) {
                        return deltaTime;
                    }
                    throw new AssertionError("Unexpected animation context call: " + method.getName());
                }));
    }
}
