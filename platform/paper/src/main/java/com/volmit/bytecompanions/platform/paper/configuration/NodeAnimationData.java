package com.volmit.bytecompanions.platform.paper.configuration;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;

public final class NodeAnimationData extends NodeConfigurationData implements AnimationData {

    private final String id;
    public NodeAnimationData(
            final @NotNull String id,
            final @NotNull ConfigurationNode configurationNode
    ) {
        super(configurationNode);
        this.id = id;
    }

    @Override
    public @NotNull String id() {
        return this.id;
    }

    @Override
    public @NotNull AnimationData section(final @NotNull String path) {
        final Object[] parentPath = super.resolvePathNode(path);
        final ConfigurationNode parentNode = super.configurationNode.node(parentPath);
        return new NodeAnimationData(this.id, parentNode);
    }
}
