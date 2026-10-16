package com.volmit.bytecompanions.adapter_spigot26_1;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

public final class Spigot26_1InteractionListener implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final CompanionEntityCache companions;
    private final CompanionEventBus events;
    private final String handlerName = "bytecompanions-interactions-" + UUID.randomUUID();
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public Spigot26_1InteractionListener(JavaPlugin plugin, CompanionEntityCache companions, CompanionEventBus events) {
        this.plugin = plugin;
        this.companions = companions;
        this.events = events;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                attach(player);
            }
        } catch (RuntimeException | LinkageError failure) {
            close();
            throw failure;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Channel channel = channels.remove(event.getPlayer().getUniqueId());
        if (channel != null) {
            detach(channel);
        }
    }

    private void attach(Player player) {
        if (closed) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Channel channel = channel(player);
        Channel previous = channels.put(playerId, channel);
        if (previous != null && previous != channel) {
            detach(previous);
        }
        channel.eventLoop().execute(() -> {
            if (closed || channels.get(playerId) != channel || !channel.isActive()) {
                return;
            }
            try {
                if (channel.pipeline().get(handlerName) == null) {
                    channel.pipeline().addBefore("packet_handler", handlerName, new InteractionHandler(playerId, channel));
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "Could not attach companion interactions for " + playerId, failure);
            }
        });
    }

    private static Channel channel(Player player) {
        ServerGamePacketListenerImpl listener = ((CraftPlayer) player).getHandle().connection;
        List<Connection> connections = MinecraftServer.getServer().getConnection().getConnections();
        synchronized (connections) {
            for (Connection connection : connections) {
                if (connection.getPacketListener() == listener) {
                    return connection.channel;
                }
            }
        }
        throw new IllegalStateException("No native connection for player " + player.getUniqueId());
    }

    private void dispatch(UUID playerId, Channel channel, int entityId, EquipmentSlot hand, boolean attack) {
        if (closed) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (closed || channels.get(playerId) != channel) {
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            Companion companion = companions.get(entityId);
            if (companion != null) {
                events.callPlayerInteract(player, CompanionPlayerInteractEvent.Actor.capture(player), companion,
                        entityId, hand, attack);
            }
        });
    }

    private void detach(Channel channel) {
        try {
            channel.eventLoop().execute(() -> {
                if (channel.pipeline().get(handlerName) != null) {
                    channel.pipeline().remove(handlerName);
                }
            });
        } catch (RejectedExecutionException failure) {
            if (channel.isOpen()) {
                plugin.getLogger().log(Level.SEVERE, "Could not detach companion interaction handler", failure);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(this);
        for (Channel channel : channels.values()) {
            detach(channel);
        }
        channels.clear();
    }

    private final class InteractionHandler extends ChannelInboundHandlerAdapter {
        private final UUID playerId;
        private final Channel channel;

        private InteractionHandler(UUID playerId, Channel channel) {
            this.playerId = playerId;
            this.channel = channel;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            try {
                if (message instanceof ServerboundInteractPacket interaction) {
                    EquipmentSlot hand = interaction.hand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                    dispatch(playerId, channel, interaction.entityId(), hand, false);
                } else if (message instanceof ServerboundAttackPacket attack) {
                    dispatch(playerId, channel, attack.entityId(), EquipmentSlot.HAND, true);
                }
            } catch (RuntimeException failure) {
                if (!closed) {
                    plugin.getLogger().log(Level.SEVERE, "Could not schedule companion interaction for " + playerId, failure);
                }
            } finally {
                context.fireChannelRead(message);
            }
        }
    }
}
