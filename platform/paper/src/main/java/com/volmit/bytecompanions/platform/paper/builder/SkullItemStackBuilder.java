package com.volmit.bytecompanions.platform.paper.builder;

import art.arcane.volmlib.util.bukkit.BukkitComponents;
import net.kyori.adventure.text.Component;
import art.arcane.volmlib.util.bukkit.BukkitProfiles;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;

public final class SkullItemStackBuilder extends ItemStackBuilder {

    private PlayerProfile playerProfile;
    private OfflinePlayer owner;

    SkullItemStackBuilder() {}

    public static @NotNull SkullItemStackBuilder builder() {
        return new SkullItemStackBuilder();
    }

    public SkullItemStackBuilder withPlayerProfile(final @NotNull PlayerProfile playerProfile) {
        this.playerProfile = playerProfile;
        this.owner = null;
        return this;
    }

    public SkullItemStackBuilder withOwner(OfflinePlayer owner) {
        this.owner = owner;
        this.playerProfile = null;
        return this;
    }

    public SkullItemStackBuilder withBase64(final @NotNull String base64) {
        return this.withPlayerProfile(BukkitProfiles.textureValue(base64));
    }

    public SkullItemStackBuilder withTexture(final @NotNull String texture) {
        return this.withPlayerProfile(BukkitProfiles.textureId(texture));
    }

    @Override
    public @NotNull ItemStack build() {
        final ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD, this.amount);
        final SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();

        if (this.name != null) {
            BukkitComponents.displayName(skullMeta, Component.text(this.name));
        }

        if (this.lore != null) {
            BukkitComponents.lore(skullMeta, this.lore.stream().map(Component::text).toList());
        }

        if (this.playerProfile != null) {
            skullMeta.setOwnerProfile(this.playerProfile);
        } else if (this.owner != null) {
            skullMeta.setOwningPlayer(this.owner);
        }

        skullMeta.setCustomModelData(this.customModelData);
        itemStack.setItemMeta(skullMeta);
        return itemStack;
    }

}
