package com.volmit.bytecompanions.platform.paper.bonus;

import com.volmit.bytecompanions.features.buffs.util.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BonusValuesTest {
    @Test
    void fractionalBoostsHaveProportionalBenefitWithoutCrossEventCarry() {
        int total = 0;
        for (double sample : new double[]{0, 0.25, 0.5, 0.75}) {
            total += BonusValues.apply(Operation.SUM, 1, 0, 25, 10, () -> sample);
        }
        assertEquals(5, total);
        assertEquals(2, BonusValues.apply(Operation.SUM, 1, 0, 25, 10, () -> 0.249));
        assertEquals(1, BonusValues.apply(Operation.SUM, 1, 0, 25, 10, () -> 0.25));
        assertEquals(1, BonusValues.apply(Operation.SUM, 1, 0, 25, 1.25, () -> 0));
        assertEquals(0, BonusValues.apply(Operation.SUM, 0, 0, 25, 10, () -> 0));
    }

    @Test
    void noActiveBoosterPreservesExistingRoundingWithinCap() {
        for (Operation operation : Operation.values()) {
            assertEquals(operation.apply(7, 1.25), BonusValues.apply(operation, 7, 1.25, 0, 10,
                    () -> { throw new AssertionError("Randomness is unnecessary without a booster"); }));
        }
    }

    @Test
    void personalAndServerBoostsAddToOriginalReward() {
        assertEquals(185, BonusValues.apply(Operation.ADD_PERCENT, 100, 10, 75, 10));
        assertEquals(185, BonusValues.apply(Operation.MULTIPLY, 100, 1.1, 75, 10));
        assertEquals(180, BonusValues.apply(Operation.SUM, 100, 5, 75, 10));
        assertEquals(0, BonusValues.apply(Operation.MULTIPLY, 0, 2, 75, 10));
    }

    @Test
    void adjustedRewardsStayWithinConfiguredAndIntegerCaps() {
        assertEquals(200, BonusValues.apply(Operation.MULTIPLY, 100, 50, 100, 2));
        assertEquals(200, BonusValues.apply(Operation.MULTIPLY, 100, 50, 0, 2));
        assertEquals(Integer.MAX_VALUE, BonusValues.apply(Operation.MULTIPLY, Integer.MAX_VALUE, 2, 100, 10));
        assertEquals(0, BonusValues.apply(Operation.SUM, 10, -100, 10, 10));
    }

    @Test
    void disabledProgressionPreservesExistingOperationRoundingAndNegativeValues() {
        for (Operation operation : Operation.values()) {
            for (double value : new double[]{-2, 0, 1.25, 33.3}) {
                assertEquals(operation.apply(7, value), BonusValues.apply(operation, 7, value, 0, Double.MAX_VALUE));
            }
        }
    }

    @Test
    void upgradesUseAdditiveValuesAndBoundPotionAmplifiers() {
        assertEquals(1.3, BonusValues.add(1.1, 0.2));
        assertEquals(4, BonusValues.amplifier(2, 2.9));
        assertEquals(255, BonusValues.amplifier(254, 10));
        assertEquals(0, BonusValues.amplifier(2, -10));
        assertThrows(IllegalArgumentException.class, () -> BonusValues.add(Double.MAX_VALUE, Double.MAX_VALUE));
    }
}
