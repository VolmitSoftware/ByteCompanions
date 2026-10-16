package com.volmit.bytecompanions.platform.paper.item;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;

public abstract class IdItemStackProvider implements ItemStackProvider {

    private final String id;
    public IdItemStackProvider(final @NotNull String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
