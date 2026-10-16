package com.volmit.bytecompanions.features.attributes.data;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ConfigurationData(Map<String, List<AttributeModifierData>> attributes) {

    public ConfigurationData(final @NotNull Map<String, List<AttributeModifierData>> attributes) {
        this.attributes = Map.copyOf(attributes);
    }

    @Contract("null -> null")
    public @Nullable List<AttributeModifierData> getAttribute(final @Nullable String name) {
        return this.attributes.get(name);
    }

    @Override
    public @NotNull Map<String, List<AttributeModifierData>> attributes() {
        return this.attributes;
    }

}
