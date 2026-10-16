package com.volmit.bytecompanions.platform.paper.bonus;

import com.volmit.bytecompanions.features.buffs.util.Operation;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

public final class BonusValues {
    private BonusValues() {
    }

    public static double add(double base, double addition) {
        double result = base + addition;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Effective bonus must be finite");
        }
        return result;
    }

    public static int amplifier(int base, double addition) {
        return (int) Math.clamp(add(base, addition), 0, 255);
    }

    public static int apply(Operation operation, int base, double value, double boosterPercent, double maximumMultiplier) {
        return apply(operation, base, value, boosterPercent, maximumMultiplier, () -> ThreadLocalRandom.current().nextDouble());
    }

    static int apply(Operation operation, int base, double value, double boosterPercent, double maximumMultiplier, DoubleSupplier random) {
        if (boosterPercent == 0 && maximumMultiplier == Double.MAX_VALUE) {
            return operation.apply(base, value);
        }
        int maximum = (int) Math.min(Integer.MAX_VALUE, Math.max(0, base) * maximumMultiplier);
        if (boosterPercent == 0) {
            return Math.clamp(operation.apply(base, value), 0, maximum);
        }
        double result = switch (operation) {
            case SUM -> base + value;
            case MULTIPLY -> base * value;
            case ADD_PERCENT -> base + base * value / 100;
        };
        result += base * boosterPercent / 100;
        double bounded = Math.clamp(result, 0, maximum);
        int whole = (int) bounded;
        return whole < maximum && random.getAsDouble() < bounded - whole ? whole + 1 : whole;
    }
}
