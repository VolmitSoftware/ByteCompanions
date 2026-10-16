package com.volmit.bytecompanions.platform.paper.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

final class ProgressionActivityCooldowns {
    private final Map<UUID, Map<String, Long>> players = new HashMap<>();
    private final LongSupplier nanoTime;

    ProgressionActivityCooldowns(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    synchronized boolean accept(UUID playerId, String source, long cooldownMillis) {
        long now = this.nanoTime.getAsLong();
        Map<String, Long> times = this.players.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Long previous = times.get(source);
        if (previous != null && now - previous < cooldownMillis * 1_000_000L) {
            return false;
        }
        times.put(source, now);
        return true;
    }

    synchronized void remove(UUID playerId) {
        this.players.remove(playerId);
    }

    synchronized void clear() {
        this.players.clear();
    }
}
