package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class MenuIcons {
    private static final NamespacedKey PLACEHOLDER = NamespacedKey.fromString("bytecompanions:menu_icon");

    private MenuIcons() {
    }

    static ItemStack of(Material material) {
        return withoutStats(new ItemStack(material));
    }

    // Armor and weapon icons would otherwise show vanilla lines such as "When on Chest: +3 Armor".
    // The flag alone hides only explicit modifiers, so a zero placeholder replaces the item's defaults first.
    static ItemStack withoutStats(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (!meta.hasAttributeModifiers()) {
            meta.addAttributeModifier(Attribute.LUCK, new AttributeModifier(PLACEHOLDER, 0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
