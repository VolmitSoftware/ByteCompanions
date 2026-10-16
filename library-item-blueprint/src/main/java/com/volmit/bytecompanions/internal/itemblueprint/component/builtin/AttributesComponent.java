package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;



import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AttributesComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final List<CompiledModifier> modifiers = new ArrayList<>();

        int index = 0;
        for (final ConfigurationNode entry : ConfigNodes.childrenList(configurationNode)) {
            final BlueprintResponse<CompiledModifier> modifierResponse = this.compileModifier(entry, index);
            if (!modifierResponse.isSuccess()) {
                return modifierResponse.propagate("[" + index + "]");
            }

            modifiers.add(modifierResponse.value());
            index++;
        }

        final List<CompiledModifier> finalModifiers = List.copyOf(modifiers);
        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                return;
            }

            finalModifiers.forEach(modifier -> meta.addAttributeModifier(modifier.attribute(), modifier.toBukkitModifier()));
            itemStack.setItemMeta(meta);
        });
    }

    private @NotNull BlueprintResponse<CompiledModifier> compileModifier(final @NotNull ConfigurationNode entry, final int index) {
        final BlueprintResponse<String> attributeResponse = ConfigNodes.requiredString(entry, "attribute");
        if (!attributeResponse.isSuccess()) {
            return attributeResponse.propagate("attribute");
        }

        final Registry<Attribute> attributeRegistry = Registry.ATTRIBUTE;
        final Attribute attribute = ConfigNodes.registryValue(attributeRegistry, attributeResponse.value());
        if (attribute == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "attribute", "Unknown attribute '" + attributeResponse.value() + "'.");
        }

        final double amount = entry.node("amount").getDouble(0.0);

        final BlueprintResponse<String> operationResponse = ConfigNodes.requiredString(entry, "operation");
        if (!operationResponse.isSuccess()) {
            return operationResponse.propagate("operation");
        }

        final AttributeModifier.Operation operation = ConfigNodes.kebabToEnum(operationResponse.value(), AttributeModifier.Operation.class);
        if (operation == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "operation", "Unknown operation '" + operationResponse.value() + "'.");
        }

        final String rawSlot = ConfigNodes.optionalString(entry, "slot", "any");
        final EquipmentSlotGroup slotGroup = EquipmentSlotGroup.getByName(rawSlot.toLowerCase(Locale.ROOT));
        if (slotGroup == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "slot", "Unknown equipment slot group '" + rawSlot + "'.");
        }

        final NamespacedKey modifierKey = new NamespacedKey("item-blueprint", "attribute-" + index);
        return BlueprintResponse.success(new CompiledModifier(attribute, modifierKey, amount, operation, slotGroup));
    }

    private record CompiledModifier(
            @NotNull Attribute attribute,
            @NotNull NamespacedKey key,
            double amount,
            @NotNull AttributeModifier.Operation operation,
            @NotNull EquipmentSlotGroup slotGroup
    ) {

        @NotNull AttributeModifier toBukkitModifier() {
            return new AttributeModifier(this.key, this.amount, this.operation, this.slotGroup);
        }
    }
}
