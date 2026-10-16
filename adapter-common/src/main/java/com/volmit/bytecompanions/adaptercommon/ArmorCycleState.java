package com.volmit.bytecompanions.adaptercommon;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.core.companion.ArmorCycleEntry;

import java.util.List;

final class ArmorCycleState {

    private final ItemStackProvider provider;
    private final List<ArmorCycleEntry> frames;
    private final int intervalTicks;

    private int currentFrameIndex;
    private int ticksRemaining;

    ArmorCycleState(final ItemStackProvider provider, final @NotNull ArmorCycle cycle) {
        this.provider = provider;
        this.frames = cycle.frames();
        this.intervalTicks = cycle.intervalTicks();
        this.ticksRemaining = this.intervalTicks;
    }

    boolean tick() {
        if (--this.ticksRemaining > 0) {
            return false;
        }

        this.ticksRemaining = this.intervalTicks;
        this.currentFrameIndex = (this.currentFrameIndex + 1) % this.frames.size();
        return true;
    }

    ArmorCycleEntry currentFrame() {
        return this.frames.get(this.currentFrameIndex);
    }

    ItemStackProvider provider() {
        return this.provider;
    }
}