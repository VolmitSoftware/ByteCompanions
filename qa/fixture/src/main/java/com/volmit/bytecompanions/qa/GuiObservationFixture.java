package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class GuiObservationFixture implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, ArrayDeque<Map<String, Object>>> events = new ConcurrentHashMap<>();

    GuiObservationFixture(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    String inspect(Player player, String operation) {
        if (operation.equals("start")) events.put(player.getUniqueId(), new ArrayDeque<>());
        Map<String, Object> result = snapshot(player);
        ArrayDeque<Map<String, Object>> history = events.get(player.getUniqueId());
        result.put("events", history == null ? new ArrayList<>() : new ArrayList<>(history));
        result.put("observing", history != null);
        result.put("clickHandlers", Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .map(listener -> listener.getPlugin().getName() + ":" + listener.getPriority().name()
                        + ":" + listener.getListener().getClass().getName()).toList());
        String filename = save(player, result);
        if (operation.equals("stop")) events.remove(player.getUniqueId());
        return "BCQA GUI FILE " + filename;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void clickLowest(InventoryClickEvent event) { click(event, "LOWEST"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void clickMonitor(InventoryClickEvent event) { click(event, "MONITOR"); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void openLowest(InventoryOpenEvent event) { open(event, "LOWEST"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void openMonitor(InventoryOpenEvent event) { open(event, "MONITOR"); }

    private void open(InventoryOpenEvent event, String priority) {
        if (event.getPlayer() instanceof Player player && events.containsKey(player.getUniqueId())) {
            Map<String, Object> data = snapshot(player);
            data.put("event", "open");
            data.put("priority", priority);
            data.put("cancelled", event.isCancelled());
            data.put("eventTitle", invoke(event.getView(), "getTitle"));
            append(player, data);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void closeLowest(InventoryCloseEvent event) { close(event, "LOWEST"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void closeMonitor(InventoryCloseEvent event) { close(event, "MONITOR"); }

    private void close(InventoryCloseEvent event, String priority) {
        if (event.getPlayer() instanceof Player player && events.containsKey(player.getUniqueId())) {
            Map<String, Object> data = snapshot(player);
            data.put("event", "close");
            data.put("priority", priority);
            data.put("eventTitle", invoke(event.getView(), "getTitle"));
            data.put("stack", StackWalker.getInstance().walk(frames -> frames.skip(1).limit(16)
                    .map(StackWalker.StackFrame::toString).toList()));
            append(player, data);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        if (events.containsKey(event.getPlayer().getUniqueId())) inspect(event.getPlayer(), "stop");
    }

    private String save(Player player, Map<String, Object> result) {
        String filename = "gui-trace-" + player.getUniqueId() + ".json";
        Path directory = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(filename), new Gson().toJson(result));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot save the bounded QA inventory trace", failure);
        }
        return filename;
    }

    private void click(InventoryClickEvent event, String priority) {
        if (!(event.getWhoClicked() instanceof Player player) || !events.containsKey(player.getUniqueId())) return;
        Map<String, Object> data = snapshot(player);
        data.put("event", "click");
        data.put("priority", priority);
        data.put("cancelled", event.isCancelled());
        data.put("rawSlot", event.getRawSlot());
        data.put("slot", event.getSlot());
        data.put("click", event.getClick().name());
        data.put("action", event.getAction().name());
        data.put("eventInventoryIdentity", System.identityHashCode(event.getInventory()));
        data.put("eventViewIdentity", System.identityHashCode(event.getView()));
        data.put("item", item(event.getCurrentItem()));
        append(player, data);
    }

    private void append(Player player, Map<String, Object> event) {
        ArrayDeque<Map<String, Object>> history = events.get(player.getUniqueId());
        if (history == null) return;
        if (history.size() == 256) history.removeFirst();
        history.addLast(event);
    }

    private Map<String, Object> snapshot(Player player) {
        InventoryView view = player.getOpenInventory();
        Inventory top = (Inventory) invoke(view, "getTopInventory");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("atMillis", System.currentTimeMillis());
        data.put("title", invoke(view, "getTitle"));
        data.put("inventoryIdentity", System.identityHashCode(top));
        data.put("viewIdentity", System.identityHashCode(view));
        data.put("inventoryType", top.getType().name());
        data.put("cursor", item(player.getItemOnCursor()));
        data.put("dead", player.isDead());
        data.put("health", player.getHealth());
        data.put("gameMode", player.getGameMode().name());
        data.put("menuPermission", player.hasPermission("bytecompanions.command.menu"));
        data.put("boostPermission", player.hasPermission("bytecompanions.command.boosts"));
        return data;
    }

    private static Map<String, Object> item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Map.of();
        return Map.of("material", stack.getType().name(), "amount", stack.getAmount(),
                "name", stack.hasItemMeta() && stack.getItemMeta().hasDisplayName() ? stack.getItemMeta().getDisplayName() : "");
    }

    private static Object invoke(InventoryView view, String method) {
        try {
            return InventoryView.class.getMethod(method).invoke(view);
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invoked ? invoked.getCause() : failure;
            throw new IllegalStateException("Cannot observe the current inventory view", cause);
        }
    }
}
