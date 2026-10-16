package com.volmit.bytecompanions.platform.paper.integration.placeholderapi;

import com.volmit.bytecompanions.platform.paper.integration.IntegrationProbe;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class PlaceholderApiInstaller implements Listener {
    private static final String HOST = "PlaceholderAPI";
    private static final String ENTRY_TYPE = "me.clip.placeholderapi.expansion.PlaceholderExpansion";

    private final Plugin plugin;
    private volatile PlaceholderApiBridge bridge;
    private volatile CompanionPlaceholders placeholders;

    public PlaceholderApiInstaller(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public static boolean hostAvailable() {
        return IntegrationProbe.available(HOST, ENTRY_TYPE);
    }

    public void attach(CompanionPlaceholders placeholders) {
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.register();
    }

    public void detach() {
        PlaceholderApiBridge current = this.bridge;
        this.placeholders = null;
        if (current != null) {
            current.unregister();
        }
    }

    public String state() {
        PlaceholderApiBridge current = this.bridge;
        if (current != null && current.registered()) {
            return "ready";
        }
        return hostAvailable() ? "pending" : "absent";
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals(HOST)) {
            this.register();
        }
    }

    private synchronized void register() {
        CompanionPlaceholders current = this.placeholders;
        if (current == null || !hostAvailable()) {
            return;
        }
        if (this.bridge == null) {
            this.bridge = new PlaceholderApiBridge(this.plugin);
        }
        if (this.bridge.register(current)) {
            this.plugin.getLogger().info("PlaceholderAPI expansion " + CompanionPlaceholders.IDENTIFIER + " registered.");
        }
    }
}
