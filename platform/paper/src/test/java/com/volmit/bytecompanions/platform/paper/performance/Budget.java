package com.volmit.bytecompanions.platform.paper.performance;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Budget {
    private Budget() {
    }

    public static long medianNanos(int warmupRounds, int measuredRounds, Runnable round) {
        for (int index = 0; index < warmupRounds; index++) {
            round.run();
        }
        long[] samples = new long[measuredRounds];
        for (int index = 0; index < measuredRounds; index++) {
            long started = System.nanoTime();
            round.run();
            samples[index] = System.nanoTime() - started;
        }
        return median(samples);
    }

    public static long median(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public static void assertWithin(String hotPath, long medianRoundNanos, int unitsPerRound, long budgetNanosPerUnit) {
        long perUnit = medianRoundNanos / unitsPerRound;
        System.out.println("budget " + hotPath + ": median " + perUnit + " ns per unit, budget " + budgetNanosPerUnit + " ns");
        assertTrue(perUnit <= budgetNanosPerUnit, () -> hotPath + ": median " + perUnit + " ns per unit exceeds the "
                + budgetNanosPerUnit + " ns budget (" + medianRoundNanos + " ns per round over " + unitsPerRound + " units)");
    }
}
