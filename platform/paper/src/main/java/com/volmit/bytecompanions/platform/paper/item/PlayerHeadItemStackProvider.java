package com.volmit.bytecompanions.platform.paper.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.platform.paper.builder.SkullItemStackBuilder;
import com.volmit.bytecompanions.platform.paper.util.StringUtil;

import java.util.List;

public final class PlayerHeadItemStackProvider extends SimpleItemStackProvider implements PlayerItemStackProvider {

    private final String headTexture;

    public PlayerHeadItemStackProvider(
            final int amount,
            final int customModelData,
            final @Nullable String headTexture
    ) {
        super(Material.PLAYER_HEAD, amount, customModelData);
        this.headTexture = headTexture;
    }

    public PlayerHeadItemStackProvider(
            final @Nullable String displayName,
            final @Nullable List<String> lore,
            final int amount,
            final int customModelData,
            final @Nullable String headTexture
    ) {
        super(displayName, lore, Material.PLAYER_HEAD, amount, customModelData);
        this.headTexture = headTexture;
    }

    public static @NotNull PlayerHeadItemStackProvider of(final @NotNull String texture) {
        return new PlayerHeadItemStackProvider(1, 0, "base64:" + texture);
    }

    public static @NotNull PlayerHeadItemStackProvider example() {
        return of("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQyZjc4YjVmNTNhMzg5MGVjMzAxYWE4YmM5MDQzODE1NTAwODU3YTM0NzYyMzgwZWNlOTFjYzg1ODdjMzJhMSJ9fX0=");
    }


    public @NotNull String headTextureKey() {
        final String[] split = StringUtil.split(this.headTexture, ':');
        if (split.length < 2) {
            return this.headTexture == null ? "" : this.headTexture;
        }

        return split[0].toLowerCase();
    }

    public @NotNull String headTextureValue() {
        final String[] split = StringUtil.split(this.headTexture, ':');
        if (split.length < 2) {
            return "";
        }

        return split[1];
    }

    public @NotNull String headTexture() {
        return this.headTexture == null ? "" : this.headTexture;
    }

    @Override
    public @NotNull ItemStack provide(final @Nullable Player player) {
        final SkullItemStackBuilder skullBuilder = SkullItemStackBuilder.builder();
        if (this.headTexture != null && !this.headTexture.isEmpty()) {
            final String[] split = StringUtil.split(this.headTexture, ':');
            final String key = split[0].toLowerCase();
            if (key.equals("base64")) {
                skullBuilder.withBase64(split[1]);
            } else if (key.equals("owner_head") && player != null) {
                skullBuilder.withOwner(player);
            } else if (key.equals("texture")) {
                skullBuilder.withTexture(split[1]);
            }
        }

        skullBuilder.material(this.material)
                .amount(this.amount)
                .customModelData(this.customModelData)
                .name(this.displayName)
                .lore(this.lore);

        return skullBuilder.build();
    }

}
