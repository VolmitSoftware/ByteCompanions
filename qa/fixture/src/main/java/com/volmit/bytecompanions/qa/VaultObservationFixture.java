package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.LinkedHashMap;
import java.util.Map;

final class VaultObservationFixture {
    private VaultObservationFixture() {
    }

    static String inspect(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        result.put("vaultPresent", vault != null && vault.isEnabled());
        if (vault == null || !vault.isEnabled()) {
            return "BCQA VAULT " + new Gson().toJson(result);
        }
        result.put("vaultVersion", vault.getDescription().getVersion());
        result.put("vaultMain", vault.getDescription().getMain());
        try {
            Class<?> api = Class.forName("net.milkbowl.vault.economy.Economy", false, vault.getClass().getClassLoader());
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(api);
            result.put("providerPresent", registration != null);
            if (registration != null) {
                Object provider = registration.getProvider();
                result.put("providerOwner", registration.getPlugin().getName());
                result.put("providerVersion", registration.getPlugin().getDescription().getVersion());
                result.put("providerClass", provider.getClass().getName());
                result.put("priority", registration.getPriority().name());
                result.put("name", api.getMethod("getName").invoke(provider));
                result.put("enabled", api.getMethod("isEnabled").invoke(provider));
                result.put("hasAccount", api.getMethod("hasAccount", OfflinePlayer.class).invoke(provider, player));
                result.put("balance", api.getMethod("getBalance", OfflinePlayer.class).invoke(provider, player));
                result.put("apiIdentity", api.isInstance(provider));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot observe the installed Vault economy provider", failure);
        }
        return "BCQA VAULT " + new Gson().toJson(result);
    }
}
