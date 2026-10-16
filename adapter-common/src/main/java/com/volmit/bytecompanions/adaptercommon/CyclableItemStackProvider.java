package com.volmit.bytecompanions.adaptercommon;

import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.core.companion.ArmorCycle;

public interface CyclableItemStackProvider extends ItemStackProvider {

    @Nullable ArmorCycle cycle();

}
