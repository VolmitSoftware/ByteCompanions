package com.volmit.bytecompanions.features.animations.util;

import org.jetbrains.annotations.NotNull;

public final class MathUtil {

    private MathUtil() {
        throw new IllegalStateException("Utility class");
    }

    // Normalize angle to [-π, π]
    private static double wrapAngle(double x) {
        x %= (Math.PI * 2.0);
        if (x < -Math.PI) {
            x += Math.PI * 2.0;
        }

        if (x > Math.PI) {
            x -= Math.PI * 2.0;
        }

        return x;
    }

    public static double ultraFastSinDouble(final double xInput) {
        double x = xInput;

        if (x < -3.141592653589793) {
            x += 6.283185307179586;
        } else if (x > 3.141592653589793) {
            x -= 6.283185307179586;
        }

        final double sin = 1.2732395447351628 * x + 0.4052847345693511 * x * Math.abs(x);
        return 0.225 * (sin * Math.abs(sin) - sin) + sin;
    }

    public static double ultraFastCosDouble(final double x) {
        return ultraFastSinDouble(x + 1.5707963267948966);
    }

    public static float ultraFastSin(float x) {
        if (x < -3.14159265f) {
            x += 6.28318531f;
        } else if (x > 3.14159265f) {
            x -= 6.28318531f;
        }

        float sin = 1.27323954f * x + 0.405284735f * x * Math.abs(x);
        return 0.225f * (sin * Math.abs(sin) - sin) + sin;
    }

    public static float ultraFastCos(float x) {
        return ultraFastSin(x + 1.57079632f);
    }


    /**
     * Fast sine approximation.
     * Error ≈ 0.0004 — invisible in animations.
     * ~3x faster than Math.sin().
     */
    public static double fastSin(double x) {
        x = wrapAngle(x);

        // Bhaskara-Fast polynomial
        final double B = 4 / Math.PI;
        final double C = -4 / (Math.PI * Math.PI);

        double y = B * x + C * x * Math.abs(x);

        // Small correction improving accuracy
        final double P = 0.225;
        return P * (y * Math.abs(y) - y) + y;
    }

    /**
     * Fast cosine based on fastSin(x + π/2).
     */
    public static double fastCos(double x) {
        return fastSin(x + Math.PI * 0.5);
    }

    public static float fastSqrt(final float value) {
        if (value <= 0f) {
            return 0f;
        }

        float xHalf = 0.5f * value;
        int bits = Float.floatToIntBits(value);
        bits = 0x1fbd1df5 + (bits >> 1);
        float estimate = Float.intBitsToFloat(bits);

        estimate = estimate * (1.5f - xHalf * estimate * estimate);
        return value * estimate;
    }

    public static double fastSqrtDouble(final double x) {
        if (x <= 0.0) {
            return 0.0;
        }

        long bits = Double.doubleToLongBits(x);
        bits = (bits >> 1) + 0x1ff8000000000000L;
        double y = Double.longBitsToDouble(bits);

        y = 0.5 * (y + x / y);
        return y;
    }

    public static double distanceSq(
            final double x1,
            final double y1,
            final double z1,
            final double x2,
            final double y2,
            final double z2
    ) {
        final double dx = x1 - x2;
        final double dy = y1 - y2;
        final double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    public static float distanceSq2D(
            final float x1,
            final float z1,
            final float x2,
            final float z2
    ) {
        final float dx = x1 - x2;
        final float dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    public static double distance(
            final double x1,
            final double y1,
            final double z1,
            final double x2,
            final double y2,
            final double z2
    ) {
        return fastSqrtDouble(distanceSq(x1, y1, z1, x2, y2, z2));
    }

    public static double lerpAngle(final double a, final double b, final double t) {
        double diff = (b - a) % 360.0;
        if (diff < -180.0) {
            diff += 360.0;
        } else if (diff > 180.0) {
            diff -= 360.0;
        }

        return a + diff * t;
    }

    public static float lerp(final float start, final float end, final float t) {
        return start + (end - start) * t;
    }

    public static double lerpDouble(final double start, final double end, final double t) {
        return start + (end - start) * t;
    }

    public static double smoothDouble(final double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static float smoothStep(final float t) {
        return t * t * (3f - 2f * t);
    }

    public static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double clampDouble(final double value, final double min, final double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static void normalize2D(final float @NotNull [] vec) {
        final float x = vec[0];
        final float z = vec[1];
        final float lengthSq = x * x + z * z;

        if (lengthSq <= 0f) {
            vec[0] = 0f;
            vec[1] = 0f;
            return;
        }

        final float length = fastSqrt(lengthSq);
        vec[0] = x / length;
        vec[1] = z / length;
    }

    public static double fastAtan2Double(final double y, final double x) {
        final double absY = Math.abs(y) + 1e-20;

        double angle;
        double r;

        if (x >= 0.0) {
            r = (x - absY) / (x + absY);
            final double correction = (0.1963 * r * r - 0.9817) * r;
            angle = 0.7853981633974483 + correction;
        } else {
            r = (x + absY) / (absY - x);
            final double correction = (0.1963 * r * r - 0.9817) * r;
            angle = 2.356194490192345 + correction;
        }

        return y < 0.0 ? -angle : angle;
    }

    public static float fastAtan2(final float y, final float x) {
        final float absY = Math.abs(y) + 1e-10f;
        float r;
        float angle;

        if (x >= 0f) {
            r = (x - absY) / (x + absY);
            angle = 0.78539816339f;
        } else {
            r = (x + absY) / (absY - x);
            angle = 2.35619449019f;
        }

        angle += (0.1963f * r * r - 0.9817f) * r;
        return y < 0f ? -angle : angle;
    }

    public static float oscillate(final float time, final float amplitude, final float speed) {
        return (float) Math.sin(time * speed) * amplitude;
    }

    public static float circularOffsetX(final float angle, final float radius) {
        return (float) Math.cos(angle) * radius;
    }

    public static float circularOffsetZ(final float angle, final float radius) {
        return (float) Math.sin(angle) * radius;
    }
}