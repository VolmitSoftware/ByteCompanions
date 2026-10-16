package com.volmit.bytecompanions.platform.paper.util.inventorygui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BukkitTextInput implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private BukkitTextInput(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public static BukkitTextInput register(Plugin plugin) {
        BukkitTextInput input = new BukkitTextInput(plugin);
        plugin.getServer().getPluginManager().registerEvents(input, plugin);
        return input;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Prompt prompt : prompts.values()) {
            retire(prompt);
        }
        HandlerList.unregisterAll(this);
    }

    public synchronized boolean request(Player player, Request request) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (!FoliaScheduler.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Text input requests must run on the player's owning thread");
        }
        if (closed || !player.isOnline() || !request.active().getAsBoolean()) {
            return false;
        }
        UUID owner = player.getUniqueId();
        Prompt prompt = new Prompt(owner, player, request);
        Prompt previous = prompts.put(owner, prompt);
        if (previous != null) {
            previous.finish();
        }
        SchedulerUtils.TaskHandle timeout = FoliaScheduler.scheduleEntity(plugin, player,
                new FoliaScheduler.DelayedTask(request.timeoutTicks(), () -> expire(prompt), () -> retire(prompt)));
        if (timeout == null) {
            retire(prompt);
            return false;
        }
        prompt.bind(timeout);
        return prompts.get(owner) == prompt;
    }

    public void cancel(Player player) {
        Prompt prompt = prompts.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (prompt != null && prompt.player == player) {
            retire(prompt);
        }
    }

    public void cancel(UUID owner) {
        Prompt prompt = prompts.remove(Objects.requireNonNull(owner, "owner"));
        if (prompt != null) {
            prompt.finish();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.get(event.getPlayer().getUniqueId());
        if (prompt == null || prompt.player != event.getPlayer()) {
            return;
        }
        event.setCancelled(true);
        if (!prompt.received.compareAndSet(false, true)) {
            return;
        }
        String input = event.getMessage();
        if (!FoliaScheduler.runEntity(plugin, prompt.player, () -> accept(prompt, input), 0L,
                () -> retire(prompt))) {
            retire(prompt);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    private void accept(Prompt prompt, String input) {
        if (!complete(prompt)) {
            return;
        }
        if (input.strip().equalsIgnoreCase("cancel")) {
            prompt.request.cancelled().accept(EndReason.CANCELLED);
        } else if (input.length() > prompt.request.maximumLength()) {
            prompt.request.cancelled().accept(EndReason.TOO_LONG);
        } else {
            prompt.request.accepted().accept(input);
        }
    }

    private void expire(Prompt prompt) {
        if (complete(prompt)) {
            prompt.request.cancelled().accept(EndReason.EXPIRED);
        }
    }

    private boolean complete(Prompt prompt) {
        if (!prompts.remove(prompt.owner, prompt)) {
            return false;
        }
        prompt.finish();
        return !closed && prompt.player.isOnline() && prompt.request.active().getAsBoolean();
    }

    private void retire(Prompt prompt) {
        prompts.remove(prompt.owner, prompt);
        prompt.finish();
    }

    public record Request(int maximumLength, long timeoutTicks, BooleanSupplier active,
                          Consumer<String> accepted, Consumer<EndReason> cancelled) {
        public Request {
            if (maximumLength < 1 || maximumLength > 1024) {
                throw new IllegalArgumentException("Text input maximum length must be between 1 and 1024");
            }
            if (timeoutTicks < 1) {
                throw new IllegalArgumentException("Text input timeout must be positive");
            }
            Objects.requireNonNull(active, "active");
            Objects.requireNonNull(accepted, "accepted");
            Objects.requireNonNull(cancelled, "cancelled");
        }
    }

    public enum EndReason {
        CANCELLED,
        EXPIRED,
        TOO_LONG
    }

    private static final class Prompt {
        private final UUID owner;
        private final Player player;
        private final Request request;
        private final AtomicBoolean received = new AtomicBoolean();
        private SchedulerUtils.TaskHandle timeout;
        private boolean finished;

        private Prompt(UUID owner, Player player, Request request) {
            this.owner = owner;
            this.player = player;
            this.request = request;
        }

        private synchronized void bind(SchedulerUtils.TaskHandle task) {
            if (finished) {
                task.cancel();
            } else {
                timeout = task;
            }
        }

        private synchronized void finish() {
            if (finished) {
                return;
            }
            finished = true;
            if (timeout != null) {
                timeout.cancel();
                timeout = null;
            }
        }
    }
}
