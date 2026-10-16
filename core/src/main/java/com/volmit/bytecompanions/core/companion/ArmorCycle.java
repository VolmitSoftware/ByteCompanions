package com.volmit.bytecompanions.core.companion;

import java.util.List;

public record ArmorCycle(
        int intervalTicks,
        List<ArmorCycleEntry> frames
) {
}
