package com.volmit.bytecompanions.features.buffs;

import org.bukkit.Bukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Configuration;

import java.util.Map;
import java.util.Objects;

public final class BuffPlatformSupport {
    private BuffPlatformSupport() {
    }

    public static boolean supportsPickupExperience() {
        try {
            ClassLoader loader = Bukkit.class.getClassLoader();
            Class.forName("com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent", false, loader);
            Class.forName("org.bukkit.entity.ExperienceOrb$SpawnReason", false, loader);
            return true;
        } catch (ClassNotFoundException unavailable) {
            return false;
        }
    }

    public static void validate(Configuration configuration, boolean pickupExperienceSupported) {
        if (pickupExperienceSupported) {
            return;
        }
        for (Map.Entry<String, Companions> entry : configuration.companions().entrySet()) {
            if (entry.getValue().vanilla().experience().enabled()) {
                throw new IllegalArgumentException("features/buffs/config.toml: companions."
                        + entry.getKey() + ".vanilla.experience.enabled requires Paper's experience pickup and origin API. "
                        + "Set it to false on Spigot; experience-block, experience-mob, experience-fishing and "
                        + "experience-furnace remain available.");
            }
        }
    }

    public static void validateNode(ConfigurationNode node, boolean pickupExperienceSupported) throws SerializationException {
        validate(Objects.requireNonNull(node.get(Configuration.class), "Buffs configuration is empty"),
                pickupExperienceSupported);
    }
}
