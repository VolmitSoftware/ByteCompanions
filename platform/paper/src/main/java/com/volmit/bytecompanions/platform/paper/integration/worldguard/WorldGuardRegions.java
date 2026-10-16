package com.volmit.bytecompanions.platform.paper.integration.worldguard;

import com.volmit.bytecompanions.platform.paper.integration.ServerPolicy;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorldGuardRegions implements Function<Player, ServerPolicy.Decision> {
    private final StateFlag visuals;
    private final StateFlag bonuses;
    private final Logger logger;
    private final Set<String> failures = ConcurrentHashMap.newKeySet();

    WorldGuardRegions(StateFlag visuals, StateFlag bonuses, Logger logger) {
        this.visuals = visuals;
        this.bonuses = bonuses;
        this.logger = logger;
    }

    public static WorldGuardRegions register(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        return new WorldGuardRegions(register(registry, "bytecompanions-visuals"),
                register(registry, "bytecompanions-bonuses"), logger);
    }

    static StateFlag register(FlagRegistry registry, String name) {
        StateFlag flag = new StateFlag(name, true);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag stateFlag) return stateFlag;
            throw new IllegalStateException("WorldGuard flag " + name + " has an incompatible type", conflict);
        }
    }

    @Override
    public ServerPolicy.Decision apply(Player player) {
        String context = "runtime";
        try {
            if (!WorldGuardPlugin.inst().isEnabled()) {
                throw new IllegalStateException("WorldGuard was disabled while its companion restrictions are active");
            }
            Location location = player.getLocation();
            World world = BukkitAdapter.adapt(location.getWorld());
            context = world.getName();
            if (!WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(world).useRegions) {
                this.recovered(context);
                return ServerPolicy.Decision.ALLOW;
            }
            RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);
            if (manager == null) throw new IllegalStateException("WorldGuard region data is unavailable for " + world.getName());
            ApplicableRegionSet regions = manager.getApplicableRegions(
                    BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            LocalPlayer subject = WorldGuardPlugin.inst().wrapPlayer(player);
            ServerPolicy.Decision decision = this.evaluate(regions, subject);
            this.recovered(context);
            return decision;
        } catch (RuntimeException | LinkageError failure) {
            if (this.failures.add(context)) {
                this.logger.log(Level.SEVERE, "WorldGuard companion restrictions failed for " + context
                        + "; visuals and bonuses are suspended", failure);
            }
            return ServerPolicy.Decision.DENY;
        }
    }

    private void recovered(String context) {
        if (this.failures.remove(context)) this.logger.info("WorldGuard companion restrictions recovered for " + context);
        if (this.failures.remove("runtime")) this.logger.info("WorldGuard companion runtime recovered");
    }

    ServerPolicy.Decision evaluate(ApplicableRegionSet regions, RegionAssociable subject) {
        return new ServerPolicy.Decision(regions.testState(subject, this.visuals), regions.testState(subject, this.bonuses));
    }
}
