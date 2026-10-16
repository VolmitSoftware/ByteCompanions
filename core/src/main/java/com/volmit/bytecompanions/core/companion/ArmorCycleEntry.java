package com.volmit.bytecompanions.core.companion;

import org.bukkit.Color;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

public record ArmorCycleEntry(
        @Nullable Color rgbColor,
        @Nullable TrimPattern trimPattern,
        @Nullable TrimMaterial trimMaterial
) {

}
