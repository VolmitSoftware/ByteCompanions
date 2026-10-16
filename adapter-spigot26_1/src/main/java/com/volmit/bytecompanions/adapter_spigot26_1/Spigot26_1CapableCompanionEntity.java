package com.volmit.bytecompanions.adapter_spigot26_1;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.Rotations;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.adaptercommon.AbstractCapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

public class Spigot26_1CapableCompanionEntity extends AbstractCapableCompanionEntity {
    private static final GsonComponentSerializer COMPONENTS = GsonComponentSerializer.gson();
    private final Map<ArmorStand, Map<Integer, SynchedEntityData.DataValue<?>>> metadata = new WeakHashMap<>();

    public Spigot26_1CapableCompanionEntity(
            @NotNull CompanionViewerEnvironment viewers,
            @NotNull CompanionAnchor anchor, @NotNull CompanionModel model,
            @NotNull AppearanceData appearance, @NotNull Component customName,
            int viewDistance, int viewerTrackerDelay, int entityId
    ) {
        super(viewers, anchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }

    @Override
    public float rotX(@NotNull Rotations rotations) {
        return rotations.x();
    }

    @Override
    public float rotY(@NotNull Rotations rotations) {
        return rotations.y();
    }

    @Override
    public float rotZ(@NotNull Rotations rotations) {
        return rotations.z();
    }

    @Override
    protected List<SynchedEntityData.DataValue<?>> metadataValues(ArmorStand stand) {
        Map<Integer, SynchedEntityData.DataValue<?>> snapshot = metadata.computeIfAbsent(stand, ignored -> new TreeMap<>());
        mergeMetadata(snapshot, stand.getEntityData().getNonDefaultValues());
        mergeMetadata(snapshot, stand.getEntityData().packDirty());
        return List.copyOf(snapshot.values());
    }

    private static void mergeMetadata(Map<Integer, SynchedEntityData.DataValue<?>> snapshot,
                                      @Nullable List<SynchedEntityData.DataValue<?>> values) {
        if (values == null) {
            return;
        }
        for (SynchedEntityData.DataValue<?> value : values) {
            snapshot.put(value.id(), value);
        }
    }

    @Override
    protected net.minecraft.network.chat.Component toNativeComponent(@Nullable Component component) {
        return component == null ? null : CraftChatMessage.fromJSON(COMPONENTS.serialize(component));
    }

    @Override
    protected Component fromNativeComponent(net.minecraft.network.chat.Component component) {
        return component == null ? Component.empty() : COMPONENTS.deserialize(CraftChatMessage.toJSON(component));
    }
}
