package com.volmit.bytecompanions.internal.itemblueprint.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;
import art.arcane.volmlib.util.bukkit.BukkitComponents;

import java.util.ArrayList;
import java.util.List;

public final class CommonFieldsParser {

    private CommonFieldsParser() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull BlueprintResponse<CompiledComponent> compile(
            final @NotNull ConfigurationNode node,
            final @NotNull TextSerializer textSerializer
    ) {
        final int amount = ConfigNodes.optionalInt(node, "amount", 1);
        final String rawName = ConfigNodes.string(node, "name");
        final List<String> rawLore = ConfigNodes.stringList(node.node("lore"));
        final Integer customModelData = node.node("custom-model-data").virtual() ? null : node.node("custom-model-data").getInt();
        final boolean glow = ConfigNodes.optionalBoolean(node, "glow", false);
        final boolean unbreakable = ConfigNodes.optionalBoolean(node, "unbreakable", false);

        final List<ItemFlag> hideFlags = new ArrayList<>();
        for (final String rawFlag : ConfigNodes.stringList(node.node("hide-flags"))) {
            final ItemFlag flag = ConfigNodes.kebabToEnum(rawFlag, ItemFlag.class);
            if (flag == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "hide-flags", "Unknown item flag '" + rawFlag + "'.");
            }

            hideFlags.add(flag);
        }

        final List<ItemFlag> finalHideFlags = List.copyOf(hideFlags);
        return BlueprintResponse.success((itemStack, context) -> {
            itemStack.setAmount(Math.max(1, amount));

            final ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                return;
            }

            if (rawName != null && !rawName.isBlank()) {
                BukkitComponents.displayName(meta, withoutDefaultItalics(ConfigNodes.resolveText(rawName, context, textSerializer)));
            }

            if (!rawLore.isEmpty()) {
                BukkitComponents.lore(meta, rawLore.stream()
                        .map(line -> withoutDefaultItalics(ConfigNodes.resolveText(line, context, textSerializer)))
                        .toList());
            }

            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }

            meta.setUnbreakable(unbreakable);
            finalHideFlags.forEach(meta::addItemFlags);

            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }

            itemStack.setItemMeta(meta);
        });
    }

    private static @NotNull Component withoutDefaultItalics(final @NotNull Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
