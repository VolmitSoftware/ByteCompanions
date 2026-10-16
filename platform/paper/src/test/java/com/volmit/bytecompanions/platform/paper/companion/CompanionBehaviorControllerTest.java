package com.volmit.bytecompanions.platform.paper.companion;

import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.api.companion.behavior.CompanionAction;
import com.volmit.bytecompanions.api.companion.behavior.CompanionActionResult;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehaviorPolicy;
import com.volmit.bytecompanions.api.companion.behavior.CompanionMode;
import com.volmit.bytecompanions.api.util.Rotations;
import com.volmit.bytecompanions.core.companion.AnimationFrame;
import com.volmit.bytecompanions.core.companion.CompanionBehaviorController;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanionBehaviorControllerTest {
    @Test
    void policyStartsDisabledAndRejectsInvalidBounds() {
        Fixture fixture = new Fixture(CompanionModel.FULL_BODY);
        assertEquals(CompanionActionResult.DISABLED, fixture.control.act(CompanionAction.SIT));
        assertNull(fixture.apply().absoluteX);
        assertThrows(IllegalArgumentException.class, () -> new CompanionBehaviorPolicy(true, true,
                Duration.ZERO, Duration.ZERO, Duration.ofSeconds(5), 16));
        assertThrows(IllegalArgumentException.class, () -> new CompanionBehaviorPolicy(true, true,
                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5), Double.NaN));
    }

    @Test
    void stayHoldsActualPositionAndCallBypassesCooldown() {
        Fixture fixture = new Fixture(CompanionModel.FULL_BODY);
        fixture.enable(false);
        assertEquals(CompanionActionResult.APPLIED, fixture.control.act(CompanionAction.STAY));
        fixture.x = 3;
        AnimationFrame held = fixture.apply();
        assertEquals(2, held.absoluteX);
        assertEquals(65, held.absoluteY);
        assertEquals(2, held.absoluteZ);
        assertEquals(0, held.deltaX);
        assertEquals(CompanionActionResult.COOLDOWN, fixture.control.act(CompanionAction.PET));
        assertEquals(CompanionActionResult.APPLIED, fixture.control.act(CompanionAction.CALL));
        assertEquals(CompanionMode.FOLLOW, fixture.control.mode());
        assertEquals(3, fixture.apply().absoluteX);
        assertNull(fixture.apply().absoluteX);
    }

    @Test
    void distanceAndWorldChangesReleaseHeldState() {
        Fixture fixture = new Fixture(CompanionModel.HEAD);
        fixture.enable(false);
        fixture.control.act(CompanionAction.STAY);
        fixture.x = 30;
        assertEquals(30, fixture.apply().absoluteX);
        assertEquals(CompanionMode.FOLLOW, fixture.control.mode());
        fixture.now += Duration.ofSeconds(3).toNanos();
        fixture.x = 0;
        fixture.control.act(CompanionAction.SIT);
        fixture.worldId = UUID.randomUUID();
        fixture.apply();
        assertEquals(CompanionMode.FOLLOW, fixture.control.mode());
    }

    @Test
    void sittingAndWaveOverrideThenReturnToConfiguredPose() {
        Fixture fixture = new Fixture(CompanionModel.FULL_BODY);
        fixture.enable(false);
        fixture.control.act(CompanionAction.SIT);
        assertEquals(-90, fixture.apply().leftLeg.x());
        fixture.now += Duration.ofSeconds(3).toNanos();
        fixture.control.act(CompanionAction.WAVE);
        fixture.now += Duration.ofMillis(250).toNanos();
        assertEquals(-150, fixture.apply().rightArm.x());
        fixture.control.act(CompanionAction.CALL);
        AnimationFrame configured = AnimationFrame.empty();
        configured.rightArm = new Rotations(22, 23, 24);
        configured.absoluteHeadPitch = 17.0f;
        fixture.control.apply(fixture.context, configured);
        assertEquals(22, configured.rightArm.x());
        assertEquals(17, configured.absoluteHeadPitch);
        assertEquals(0, configured.leftLeg.x());
    }

    @Test
    void emoteExpiresAndHeadModelsNeverInventLimbs() {
        Fixture fixture = new Fixture(CompanionModel.HEAD);
        fixture.enable(false);
        fixture.control.act(CompanionAction.WAVE);
        fixture.now += Duration.ofMillis(250).toNanos();
        AnimationFrame active = fixture.apply();
        assertNotEquals(0, active.absoluteHeadPitch);
        assertNull(active.rightArm);
        fixture.now += Duration.ofSeconds(2).toNanos();
        assertEquals(0, fixture.apply().absoluteHeadPitch);
        assertNull(fixture.apply().absoluteHeadPitch);
    }

    @Test
    void replacingWaveRestoresArmBeforeNewEmoteCompletes() {
        Fixture fixture = new Fixture(CompanionModel.FULL_BODY);
        fixture.control.policy(new CompanionBehaviorPolicy(true, false, Duration.ZERO, Duration.ofSeconds(2),
                Duration.ofSeconds(5), 8));
        fixture.control.act(CompanionAction.WAVE);
        assertEquals(-150, fixture.apply().rightArm.x());
        fixture.control.act(CompanionAction.PET);
        assertEquals(0, fixture.apply().rightArm.x());
    }

    @Test
    void replacementPoseClearsMissingChannelsWithoutReleasingStay() {
        Fixture fixture = new Fixture(CompanionModel.FULL_BODY);
        fixture.enable(false);
        fixture.control.act(CompanionAction.STAY);
        fixture.apply();
        fixture.control.resetPose();
        AnimationFrame replacement = AnimationFrame.empty();
        replacement.leftArm = new Rotations(40, 0, 0);
        fixture.control.apply(fixture.context, replacement);
        assertEquals(40, replacement.leftArm.x());
        assertEquals(0, replacement.rightArm.x());
        assertEquals(0, replacement.leftLeg.x());
        assertEquals(0, replacement.rightLeg.x());
        assertEquals(0, replacement.absoluteHeadPitch);
        assertEquals(CompanionMode.STAY, fixture.control.mode());
        assertEquals(2, replacement.absoluteX);
        assertNull(fixture.apply().rightArm);
    }

    @Test
    void idleReactionsAreOptInAndWaitForStationaryInterval() {
        Fixture fixture = new Fixture(CompanionModel.HEAD);
        fixture.enable(false);
        fixture.now = Duration.ofMinutes(1).toNanos();
        assertNull(fixture.apply().absoluteHeadPitch);
        fixture.enable(true);
        fixture.now += Duration.ofSeconds(5).toNanos();
        fixture.x = 1;
        assertNull(fixture.apply().absoluteHeadPitch);
        fixture.previousX = fixture.x;
        fixture.now += Duration.ofSeconds(5).toNanos();
        fixture.apply();
        fixture.now += Duration.ofMillis(250).toNanos();
        assertNotEquals(0, fixture.apply().absoluteHeadPitch);
        fixture.control.policy(CompanionBehaviorPolicy.disabled());
        assertEquals(0, fixture.apply().absoluteHeadPitch);
        assertNull(fixture.apply().absoluteHeadPitch);
    }

    @Test
    void formationOverridesOnlyHorizontalMovementAndRetainsLifecycle() {
        Fixture fixture = new Fixture(CompanionModel.HEAD);
        List<String> calls = new ArrayList<>();
        MovementAnimation formation = new MovementAnimation() {
            @Override
            public MovementOutput tick(AnimationContext context) {
                return MovementOutput.of(0.25, 99, -0.75);
            }
            @Override
            public void onStart(AnimationContext context) {
                calls.add("start");
            }
            @Override
            public void onStop(AnimationContext context) {
                calls.add("stop");
            }
        };
        fixture.control.formation(formation);
        AnimationFrame frame = AnimationFrame.empty();
        frame.absoluteX = 10.0;
        frame.absoluteZ = 12.0;
        frame.deltaY = 0.1;
        frame.absoluteY = 70.0;
        fixture.control.apply(fixture.context, frame);
        assertNull(frame.absoluteX);
        assertNull(frame.absoluteZ);
        assertEquals(0.25, frame.deltaX);
        assertEquals(-0.75, frame.deltaZ);
        assertEquals(70, frame.absoluteY);
        assertEquals(0.1, frame.deltaY);
        fixture.control.formation(null);
        assertEquals(List.of("start", "stop"), calls);
        assertEquals(0, fixture.apply().deltaX);
    }

    @Test
    void closedBehaviorCannotReactivateAndCleanupIsIdempotent() {
        Fixture fixture = new Fixture(CompanionModel.HEAD);
        fixture.enable(false);
        fixture.control.act(CompanionAction.SIT);
        fixture.control.close();
        fixture.control.close();
        assertEquals(CompanionMode.FOLLOW, fixture.control.mode());
        assertEquals(CompanionActionResult.DISABLED, fixture.control.act(CompanionAction.PET));
        assertThrows(IllegalStateException.class, () -> fixture.control.policy(CompanionBehaviorPolicy.disabled()));
        assertNull(fixture.apply().absoluteX);
    }

    private static final class Fixture {
        private UUID worldId = UUID.randomUUID();
        private long now;
        private double x;
        private double previousX;
        private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUID")) return worldId;
                    throw new AssertionError(method);
                });
        private final CompanionAnchor anchor = new CompanionAnchor() {
            public double x() { return x; }
            public double y() { return 64; }
            public double z() { return 0; }
            public float yaw() { return 0; }
            public float pitch() { return 0; }
            public float bodyYaw() { return 0; }
            public double eyeHeight() { return 1.6; }
            public double height() { return 1.8; }
            public World world() { return world; }
        };
        private final AnimationContext context = (AnimationContext) Proxy.newProxyInstance(AnimationContext.class.getClassLoader(),
                new Class<?>[]{AnimationContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "anchor" -> anchor;
                    case "previousAnchorX" -> previousX;
                    case "previousAnchorZ" -> 0.0;
                    default -> throw new AssertionError(method);
                });
        private final CompanionBehaviorController control;

        private Fixture(CompanionModel model) {
            control = new CompanionBehaviorController(model,
                    () -> new CompanionBehaviorController.Position(worldId, 2, 65, 2, 45), () -> now);
        }

        private void enable(boolean idle) {
            control.policy(new CompanionBehaviorPolicy(true, idle, Duration.ofSeconds(2), Duration.ofSeconds(2),
                    Duration.ofSeconds(5), 8));
        }

        private AnimationFrame apply() {
            AnimationFrame frame = AnimationFrame.empty();
            control.apply(context, frame);
            return frame;
        }
    }
}
