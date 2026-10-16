package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;



import org.bukkit.Color;
import org.bukkit.Registry;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

import java.util.ArrayList;
import java.util.List;

public final class PotionComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final String rawType = configurationNode.node("type").getString();
        PotionType potionType = null;
        if (rawType != null) {
            potionType = ConfigNodes.kebabToEnum(rawType, PotionType.class);
            if (potionType == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "type", "Unknown potion type '" + rawType + "'.");
            }
        }

        final String rawColor = configurationNode.node("color").getString();
        Color color = null;
        if (rawColor != null) {
            color = ConfigNodes.parseHexColor(rawColor);
            if (color == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "color", "'" + rawColor + "' is not a valid hex color.");
            }
        }

        final BlueprintResponse<List<CompiledEffect>> effectsResponse = this.compileEffects(configurationNode.node("effects"));
        if (!effectsResponse.isSuccess()) {
            return effectsResponse.propagate("effects");
        }

        final PotionType finalType = potionType;
        final Color finalColor = color;
        final List<CompiledEffect> finalEffects = effectsResponse.value();

        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (!(meta instanceof final PotionMeta potionMeta)) {
                return;
            }

            if (finalType != null) {
                potionMeta.setBasePotionType(finalType);
            }
            if (finalColor != null) {
                potionMeta.setColor(finalColor);
            }

            if (finalEffects != null) {
                finalEffects.forEach(effect -> potionMeta.addCustomEffect(
                        new PotionEffect(effect.type(), effect.duration(), effect.amplifier()), true));
            }

            itemStack.setItemMeta(potionMeta);
        });
    }

    private @NotNull BlueprintResponse<List<CompiledEffect>> compileEffects(final @NotNull ConfigurationNode effectsNode) {
        final List<CompiledEffect> effects = new ArrayList<>();

        int index = 0;
        for (final ConfigurationNode entry : ConfigNodes.childrenList(effectsNode)) {
            final BlueprintResponse<String> typeResponse = ConfigNodes.requiredString(entry, "type");
            if (!typeResponse.isSuccess()) {
                return typeResponse.propagate("[" + index + "]");
            }

            final Registry<PotionEffectType> potionEffectRegistry = Registry.EFFECT;
            final PotionEffectType effectType = ConfigNodes.registryValue(potionEffectRegistry, typeResponse.value());
            if (effectType == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "[" + index + "].type", "Unknown potion effect '" + typeResponse.value() + "'.");
            }

            final int duration = ConfigNodes.optionalInt(entry, "duration", 200);
            final int amplifier = ConfigNodes.optionalInt(entry, "amplifier", 0);
            effects.add(new CompiledEffect(effectType, duration, amplifier));
            index++;
        }

        return BlueprintResponse.success(List.copyOf(effects));
    }

    private record CompiledEffect(@NotNull PotionEffectType type, int duration, int amplifier) { }
}
