package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation;

import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.FormationTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormationDirectionTest {

    private static final double SPACING = 1.5;
    private static final double TOLERANCE = 0.001;
    private static final double STEP = 0.2;

    // Yaw 0 faces +Z and yaw 90 faces -X, so each row is a yaw and the way a player with it looks.
    private static final double[][] HEADINGS = {{0, 0, 1}, {90, -1, 0}, {180, 0, -1}, {-90, 1, 0}};

    @Test
    void lineStartsBehindAnOwnerStandingStill() {
        for (double[] heading : HEADINGS) {
            assertBehind(new LineFormation(SPACING), heading, 0);
        }
    }

    @Test
    void gridStartsBehindAnOwnerStandingStill() {
        for (double[] heading : HEADINGS) {
            assertBehind(new GridFormation(SPACING, 3), heading, 1);
        }
    }

    @Test
    void lineStaysBehindAnOwnerWalkingAnyDirection() {
        for (double[] heading : HEADINGS) {
            assertBehindAfterWalking(new LineFormation(SPACING), heading, 0);
        }
    }

    @Test
    void gridStaysBehindAnOwnerWalkingAnyDirection() {
        for (double[] heading : HEADINGS) {
            assertBehindAfterWalking(new GridFormation(SPACING, 3), heading, 1);
        }
    }

    @Test
    void lookingAroundWhileStandingDoesNotTurnTheFormation() {
        LineFormation formation = new LineFormation(SPACING);
        formation.target(context(0, 0, 0, 0, 0, 0));

        FormationTarget target = formation.target(context(90, 0, 0, 0, 0, 0));

        assertEquals(0, target.x(), TOLERANCE);
        assertEquals(-SPACING, target.z(), TOLERANCE);
    }

    private static void assertBehind(Formation formation, double[] heading, int slot) {
        FormationTarget target = formation.target(context(heading[0], 0, 0, 0, 0, slot));

        assertEquals(-heading[1] * SPACING, target.x(), TOLERANCE, "x at yaw " + heading[0]);
        assertEquals(-heading[2] * SPACING, target.z(), TOLERANCE, "z at yaw " + heading[0]);
    }

    private static void assertBehindAfterWalking(Formation formation, double[] heading, int slot) {
        // The owner looks off to one side, since the formation should follow their steps and not their camera.
        formation.target(context(heading[0] + 45, 0, 0, 0, 0, slot));
        double x = 0;
        double z = 0;
        FormationTarget target = null;
        for (int tick = 0; tick < 40; tick++) {
            double nextX = x + heading[1] * STEP;
            double nextZ = z + heading[2] * STEP;
            target = formation.target(context(heading[0] + 45, nextX, nextZ, x, z, slot));
            x = nextX;
            z = nextZ;
        }

        assertEquals(x - heading[1] * SPACING, target.x(), TOLERANCE, "x walking at yaw " + heading[0]);
        assertEquals(z - heading[2] * SPACING, target.z(), TOLERANCE, "z walking at yaw " + heading[0]);
    }

    private static AnimationContext context(double yaw, double x, double z, double previousX, double previousZ, int slot) {
        CompanionAnchor anchor = stub(CompanionAnchor.class, Map.of("x", x, "z", z, "y", 0.0, "yaw", (float) yaw));
        return stub(AnimationContext.class, Map.of("anchor", anchor, "previousAnchorX", previousX, "previousAnchorZ", previousZ,
                "deltaTime", 1.0, "formatIndex", slot));
    }

    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object value = values.get(method.getName());
            if (value == null) throw new AssertionError("Unexpected call to " + method.getName());
            return value;
        }));
    }
}
