package com.volmit.bytecompanions.platform.paper.integration;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerPolicyTest {
    @Test
    void worldRulesKeepVisualsAndBonusesIndependent() {
        ServerPolicy policy = new ServerPolicy(Set.of("lobby"), Set.of("arena"), player -> ServerPolicy.Decision.ALLOW);
        assertEquals(new ServerPolicy.Decision(false, true), policy.evaluate(player("lobby")));
        assertEquals(new ServerPolicy.Decision(true, false), policy.evaluate(player("arena")));
        assertEquals(ServerPolicy.Decision.ALLOW, policy.evaluate(player("survival")));
    }

    @Test
    void regionAllowCannotOverrideWorldDenial() {
        ServerPolicy policy = new ServerPolicy(Set.of("arena"), Set.of(), player -> new ServerPolicy.Decision(true, false));
        assertEquals(ServerPolicy.Decision.DENY, policy.evaluate(player("arena")));
        assertEquals(new ServerPolicy.Decision(true, false), policy.evaluate(player("survival")));
    }

    @Test
    void configurationIsCopiedAndFullWorldDenialSkipsRegionWork() {
        Set<String> denied = new HashSet<>(Set.of("arena"));
        AtomicInteger calls = new AtomicInteger();
        ServerPolicy policy = new ServerPolicy(denied, denied, player -> {
            calls.incrementAndGet();
            return ServerPolicy.Decision.ALLOW;
        });
        denied.clear();
        assertEquals(ServerPolicy.Decision.DENY, policy.evaluate(player("arena")));
        assertEquals(0, calls.get());
        assertEquals(ServerPolicy.Decision.ALLOW, policy.evaluate(player("Arena")));
        assertEquals(1, calls.get());
    }

    private static Player player(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
