package com.volmit.bytecompanions.platform.paper.util.inventorygui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitTextInputTest {
    @Test
    public void commandSilentlyInvalidatesQueuedInputWithoutCancellingTheNextPrompt() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("old value");
            PlayerCommandPreprocessEvent command = mock(PlayerCommandPreprocessEvent.class);
            when(command.getPlayer()).thenReturn(fixture.player);
            when(command.isCancelled()).thenReturn(true);
            fixture.input.onCommand(command);
            assertTrue(fixture.input.request(fixture.player, fixture.request()));
            fixture.continuations.get(0).run();
            fixture.timeouts.get(0).action().run();
            fixture.chat("new value");
            fixture.continuations.get(1).run();

            assertEquals(List.of("new value"), fixture.accepted);
            assertTrue(fixture.ended.isEmpty());
            verify(fixture.handles.get(0)).cancel();
            verify(command, never()).setCancelled(true);
        }
    }

    @Test
    public void chatRemainsPrivateUntilOwnerContinuationConsumesItOnce() {
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.input.request(fixture.player, fixture.request()));
            AsyncPlayerChatEvent first = fixture.chat("  first value  ");
            AsyncPlayerChatEvent second = fixture.chat("second value");

            verify(first).setCancelled(true);
            verify(second).setCancelled(true);
            assertTrue(fixture.accepted.isEmpty());
            assertEquals(1, fixture.continuations.size());
            fixture.continuations.get(0).run();

            assertEquals(List.of("  first value  "), fixture.accepted);
            verify(fixture.handles.get(0)).cancel();
            verify(fixture.chat("normal chat"), never()).setCancelled(true);
        }
    }

    @Test
    public void replacementAndExplicitCancellationInvalidateQueuedInputs() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("old value");
            fixture.input.request(fixture.player, fixture.request());
            fixture.continuations.get(0).run();
            fixture.chat("new value");
            fixture.input.cancel(fixture.player.getUniqueId());
            fixture.continuations.get(1).run();
            fixture.timeouts.get(0).action().run();
            fixture.timeouts.get(1).action().run();

            assertTrue(fixture.accepted.isEmpty());
            assertTrue(fixture.ended.isEmpty());
            verify(fixture.handles.get(0)).cancel();
            verify(fixture.handles.get(1)).cancel();
        }
    }

    @Test
    public void revokedContextSilentlyRejectsQueuedInput() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("value");
            fixture.active.set(false);
            fixture.continuations.get(0).run();

            assertTrue(fixture.accepted.isEmpty());
            assertTrue(fixture.ended.isEmpty());
            verify(fixture.chat("normal chat"), never()).setCancelled(true);
        }
    }

    @Test
    public void cancellationLengthAndTimeoutHaveDistinctOwnerCallbacks() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat(" CANCEL ");
            fixture.continuations.get(0).run();
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("x".repeat(257));
            fixture.continuations.get(1).run();
            fixture.input.request(fixture.player, fixture.request());
            fixture.timeouts.get(2).action().run();
            fixture.timeouts.get(2).action().run();

            assertEquals(List.of(BukkitTextInput.EndReason.CANCELLED, BukkitTextInput.EndReason.TOO_LONG,
                    BukkitTextInput.EndReason.EXPIRED), fixture.ended);
            assertTrue(fixture.accepted.isEmpty());
        }
    }

    @Test
    public void timeoutAndEntityRetirementInvalidateQueuedContinuations() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("expired value");
            fixture.timeouts.get(0).action().run();
            fixture.continuations.get(0).run();
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("retired value");
            fixture.timeouts.get(1).retired().run();
            fixture.continuations.get(1).run();

            assertTrue(fixture.accepted.isEmpty());
            assertEquals(List.of(BukkitTextInput.EndReason.EXPIRED), fixture.ended);
        }
    }

    @Test
    public void quitAndCloseCancelPendingTimeoutsAndCallbacks() {
        try (Fixture fixture = new Fixture()) {
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("quit value");
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(fixture.player);
            fixture.input.onQuit(quit);
            fixture.continuations.get(0).run();
            fixture.input.request(fixture.player, fixture.request());
            fixture.chat("closed value");
            fixture.input.close();
            fixture.continuations.get(1).run();

            assertTrue(fixture.accepted.isEmpty());
            assertTrue(fixture.ended.isEmpty());
            assertFalse(fixture.input.request(fixture.player, fixture.request()));
            verify(fixture.handles.get(0)).cancel();
            verify(fixture.handles.get(1)).cancel();
        }
    }

    @Test
    public void rejectedSchedulingDoesNotLeaveChatCaptured() {
        try (Fixture fixture = new Fixture()) {
            fixture.acceptScheduling = false;
            fixture.input.request(fixture.player, fixture.request());
            verify(fixture.chat("value")).setCancelled(true);
            verify(fixture.chat("normal chat"), never()).setCancelled(true);
            assertTrue(fixture.accepted.isEmpty());
            verify(fixture.handles.get(0)).cancel();
            fixture.scheduler.when(() -> FoliaScheduler.scheduleEntity(same(fixture.plugin),
                    same(fixture.entity), any(FoliaScheduler.DelayedTask.class))).thenReturn(null);
            assertFalse(fixture.input.request(fixture.player, fixture.request()));
            verify(fixture.chat("normal chat"), never()).setCancelled(true);
        }
    }

    @Test
    public void requestsRequireOwnershipAndBoundedInput() {
        try (Fixture fixture = new Fixture()) {
            fixture.scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(fixture.entity)).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> fixture.input.request(fixture.player, fixture.request()));
            assertThrows(IllegalArgumentException.class, () -> new BukkitTextInput.Request(
                    1025, 1200, fixture.active::get, fixture.accepted::add, fixture.ended::add));
            assertThrows(IllegalArgumentException.class, () -> new BukkitTextInput.Request(
                    256, 0, fixture.active::get, fixture.accepted::add, fixture.ended::add));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Plugin plugin = mock(Plugin.class);
        private final Player player = mock(Player.class);
        private final Entity entity = player;
        private final List<String> accepted = new ArrayList<>();
        private final List<BukkitTextInput.EndReason> ended = new ArrayList<>();
        private final List<FoliaScheduler.DelayedTask> timeouts = new ArrayList<>();
        private final List<SchedulerUtils.TaskHandle> handles = new ArrayList<>();
        private final List<Runnable> continuations = new ArrayList<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        private final BukkitTextInput input;
        private boolean acceptScheduling = true;

        private Fixture() {
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(true);
            scheduler.when(() -> FoliaScheduler.scheduleEntity(same(plugin), same(entity),
                    any(FoliaScheduler.DelayedTask.class))).thenAnswer(invocation -> {
                        timeouts.add(invocation.getArgument(2));
                        SchedulerUtils.TaskHandle handle = mock(SchedulerUtils.TaskHandle.class);
                        handles.add(handle);
                        return handle;
                    });
            scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                    eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                        if (acceptScheduling) {
                            continuations.add(invocation.getArgument(2));
                        }
                        return acceptScheduling;
                    });
            input = BukkitTextInput.register(plugin);
        }

        @Override
        public void close() {
            try {
                input.close();
            } finally {
                scheduler.close();
            }
        }

        private BukkitTextInput.Request request() {
            return new BukkitTextInput.Request(256, 1200, active::get, accepted::add, ended::add);
        }

        private AsyncPlayerChatEvent chat(String text) {
            AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getMessage()).thenReturn(text);
            input.onChat(event);
            return event;
        }
    }
}
