package com.volmit.bytecompanions.platform.paper.util.scheduling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BukkitOwnerTasksTest {
    @Test
    public void ownedCleanupRunsWithoutTouchingDisabledScheduler() {
        AtomicInteger calls = new AtomicInteger();
        BukkitOwnerTasks.dispatch(true, calls::incrementAndGet, (run, retired) -> {
            throw new AssertionError("Owner must not schedule");
        }).join();
        assertEquals(1, calls.get());
    }

    @Test
    public void foreignCleanupWaitsForOwnerCompletion() {
        AtomicReference<Runnable> pending = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> future = BukkitOwnerTasks.dispatch(false, calls::incrementAndGet, (run, retired) -> {
            pending.set(run);
            return true;
        });
        assertFalse(future.isDone());
        assertEquals(0, calls.get());
        pending.get().run();
        future.join();
        assertEquals(1, calls.get());
    }

    @Test
    public void rejectedScheduleDoesNotBecomeSuccessfulRetirement() {
        CompletableFuture<Void> future = BukkitOwnerTasks.dispatch(false, () -> fail("Foreign mutation"), (run, retired) -> {
            retired.run();
            return false;
        });
        assertTrue(assertThrows(CompletionException.class, future::join).getCause() instanceof RejectedExecutionException);
    }

    @Test
    public void acceptedRetirementReleasesBarrierWithoutPlayerAccess() {
        AtomicReference<Runnable> retirement = new AtomicReference<>();
        CompletableFuture<Void> future = BukkitOwnerTasks.dispatch(false, () -> fail("Retired entity access"), (run, retired) -> {
            retirement.set(retired);
            return true;
        });
        retirement.get().run();
        future.join();
    }

    @Test
    public void synchronousAcceptedRetirementAlsoCompletes() {
        BukkitOwnerTasks.dispatch(false, () -> fail("Retired entity access"), (run, retired) -> {
            retired.run();
            return true;
        }).join();
    }

    @Test
    public void mutationAndSchedulingFailuresRetainOriginalCause() {
        RuntimeException failure = new IllegalStateException("original");
        CompletableFuture<Void> mutation = BukkitOwnerTasks.dispatch(true, () -> { throw failure; }, (run, retired) -> false);
        CompletableFuture<Void> scheduling = BukkitOwnerTasks.dispatch(false, () -> {}, (run, retired) -> { throw failure; });
        assertSame(failure, assertThrows(CompletionException.class, mutation::join).getCause());
        assertSame(failure, assertThrows(CompletionException.class, scheduling::join).getCause());
    }
}
