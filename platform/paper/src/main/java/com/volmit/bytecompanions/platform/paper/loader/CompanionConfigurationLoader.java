package com.volmit.bytecompanions.platform.paper.loader;

import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import java.io.IOException;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import com.volmit.bytecompanions.api.util.Loader;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.core.companion.ArmorCycleEntry;
import com.volmit.bytecompanions.core.companion.DefaultCompanionType;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.configuration.NodeAnimationData;
import com.volmit.bytecompanions.platform.paper.configuration.NodeConfigurationData;
import com.volmit.bytecompanions.platform.paper.configuration.companion.CompanionTypeConfig;
import com.volmit.bytecompanions.platform.paper.configuration.serializer.ArmorCycleEntrySerializer;
import com.volmit.bytecompanions.platform.paper.configuration.serializer.ArmorCycleSerializer;
import com.volmit.bytecompanions.platform.paper.configuration.serializer.ItemStackProviderSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprint;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;

import java.io.File;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.platform.paper.companion.animations.BuiltinAnimationRegistry;
import com.volmit.bytecompanions.features.animations.AnimationsFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class CompanionConfigurationLoader implements Loader {

    private final PaperPlugin paperPlugin;
    private final CompanionTypeRegistry companionTypeRegistry;
    private final ItemBlueprintRegistry itemBlueprintRegistry;
    private final AnimationRegistry validationAnimations = BuiltinAnimationRegistry.createDefaults();
    private final ContentSnapshot content;

    public CompanionConfigurationLoader(
            final @NotNull PaperPlugin paperPlugin,
            final @NotNull CompanionTypeRegistry companionTypeRegistry,
            final @NotNull ItemBlueprintRegistry itemBlueprintRegistry
    ) {

        this(paperPlugin, companionTypeRegistry, itemBlueprintRegistry, paperPlugin.contentSnapshot());
    }

    public CompanionConfigurationLoader(PaperPlugin paperPlugin, CompanionTypeRegistry companionTypeRegistry,
                                        ItemBlueprintRegistry itemBlueprintRegistry, ContentSnapshot content) {
        AnimationsFeature.register(this.validationAnimations);
        this.content = content;
        this.paperPlugin = paperPlugin;
        this.companionTypeRegistry = companionTypeRegistry;
        this.itemBlueprintRegistry = itemBlueprintRegistry;
    }

    @Override
    public void load() {
        for (String path : this.content.paths().stream().filter(path -> path.startsWith("companions/")).sorted().toList()) {
            File file = new File(path);

            try {
                final String id = file.getName().substring(0, file.getName().length() - ".toml".length());
                final ConfigurationNode root = this.content.node(path, ConfigurationOptions.defaults().serializers(builder -> builder
                        .register(ItemStackProvider.class, ItemStackProviderSerializer.INSTANCE)
                        .register(ArmorCycleEntry.class, ArmorCycleEntrySerializer.INSTANCE)
                        .register(ArmorCycle.class, ArmorCycleSerializer.INSTANCE)));
                final CompanionTypeConfig companionTypeConfig = root.get(CompanionTypeConfig.class);

                if (companionTypeConfig == null) {
                    throw new IllegalArgumentException("Invalid companion config: " + file.getName());
                }

                validateDefinition(companionTypeConfig, file.getName());
                final AppearanceData appearance =
                        new AppearanceData(
                                companionTypeConfig.appearance().small(),
                                companionTypeConfig.appearance().visible(),
                                companionTypeConfig.appearance().showArms(),
                                companionTypeConfig.appearance().showBasePlate(),
                                companionTypeConfig.appearance().marker(),
                                companionTypeConfig.appearance().customName(),
                                companionTypeConfig.appearance().customNameVisible(),
                                companionTypeConfig.appearance().scale()
                        );

                final Map<AnimationChannel, Map<String, AnimationData>> animations = new EnumMap<>(AnimationChannel.class);
                final ConfigurationNode animationsNode = root.node("animations");

                this.addAnimationChannel(
                        animations,
                        AnimationChannel.TRACKING,
                        animationsNode.node("tracking")
                );

                this.addAnimationChannel(
                        animations,
                        AnimationChannel.POST_TRACKING,
                        animationsNode.node("post-tracking")
                );

                this.addAnimationChannel(
                        animations,
                        AnimationChannel.MOVEMENT,
                        animationsNode.node("movement")
                );

                this.addAnimationChannel(
                        animations,
                        AnimationChannel.ROTATION,
                        animationsNode.node("rotation")
                );

                this.addAnimationChannel(
                        animations,
                        AnimationChannel.POSE,
                        animationsNode.node("pose")
                );


                this.addSingleAnimation(
                        animations,
                        AnimationChannel.FULL_BODY,
                        animationsNode.node("full-body")
                );

                this.addSingleAnimation(
                        animations,
                        AnimationChannel.HEAD_ONLY,
                        animationsNode.node("head-only")
                );

                final Map<EquipmentSlot, ItemStackProvider> equipment = new EnumMap<>(EquipmentSlot.class);
                equipment.putAll(companionTypeConfig.equipment());

                final Map<String, OverlayData> overlays = new HashMap<>(companionTypeConfig.overlays().size());

                companionTypeConfig.overlays()
                        .forEach((name, overlay) -> overlays.put(name, new OverlayData(overlay.lockHeadYaw(), overlay.equipment())));

                final CompanionTypeConfig.Display display = companionTypeConfig.display();
                ItemBlueprint itemBlueprint = null;
                if (display.item() != null) {
                    itemBlueprint = this.itemBlueprintRegistry.compile(companionTypeConfig.display().item()).orElseThrow();
                }

                final ConfigurationNode extensionsNode = root.node("extensions");
                final CompanionType companionType =
                        new DefaultCompanionType(
                                id,
                                companionTypeConfig.exclusive(),
                                itemBlueprint,
                                appearance,
                                companionTypeConfig.miniatureModel(),
                                new NodeConfigurationData(extensionsNode),
                                animations,
                                equipment,
                                overlays,
                                new NodeConfigurationData(root.copy())
                        );

                this.companionTypeRegistry.register(id, companionType);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Failed loading companion from: " + file.getName(), exception);
            }
        }
    }

    @Override
    public void unload() {
        this.companionTypeRegistry.clear();
    }

    private void addAnimationChannel(
            final Map<AnimationChannel, Map<String, AnimationData>> animations,
            final AnimationChannel channel,
            final @NotNull ConfigurationNode node
    ) {

        if (node.virtual()) {
            return;
        }

        if (!node.isList()) {
            throw new IllegalArgumentException("Animation channel must be a list: " + channel);
        }
        final Map<String, AnimationData> channelAnimations = new HashMap<>();
        for (final ConfigurationNode child : node.childrenList()) {
            final String id = child.node("id").getString();
            if (id == null || id.isBlank() || channelAnimations.containsKey(id)) {
                throw new IllegalArgumentException("Animation requires a unique ID in " + channel);
            }
            NodeAnimationData data = new NodeAnimationData(id, child);
            this.validateAnimation(channel, data);
            channelAnimations.put(id, data);
        }

        animations.put(channel, channelAnimations);
    }

    private void addSingleAnimation(
            final Map<AnimationChannel, Map<String, AnimationData>> animations,
            final AnimationChannel channel,
            final @NotNull ConfigurationNode node
    ) {

        if (node.virtual()) {
            return;
        }

        final String id = node.node("id").getString();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Animation requires an ID in " + channel);
        }
        NodeAnimationData data = new NodeAnimationData(id, node);
        this.validateAnimation(channel, data);
        animations.put(channel, Map.of(id, data));
    }

    public static void validateDefinition(CompanionTypeConfig definition, String source) {
        double scale = definition.appearance().scale();
        if (definition.miniatureModel() == null || !Double.isFinite(scale) || scale != -1 && scale <= 0) {
            throw new IllegalArgumentException("Companion requires a model and a finite scale of -1 (default) or greater than zero: " + source);
        }
    }

    private void validateAnimation(AnimationChannel channel, NodeAnimationData data) {
        if (channel == AnimationChannel.POST_TRACKING && ParticleEffectCatalog.contains(data.id())) {
            return;
        }
        this.validationAnimations.get(channel, data.id()).create(data);
    }

}
