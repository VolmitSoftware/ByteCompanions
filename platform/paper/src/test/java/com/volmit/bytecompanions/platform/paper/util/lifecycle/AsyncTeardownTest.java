package com.volmit.bytecompanions.platform.paper.util.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncTeardownTest {
    @Test
    public void nextStageAndFinalCompletionWaitForTheirPredecessors() {
        List<String> attempts = new ArrayList<>();
        CompletableFuture<Void> owners = new CompletableFuture<>();
        CompletableFuture<Void> storage = new CompletableFuture<>();
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> { attempts.add("owners"); return owners; },
                () -> { attempts.add("storage"); return storage; },
                () -> { attempts.add("release"); return CompletableFuture.completedFuture(null); }));

        assertEquals(List.of("owners"), attempts);
        assertFalse(result.isDone());
        owners.complete(null);
        assertEquals(List.of("owners", "storage"), attempts);
        assertFalse(result.isDone());
        storage.complete(null);
        result.join();
        assertEquals(List.of("owners", "storage", "release"), attempts);
    }

    @Test
    public void failuresDoNotSkipCleanupAndRemainOrderedUntilFinalCompletion() {
        IllegalStateException first = new IllegalStateException("owners");
        LinkageError second = new NoSuchMethodError("viewer");
        IllegalArgumentException third = new IllegalArgumentException("storage");
        CompletableFuture<Void> storage = new CompletableFuture<>();
        List<String> attempts = new ArrayList<>();
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> { throw first; },
                () -> { throw second; },
                () -> storage,
                () -> { attempts.add("release"); return CompletableFuture.completedFuture(null); }));

        assertFalse(result.isDone());
        assertTrue(attempts.isEmpty());
        storage.completeExceptionally(third);
        assertSame(first, assertThrows(CompletionException.class, result::join).getCause());
        assertArrayEquals(new Throwable[]{second, third}, first.getSuppressed());
        assertEquals(List.of("release"), attempts);
    }

    @Test
    public void wrappedFailuresPreserveTheOriginalCauseWithoutSelfSuppression() {
        IllegalStateException cause = new IllegalStateException("original");
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> CompletableFuture.failedFuture(new CompletionException(new ExecutionException(cause))),
                () -> CompletableFuture.failedFuture(cause)));

        assertSame(cause, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(0, cause.getSuppressed().length);
    }

    @Test
    public void cancellingObservationDoesNotAbandonAcceptedCleanup() {
        CompletableFuture<Void> owner = new CompletableFuture<>();
        CompletableFuture<Void> storage = new CompletableFuture<>();
        List<String> attempts = new ArrayList<>();
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> owner,
                () -> { attempts.add("storage"); return storage; },
                () -> { attempts.add("release"); return CompletableFuture.completedFuture(null); }));

        assertTrue(result.cancel(true));
        assertFalse(owner.isCancelled());
        owner.complete(null);
        assertEquals(List.of("storage"), attempts);
        assertFalse(storage.isCancelled());
        storage.complete(null);
        assertEquals(List.of("storage", "release"), attempts);
    }

    @Test
    public void stagesOwnTheirSchedulingWithoutAnImplicitExecutor() throws InterruptedException {
        CompletableFuture<Void> owner = new CompletableFuture<>();
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> nextThread = new AtomicReference<>();
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> { firstThread.set(Thread.currentThread()); return owner; },
                () -> { nextThread.set(Thread.currentThread()); return CompletableFuture.completedFuture(null); }));
        Thread completion = new Thread(() -> owner.complete(null), "teardown-test-completion");
        completion.start();
        completion.join();
        result.join();

        assertSame(Thread.currentThread(), firstThread.get());
        assertSame(completion, nextThread.get());
    }

    @Test
    public void missingStageFutureFailsButStillReleasesResources() {
        List<String> attempts = new ArrayList<>();
        CompletableFuture<Void> result = AsyncTeardown.run(List.of(
                () -> null,
                () -> { attempts.add("release"); return CompletableFuture.completedFuture(null); }));

        assertTrue(assertThrows(CompletionException.class, result::join).getCause() instanceof NullPointerException);
        assertEquals(List.of("release"), attempts);
    }

    @Test
    public void acceptedStageListIsSnapshottedBeforeExecution() {
        CompletableFuture<Void> first = new CompletableFuture<>();
        List<String> attempts = new ArrayList<>();
        List<Supplier<CompletableFuture<Void>>> stages = new ArrayList<>();
        stages.add(() -> first);
        stages.add(() -> { attempts.add("release"); return CompletableFuture.completedFuture(null); });
        CompletableFuture<Void> result = AsyncTeardown.run(stages);
        stages.clear();
        first.complete(null);

        result.join();
        assertEquals(List.of("release"), attempts);
    }

    @Test
    public void emptyTeardownCompletesImmediately() {
        CompletableFuture<Void> result = AsyncTeardown.run(List.of());
        assertTrue(result.isDone());
        result.join();
    }
}
