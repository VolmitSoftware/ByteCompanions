package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ServerPolishFixture {
    private ServerPolishFixture() {}

    static String execute(Plugin plugin, Player player, String[] arguments) {
        if (arguments[0].equals("polish")) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("world", player.getWorld().getName());
            state.put("attackDamage", player.getAttribute(Attribute.ATTACK_DAMAGE).getValue());
            state.put("armor", player.getAttribute(Attribute.ARMOR).getValue());
            state.put("movementSpeed", player.getAttribute(Attribute.MOVEMENT_SPEED).getValue());
            state.put("effects", player.getActivePotionEffects().stream()
                    .map(effect -> effect.getType().getKey().toString() + ":" + effect.getAmplifier()).toList());
            return "BCQA POLISH " + new Gson().toJson(state);
        }
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: /bcqa privacy hide|show <uuid> or vanish true|false");
        if (arguments[1].equals("overlay")) {
            Companion companion = ByteCompanions.getAPI().getCompanionPlayerOrNull(player).companions().get("bee");
            if (companion == null) throw new IllegalArgumentException("Equip bee first");
            switch (arguments[2]) {
                case "add" -> companion.addOverlay("qa-privacy", new OverlayData(false,
                        Map.of(EquipmentSlot.HEAD, () -> new ItemStack(Material.GLASS))));
                case "remove" -> companion.removeOverlay("qa-privacy");
                default -> throw new IllegalArgumentException("Expected add or remove");
            }
        } else if (arguments[1].equals("vanish")) {
            if (!arguments[2].equals("true") && !arguments[2].equals("false")) throw new IllegalArgumentException("Expected true or false");
            player.setMetadata("vanished", new FixedMetadataValue(plugin, Boolean.parseBoolean(arguments[2])));
        } else {
            Player target = Bukkit.getPlayer(UUID.fromString(arguments[2]));
            if (target == null) throw new IllegalArgumentException("Target must be online");
            switch (arguments[1]) {
                case "hide" -> player.hidePlayer(plugin, target);
                case "show" -> player.showPlayer(plugin, target);
                default -> throw new IllegalArgumentException("Expected hide, show or vanish");
            }
        }
        return "BCQA PRIVACY " + arguments[1] + " " + arguments[2];
    }
}
