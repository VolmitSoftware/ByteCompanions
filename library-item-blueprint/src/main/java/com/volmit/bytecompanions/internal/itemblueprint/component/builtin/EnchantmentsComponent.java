package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;



import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
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

public final class EnchantmentsComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final List<CompiledEnchant> enchants = new ArrayList<>();

        int index = 0;
        for (final ConfigurationNode entry : ConfigNodes.childrenList(configurationNode)) {
            final BlueprintResponse<String> idResponse = ConfigNodes.requiredString(entry, "id");
            if (!idResponse.isSuccess()) {
                return idResponse.propagate("[" + index + "]");
            }

            final Registry<Enchantment> enchantmentRegistry = Registry.ENCHANTMENT;
            final Enchantment enchantment = ConfigNodes.registryValue(enchantmentRegistry, idResponse.value());
            if (enchantment == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "[" + index + "].id", "Unknown enchantment '" + idResponse.value() + "'.");
            }

            final int level = ConfigNodes.optionalInt(entry, "level", 1);
            enchants.add(new CompiledEnchant(enchantment, level));
            index++;
        }

        final List<CompiledEnchant> finalEnchants = List.copyOf(enchants);
        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                return;
            }

            finalEnchants.forEach(enchant -> meta.addEnchant(enchant.enchantment(), enchant.level(), true));
            itemStack.setItemMeta(meta);
        });
    }

    private record CompiledEnchant(@NotNull Enchantment enchantment, int level) { }
}
