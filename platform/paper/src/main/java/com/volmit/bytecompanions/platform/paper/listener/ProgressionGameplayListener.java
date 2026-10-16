package com.volmit.bytecompanions.platform.paper.listener;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class ProgressionGameplayListener implements Listener, AutoCloseable {
    private static final Set<Material> ORES = Set.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE);
    private final JavaPlugin plugin;
    private final ByteCompanions companions;
    private final ProgressionService progression;
    private final NamespacedKey placedOres;
    private final NamespacedKey naturalMob;
    private final Map<Event, Boolean> original = Collections.synchronizedMap(new WeakHashMap<>());
    private final ProgressionActivityCooldowns cooldowns = new ProgressionActivityCooldowns(System::nanoTime);
    private volatile boolean active = true;
    private volatile Predicate<Player> bonusesAllowed = player -> true;

    public ProgressionGameplayListener(JavaPlugin plugin, ByteCompanions companions, ProgressionService progression) {
        this.plugin = plugin;
        this.companions = companions;
        this.progression = progression;
        this.placedOres = new NamespacedKey(plugin, "progression_placed_ores");
        this.naturalMob = new NamespacedKey(plugin, "progression_natural_mob");
    }

    public void bonusesAllowed(Predicate<Player> allowed) {
        this.bonusesAllowed = Objects.requireNonNull(allowed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (this.active && ORES.contains(event.getBlockPlaced().getType())) {
            this.markPlaced(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        this.markMoved(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        this.markMoved(event.getBlocks(), event.getDirection());
    }

    private void markMoved(List<Block> blocks, BlockFace direction) {
        if (this.active) {
            for (Block block : blocks) {
                if (ORES.contains(block.getType())) {
                    this.markPlaced(block.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ()));
                }
            }
        }
    }

    private void markPlaced(Location location) {
        Runnable mark = () -> {
            if (this.active) {
                PersistentDataContainer data = location.getChunk().getPersistentDataContainer();
                long[] existing = data.getOrDefault(this.placedOres, PersistentDataType.LONG_ARRAY, new long[0]);
                long key = blockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                if (Arrays.stream(existing).noneMatch(value -> value == key)) {
                    long[] updated = Arrays.copyOf(existing, existing.length + 1);
                    updated[existing.length] = key;
                    data.set(this.placedOres, PersistentDataType.LONG_ARRAY, updated);
                }
            }
        };
        if (FoliaScheduler.isOwnedByCurrentRegion(location)) {
            mark.run();
        } else if (!FoliaScheduler.runRegion(this.plugin, location, mark)) {
            this.plugin.getLogger().warning("Could not record moved ore for progression in " + location);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureBlock(BlockBreakEvent event) {
        this.original.put(event, this.active && ORES.contains(event.getBlock().getType()) && event.getExpToDrop() > 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        boolean eligible = Boolean.TRUE.equals(this.original.remove(event));
        if (!this.active || event.isCancelled()) {
            return;
        }
        PersistentDataContainer data = event.getBlock().getChunk().getPersistentDataContainer();
        long[] placed = data.getOrDefault(this.placedOres, PersistentDataType.LONG_ARRAY, new long[0]);
        long key = blockKey(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (Arrays.stream(placed).anyMatch(value -> value == key)) {
            long[] remaining = Arrays.stream(placed).filter(value -> value != key).toArray();
            if (remaining.length == 0) {
                data.remove(this.placedOres);
            } else {
                data.set(this.placedOres, PersistentDataType.LONG_ARRAY, remaining);
            }
            return;
        }
        if (eligible) {
            this.award(event.getPlayer(), "block_break", event.getBlock().getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (this.active) {
            boolean natural = switch (event.getSpawnReason()) {
                case NATURAL, CHUNK_GEN, RAID -> true;
                default -> false;
            };
            event.getEntity().getPersistentDataContainer().set(this.naturalMob, PersistentDataType.BYTE, (byte) (natural ? 1 : 0));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureMob(EntityDeathEvent event) {
        this.original.put(event, this.active && !(event.getEntity() instanceof Player) && event.getDroppedExp() > 0
                && event.getEntity().getPersistentDataContainer().getOrDefault(this.naturalMob, PersistentDataType.BYTE, (byte) 0) == 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (Boolean.TRUE.equals(this.original.remove(event)) && !cancelled(event)
                && event.getDamageSource().getCausingEntity() instanceof Player player) {
            this.award(player, "mob_kill", event.getEntity().getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureFishing(PlayerFishEvent event) {
        this.original.put(event, this.active && event.getState() == PlayerFishEvent.State.CAUGHT_FISH
                && event.getCaught() instanceof Item && event.getExpToDrop() > 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishing(PlayerFishEvent event) {
        if (Boolean.TRUE.equals(this.original.remove(event)) && !event.isCancelled()) {
            this.award(event.getPlayer(), "fishing", event.getPlayer().getWorld().getName());
        }
    }

    private void award(Player player, String source, String world) {
        UUID playerId = player.getUniqueId();
        CompanionPlayer session = this.companions.getCompanionPlayerOrNull(playerId);
        long amount = this.progression.definitions().xpSources().getOrDefault(source, 0L);
        Set<String> worlds = this.progression.definitions().allowedWorlds();
        if (!this.active || session == null || amount <= 0 || !worlds.isEmpty() && !worlds.contains(world)) {
            return;
        }
        Runnable action = () -> {
            if (!this.active || !this.progression.active() || !this.progression.isLoaded(playerId)
                    || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR || !player.getWorld().getName().equals(world)
                    || !this.bonusesAllowed.test(player)
                    || this.companions.getCompanionPlayerOrNull(playerId) != session || session.companions().isEmpty()) {
                return;
            }
            if (!this.cooldowns.accept(playerId, source, this.progression.definitions().sourceCooldownMillis())) {
                return;
            }
            for (String companionId : List.copyOf(session.companions().keySet())) {
                if (!this.progression.definitions().companions().containsKey(companionId)) {
                    continue;
                }
                this.progression.award(playerId, companionId, amount).exceptionally(failure -> {
                    this.plugin.getLogger().log(Level.SEVERE, "Could not award companion progression to " + playerId, failure);
                    return null;
                });
            }
        };
        if (FoliaScheduler.isOwnedByCurrentRegion(player)) {
            action.run();
        } else if (!FoliaScheduler.runEntity(this.plugin, player, action)) {
            this.plugin.getLogger().warning("Could not schedule companion progression for " + playerId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private static boolean cancelled(Event event) {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    static long blockKey(int x, int y, int z) {
        return (long) (x & 15) << 36 | (long) (z & 15) << 32 | y & 0xffffffffL;
    }

    @Override
    public void close() {
        this.active = false;
        HandlerList.unregisterAll(this);
        this.original.clear();
        this.cooldowns.clear();
    }
}
