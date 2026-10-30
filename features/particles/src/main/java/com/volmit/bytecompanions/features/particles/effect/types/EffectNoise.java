package com.volmit.bytecompanions.features.particles.effect.types;

final class EffectNoise {
    private EffectNoise() {
    }

    static double unit(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    static double signed(long seed) {
        return unit(seed) * 2.0 - 1.0;
    }

    static long seed(long frame, int point, int channel) {
        return frame * 1_000_003L + point * 7_919L + channel;
    }
}
