package com.volmit.bytecompanions.features.buffs;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuffPlatformSupportTest {
    @Test
    void spigotRejectsGeneralPickupBonusWithActionableLocation() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("companions", "bee", "vanilla", "experience", "enabled").set(true);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BuffPlatformSupport.validateNode(node, false));
        assertTrue(error.getMessage().contains("companions.bee.vanilla.experience.enabled"));
        assertTrue(error.getMessage().contains("Set it to false on Spigot"));
    }

    @Test
    void spigotRetainsAllFourSpecificExperienceBonuses() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("companions", "bee", "vanilla", "experience", "enabled").set(false);
        for (String kind : new String[]{"block", "mob", "fishing", "furnace"}) {
            node.node("companions", "bee", "vanilla", "experience-" + kind, "enabled").set(true);
        }
        assertDoesNotThrow(() -> BuffPlatformSupport.validateNode(node, false));
    }

    @Test
    void paperRetainsGeneralAndSpecificExperienceBonuses() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("companions", "bee", "vanilla", "experience", "enabled").set(true);
        node.node("companions", "bee", "vanilla", "experience-block", "enabled").set(true);
        assertDoesNotThrow(() -> BuffPlatformSupport.validateNode(node, true));
    }

    @Test
    void spigotAcceptsExplicitlyDisabledGeneralExperience() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("companions", "bee", "vanilla", "experience", "enabled").set(false);
        assertDoesNotThrow(() -> BuffPlatformSupport.validateNode(node, false));
    }

    @Test
    void spigotRejectsAnImplicitlyEnabledGeneralExperienceDefault() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("companions", "bee", "vanilla", "experience-block", "enabled").set(true);
        assertThrows(IllegalArgumentException.class, () -> BuffPlatformSupport.validateNode(node, false));
    }
}
