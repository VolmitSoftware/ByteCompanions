package com.volmit.bytecompanions.platform.paper.integration;

import com.volmit.bytecompanions.platform.paper.collection.ViewerPreferences;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionViewerService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeServerRulesTest {
    @Test
    void audienceCombinesBothOwnersPolicyWithViewerPreferencesAndServerParticleCap() {
        Player viewer = player("survival");
        Player owner = player("survival");
        UUID ownerId = owner.getUniqueId();
        Map<UUID, ServerPolicy.Decision> decisions = new HashMap<>();
        AtomicReference<ViewerPreferences> preference = new AtomicReference<>(ViewerPreferences.defaults());
        ServerPolicy policy = new ServerPolicy(Set.of(), Set.of(),
                player -> decisions.getOrDefault(player.getUniqueId(), ServerPolicy.Decision.ALLOW));
        RuntimeServerRules rules = new RuntimeServerRules(policy, ViewerPreferences.ParticleIntensity.REDUCED,
                ignored -> preference.get(), ignored -> { });
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(viewer, ownerId));
        rules.refresh(viewer);
        rules.refresh(owner);
        assertEquals(new CompanionViewerService.Audience(true, 2), rules.audience(viewer, ownerId));
        decisions.put(ownerId, new ServerPolicy.Decision(false, true));
        rules.refresh(owner);
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(viewer, ownerId));
        decisions.clear();
        rules.refresh(owner);
        preference.set(new ViewerPreferences(ViewerPreferences.Visibility.OWN, ViewerPreferences.ParticleIntensity.FULL));
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(viewer, ownerId));
        assertEquals(new CompanionViewerService.Audience(true, 2), rules.audience(viewer, viewer.getUniqueId()));
        preference.set(new ViewerPreferences(ViewerPreferences.Visibility.ALL, ViewerPreferences.ParticleIntensity.OFF));
        assertEquals(new CompanionViewerService.Audience(true, 0), rules.audience(viewer, ownerId));
        decisions.put(viewer.getUniqueId(), new ServerPolicy.Decision(false, true));
        rules.refresh(viewer);
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(viewer, ownerId));
        rules.close();
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(viewer, ownerId));
    }

    @Test
    void worldChangesApplyImmediatelyAndRespawnRetiresTheBonusSnapshot() {
        Player player = player("survival");
        UUID id = player.getUniqueId();
        ServerPolicy policy = new ServerPolicy(Set.of("arena"), Set.of("arena"), ignored -> ServerPolicy.Decision.ALLOW);
        RuntimeServerRules rules = new RuntimeServerRules(policy, ViewerPreferences.ParticleIntensity.FULL,
                ignored -> ViewerPreferences.defaults(), ignored -> { });
        rules.refresh(player);
        World oldWorld = player.getWorld();
        World arena = mock(World.class);
        when(arena.getName()).thenReturn("arena");
        when(player.getWorld()).thenReturn(arena);
        rules.onWorldChange(new PlayerChangedWorldEvent(player, oldWorld));
        assertFalse(rules.bonusesAllowed(id));
        assertEquals(new CompanionViewerService.Audience(false, 0), rules.audience(player, null));
        when(player.getWorld()).thenReturn(oldWorld);
        rules.onWorldChange(new PlayerChangedWorldEvent(player, arena));
        assertTrue(rules.bonusesAllowed(id));
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);
        rules.onRespawn(respawn);
        assertFalse(rules.bonusesAllowed(id));
        rules.refresh(player);
        assertTrue(rules.bonusesAllowed(id));
    }

    @Test
    void foreignOwnerBonusReadsUsePublishedStateWithoutTouchingPlayerOrWorld() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("survival");
        AtomicReference<ServerPolicy.Decision> decision = new AtomicReference<>(ServerPolicy.Decision.ALLOW);
        AtomicInteger refreshes = new AtomicInteger();
        ServerPolicy policy = new ServerPolicy(Set.of(), Set.of(), ignored -> decision.get());
        RuntimeServerRules rules = new RuntimeServerRules(policy, ViewerPreferences.ParticleIntensity.FULL,
                ignored -> ViewerPreferences.defaults(), ignored -> refreshes.incrementAndGet());
        assertFalse(rules.bonusesAllowed(id));
        rules.refresh(player);
        clearInvocations(player, world);
        assertTrue(rules.bonusesAllowed(id));
        verifyNoInteractions(player, world);
        rules.refresh(player);
        assertEquals(0, refreshes.get());
        decision.set(new ServerPolicy.Decision(true, false));
        rules.refresh(player);
        assertFalse(rules.bonusesAllowed(id));
        assertEquals(1, refreshes.get());
        rules.refresh(player);
        assertEquals(1, refreshes.get());
        decision.set(ServerPolicy.Decision.ALLOW);
        rules.refresh(player);
        assertTrue(rules.bonusesAllowed(id));
        assertEquals(2, refreshes.get());
        rules.onQuit(new PlayerQuitEvent(player, (String) null));
        assertFalse(rules.bonusesAllowed(id));
        rules.refresh(player);
        rules.close();
        assertFalse(rules.bonusesAllowed(id));
        clearInvocations(player, world);
        rules.refresh(player);
        assertFalse(rules.bonusesAllowed(id));
        verifyNoInteractions(player, world);
    }

    private static Player player(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
