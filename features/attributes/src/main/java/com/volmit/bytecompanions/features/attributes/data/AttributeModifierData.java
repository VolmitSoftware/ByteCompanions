package com.volmit.bytecompanions.features.attributes.data;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record AttributeModifierData(
        @NotNull Attribute attribute,
        @NotNull NamespacedKey key,
        double amount,
        AttributeModifier.Operation operation
) {

    public AttributeModifierData {
        Objects.requireNonNull(attribute);
        Objects.requireNonNull(key);
        Objects.requireNonNull(operation);
    }

    public @NotNull AttributeModifier createBukkitInstance() {
        return new AttributeModifier(this.key, this.amount, this.operation, EquipmentSlotGroup.ANY);
    }

}
