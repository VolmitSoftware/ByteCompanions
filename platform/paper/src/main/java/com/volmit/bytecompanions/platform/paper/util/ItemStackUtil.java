package com.volmit.bytecompanions.platform.paper.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

public final class ItemStackUtil {

    private ItemStackUtil() {
        throw new NonInstantiableClassException();
    }

    public static boolean isArmorMete(final @NotNull Material material) {
        return isArmorMete(Bukkit.getItemFactory().getItemMeta(material));
    }

    public static boolean isArmorMete(final @NotNull ItemStack itemStack) {
        return isArmorMete(itemStack.getItemMeta());
    }

    public static boolean isArmorMete(final @NotNull ItemMeta itemMeta) {
        return itemMeta instanceof ArmorMeta;
    }

    public static boolean isLeatherArmorMeta(final @NotNull Material material) {
        return isLeatherArmorMeta(Bukkit.getItemFactory().getItemMeta(material));
    }

    public static boolean isLeatherArmorMeta(final @NotNull ItemMeta itemMeta) {
        return itemMeta instanceof LeatherArmorMeta;
    }

    public static boolean isLeatherArmor(final @NotNull ItemStack itemStack) {
        return isLeatherArmor(itemStack.getType());
    }

    public static boolean isLeatherArmor(final @NotNull Material material) {
        return switch (material) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS -> true;
            default -> false;
        };
    }

}
