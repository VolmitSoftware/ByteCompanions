package com.volmit.bytecompanions.features.particles.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public class BukkitUtil {

    private BukkitUtil() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull Optional<Player> getPlayer(final UUID uuid) {
        return Optional.ofNullable(Bukkit.getPlayer(uuid));
    }

}
