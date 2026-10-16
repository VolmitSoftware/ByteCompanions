package com.volmit.bytecompanions.features.buffs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.configuration.buff.MobLoot;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public record ResolvedBuff(
        @Nullable String companionTypeId,
        int priority,
        @NotNull Buff buff
) {
    private static final ResolvedBuff BOOSTER = new ResolvedBuff(null, 0, new Buff());
    private static final ResolvedBuff MOB_BOOSTER = new ResolvedBuff(null, 0, new MobLoot());

    public static ResolvedBuff booster(BuffKey key) {
        return key == BuffKey.MOB_LOOT ? MOB_BOOSTER : BOOSTER;
    }
}
