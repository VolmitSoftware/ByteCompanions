package com.volmit.bytecompanions.internal.itemblueprint.base.builtin;

import org.bukkit.profile.PlayerProfile;
import art.arcane.volmlib.util.bukkit.BukkitProfiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.base.CompiledBase;
import com.volmit.bytecompanions.internal.itemblueprint.base.ItemBase;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;



import java.util.Locale;
import java.util.Objects;


public final class SkullBase implements ItemBase {

    @Override
    public @NotNull BlueprintResponse<CompiledBase> compile(final @NotNull ConfigurationNode node) {
        final BlueprintResponse<String> sourceResponse = ConfigNodes.requiredString(node, "source");
        if (!sourceResponse.isSuccess()) {
            return sourceResponse.propagate("source");
        }

        final BlueprintResponse<String> valueResponse = ConfigNodes.requiredString(node, "value");
        if (!valueResponse.isSuccess()) {
            return valueResponse.propagate("value");
        }

        final String source = Objects.requireNonNull(sourceResponse.value()).toLowerCase(Locale.ROOT);
        if (!source.equals("player") && !source.equals("base64") && !source.equals("texture-id")) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "source", "'source' must be one of: player, base64, texture-id.");
        }

        final String rawValue = valueResponse.value();
        return BlueprintResponse.success(context -> {
            final String resolvedValue = context.resolve(rawValue);
            final ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
            final SkullMeta meta = (SkullMeta) itemStack.getItemMeta();

            if (meta != null) {
                switch (source) {
                    case "player" -> meta.setOwnerProfile(this.profileForName(resolvedValue));
                    case "base64" -> meta.setOwnerProfile(BukkitProfiles.textureValue(resolvedValue));
                    case "texture-id" -> meta.setOwnerProfile(BukkitProfiles.textureId(resolvedValue));
                    default -> throw new IllegalStateException("Unreachable: source already validated.");
                }

                itemStack.setItemMeta(meta);
            }

            return itemStack;
        });
    }

    private @NotNull PlayerProfile profileForName(final @NotNull String playerName) {
        final Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return BukkitProfiles.forPlayer(online);
        }

        return Bukkit.createPlayerProfile(null, playerName);
    }

}
