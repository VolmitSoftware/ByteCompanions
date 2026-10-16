package com.volmit.bytecompanions.platform.paper.integration;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class ServerPolicy {
    private final Set<String> hiddenWorlds;
    private final Set<String> bonusDisabledWorlds;
    private final Function<Player, Decision> regionRules;

    public ServerPolicy(Set<String> hiddenWorlds, Set<String> bonusDisabledWorlds,
                        Function<Player, Decision> regionRules) {
        this.hiddenWorlds = Set.copyOf(hiddenWorlds);
        this.bonusDisabledWorlds = Set.copyOf(bonusDisabledWorlds);
        this.regionRules = Objects.requireNonNull(regionRules);
    }

    public Decision evaluate(Player player) {
        String world = player.getWorld().getName();
        boolean visuals = !this.hiddenWorlds.contains(world);
        boolean bonuses = !this.bonusDisabledWorlds.contains(world);
        if (!visuals && !bonuses) return Decision.DENY;
        Decision regions = this.regionRules.apply(player);
        return new Decision(visuals && regions.visualsAllowed(), bonuses && regions.bonusesAllowed());
    }

    public boolean visualsAllowed(Player player) {
        return this.evaluate(player).visualsAllowed();
    }

    public boolean worldVisualsAllowed(Player player) {
        return !this.hiddenWorlds.contains(player.getWorld().getName());
    }

    public boolean bonusesAllowed(Player player) {
        return this.evaluate(player).bonusesAllowed();
    }

    public record Decision(boolean visualsAllowed, boolean bonusesAllowed) {
        public static final Decision ALLOW = new Decision(true, true);
        public static final Decision DENY = new Decision(false, false);
    }
}
