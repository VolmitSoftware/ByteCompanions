package com.volmit.bytecompanions.platform.paper.integration;

import com.volmit.bytecompanions.features.attributes.AttributesFeature;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.cache.PlayerBuffCache;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.manager.CooldownManager;
import com.volmit.bytecompanions.features.buffs.service.BuffService;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;
import com.volmit.bytecompanions.features.buffs.util.Operation;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BonusPolicyTest {
    @Test
    void denialBlocksCachedBuffWithoutConsumingCooldownAndCanResume() throws Exception {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getEntityId()).thenReturn(42);
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("value").set(2);
        node.node("operation").set(Operation.MULTIPLY.name());
        node.node("cooldown").set(1200);
        Buff buff = node.get(Buff.class);
        ResolvedBuff selected = new ResolvedBuff("bee", 0, buff);
        PlayerBuffCache cache = new PlayerBuffCache();
        cache.refresh(player, Map.of(BuffKey.EXPERIENCE_BLOCK, selected));
        CooldownManager cooldowns = new CooldownManager();
        BuffService service = new BuffService(cache, cooldowns, null);
        AtomicBoolean allowed = new AtomicBoolean(false);
        service.bonusesAllowed(ignored -> allowed.get());
        assertTrue(service.peek(player, BuffKey.EXPERIENCE_BLOCK).isEmpty());
        assertTrue(service.resolve(player, BuffKey.EXPERIENCE_BLOCK).isEmpty());
        assertEquals(10, service.apply(player, BuffKey.EXPERIENCE_BLOCK, selected, 10));
        assertEquals(0, cooldowns.remainingMillis(42, BuffKey.EXPERIENCE_BLOCK, 1200));
        assertEquals(selected, cache.get(player, BuffKey.EXPERIENCE_BLOCK).orElseThrow());
        allowed.set(true);
        assertEquals(selected, service.resolve(player, BuffKey.EXPERIENCE_BLOCK).orElseThrow());
        assertEquals(20, service.apply(player, BuffKey.EXPERIENCE_BLOCK, selected, 10));
        assertFalse(service.resolve(player, BuffKey.EXPERIENCE_BLOCK).isPresent());
    }

    @Test
    void attributeEquipCannotApplyWhileDenied() throws Exception {
        AttributesFeature feature = new AttributesFeature(null, null, null);
        Field active = AttributesFeature.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(feature, true);
        feature.bonusesAllowed(player -> false);
        Player player = mock(Player.class);
        feature.applyAttributes(player, "bee");
        verifyNoInteractions(player);
    }
}
