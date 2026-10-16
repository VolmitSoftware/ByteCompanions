package com.volmit.bytecompanions.platform.paper.integration.placeholderapi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

final class PlaceholderApiBridge {
    private final Plugin plugin;
    private final PlaceholderRegistration registration;

    PlaceholderApiBridge(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registration = new PlaceholderRegistration(plugin.getLogger());
    }

    boolean register(CompanionPlaceholders placeholders) {
        return this.registration.register(() -> new CompanionPlaceholderExpansion(this.plugin, placeholders));
    }

    void unregister() {
        this.registration.unregister();
    }

    boolean registered() {
        return this.registration.isRegistered();
    }
}
