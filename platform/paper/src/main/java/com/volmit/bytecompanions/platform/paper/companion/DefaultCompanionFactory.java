package com.volmit.bytecompanions.platform.paper.companion;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import com.volmit.bytecompanions.api.companion.entity.CompanionEntity;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import com.volmit.bytecompanions.adaptercommon.CyclableItemStackProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionOverlay;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationFactory;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.core.util.CompanionUtil;
import com.volmit.bytecompanions.platform.paper.component.adapter.ComponentSerializerAdapter;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.item.PlayerItemStackProvider;
import com.volmit.bytecompanions.platform.paper.util.StringUtil;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.companions.CompanionLocalSettings;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import com.volmit.bytecompanions.spi.companions.NmsCompanionEntityFactory;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;

import java.util.Map;
import java.util.List;
import java.util.UUID;

public final class DefaultCompanionFactory implements CompanionFactory {

    private final ComponentSerializerAdapter componentSerializerAdapter;
    private final NmsCompanionEntityFactory nmsCompanionEntityFactory;
    private final AnimationRegistry animationRegistry;
    private final Configuration configuration;

    public DefaultCompanionFactory(
            final @NotNull ComponentSerializerAdapter componentSerializerAdapter,
            final @NotNull NmsCompanionEntityFactory nmsCompanionEntityFactory,
            final @NotNull AnimationRegistry animationRegistry,
            final @NotNull Configuration configuration
    ) {
        this.componentSerializerAdapter = componentSerializerAdapter;
        this.nmsCompanionEntityFactory = nmsCompanionEntityFactory;
        this.animationRegistry = animationRegistry;
        this.configuration = configuration;
    }

    // ── Player shortcut (resolves name placeholder, player-item providers) ─

    @Override
    public @NonNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings,
            final int entityId
    ) {
        return this.create(player, companionType, localSettings, entityId, false);
    }

    public @NotNull Companion createPreview(Player player, CompanionType companionType) {
        return this.create(player, companionType, new CompanionLocalSettings(
                this.configuration.performance().viewDistance(),
                this.configuration.performance().viewerRefreshDelay()), -1, true);
    }

    private @NotNull Companion create(Player player, CompanionType companionType,
                                      CompanionLocalSettings localSettings, int entityId, boolean preview) {

        final String resolvedName = StringUtil.replace(
                companionType.appearance().customName(),
                "{player}", player.getName()
        );

        final AppearanceData appearance = CompanionUtil.cloneWithCustomName(
                companionType.appearance(),
                resolvedName
        );

        final Component nameComponent = this.componentSerializerAdapter.deserialize(resolvedName);
        final CompanionAnchor anchor = this.nmsCompanionEntityFactory.createPlayerAnchor(player);
        final NmsCapableCompanionEntity nmsEntity = this.buildEntity(anchor, companionType, appearance, nameComponent, localSettings, entityId);

        if (preview) {
            nmsEntity.restrictViewer(player);
        }

        try {
            this.applyEquipment(nmsEntity, companionType, player);
            this.applyOverlayEquipment(nmsEntity, companionType, player);

            final DefaultCompanion companion = new DefaultCompanion(player.getUniqueId(), player.getName(), anchor, companionType, nmsEntity);
            nmsEntity.spawn();
            return companion;
        } catch (RuntimeException | LinkageError failure) {
            if (preview) {
                try {
                    nmsEntity.animations().clear();
                    nmsEntity.destroy();
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public @NotNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType,
            final int entityId
    ) {
        final CompanionLocalSettings companionLocalSettings = new CompanionLocalSettings(
                this.configuration.performance().viewDistance(),
                this.configuration.performance().viewerRefreshDelay()
        );

        return this.create(player, companionType, companionLocalSettings, entityId);
    }

    // ── Generic anchor variant (used by addons for decorative companions) ──

    @Override
    public @NonNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings,
            final int entityId
    ) {
        final AppearanceData appearance = companionType.appearance();
        final Component nameComponent = this.componentSerializerAdapter
                .deserializeOrNull(appearance.customName());

        final NmsCapableCompanionEntity nmsEntity = this.buildEntity(
                anchor,
                companionType,
                appearance,
                nameComponent == null ? Component.empty() : nameComponent,
                localSettings,
                entityId
        );

        // No player — use default item providers only
        this.applyEquipment(nmsEntity, companionType, null);
        this.applyOverlayEquipment(nmsEntity, companionType, null);

        final Companion companion = new DefaultCompanion(new UUID(0, 0), "none", anchor, companionType, nmsEntity);
        nmsEntity.spawn();
        return companion;
    }

    @Override
    public @NotNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final int entityId
    ) {
        final CompanionLocalSettings companionLocalSettings = new CompanionLocalSettings(
                this.configuration.performance().viewDistance(),
                this.configuration.performance().viewerRefreshDelay()
        );

        return this.create(anchor, companionType, companionLocalSettings, entityId);
    }

    // ── Shared build logic ─────────────────────────────────────────────────

    private @NotNull NmsCapableCompanionEntity buildEntity(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final @NotNull AppearanceData appearance,
            final @NotNull Component nameComponent,
            final @NotNull CompanionLocalSettings companionLocalSettings,
            final int entityId
    ) {
        final NmsCapableCompanionEntity entity = this.nmsCompanionEntityFactory.create(
                anchor,
                companionType.model(),
                appearance,
                nameComponent,
                companionLocalSettings.viewDistance(),
                companionLocalSettings.viewerRefreshDelay(),
                entityId
        );

        this.applyAnimations(entity, companionType.animations());
        return entity;
    }

    public void applyCosmetics(Player player, Companion companion, CompanionType template, String nickname) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            companion.equipment(slot, (ItemStack) null);
        }
        this.applyEquipment(companion, template, player);
        companion.animations().clear();
        companion.behavior().resetPose();
        this.applyAnimations(companion, template.animations());
        String configuredName = companion.type().appearance().customName();
        companion.customName(nickname == null ? this.componentSerializerAdapter.deserializeOrNull(
                configuredName == null ? null : StringUtil.replace(configuredName, "{player}", player.getName()))
                : Component.text(nickname));
        companion.setNameVisible(nickname != null || companion.type().appearance().customNameVisible());
    }

    void applyAnimations(CompanionEntity entity, Map<AnimationChannel, Map<String, AnimationData>> animations) {
        for (Map.Entry<AnimationChannel, Map<String, AnimationData>> channelEntry : animations.entrySet()) {
            for (Map.Entry<String, AnimationData> animationEntry : channelEntry.getValue().entrySet()) {
                if (!this.configuration.features().enabled("particles")
                        && channelEntry.getKey() == AnimationChannel.POST_TRACKING
                        && ParticleEffectCatalog.contains(animationEntry.getKey())) {
                    continue;
                }
                AnimationFactory<?> factory = this.animationRegistry.get(channelEntry.getKey(), animationEntry.getKey());
                entity.animations().add(animationEntry.getKey(), factory.create(animationEntry.getValue()));
            }
        }
    }

    private void applyEquipment(
            final @NotNull CompanionEntity nmsCompanionEntity,
            final @NotNull CompanionType companionType,
            final @Nullable Player player
    ) {
        companionType.equipment().forEach((slot, provider) -> {
            if (provider instanceof CyclableItemStackProvider cyclableItemStackProvider) {
                nmsCompanionEntity.equipment(slot, cyclableItemStackProvider);
            } else {
                final ItemStack item = provider instanceof PlayerItemStackProvider pip ? pip.provide(player) : provider.provide();
                nmsCompanionEntity.equipment(slot, item);
            }
        });
    }

    private void applyOverlayEquipment(
            final @NotNull NmsCapableCompanionEntity entity,
            final @NotNull CompanionType companionType,
            final @Nullable Player player
    ) {
        if (companionType.overlays().isEmpty()) {
            return;
        }

        companionType.overlays().forEach((overlayId, overlayData) -> {
            final CompanionOverlay companionOverlay = entity.addOverlay(overlayId, overlayData);
            if (companionOverlay == null) {
                return;
            }

            overlayData.equipment().forEach((slot, provider) -> {
                final ItemStack item = (player != null && provider instanceof PlayerItemStackProvider pip)
                        ? pip.provide(player)
                        : provider.provide();

                companionOverlay.equipment(slot, item);
            });
        });
    }

}
