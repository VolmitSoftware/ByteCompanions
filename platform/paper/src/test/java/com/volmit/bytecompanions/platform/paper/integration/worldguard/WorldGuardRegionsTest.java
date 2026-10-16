package com.volmit.bytecompanions.platform.paper.integration.worldguard;

import com.volmit.bytecompanions.platform.paper.integration.ServerPolicy;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldGuardRegionsTest {
    private final StateFlag visuals = new StateFlag("bytecompanions-visuals", true);
    private final StateFlag bonuses = new StateFlag("bytecompanions-bonuses", true);
    private final WorldGuardRegions hook = new WorldGuardRegions(this.visuals, this.bonuses, Logger.getAnonymousLogger());

    @Test
    void flagsDefaultToAllowAndCompatibleRegistrationsAreReused() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        StateFlag first = WorldGuardRegions.register(registry, "bytecompanions-visuals");
        assertEquals(StateFlag.State.ALLOW, first.getDefault());
        assertSame(first, WorldGuardRegions.register(registry, "bytecompanions-visuals"));
        assertEquals(ServerPolicy.Decision.ALLOW, this.hook.evaluate(regions(), null));
    }

    @Test
    void incompatibleFlagRegistrationFailsExplicitly() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        registry.register(new StringFlag("bytecompanions-visuals"));
        assertThrows(IllegalStateException.class, () -> WorldGuardRegions.register(registry, "bytecompanions-visuals"));
    }

    @Test
    void worldGuardResolvesPriorityAndIndependentFlags() {
        ProtectedRegion low = region("low", 0);
        low.setFlag(this.visuals, StateFlag.State.DENY);
        ProtectedRegion high = region("high", 10);
        high.setFlag(this.visuals, StateFlag.State.ALLOW);
        high.setFlag(this.bonuses, StateFlag.State.DENY);
        assertEquals(new ServerPolicy.Decision(true, false), this.hook.evaluate(regions(low, high), null));
        high.setPriority(0);
        assertEquals(ServerPolicy.Decision.DENY, this.hook.evaluate(regions(low, high), null));
    }

    @Test
    void worldGuardResolvesMembershipGroups() {
        ProtectedRegion arena = region("arena", 0);
        arena.setFlag(this.bonuses, StateFlag.State.DENY);
        arena.setFlag(this.bonuses.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);
        assertEquals(new ServerPolicy.Decision(true, false),
                this.hook.evaluate(regions(arena), ignored -> Association.NON_MEMBER));
        assertEquals(ServerPolicy.Decision.ALLOW,
                this.hook.evaluate(regions(arena), ignored -> Association.MEMBER));
    }

    @Test
    void missingRuntimeFailsClosedAndLogsTheFullFailureOnce() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        WorldGuardRegions unavailable = new WorldGuardRegions(this.visuals, this.bonuses, logger);
        assertEquals(ServerPolicy.Decision.DENY, unavailable.apply(null));
        assertEquals(ServerPolicy.Decision.DENY, unavailable.apply(null));
        assertEquals(1, records.size());
        assertNotNull(records.getFirst().getThrown());
    }

    private static ProtectedRegion region(String name, int priority) {
        ProtectedRegion region = new ProtectedCuboidRegion(name, BlockVector3.at(0, 0, 0), BlockVector3.at(10, 100, 10));
        region.setPriority(priority);
        return region;
    }

    private static RegionResultSet regions(ProtectedRegion... regions) {
        return new RegionResultSet(new ArrayList<>(List.of(regions)), null);
    }
}
