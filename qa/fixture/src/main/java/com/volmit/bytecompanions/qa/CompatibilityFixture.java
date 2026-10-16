package com.volmit.bytecompanions.qa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class CompatibilityFixture extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> interactions = new ConcurrentHashMap<>();
    private final Map<UUID, String> cancelledCollectionEvents = new ConcurrentHashMap<>();
    private final Map<UUID, Cow> targets = new ConcurrentHashMap<>();
    private final Map<UUID, FishHook> fishingHooks = new ConcurrentHashMap<>();
    private final Set<UUID> particleAccess = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PermissionAttachment> particleAttachments = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> dashboardAttachments = new ConcurrentHashMap<>();
    private boolean regionized;
    private final GuiObservationFixture guiObservation = new GuiObservationFixture(this);
    private final NaturalExperienceFixture naturalExperience = new NaturalExperienceFixture();

    @Override
    public void onEnable() {
        this.regionized = this.regionizedRuntime();
        try {
            if (Companion.class.getMethod("customName").getReturnType() != Component.class
                    || Companion.class.getClassLoader() == getClassLoader()) {
                throw new IllegalStateException("Fixture and public companion API do not share Component identity");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the public companion API", exception);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(guiObservation, this);
        Bukkit.getPluginManager().registerEvents(naturalExperience, this);
        getLogger().info("BCQA COMPONENT_IDENTITY PASS publicAPI=true sharedComponent=true");
    }

    @Override
    public void onDisable() {
        cancelledCollectionEvents.clear();
        targets.values().forEach(this::removeAtShutdown);
        targets.clear();
        fishingHooks.values().forEach(this::removeAtShutdown);
        fishingHooks.clear();
        particleAccess.clear();
        for (PermissionAttachment attachment : particleAttachments.values()) {
            if (attachment.getPermissible() instanceof Player player
                    && (!this.regionized || Bukkit.isOwnedByCurrentRegion(player))) {
                player.removeAttachment(attachment);
            }
        }
        particleAttachments.clear();
        for (PermissionAttachment attachment : dashboardAttachments.values()) {
            if (attachment.getPermissible() instanceof Player player
                    && (!this.regionized || Bukkit.isOwnedByCurrentRegion(player))) {
                player.removeAttachment(attachment);
            }
        }
        dashboardAttachments.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelCollectionEquip(CompanionEquipEvent event) {
        if ("equip".equals(cancelledCollectionEvents.get(event.player().getUniqueId()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelCollectionUnequip(CompanionUnequipEvent event) {
        if ("unequip".equals(cancelledCollectionEvents.get(event.player().getUniqueId()))) event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length == 1 && arguments[0].equals("h2diagnostics") && sender.isOp()) {
            sender.sendMessage(H2DiagnosticsFixture.inspect(this));
            return true;
        }
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage("BCQA operator player required");
            return true;
        }
        String operation = arguments.length == 0 ? "status" : arguments[0];
        switch (operation) {
            case "naturalxp" -> player.sendMessage(naturalExperience.execute(player, arguments));
            case "ticktimes" -> player.sendMessage("BCQA TICKS {\"tick\":" + Bukkit.getCurrentTick()
                    + ",\"timesNs\":" + java.util.Arrays.toString(getServer().getTickTimes()) + "}");
            case "commands" -> {
                player.updateCommands();
                player.sendMessage("BCQA COMMANDS_REFRESHED");
            }
            case "metrics" -> player.sendMessage(MetricsObservationFixture.inspect());
            case "privacy", "polish" -> player.sendMessage(ServerPolishFixture.execute(this, player, arguments));
            case "collection" -> player.sendMessage(CollectionObservationFixture.inspect(player));
            case "collectioncancel" -> {
                if (arguments.length != 2 || !Set.of("equip", "unequip", "clear").contains(arguments[1])) {
                    throw new IllegalArgumentException("Usage: /bcqa collectioncancel equip|unequip|clear");
                }
                if (arguments[1].equals("clear")) cancelledCollectionEvents.remove(player.getUniqueId());
                else cancelledCollectionEvents.put(player.getUniqueId(), arguments[1]);
                player.sendMessage("BCQA COLLECTION_CANCEL " + arguments[1]);
            }
            case "api" -> player.sendMessage(PublicApiFixture.execute(player, arguments));
            case "itemsadder" -> player.sendMessage(ItemsAdderFixture.inspect(player, arguments));
            case "nexo" -> {
                try {
                    player.sendMessage(NexoFixture.inspect(player, arguments));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Genuine Nexo API inspection failed", failure);
                }
            }
            case "papi" -> {
                if (arguments.length != 3 || !Set.of("owner", "async").contains(arguments[1])) {
                    throw new IllegalArgumentException("Usage: /bcqa papi owner|async <placeholder>");
                }
                if (arguments[1].equals("async")) {
                    CompletableFuture.supplyAsync(() -> PlaceholderFixture.parse(player, arguments[2]))
                            .whenComplete((value, failure) -> this.runOwned(player, () -> {
                                if (failure != null) {
                                    getLogger().log(Level.SEVERE, "Placeholder fixture failed", failure);
                                    player.sendMessage("BCQA PAPI ERROR");
                                } else {
                                    player.sendMessage(value);
                                }
                            }));
                } else {
                    player.sendMessage(PlaceholderFixture.parse(player, arguments[2]));
                }
            }
            case "status" -> player.sendMessage("BCQA COMPONENT_IDENTITY PASS");
            case "vault" -> player.sendMessage(VaultObservationFixture.inspect(player));
            case "guitrace" -> {
                if (arguments.length != 2 || !List.of("start", "state", "stop").contains(arguments[1])) {
                    throw new IllegalArgumentException("Usage: /bcqa guitrace start|state|stop");
                }
                player.sendMessage(guiObservation.inspect(player, arguments[1]));
            }
            case "paymentitems" -> {
                if (arguments.length != 3) throw new IllegalArgumentException("Usage: /bcqa paymentitems <plain> <named>");
                int plain = Integer.parseInt(arguments[1]);
                int named = Integer.parseInt(arguments[2]);
                if (plain < 0 || plain > 64 || named < 0 || named > 64) throw new IllegalArgumentException("QA item count must be 0-64");
                player.getInventory().clear();
                if (plain > 0) player.getInventory().addItem(new ItemStack(Material.EMERALD, plain));
                if (named > 0) {
                    ItemStack stack = new ItemStack(Material.EMERALD, named);
                    org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                    meta.setDisplayName("QA Named Emerald");
                    stack.setItemMeta(meta);
                    player.getInventory().addItem(stack);
                }
                player.sendMessage("BCQA PAYMENT_ITEMS plain=" + plain + " named=" + named);
            }
            case "naturalore" -> {
                Location location = player.getLocation().add(2, 0, 0);
                if (regionized && !Bukkit.isOwnedByCurrentRegion(location)) throw new IllegalStateException("QA ore must be on player's region");
                location.getBlock().setType(Material.DIAMOND_ORE, false);
                player.sendMessage("BCQA NATURAL_ORE x=" + location.getBlockX() + " y=" + location.getBlockY() + " z=" + location.getBlockZ());
            }
            case "progression" -> {
                if (arguments.length != 2 || !arguments[1].matches("[a-z0-9_.-]{1,64}")) {
                    throw new IllegalArgumentException("Usage: /bcqa progression <companion>");
                }
                try {
                    Plugin plugin = Bukkit.getPluginManager().getPlugin("ByteCompanions");
                    Object service = plugin.getClass().getMethod("progressionService").invoke(plugin);
                    if (service == null) {
                        player.sendMessage("BCQA PROGRESSION null");
                    } else {
                        Object snapshot = service.getClass().getMethod("snapshot", UUID.class, String.class)
                                .invoke(service, player.getUniqueId(), arguments[1]);
                        Map<String, Object> state = new java.util.LinkedHashMap<>();
                        for (java.lang.reflect.RecordComponent component : snapshot.getClass().getRecordComponents()) {
                            state.put(component.getName(), component.getAccessor().invoke(snapshot));
                        }
                        Map<String, Double> rates = new java.util.LinkedHashMap<>();
                        for (String channel : List.of("companion_xp", "experience_block", "block_drops")) {
                            rates.put(channel, (Double) service.getClass().getMethod("boosterPercent", UUID.class, String.class)
                                    .invoke(service, player.getUniqueId(), channel));
                        }
                        state.put("rates", rates);
                        player.sendMessage("BCQA PROGRESSION " + new com.google.gson.Gson().toJson(state));
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Could not inspect progression snapshot", failure);
                }
            }
            case "permission" -> {
                if (arguments.length != 4 || !arguments[2].matches("bytecompanions\\.[a-z0-9_.-]+")
                        || !(arguments[3].equals("true") || arguments[3].equals("false") || arguments[3].equals("clear"))) {
                    throw new IllegalArgumentException("Usage: /bcqa permission <online-player-uuid> <bytecompanions.node> true|false|clear");
                }
                UUID targetId = UUID.fromString(arguments[1]);
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) {
                    throw new IllegalArgumentException("Permission target must be online");
                }
                String node = arguments[2];
                String value = arguments[3];
                this.runOwned(target, () -> {
                    PermissionAttachment attachment = dashboardAttachments.get(targetId);
                    if (value.equals("clear")) {
                        if (attachment != null) {
                            attachment.unsetPermission(node);
                            if (attachment.getPermissions().isEmpty()) {
                                target.removeAttachment(attachment);
                                dashboardAttachments.remove(targetId);
                            }
                        }
                    } else {
                        if (attachment == null) {
                            attachment = target.addAttachment(this);
                            dashboardAttachments.put(targetId, attachment);
                        }
                        attachment.setPermission(node, Boolean.parseBoolean(value));
                    }
                    boolean effective = target.hasPermission(node);
                    this.runOwned(player, () -> player.sendMessage("BCQA PERMISSION player=" + targetId
                            + " node=" + node + " value=" + value + " effective=" + effective));
                });
            }
            case "particleaccess" -> {
                if (arguments.length != 3 || !(arguments[2].equals("grant") || arguments[2].equals("revoke"))) {
                    throw new IllegalArgumentException("Usage: /bcqa particleaccess <player-uuid> grant|revoke");
                }
                UUID targetId = UUID.fromString(arguments[1]);
                boolean grant = arguments[2].equals("grant");
                if (grant) {
                    particleAccess.add(targetId);
                } else {
                    particleAccess.remove(targetId);
                }
                Runnable reply = () -> player.sendMessage("BCQA PARTICLE_ACCESS player=" + targetId + " enabled=" + grant);
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) {
                    reply.run();
                } else {
                    this.runOwned(target, () -> {
                        this.updateParticleAccess(target);
                        this.runOwned(player, reply);
                    });
                }
            }
            case "owner" -> {
                if (arguments.length != 2) {
                    throw new IllegalArgumentException("Usage: /bcqa owner <online-player>");
                }
                Player target = Bukkit.getPlayerExact(arguments[1]);
                if (target == null) {
                    throw new IllegalArgumentException("The ownership target must be online");
                }
                boolean selfOwned = !this.regionized || Bukkit.isOwnedByCurrentRegion(player);
                boolean targetOwned = !this.regionized || Bukkit.isOwnedByCurrentRegion(target);
                player.sendMessage("BCQA OWNER self=" + selfOwned + " target=" + targetOwned + " player=" + arguments[1]);
            }
            case "move" -> {
                if (arguments.length != 4) {
                    throw new IllegalArgumentException("Usage: /bcqa move <x> <y> <z>");
                }
                double x = Double.parseDouble(arguments[1]);
                double y = Double.parseDouble(arguments[2]);
                double z = Double.parseDouble(arguments[3]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || Math.abs(x) > 100_000 || Math.abs(z) > 100_000 || y < -64 || y > 319) {
                    throw new IllegalArgumentException("QA teleport must stay within the bounded test area");
                }
                Location destination = new Location(player.getWorld(), x, y, z);
                UUID worldId = destination.getWorld().getUID();
                if (this.regionized) {
                    player.teleportAsync(destination).whenComplete((success, failure) -> this.runOwned(player,
                            () -> player.sendMessage("BCQA MOVE success=" + (failure == null && Boolean.TRUE.equals(success))
                                    + " world=" + worldId)));
                } else {
                    player.sendMessage("BCQA MOVE success=" + player.teleport(destination) + " world=" + worldId);
                }
            }
            case "platform" -> {
                Location center = player.getLocation();
                if (center.getBlockY() <= center.getWorld().getMinHeight()
                        || center.getBlockY() + 3 >= center.getWorld().getMaxHeight()) {
                    throw new IllegalStateException("The QA platform needs four blocks of valid headroom");
                }
                ArrayList<Location> blocks = new ArrayList<>();
                ArrayList<Location> headroom = new ArrayList<>();
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        for (int dy = -1; dy <= 3; dy++) {
                            Location block = new Location(center.getWorld(), center.getBlockX() + dx,
                                    center.getBlockY() + dy, center.getBlockZ() + dz);
                            if (this.regionized && !Bukkit.isOwnedByCurrentRegion(block)) {
                                throw new IllegalStateException("The entire QA platform must belong to the caller's region");
                            }
                            (dy == -1 ? blocks : headroom).add(block);
                        }
                    }
                }
                blocks.forEach(block -> block.getBlock().setType(Material.STONE, false));
                headroom.forEach(block -> block.getBlock().setType(Material.AIR, false));
                org.bukkit.util.BoundingBox standing = player.getBoundingBox();
                for (int x = (int) Math.floor(standing.getMinX()); x < Math.ceil(standing.getMaxX()); x++) {
                    for (int y = (int) Math.floor(standing.getMinY()); y < Math.ceil(standing.getMaxY()); y++) {
                        for (int z = (int) Math.floor(standing.getMinZ()); z < Math.ceil(standing.getMaxZ()); z++) {
                            if (!center.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                                throw new IllegalStateException("The player's QA standing space is obstructed");
                            }
                        }
                    }
                }
                player.sendMessage("BCQA PLATFORM blocks=" + blocks.size() + " y=" + (center.getBlockY() - 1)
                        + " headroom=" + headroom.size() + " standingClear=true");
            }
            case "disable" -> {
                if (this.regionized) {
                    throw new IllegalStateException("Regionized QA uses built-in reload and normal server restart only");
                }
                Plugin companions = Bukkit.getPluginManager().getPlugin("ByteCompanions");
                if (companions == null || !companions.isEnabled()) {
                    throw new IllegalStateException("ByteCompanions must be enabled before the live-disable check");
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().disablePlugin(companions);
                    player.sendMessage("BCQA DISABLED enabled=" + companions.isEnabled());
                    getLogger().info("BCQA DISABLED enabled=" + companions.isEnabled());
                });
            }
            case "reset" -> {
                interactions.put(player.getUniqueId(), 0);
                player.sendMessage("BCQA RESET count=0");
            }
            case "state" -> {
                CompanionPlayer companionPlayer = ByteCompanions.getAPI().getCompanionPlayerOrNull(player);
                Companion companion = companionPlayer == null ? null : companionPlayer.companions().get("bee");
                player.sendMessage("BCQA STATE count=" + interactions.getOrDefault(player.getUniqueId(), 0)
                        + " base=" + (companion == null ? -1 : companion.entityId())
                        + " overlays=" + (companion == null ? 0 : companion.overlayIds().size()));
            }
            case "target" -> {
                Cow previous = targets.remove(player.getUniqueId());
                if (previous != null) {
                    this.runOwned(previous, previous::remove);
                }
                Location location = player.getLocation().add(2, 0, 0);
                if (this.regionized && !Bukkit.isOwnedByCurrentRegion(location)) {
                    throw new IllegalStateException("The QA target must spawn inside the player's current region");
                }
                Cow target = player.getWorld().spawn(location, Cow.class);
                target.setAI(false);
                target.setGravity(false);
                targets.put(player.getUniqueId(), target);
                player.sendMessage("BCQA TARGET id=" + target.getEntityId() + " health=" + target.getHealth());
            }
            case "health" -> {
                Cow target = targets.get(player.getUniqueId());
                if (target == null) {
                    player.sendMessage("BCQA HEALTH value=0");
                } else {
                    this.runOwned(target, () -> {
                        double health = target.isDead() ? 0 : target.getHealth();
                        this.runOwned(player, () -> player.sendMessage("BCQA HEALTH value=" + health));
                    });
                }
            }
            case "xp" -> {
                if (arguments.length < 2) {
                    player.sendMessage("BCQA xp requires block, furnace, fishing, or mob");
                    return true;
                }
                int amount = syntheticExperience(player, arguments[1]);
                player.sendMessage("BCQA SYNTHETIC_XP source=" + arguments[1] + " base=10 result=" + amount);
            }
            default -> player.sendMessage("BCQA unknown operation");
        }
        return true;
    }

    private int syntheticExperience(Player player, String source) {
        return switch (source) {
            case "block" -> {
                BlockBreakEvent event = new BlockBreakEvent(player.getLocation().getBlock(), player);
                event.setExpToDrop(10);
                Bukkit.getPluginManager().callEvent(event);
                yield event.getExpToDrop();
            }
            case "furnace" -> {
                FurnaceExtractEvent event = furnaceExperienceEvent(player);
                Bukkit.getPluginManager().callEvent(event);
                yield event.getExpToDrop();
            }
            case "fishing" -> {
                FishHook hook = fishingHooks.remove(player.getUniqueId());
                if (hook != null && this.regionized && !Bukkit.isOwnedByCurrentRegion(hook)) {
                    this.runOwned(hook, hook::remove);
                    throw new IllegalStateException("The QA fishing hook must remain in the player's current region");
                }
                if (hook == null || hook.isDead()) {
                    throw new IllegalStateException("Cast a fishing rod before running the synthetic fishing XP check");
                }
                try {
                    PlayerFishEvent event = new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.CAUGHT_FISH);
                    event.setExpToDrop(10);
                    Bukkit.getPluginManager().callEvent(event);
                    yield event.getExpToDrop();
                } finally {
                    hook.remove();
                }
            }
            case "mob" -> {
                Cow cow = player.getWorld().spawn(player.getLocation(), Cow.class);
                try {
                    DamageSource damage = DamageSource.builder(DamageType.PLAYER_ATTACK)
                            .withCausingEntity(player).withDirectEntity(player).build();
                    EntityDeathEvent event = new EntityDeathEvent(cow, damage, new ArrayList<ItemStack>(), 10);
                    Bukkit.getPluginManager().callEvent(event);
                    yield event.getDroppedExp();
                } finally {
                    cow.remove();
                }
            }
            default -> throw new IllegalArgumentException("Unknown XP source: " + source);
        };
    }

    @EventHandler
    public void onCompanionInteraction(CompanionPlayerInteractEvent event) {
        int count = interactions.merge(event.actor().playerId(), 1, Integer::sum);
        String message = "BCQA INTERACT count=" + count + " hand=" + event.hand()
                + " attack=" + event.attack() + " base=" + event.companion().entityId();
        Player visitor = event.player();
        this.runOwned(visitor, () -> visitor.sendMessage(message));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.updateParticleAccess(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = particleAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
        PermissionAttachment dashboard = dashboardAttachments.remove(event.getPlayer().getUniqueId());
        if (dashboard != null) {
            event.getPlayer().removeAttachment(dashboard);
        }
    }

    private void updateParticleAccess(Player player) {
        UUID playerId = player.getUniqueId();
        PermissionAttachment previous = particleAttachments.remove(playerId);
        if (previous != null) {
            player.removeAttachment(previous);
        }
        if (particleAccess.contains(playerId)) {
            particleAttachments.put(playerId, player.addAttachment(this, "bytecompanions.particle.angel_ring_effect", true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishingCast(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING && event.getPlayer().isOp()) {
            fishingHooks.put(event.getPlayer().getUniqueId(), event.getHook());
            event.getPlayer().sendMessage("BCQA FISHING_HOOK ready=true");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() == targets.get(player.getUniqueId())) {
            double damage = event.getFinalDamage();
            this.runOwned(player, () -> player.sendMessage("BCQA REAL_DAMAGE amount=" + damage));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExperience(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.DIAMOND_ORE) {
            event.getPlayer().sendMessage("BCQA BLOCK_XP amount=" + event.getExpToDrop());
        }
    }

    private void runOwned(Entity entity, Runnable action) {
        if (!this.regionized || Bukkit.isOwnedByCurrentRegion(entity)) {
            action.run();
        } else if (!entity.getScheduler().execute(this, action, null, 1L)) {
            throw new IllegalStateException("The QA entity retired before its owner task could run");
        }
    }

    private FurnaceExtractEvent furnaceExperienceEvent(Player player) {
        Block block = player.getLocation().getBlock();
        try {
            try {
                return FurnaceExtractEvent.class.getConstructor(Player.class, Block.class, Material.class, int.class, int.class)
                        .newInstance(player, block, Material.IRON_INGOT, 1, 10);
            } catch (NoSuchMethodException changedSignature) {
                return FurnaceExtractEvent.class.getConstructor(Player.class, Block.class, ItemStack.class, int.class, int.class)
                        .newInstance(player, block, new ItemStack(Material.IRON_INGOT), 1, 10);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot construct the QA furnace experience event on this server", failure);
        }
    }

    private void removeAtShutdown(Entity entity) {
        if (!this.regionized || Bukkit.isOwnedByCurrentRegion(entity)) {
            entity.remove();
        } else {
            getLogger().warning("Skipped foreign-region QA entity cleanup during unsupported live disable");
        }
    }

    private boolean regionizedRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, getServer().getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException failure) {
            return getServer().getName().equalsIgnoreCase("Canvas");
        }
    }
}
