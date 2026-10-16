package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class NaturalExperienceFixture implements Listener {
    private final Map<UUID, Observation> observations = new ConcurrentHashMap<>();
    private final Map<Event, Integer> bases = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    String execute(Player player, String[] arguments) {
        if (arguments.length < 2) throw new IllegalArgumentException("naturalxp begin|status|mob|furnace");
        if (arguments[1].equals("begin")) {
            if (arguments.length != 3 || !List.of("fishing", "furnace", "mob").contains(arguments[2])) {
                throw new IllegalArgumentException("naturalxp begin fishing|furnace|mob");
            }
            player.setLevel(0);
            player.setExp(0);
            player.setTotalExperience(0);
            observations.put(player.getUniqueId(), new Observation(arguments[2]));
        }
        Observation observation = observations.get(player.getUniqueId());
        if (observation == null) throw new IllegalStateException("Begin a natural XP observation first");
        if (arguments[1].equals("mob")) {
            Zombie zombie = player.getWorld().spawn(player.getLocation().add(-2, 0, 0), Zombie.class, target -> {
                target.setAdult();
                target.setAI(false);
                target.setSilent(true);
                target.getEquipment().clear();
                target.setHealth(1);
            });
            observation.mob = zombie.getUniqueId();
            observation.target = zombie;
            observation.entityId = zombie.getEntityId();
        }
        if (arguments[1].equals("furnace")) {
            observation.furnace = player.getLocation().getBlock().getRelative(1, 0, 0).getLocation();
            observation.furnace.getBlock().setType(Material.AIR);
            observation.furnace.getBlock().setType(Material.FURNACE);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", observation.source);
        result.put("events", observation.events);
        result.put("fishStates", observation.fishStates);
        result.put("smelted", observation.smelted);
        result.put("pickupTotal", observation.pickupTotal);
        result.put("totalExperience", player.getTotalExperience());
        result.put("entityId", observation.entityId);
        result.put("attacks", observation.attacks);
        if (observation.target != null) result.put("mobHealth", observation.target.getHealth());
        if (observation.furnace != null) result.put("furnace", List.of(observation.furnace.getBlockX(), observation.furnace.getBlockY(), observation.furnace.getBlockZ()));
        return "BCQA NATURAL_XP " + gson.toJson(result);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fishingBase(PlayerFishEvent event) {
        Observation observation = observations.get(event.getPlayer().getUniqueId());
        if (observation == null || !observation.source.equals("fishing")) return;
        observation.fishStates.add(event.getState().name());
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            event.getHook().setApplyLure(false);
            event.getHook().setMinWaitTime(20);
            event.getHook().setMaxWaitTime(40);
            event.getHook().setMinLureTime(20);
            event.getHook().setMaxLureTime(40);
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) bases.put(event, event.getExpToDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fishingFinal(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) complete(event.getPlayer(), event, event.getExpToDrop());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void furnaceBase(FurnaceExtractEvent event) {
        Observation observation = observations.get(event.getPlayer().getUniqueId());
        if (observation != null && observation.source.equals("furnace") && event.getBlock().getLocation().equals(observation.furnace)) bases.put(event, event.getExpToDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void furnaceFinal(FurnaceExtractEvent event) { complete(event.getPlayer(), event, event.getExpToDrop()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void smelt(FurnaceSmeltEvent event) {
        for (Observation observation : observations.values()) {
            if (event.getBlock().getLocation().equals(observation.furnace)) observation.smelted += event.getResult().getAmount();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mobBase(EntityDeathEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player player)) return;
        Observation observation = observations.get(player.getUniqueId());
        if (observation != null && event.getEntity().getUniqueId().equals(observation.mob)) bases.put(event, event.getDroppedExp());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mobFinal(EntityDeathEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player player) complete(player, event, event.getDroppedExp());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void attack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Observation observation = observations.get(player.getUniqueId());
        if (observation != null && event.getEntity().getUniqueId().equals(observation.mob)) {
            observation.attacks.add(Map.of("cancelled", event.isCancelled(), "damage", event.getFinalDamage(),
                    "healthBefore", observation.target.getHealth(), "distance", player.getLocation().distance(event.getEntity().getLocation())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void experience(PlayerExpChangeEvent event) {
        Observation observation = observations.get(event.getPlayer().getUniqueId());
        if (observation != null) observation.pickupTotal += event.getAmount();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { observations.remove(event.getPlayer().getUniqueId()); }

    private void complete(Player player, Event event, int result) {
        Integer base = bases.remove(event);
        Observation observation = observations.get(player.getUniqueId());
        if (base != null && observation != null) observation.events.add(Map.of("base", base, "result", result, "event", event.getEventName()));
    }

    private static final class Observation {
        final String source;
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<String> fishStates = new ArrayList<>();
        final List<Map<String, Object>> attacks = new ArrayList<>();
        UUID mob;
        Zombie target;
        Location furnace;
        int entityId;
        int smelted;
        int pickupTotal;
        Observation(String source) { this.source = source; }
    }
}
