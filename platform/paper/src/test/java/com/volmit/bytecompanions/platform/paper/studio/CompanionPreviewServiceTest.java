package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionPreviewServiceTest {
    @Test
    void equippedTypeCannotCreateTrialAndRejectionPreservesExistingPreview() {
        Fixture fixture = new Fixture();
        CompanionType equipped = proxy(CompanionType.class, (method, arguments) -> { throw new AssertionError(method); });
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30));
        fixture.equipped = equipped;
        assertFalse(fixture.service.canTrial(fixture.player, equipped));
        assertTrue(fixture.service.canTrial(fixture.player, null));
        assertThrows(IllegalStateException.class, () -> fixture.service.show(fixture.player, equipped,
                CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30)));
        assertEquals(List.of("create:0"), fixture.events);
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.TRIAL));
        fixture.service.show(fixture.player, equipped, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        assertEquals(List.of("create:0", "destroy:0", "create:1"), fixture.events);
    }

    @Test
    void trialEligibilityIsRecheckedAtCreationAfterSelectionChanges() {
        Fixture fixture = new Fixture();
        CompanionType type = proxy(CompanionType.class, (method, arguments) -> { throw new AssertionError(method); });
        assertTrue(fixture.service.canTrial(fixture.player, type));
        fixture.equipped = type;
        assertThrows(IllegalStateException.class, () -> fixture.service.show(fixture.player, type,
                CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30)));
        assertTrue(fixture.events.isEmpty());
        fixture.equipped = null;
        fixture.service.show(fixture.player, type, CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30));
        assertEquals(List.of("create:0"), fixture.events);
    }
    @Test
    void trialUsesItsOwnExpiryAndStudioActionsCannotStopIt() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30));
        fixture.service.stop(fixture.player, CompanionPreviewService.Purpose.STUDIO).join();
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.TRIAL));
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        fixture.now = Duration.ofSeconds(30).toNanos();
        fixture.tick();
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.TRIAL));
        assertEquals(List.of("create:0", "destroy:0"), fixture.events);
    }

    @Test
    void realEquipStopsTrialButPreservesStudioAndInvalidDurationCreatesNothing() {
        Fixture fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.service.show(fixture.player, null,
                CompanionPreviewService.Purpose.TRIAL, Duration.ofMinutes(3)));
        assertTrue(fixture.events.isEmpty());
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.TRIAL, Duration.ofSeconds(30));
        fixture.service.onEquip(new CompanionEquipEvent(fixture.player, null, true));
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.TRIAL));
        fixture.service.onEquip(new CompanionEquipEvent(fixture.player, null));
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.TRIAL));
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.service.onEquip(new CompanionEquipEvent(fixture.player, null));
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
    }

    @Test
    void replacementDestroysOldPreviewAndQueuedOldTickCannotAffectNewPreview() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.tick();
        assertEquals(List.of("create:0", "destroy:0", "create:1"), fixture.events);
        fixture.tick();
        assertEquals("tick:1", fixture.events.getLast());
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
    }

    @Test
    void stoppingInvalidatesPendingTicksAndDestroysOnlyOnce() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.service.stop(fixture.player).join();
        fixture.service.stop(fixture.player).join();
        fixture.tick();
        assertEquals(List.of("create:0", "destroy:0"), fixture.events);
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        assertTrue(fixture.ticks.isEmpty());
    }

    @Test
    void previewExpiresAndRefreshingResetsDeadline() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.now = CompanionPreviewService.PREVIEW_DURATION.toNanos() - 1;
        fixture.tick();
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.now++;
        fixture.tick();
        fixture.tick();
        assertTrue(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        fixture.now += CompanionPreviewService.PREVIEW_DURATION.toNanos();
        fixture.tick();
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        assertEquals("destroy:1", fixture.events.getLast());
    }

    @Test
    void worldChangesAndDeathEndPreviewsBeforeAnotherTick() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.world = world();
        fixture.tick();
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.dead = true;
        fixture.tick();
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        assertEquals(List.of("create:0", "destroy:0", "create:1", "destroy:1"), fixture.events);
    }

    @Test
    void runtimeCloseAwaitsOwnerCleanupAndRejectsNewPreviews() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.deferCleanup = true;
        CompletableFuture<Void> cleanup = fixture.service.closeAsync();
        assertFalse(cleanup.isDone());
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        assertThrows(IllegalStateException.class, () -> fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION));
        fixture.tick();
        assertEquals(List.of("create:0"), fixture.events);
        fixture.cleanup.removeFirst().run();
        cleanup.join();
        assertEquals(List.of("create:0", "destroy:0"), fixture.events);
        fixture.service.closeAsync().join();
    }

    @Test
    void ownerRetirementDropsPreviewWithoutAccessingForeignEntityState() {
        Fixture fixture = new Fixture();
        fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION);
        fixture.retired.removeFirst().run();
        fixture.owned = false;
        fixture.tick();
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
        assertEquals(List.of("create:0"), fixture.events);
        fixture.service.closeAsync().join();
    }

    @Test
    void offOwnerCreationIsRejectedBeforeReadingPlayerState() {
        Fixture fixture = new Fixture();
        fixture.owned = false;
        assertThrows(IllegalStateException.class, () -> fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION));
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void rejectedTickCleansSpawnedPreview() {
        Fixture fixture = new Fixture();
        fixture.rejectTick = true;
        assertThrows(RejectedExecutionException.class, () -> fixture.service.show(fixture.player, null, CompanionPreviewService.Purpose.STUDIO, CompanionPreviewService.PREVIEW_DURATION));
        assertEquals(List.of("create:0", "destroy:0"), fixture.events);
        assertFalse(fixture.service.active(fixture.player, CompanionPreviewService.Purpose.STUDIO));
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return proxy(World.class, (method, arguments) -> {
            if (method.equals("getUID")) return id;
            throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, arguments) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
            if (method.getName().equals("equals")) return instance == arguments[0];
            return invocation.call(method.getName(), arguments);
        }));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class Fixture {
        private World world = world();
        private final List<String> events = new ArrayList<>();
        private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
        private final ArrayDeque<Runnable> retired = new ArrayDeque<>();
        private final ArrayDeque<Runnable> cleanup = new ArrayDeque<>();
        private long now;
        private int created;
        private boolean dead;
        private boolean owned = true;
        private boolean deferCleanup;
        private boolean rejectTick;
        private CompanionType equipped;
        private final Player player = proxy(Player.class, (method, arguments) -> {
            assertTrue(this.owned, "Player state read off owner: " + method);
            return switch (method) {
                case "isOnline" -> true;
                case "isDead" -> this.dead;
                case "getWorld" -> this.world;
                default -> throw new AssertionError(method);
            };
        });
        private final Plugin plugin = proxy(Plugin.class, (method, arguments) -> switch (method) {
            case "getLogger" -> Logger.getAnonymousLogger();
            default -> throw new AssertionError(method);
        });
        private final CompanionPreviewService service = new CompanionPreviewService(this.plugin,
                (owner, type) -> this.companion(), (owner, type) -> this.equipped != null && this.equipped == type,
                owner -> this.owned,
                (owner, action, retirement) -> {
                    if (this.rejectTick) return false;
                    this.ticks.add(action);
                    this.retired.add(retirement);
                    return true;
                }, (owner, action) -> {
                    CompletableFuture<Void> completion = new CompletableFuture<>();
                    Runnable task = () -> {
                        try {
                            action.run();
                            completion.complete(null);
                        } catch (RuntimeException failure) {
                            completion.completeExceptionally(failure);
                        }
                    };
                    if (this.deferCleanup) this.cleanup.add(task);
                    else task.run();
                    return completion;
                }, () -> this.now);

        private Companion companion() {
            int index = this.created++;
            this.events.add("create:" + index);
            return proxy(Companion.class, (method, arguments) -> {
                assertTrue(this.owned, "Companion state read off owner: " + method);
                if (method.equals("destroy") || method.equals("tick")) {
                    this.events.add(method + ":" + index);
                    return null;
                }
                throw new AssertionError(method);
            });
        }

        private void tick() {
            this.ticks.removeFirst().run();
        }
    }
}
