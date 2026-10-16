package com.volmit.bytecompanions.platform.paper.configuration.serializer;

import org.bukkit.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.platform.paper.item .ArmorItemStackProvider;
import com.volmit.bytecompanions.platform.paper.item.LeatherArmorItemStackProvider;
import com.volmit.bytecompanions.platform.paper.item.PlayerHeadItemStackProvider;
import com.volmit.bytecompanions.platform.paper.item.SimpleItemStackProvider;
import com.volmit.bytecompanions.platform.paper.integration.itemsadder.ItemsAdderItemStackProvider;
import com.volmit.bytecompanions.platform.paper.integration.nexo.NexoMCItemStackProvider;
import com.volmit.bytecompanions.platform.paper.util.ColorUtil;
import com.volmit.bytecompanions.platform.paper.util.ItemStackUtil;
import com.volmit.bytecompanions.platform.paper.util.StringUtil;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;

public final class ItemStackProviderSerializer implements TypeSerializer<ItemStackProvider> {

    public static final ItemStackProviderSerializer INSTANCE = new ItemStackProviderSerializer();
    public static final ItemStackProvider EMPTY_ITEM_STACK_PROVIDER = SimpleItemStackProvider.empty();

    @Contract(pure = true)
    @Override
    public @Nullable ItemStackProvider deserialize(final @NotNull Type type, final @NotNull ConfigurationNode node) {
        if (node.virtual()) {
            return null;
        }

        final String materialName = node.node("material").getString();
        if (materialName == null || materialName.isBlank()) {
            return EMPTY_ITEM_STACK_PROVIDER;
        }

        final String[] parts = StringUtil.split(materialName, ':');
        if (parts.length > 1) {
            final String dataString = parts[0].toLowerCase();
            switch (dataString) {
                case "nexo" -> {
                    return new NexoMCItemStackProvider(materialName);
                }

                case "itemsadder" -> {
                    return new ItemsAdderItemStackProvider(materialName);
                }
            }
        }

        final Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            return EMPTY_ITEM_STACK_PROVIDER;
        }

        final String displayName = node.node("display-name").getString();
        final List<String> lore;
        try {
            lore = node.node("lore").getList(String.class);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }

        final int amount = node.node("amount").getInt(1);
        final int customModelData = node.node("custom-model-data").getInt();
        final ItemMeta materialMeta = Bukkit.getItemFactory().getItemMeta(material);

        final ItemStackProvider itemStackProvider;
        if (ItemStackUtil.isArmorMete(materialMeta)) {
            ArmorCycle armorCycle;
            try {
                armorCycle = node.node("cycle").get(ArmorCycle.class);
            } catch (SerializationException e) {
                armorCycle = null;
            }

            final @Nullable TrimMaterial trimMaterial = this.getTrimMaterial(node);
            final @Nullable TrimPattern trimPattern = this.getTrimPattern(node);

            if (ItemStackUtil.isLeatherArmorMeta(materialMeta)) {
                final String rgbColor = node.node("rgb-color").getString();
                final Color parsedColor = ColorUtil.parseColor(rgbColor);
                final Color leatherArmorColor = parsedColor != null ? parsedColor : Bukkit.getItemFactory().getDefaultLeatherColor();

                itemStackProvider = new LeatherArmorItemStackProvider(displayName, lore, material, trimMaterial, trimPattern, armorCycle, amount, customModelData, leatherArmorColor.asRGB());
            } else {
                itemStackProvider = new ArmorItemStackProvider(displayName, lore, material, trimMaterial, trimPattern, armorCycle, amount, customModelData);
            }

        } else if (material == Material.PLAYER_HEAD) {
            final String texture = node.node("texture").getString();
            itemStackProvider = new PlayerHeadItemStackProvider(displayName, lore, amount, customModelData, texture);
        } else {
            itemStackProvider = new SimpleItemStackProvider(displayName, lore, material, amount, customModelData);
        }

        return itemStackProvider;
    }

    @Override
    public void serialize(final @NotNull Type type, final @Nullable ItemStackProvider obj, final @NotNull ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }

        if (!(obj instanceof SimpleItemStackProvider provider)) {
            throw new SerializationException("Can only serialize subclasses of ConfigurableItemProvider, not " + obj.getClass().getName());
        }

        node.node("material").set(provider.material().name());
        node.node("amount").set(provider.amount());
        node.node("custom-model-data").set(provider.customModelData());
        node.node("display-name").set(provider.displayName());
        node.node("lore").set(provider.lore());

        if (provider instanceof LeatherArmorItemStackProvider leatherArmorProvider) {
            node.node("rgb-color").set(leatherArmorProvider.rgbColor());
        } else if (provider instanceof PlayerHeadItemStackProvider playerHeadProvider) {
            node.node("texture").set(playerHeadProvider.headTexture());
        }
    }

    private @Nullable TrimMaterial getTrimMaterial(final @NotNull ConfigurationNode node) {
        final String trimMaterialKey = node.node("trim-material").getString();
        TrimMaterial trimMaterial = null;
        if (trimMaterialKey != null && !trimMaterialKey.isBlank()) {
            final String key = trimMaterialKey.toLowerCase(Locale.ROOT);
            final Registry<TrimMaterial> registryKey = Registry.TRIM_MATERIAL;
            trimMaterial = registryKey.get(NamespacedKey.minecraft(key));
        }

        return trimMaterial;
    }

    private @Nullable TrimPattern getTrimPattern(final @NotNull ConfigurationNode node) {
        final String trimPatternKey = node.node("trim-pattern").getString();
        TrimPattern trimPattern = null;
        if (trimPatternKey != null && !trimPatternKey.isBlank()) {
            final String key = trimPatternKey.toLowerCase(Locale.ROOT);
            final Registry<TrimPattern> registryKey = Registry.TRIM_PATTERN;
            trimPattern = registryKey.get(NamespacedKey.minecraft(key));
        }

        return trimPattern;
    }



}
