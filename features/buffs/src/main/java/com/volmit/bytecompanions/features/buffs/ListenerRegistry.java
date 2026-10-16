package com.volmit.bytecompanions.features.buffs;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Configuration;
import com.volmit.bytecompanions.features.buffs.configuration.Vanilla;
import com.volmit.bytecompanions.features.buffs.listener.BlockDropListener;
import com.volmit.bytecompanions.features.buffs.listener.BlockExperienceListener;
import com.volmit.bytecompanions.features.buffs.listener.ExperienceListener;
import com.volmit.bytecompanions.features.buffs.listener.FishingListener;
import com.volmit.bytecompanions.features.buffs.listener.FurnaceExperienceListener;
import com.volmit.bytecompanions.features.buffs.listener.MobExperienceListener;
import com.volmit.bytecompanions.features.buffs.listener.MobLootListener;
import com.volmit.bytecompanions.features.buffs.listener.PlayerQuitListener;

import com.volmit.bytecompanions.features.buffs.util.BuffKey;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ListenerRegistry {

    private final BuffsFeature feature;
    private final JavaPlugin plugin;
    private final Configuration configuration;
    private final List<Listener> listeners = new ArrayList<>();

    public ListenerRegistry(
            JavaPlugin plugin,
            final @NotNull BuffsFeature feature,
            final @NotNull Configuration configuration
    ) {
        this.feature = feature;
        this.plugin = plugin;
        this.configuration = configuration;
    }

    public void registerAll() {
        boolean pickupExperienceSupported = BuffPlatformSupport.supportsPickupExperience();
        BuffPlatformSupport.validate(this.configuration, pickupExperienceSupported);
        final Collection<Companions> companions = this.configuration.companions().values();

        // Always registered — handles cooldown cleanup on disconnect.
        this.register(new PlayerQuitListener(this.feature));

        if (pickupExperienceSupported && (this.anyEnabled(companions, vanilla -> vanilla.experience().enabled()) || this.feature.hasBoosterChannel(BuffKey.EXPERIENCE))) {
            this.register(new ExperienceListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.experienceMob().enabled()) || this.feature.hasBoosterChannel(BuffKey.EXPERIENCE_MOB)) {
            this.register(new MobExperienceListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.mobLoot().enabled()) || this.feature.hasBoosterChannel(BuffKey.MOB_LOOT)) {
            this.register(new MobLootListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.experienceBlock().enabled()) || this.feature.hasBoosterChannel(BuffKey.EXPERIENCE_BLOCK)) {
            this.register(new BlockExperienceListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.blockDrops().enabled()) || this.feature.hasBoosterChannel(BuffKey.BLOCK_DROPS)) {
            this.register(new BlockDropListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.experienceFurnace().enabled()) || this.feature.hasBoosterChannel(BuffKey.EXPERIENCE_FURNACE)) {
            this.register(new FurnaceExperienceListener(this.feature));
        }

        if (this.anyEnabled(companions, vanilla -> vanilla.experienceFishing().enabled()) || this.anyEnabled(companions, v -> v.fishingLoot().enabled())
                || this.feature.hasBoosterChannel(BuffKey.EXPERIENCE_FISHING) || this.feature.hasBoosterChannel(BuffKey.FISHING_LOOT)) {
            this.register(new FishingListener(this.feature));
        }
    }

    private void register(Listener listener) {
        this.listeners.add(listener);
        this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
    }

    public void unregisterAll() {
        for (Listener listener : this.listeners) {
            HandlerList.unregisterAll(listener);
        }
        this.listeners.clear();
    }

    private boolean anyEnabled(
            final @NotNull Collection<Companions> companions,
            final @NotNull Function<Vanilla, Boolean> predicate
    ) {
        return companions.stream().anyMatch(companion -> predicate.apply(companion.vanilla()));
    }
}
