package com.volmit.bytecompanions.features.decorations;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.Companion;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecorationRuntimeTest {
    @Test
    void startupWaitsForCreationAndFirstTickOnOwner() {
        Owner owner = new Owner();
        DecorationRuntime decoration = owner.decoration();
        CompletableFuture<Void> started = decoration.startAsync();
        assertFalse(started.isDone());
        assertNull(decoration.companion());
        owner.runOwned();
        started.join();
        assertSame(owner.companion, decoration.companion());
        assertEquals(List.of("create", "tick"), owner.events);
        assertEquals(1, owner.ticks.size());
    }

    @Test
    void stopBeforeScheduledStartupPreventsCreation() {
        Owner owner = new Owner();
        DecorationRuntime decoration = owner.decoration();
        CompletableFuture<Void> started = decoration.startAsync();
        decoration.closeAsync().join();
        owner.runOwned();
        assertThrows(CompletionException.class, started::join);
        assertTrue(owner.events.isEmpty());
        assertTrue(owner.ticks.isEmpty());
    }

    @Test
    void firstTickFailureReachesStartupAndLeavesCompanionForRollback() {
        Owner owner = new Owner();
        owner.failure = new IllegalStateException("native packet failed");
        DecorationRuntime decoration = owner.decoration();
        CompletableFuture<Void> started = decoration.startAsync();
        owner.runOwned();
        assertSame(owner.failure, assertThrows(CompletionException.class, started::join).getCause());
        CompletableFuture<Void> stopped = decoration.closeAsync();
        assertFalse(stopped.isDone());
        owner.runOwned();
        stopped.join();
        assertEquals(List.of("create", "tick", "destroy"), owner.events);
    }

    @Test
    void rejectedRepeatSchedulingFailsStartup() {
        Owner owner = new Owner();
        owner.acceptTicks = false;
        DecorationRuntime decoration = owner.decoration();
        CompletableFuture<Void> started = decoration.startAsync();
        owner.runOwned();
        assertTrue(assertThrows(CompletionException.class, started::join).getCause()
                instanceof RejectedExecutionException);
    }

    @Test
    void cancelledWaitersCannotAbandonCleanupAndOldTicksCannotRestart() {
        Owner owner = new Owner();
        DecorationRuntime decoration = owner.decoration();
        decoration.startAsync().cancel(false);
        owner.runOwned();
        decoration.startAsync().join();
        CompletableFuture<Void> cancelled = decoration.closeAsync();
        cancelled.cancel(false);
        CompletableFuture<Void> stopped = decoration.closeAsync();
        assertFalse(stopped.isDone());
        owner.runOwned();
        stopped.join();
        owner.ticks.remove().run();
        assertTrue(owner.ticks.isEmpty());
        assertEquals(List.of("create", "tick", "destroy"), owner.events);
    }

    private static final class Owner {
        private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
        private final List<String> events = new ArrayList<>();
        private boolean owned;
        private boolean acceptTicks = true;
        private RuntimeException failure;
        private final Companion companion = Companion.class.cast(Proxy.newProxyInstance(
                Companion.class.getClassLoader(), new Class<?>[]{Companion.class}, (proxy, method, arguments) -> {
                    assertTrue(this.owned, "Foreign companion access: " + method.getName());
                    this.events.add(method.getName());
                    if (method.getName().equals("tick") && this.failure != null) {
                        throw this.failure;
                    }
                    return null;
                }));

        private DecorationRuntime decoration() {
            return new DecorationRuntime(() -> {
                assertTrue(this.owned);
                this.events.add("create");
                return this.companion;
            }, action -> {
                CompletableFuture<Void> completion = new CompletableFuture<>();
                this.actions.add(() -> {
                    try {
                        action.run();
                        completion.complete(null);
                    } catch (Throwable thrown) {
                        completion.completeExceptionally(thrown);
                    }
                });
                return completion;
            }, action -> this.acceptTicks && this.ticks.add(action), failure -> {
                throw new AssertionError("Unexpected periodic failure", failure);
            });
        }

        private void runOwned() {
            this.owned = true;
            try {
                this.actions.remove().run();
            } finally {
                this.owned = false;
            }
        }
    }
}
