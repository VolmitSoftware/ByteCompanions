package com.volmit.bytecompanions.platform.paper.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionLoadSessionTest {
    @Test
    void closedSessionDiscardsLateStorageResult() {
        CompanionPlayerLifecyclePlatformService.LoadSession session = new CompanionPlayerLifecyclePlatformService.LoadSession();
        AtomicBoolean installed = new AtomicBoolean();
        session.close();
        session.install(() -> {
            installed.set(true);
            return null;
        });
        assertFalse(installed.get());
        assertNull(session.result.join());
    }

    @Test
    void failedSessionCannotInstallAnotherResult() {
        CompanionPlayerLifecyclePlatformService.LoadSession session = new CompanionPlayerLifecyclePlatformService.LoadSession();
        AtomicBoolean installed = new AtomicBoolean();
        session.fail(new IllegalStateException("storage unavailable"));
        session.install(() -> {
            installed.set(true);
            return null;
        });
        assertFalse(installed.get());
        assertTrue(session.result.isCompletedExceptionally());
    }

    @Test
    void closeWaitsForRunningInstallationBeforeTeardown() throws Exception {
        CompanionPlayerLifecyclePlatformService.LoadSession session = new CompanionPlayerLifecyclePlatformService.LoadSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Void> installation = CompletableFuture.runAsync(() -> session.install(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test release timed out");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return null;
            }), executor);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> closed = CompletableFuture.runAsync(() -> {
                closing.countDown();
                session.close();
            }, executor);
            try {
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                assertFalse(closed.isDone());
            } finally {
                release.countDown();
            }
            installation.get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
            assertFalse(session.active());
        }
    }
}
