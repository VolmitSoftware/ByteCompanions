package com.volmit.bytecompanions.platform.paper.configuration;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import com.volmit.bytecompanions.api.companion.ConfigurationData;

import java.util.List;

public class NodeConfigurationData implements ConfigurationData {

    protected final ConfigurationNode configurationNode;
    public NodeConfigurationData(final @NotNull ConfigurationNode configurationNode) {
        this.configurationNode = configurationNode;
    }

    @Override
    public boolean has(final @NotNull String path) {
        return !this.resolveNode(path).virtual();
    }

    @Override
    public @Nullable Object get(final @NotNull String path) {
        return this.resolveNode(path).raw();
    }

    @Override
    public @Nullable ConfigurationData section(final @NotNull String path) {
        final ConfigurationNode child = this.resolveNode(path);
        return child.virtual() ? null : new NodeConfigurationData(child);
    }

    @Override
    public @Nullable String getString(final @NotNull String path) {
        return this.resolveNode(path).getString();
    }

    @Override
    public @NotNull String getStringOr(final @NotNull String path, final @NotNull String defaultValue) {
        return this.resolveNode(path).getString(defaultValue);
    }

    @Override
    public @Nullable List<String> getStringList(final @NotNull String path) {
        try {
            return this.resolveNode(path).getList(String.class);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getIntOr(final @NotNull String path, final int defaultValue) {
        return this.resolveNode(path).getInt(defaultValue);
    }

    @Override
    public double getDoubleOr(final @NotNull String path, final double defaultValue) {
        return this.resolveNode(path).getDouble(defaultValue);
    }

    @Override
    public float getFloatOr(final @NotNull String path, final float defaultValue) {
        return this.resolveNode(path).getFloat(defaultValue);
    }

    @Override
    public long getLongOr(final @NotNull String path, final long defaultValue) {
        return this.resolveNode(path).getLong(defaultValue);
    }

    @Override
    public boolean getBooleanOr(final @NotNull String path, final boolean defaultValue) {
        return this.resolveNode(path).getBoolean(defaultValue);
    }

    private @NotNull ConfigurationNode resolveNode(final @NotNull String key) {
        final Object[] nodes = this.resolvePathNode(key);
        return this.configurationNode.node(nodes);
    }

    protected Object[] resolvePathNode(final @NotNull String path) {
        return StringUtils.split(path, '.');
    }

}