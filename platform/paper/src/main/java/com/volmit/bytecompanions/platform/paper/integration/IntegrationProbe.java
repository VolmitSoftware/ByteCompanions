package com.volmit.bytecompanions.platform.paper.integration;

import org.bukkit.Bukkit;

public final class IntegrationProbe {
    private IntegrationProbe() {
    }

    public static boolean available(String pluginName, String entryType) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return false;
            }
            Class.forName(entryType, false, IntegrationProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException failure) {
            return false;
        }
    }

    public static boolean classAvailable(String entryType) {
        try {
            Class.forName(entryType, false, IntegrationProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError failure) {
            return false;
        }
    }
}
