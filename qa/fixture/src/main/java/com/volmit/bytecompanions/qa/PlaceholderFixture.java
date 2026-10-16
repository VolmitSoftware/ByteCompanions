package com.volmit.bytecompanions.qa;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PlaceholderFixture {
    private PlaceholderFixture() {
    }

    public static String parse(Player player, String placeholder) {
        return "BCQA PAPI [" + PlaceholderAPI.setPlaceholders(player, placeholder) + "]";
    }
}
