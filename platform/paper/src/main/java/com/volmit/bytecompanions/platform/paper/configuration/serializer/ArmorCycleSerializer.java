package com.volmit.bytecompanions.platform.paper.configuration.serializer;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.core.companion.ArmorCycleEntry;

import java.lang.reflect.Type;
import java.util.List;

public final class ArmorCycleSerializer implements TypeSerializer<ArmorCycle> {

    public static final ArmorCycleSerializer INSTANCE = new ArmorCycleSerializer();

    @Override
    public @Nullable ArmorCycle deserialize(final @NotNull Type type, final @NotNull ConfigurationNode node) throws SerializationException {
        if (node.virtual()) {
            return null;
        }

        final int intervalTicks = node.node("interval-ticks").getInt(40);

        final List<ArmorCycleEntry> frames;
        try {
            frames = node.node("frames").getList(ArmorCycleEntry.class);
        } catch (final SerializationException e) {
            throw new SerializationException(node, type, "No se pudo deserializar 'frames' del armor cycle", e);
        }

        return new ArmorCycle(intervalTicks, frames);
    }

    @Override
    public void serialize(
            final @NotNull Type type,
            final @Nullable ArmorCycle obj,
            final @NotNull ConfigurationNode node
    ) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }

        node.node("interval-ticks").set(obj.intervalTicks());
        node.node("frames").setList(ArmorCycleEntry.class, obj.frames());
    }
}