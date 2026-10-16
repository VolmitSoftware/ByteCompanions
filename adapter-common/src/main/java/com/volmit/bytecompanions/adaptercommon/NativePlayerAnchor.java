package com.volmit.bytecompanions.adaptercommon;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;

public final class NativePlayerAnchor extends PlayerAnchor {
    public NativePlayerAnchor(@NotNull Player player) {
        super(player);
    }

    @Override
    public float bodyYaw() {
        return ((CraftPlayer) this.player()).getHandle().yBodyRot;
    }
}
