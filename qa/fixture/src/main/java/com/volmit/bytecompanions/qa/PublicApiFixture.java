package com.volmit.bytecompanions.qa;

import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.access.ByteCompanionsProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.api.util.response.Response;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PublicApiFixture {
    private PublicApiFixture() {
    }

    public static String execute(Player player, String[] arguments) {
        ByteCompanions api = ByteCompanions.getAPI();
        if (!ByteCompanionsProvider.hasInstance() || ByteCompanionsProvider.getInstance() != api) {
            throw new IllegalStateException("Public API provider identity differs");
        }
        CompanionPlayer owner = api.getCompanionPlayerOrNull(player);
        if (owner == null) {
            return "BCQA API LOADING";
        }
        if (owner != api.getCompanionPlayerOrNull(player.getUniqueId())
                || owner != api.getCompanionPlayer(player).orElseThrow()
                || owner != api.getCompanionPlayer(player.getUniqueId()).orElseThrow()
                || !owner.playerId().equals(player.getUniqueId())) {
            throw new IllegalStateException("Public player lookup identity differs");
        }
        String action = arguments.length > 1 ? arguments[1] : "inspect";
        switch (action) {
            case "equip" -> {
                requireId(arguments);
                CompanionType type = api.getCompanionType(arguments[2]).orElseThrow();
                if (type != api.getCompanionTypeOrNull(arguments[2])
                        || type != api.typeRegistry().get(arguments[2]).orElseThrow()) {
                    throw new IllegalStateException("Public type lookup identity differs");
                }
                Response<?> result = api.companionManager().equip(player, type);
                return "BCQA API EQUIP success=" + result.success() + " result=" + result.result();
            }
            case "unequip" -> {
                requireId(arguments);
                Response<?> result = api.companionManager().unequip(player, arguments[2]);
                return "BCQA API UNEQUIP success=" + result.success() + " result=" + result.result();
            }
            case "hide" -> api.companionManager().unequipVisual(player);
            case "show" -> api.companionManager().equipVisual(player);
            case "inspect" -> {
            }
            default -> throw new IllegalArgumentException("Unknown public API fixture action");
        }
        if (api.configurationSettings() == null || api.animationRegistry() == null) {
            throw new IllegalStateException("Public API service unavailable");
        }
        List<String> selected = new ArrayList<>(owner.companions().keySet());
        Collections.sort(selected);
        int spawned = 0;
        for (Companion companion : owner.companions().values()) {
            if (!owner.hasCompanion(companion.type().id()) || !companion.hasOwner()) {
                throw new IllegalStateException("Public companion selection differs");
            }
            if (companion.isSpawned()) {
                spawned++;
            }
        }
        if (owner.companionsActive() != selected.size() || owner.hasCompanion() != !selected.isEmpty()) {
            throw new IllegalStateException("Public selection totals differ");
        }
        return "BCQA API PASS selected=" + String.join(",", selected) + " count=" + selected.size()
                + " spawned=" + spawned;
    }

    private static void requireId(String[] arguments) {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("An explicit companion ID is required");
        }
    }
}
