package com.volmit.bytecompanions.platform.paper.integration.placeholderapi;

import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;
import org.bukkit.plugin.Plugin;

public final class CompanionPlaceholderExpansion extends VolmitPlaceholderExpansion {
    public CompanionPlaceholderExpansion(Plugin plugin, CompanionPlaceholders placeholders) {
        super(CompanionPlaceholders.IDENTIFIER, String.join(", ", plugin.getDescription().getAuthors()),
                plugin.getDescription().getVersion(), plugin.getDescription().getName(), placeholders.registry(), plugin.getLogger());
    }
}
