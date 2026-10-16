package com.volmit.bytecompanions.qa;

import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ItemsAdderFixture {
    private static final String TYPE = "qa_itemsadder";
    private static final String ITEM = "_iainternal:icon_left_blue";

    private ItemsAdderFixture() {
    }

    public static String inspect(Player player, String[] arguments) {
        if (arguments.length != 2 || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            throw new IllegalStateException("An enabled genuine ItemsAdder plugin and explicit inspection mode are required");
        }
        CustomStack custom = CustomStack.getInstance(ITEM);
        if (custom == null) {
            return "BCQA ITEMSADDER LOADING";
        }
        ItemStack expected = custom.getItemStack();
        if (expected.getType().isAir() || !ITEM.equals(custom.getNamespacedID())) {
            throw new IllegalStateException("Genuine ItemsAdder reference item is invalid");
        }
        ByteCompanions api = ByteCompanions.getAPI();
        CompanionType type = api.getCompanionType(TYPE).orElseThrow();
        return switch (arguments[1]) {
            case "provider" -> {
                requireItem(type.equipment().get(EquipmentSlot.HEAD).provide(), expected);
                yield "BCQA ITEMSADDER PROVIDER PASS namespace=" + ITEM + " material=" + expected.getType();
            }
            case "invalid" -> {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.FEET}) {
                    ItemStackProvider provider = type.equipment().get(slot);
                    if (provider == null || !provider.provide().getType().isAir()) {
                        throw new IllegalStateException("Unknown or empty ItemsAdder IDs must return AIR: " + slot);
                    }
                }
                yield "BCQA ITEMSADDER INVALID PASS unknown=AIR empty=AIR";
            }
            case "equipped" -> {
                CompanionPlayer owner = api.getCompanionPlayerOrNull(player);
                if (owner == null) {
                    yield "BCQA ITEMSADDER LOADING";
                }
                Companion companion = owner.companions().get(TYPE);
                if (companion == null || !companion.isSpawned()) {
                    throw new IllegalStateException("Configured ItemsAdder companion is not spawned");
                }
                requireItem(companion.equipment().get(EquipmentSlot.HEAD), expected);
                yield "BCQA ITEMSADDER EQUIPPED PASS namespace=" + ITEM + " material=" + expected.getType();
            }
            default -> throw new IllegalArgumentException("Unknown ItemsAdder inspection mode");
        };
    }

    private static void requireItem(ItemStack actual, ItemStack expected) {
        if (actual == null || actual.getAmount() != expected.getAmount() || !actual.isSimilar(expected)) {
            throw new IllegalStateException("ByteCompanions item metadata differs from genuine ItemsAdder output");
        }
        CustomStack resolved = CustomStack.byItemStack(actual);
        if (resolved == null || !ITEM.equals(resolved.getNamespacedID())) {
            throw new IllegalStateException("ByteCompanions item lost its ItemsAdder namespace identity");
        }
    }
}
