package com.volmit.bytecompanions.qa;

import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class NexoFixture {
    private static final String TYPE = "qa_nexo";
    private static final String ITEM = "bytecompanions_qa";

    private NexoFixture() {
    }

    public static String inspect(Player player, String[] arguments) throws ReflectiveOperationException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Nexo");
        if (arguments.length != 2 || plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("An enabled genuine Nexo plugin and explicit inspection mode are required");
        }
        Class<?> items = Class.forName("com.nexomc.nexo.api.NexoItems", true, plugin.getClass().getClassLoader());
        Object builder = items.getMethod("itemFromId", String.class).invoke(null, ITEM);
        if (builder == null) {
            return "BCQA NEXO LOADING";
        }
        ItemStack expected = (ItemStack) builder.getClass().getMethod("build").invoke(builder);
        if (expected.getType().isAir() || !ITEM.equals(items.getMethod("idFromItem", ItemStack.class).invoke(null, expected))) {
            throw new IllegalStateException("Genuine Nexo reference item is invalid");
        }
        ByteCompanions api = ByteCompanions.getAPI();
        CompanionType type = api.getCompanionType(TYPE).orElseThrow();
        return switch (arguments[1]) {
            case "provider" -> {
                requireItem(type.equipment().get(EquipmentSlot.HEAD).provide(), expected, items);
                yield "BCQA NEXO PROVIDER PASS id=" + ITEM + " material=" + expected.getType();
            }
            case "invalid" -> {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.FEET}) {
                    ItemStackProvider provider = type.equipment().get(slot);
                    if (provider == null || !provider.provide().getType().isAir()) {
                        throw new IllegalStateException("Unknown or empty Nexo IDs must return AIR: " + slot);
                    }
                }
                yield "BCQA NEXO INVALID PASS unknown=AIR empty=AIR";
            }
            case "equipped" -> {
                CompanionPlayer owner = api.getCompanionPlayerOrNull(player);
                if (owner == null) {
                    yield "BCQA NEXO LOADING";
                }
                Companion companion = owner.companions().get(TYPE);
                if (companion == null || !companion.isSpawned()) {
                    throw new IllegalStateException("Configured Nexo companion is not spawned");
                }
                requireItem(companion.equipment().get(EquipmentSlot.HEAD), expected, items);
                yield "BCQA NEXO EQUIPPED PASS id=" + ITEM + " material=" + expected.getType();
            }
            default -> throw new IllegalArgumentException("Unknown Nexo inspection mode");
        };
    }

    private static void requireItem(ItemStack actual, ItemStack expected, Class<?> items) throws ReflectiveOperationException {
        if (actual == null || actual.getAmount() != expected.getAmount() || !actual.isSimilar(expected)) {
            throw new IllegalStateException("ByteCompanions item metadata differs from genuine Nexo output");
        }
        if (!ITEM.equals(items.getMethod("idFromItem", ItemStack.class).invoke(null, actual))) {
            throw new IllegalStateException("ByteCompanions item lost its Nexo identity");
        }
    }
}
