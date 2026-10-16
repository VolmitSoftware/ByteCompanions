package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CustomDataComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final Map<String, String> values = new LinkedHashMap<>();
        for (final Map.Entry<Object, ? extends ConfigurationNode> entry : configurationNode.childrenMap().entrySet()) {
            values.put(String.valueOf(entry.getKey()), entry.getValue().getString(""));
        }

        final Map<String, String> finalValues = Map.copyOf(values);
        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                return;
            }

            final PersistentDataContainer container = meta.getPersistentDataContainer();
            finalValues.forEach((key, rawValue) -> container.set(
                    new NamespacedKey("item-blueprint", key),
                    PersistentDataType.STRING,
                    context.resolve(rawValue)
            ));

            itemStack.setItemMeta(meta);
        });
    }
}
