package com.volmit.bytecompanions.platform.paper.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionActivityCooldownsTest {
    @Test
    void repeatedActivityDoesNotPushBackTheNextEligibleReward() {
        AtomicLong clock = new AtomicLong();
        ProgressionActivityCooldowns cooldowns = new ProgressionActivityCooldowns(clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(cooldowns.accept(player, "block_break", 1000));
        clock.set(999_000_000);
        assertFalse(cooldowns.accept(player, "block_break", 1000));
        clock.set(1_000_000_000);
        assertTrue(cooldowns.accept(player, "block_break", 1000));
    }

    @Test
    void playersAndActivitySourcesRemainIndependentAndQuitClearsOnlyOnePlayer() {
        ProgressionActivityCooldowns cooldowns = new ProgressionActivityCooldowns(() -> 0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(cooldowns.accept(first, "block_break", 1000));
        assertTrue(cooldowns.accept(first, "fishing", 1000));
        assertTrue(cooldowns.accept(second, "block_break", 1000));
        cooldowns.remove(first);
        assertTrue(cooldowns.accept(first, "block_break", 1000));
        assertFalse(cooldowns.accept(second, "block_break", 1000));
        cooldowns.clear();
        assertTrue(cooldowns.accept(second, "block_break", 1000));
    }

    @Test
    void persistedBlockCoordinatesDistinguishNegativeHeightAndChunkEdges() {
        assertNotEquals(ProgressionGameplayListener.blockKey(0, -64, 0), ProgressionGameplayListener.blockKey(0, 64, 0));
        assertNotEquals(ProgressionGameplayListener.blockKey(-1, -64, 0), ProgressionGameplayListener.blockKey(0, -64, 0));
        assertNotEquals(ProgressionGameplayListener.blockKey(0, -64, -1), ProgressionGameplayListener.blockKey(0, -64, 0));
        assertEquals(ProgressionGameplayListener.blockKey(-1, -64, -1), ProgressionGameplayListener.blockKey(15, -64, 15));
    }
}
