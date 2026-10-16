package com.volmit.bytecompanions.platform.paper;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeActivationTest {
    @Test
    void activationWaitsForAllRegionalWorkAndReturnsToGlobalOwner() {
        CompletableFuture<Void> region = new CompletableFuture<>();
        ArrayDeque<Runnable> global = new ArrayDeque<>();
        AtomicBoolean activated = new AtomicBoolean();
        CompletableFuture<Void> startup = PaperPlugin.activateRuntimeWhenReady(region, () -> true,
                () -> activated.set(true), global::add);

        assertTrue(global.isEmpty());
        assertFalse(startup.isDone());
        region.complete(null);
        assertFalse(activated.get());
        assertFalse(startup.isDone());
        global.remove().run();
        startup.join();
        assertTrue(activated.get());
    }

    @Test
    void failedRegionalStartupIsReturnedWithoutPublishingRuntime() {
        IllegalStateException failure = new IllegalStateException("decoration construction");
        AtomicBoolean activated = new AtomicBoolean();
        CompletableFuture<Void> startup = PaperPlugin.activateRuntimeWhenReady(
                CompletableFuture.failedFuture(failure), () -> true,
                () -> activated.set(true), Runnable::run);

        assertSame(failure, assertThrows(CompletionException.class, startup::join).getCause());
        assertFalse(activated.get());
    }

    @Test
    void stoppedRuntimeCannotActivateAfterItsRegionCompletes() {
        CompletableFuture<Void> region = new CompletableFuture<>();
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicBoolean activated = new AtomicBoolean();
        CompletableFuture<Void> startup = PaperPlugin.activateRuntimeWhenReady(region, current::get,
                () -> activated.set(true), Runnable::run);

        current.set(false);
        region.complete(null);
        assertThrows(CompletionException.class, startup::join);
        assertFalse(activated.get());
    }

    @Test
    void rejectedGlobalDispatchCompletesStartupExceptionally() {
        RejectedExecutionException failure = new RejectedExecutionException("plugin disabled");
        CompletableFuture<Void> startup = PaperPlugin.activateRuntimeWhenReady(
                CompletableFuture.completedFuture(null), () -> true, () -> {},
                action -> { throw failure; });

        assertSame(failure, assertThrows(CompletionException.class, startup::join).getCause());
    }

    @Test
    void disableCanInvalidateAnAlreadyQueuedActivation() {
        ArrayDeque<Runnable> global = new ArrayDeque<>();
        AtomicBoolean activated = new AtomicBoolean();
        CompletableFuture<Void> startup = PaperPlugin.activateRuntimeWhenReady(
                CompletableFuture.completedFuture(null), () -> true,
                () -> activated.set(true), global::add);

        startup.completeExceptionally(new IllegalStateException("stopping"));
        global.remove().run();
        assertFalse(activated.get());
        assertThrows(CompletionException.class, startup::join);
    }
}
