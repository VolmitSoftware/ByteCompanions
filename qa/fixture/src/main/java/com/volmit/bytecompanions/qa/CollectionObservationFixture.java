package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CollectionObservationFixture {
    private CollectionObservationFixture() {
    }

    static String inspect(Player player) {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ByteCompanions");
            Object service = plugin.getClass().getMethod("collectionService").invoke(plugin);
            Optional<?> profile = (Optional<?>) service.getClass().getMethod("profile", UUID.class).invoke(service, player.getUniqueId());
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("ready", profile.isPresent());
            state.put("profile", profile.orElse(null));
            Map<String, Object> companions = new LinkedHashMap<>();
            CompanionPlayer owner = ByteCompanions.getAPI().getCompanionPlayerOrNull(player);
            if (owner != null) {
                for (Map.Entry<String, Companion> entry : owner.companions().entrySet()) {
                    Companion companion = entry.getValue();
                    companions.put(entry.getKey(), Map.of("entityId", companion.entityId(),
                            "x", companion.currentX(), "y", companion.currentY(), "z", companion.currentZ(),
                            "mode", companion.behavior().mode().name()));
                }
            }
            state.put("companions", companions);
            return "BCQA COLLECTION " + new Gson().toJson(state);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect collection state", failure);
        }
    }
}
