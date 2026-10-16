package com.volmit.bytecompanions.features.buffs.util;

public enum Operation {

    SUM {
        @Override
        public int apply(final int base, final double value) {
            return (int) (base + value);
        }
    },

    MULTIPLY {
        @Override
        public int apply(final int base, final double value) {
            return (int) (base * value);
        }
    },

    ADD_PERCENT {
        @Override
        public int apply(final int base, final double value) {
            return (int) (base + (base * (value / 100.0)));
        }
    };

    public abstract int apply(final int base, final double value);

}
