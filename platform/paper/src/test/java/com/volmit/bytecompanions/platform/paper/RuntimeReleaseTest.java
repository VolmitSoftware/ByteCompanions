package com.volmit.bytecompanions.platform.paper;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class RuntimeReleaseTest {
    @Test
    void storageWorkerCanFinishBeforeIndependentReleaseTerminatesItsExecutor() throws Exception {
        VirtualThreadAsyncExecutor storage = new VirtualThreadAsyncExecutor();
        try (ExecutorService configuration = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicReference<Thread> storageThread = new AtomicReference<>();
            AtomicReference<Thread> releaseThread = new AtomicReference<>();
            CompletableFuture<Void> checkpoint = new CompletableFuture<>();
            CompletableFuture<Void> release = checkpoint.thenCompose(ignored -> PaperPlugin.releaseRuntimeResources(
                    () -> {
                        releaseThread.set(Thread.currentThread());
                        storage.shutdown();
                        assertTrue(storage.executor().isTerminated());
                    }, () -> {}, configuration));
            storage.runAsync(() -> {
                storageThread.set(Thread.currentThread());
                checkpoint.complete(null);
            });

            assertTrue(PaperPlugin.awaitRuntimeStorage(CompletableFuture.completedFuture(null), release, 3, TimeUnit.SECONDS));
            assertFalse(storageThread.get() == releaseThread.get());
        } finally {
            storage.shutdown();
        }
    }

    @Test
    void pendingOwnerCleanupIsNeverBlockedOrCanceled() throws Exception {
        CompletableFuture<Void> owner = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        assertFalse(PaperPlugin.awaitRuntimeStorage(owner, release, 0, TimeUnit.NANOSECONDS));
        assertFalse(owner.isDone());
        assertFalse(release.isDone());
        owner.complete(null);
        release.complete(null);
        assertTrue(PaperPlugin.awaitRuntimeStorage(owner, release, 0, TimeUnit.NANOSECONDS));
    }

    @Test
    void completedOwnerBarrierWaitsForStorageCompletion() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        try (ExecutorService waiter = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                try {
                    return PaperPlugin.awaitRuntimeStorage(CompletableFuture.completedFuture(null), release, 3, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, waiter);
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            release.complete(null);
            assertTrue(result.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void boundedStorageWaitPreservesFailureAndDoesNotCancelTimedOutCleanup() {
        IllegalStateException failure = new IllegalStateException("storage");
        CompletableFuture<Void> owner = CompletableFuture.completedFuture(null);
        ExecutionException reported = assertThrows(ExecutionException.class, () -> PaperPlugin.awaitRuntimeStorage(
                owner, CompletableFuture.failedFuture(failure), 1, TimeUnit.SECONDS));
        assertSame(failure, reported.getCause());
        CompletableFuture<Void> delayed = new CompletableFuture<>();
        assertThrows(TimeoutException.class, () -> PaperPlugin.awaitRuntimeStorage(owner, delayed, 0, TimeUnit.NANOSECONDS));
        assertFalse(delayed.isDone());
        delayed.complete(null);
    }

    @Test
    void queuedStorageReleaseCompletesWithoutAnotherSchedulerDispatch() {
        ArrayDeque<Runnable> accepted = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        AtomicReference<Thread> releaseThread = new AtomicReference<>();
        CompletableFuture<Void> release = PaperPlugin.releaseRuntimeResources(
                () -> events.add("storage"), () -> {
                    events.add("references");
                    releaseThread.set(Thread.currentThread());
                }, accepted::add);
        release.whenComplete((ignored, failure) -> events.add("executor shutdown"));

        assertFalse(release.isDone());
        assertEquals(1, accepted.size());
        accepted.remove().run();

        assertTrue(release.isDone());
        assertFalse(release.isCompletedExceptionally());
        assertTrue(accepted.isEmpty());
        assertSame(Thread.currentThread(), releaseThread.get());
        assertEquals(List.of("storage", "references", "executor shutdown"), events);
    }

    @Test
    void canceledWaiterDoesNotAbandonAcceptedReferenceRelease() {
        ArrayDeque<Runnable> accepted = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        CompletableFuture<Void> release = PaperPlugin.releaseRuntimeResources(
                () -> events.add("storage"), () -> events.add("references"), accepted::add);

        release.cancel(false);
        accepted.remove().run();

        assertEquals(List.of("storage", "references"), events);
        assertTrue(accepted.isEmpty());
    }

    @Test
    void storageFailureStillReleasesReferencesAndRetainsBothFailures() {
        IllegalStateException storageFailure = new IllegalStateException("storage");
        LinkageError referenceFailure = new LinkageError("references");

        CompletableFuture<Void> release = PaperPlugin.releaseRuntimeResources(
                () -> { throw storageFailure; },
                () -> { throw referenceFailure; }, Runnable::run);

        CompletionException failure = assertThrows(CompletionException.class, release::join);
        assertSame(storageFailure, failure.getCause());
        assertArrayEquals(new Throwable[]{referenceFailure}, storageFailure.getSuppressed());
    }
}
