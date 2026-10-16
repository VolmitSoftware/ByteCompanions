package com.volmit.bytecompanions.core.concurrent;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link AsyncExecutor} implementation backed by a virtual-thread-per-task executor.
 * Each submitted task runs on a new virtual thread — lightweight and non-blocking.
 */
public final class VirtualThreadAsyncExecutor implements AsyncExecutor {

    private final ExecutorService executor;

    public VirtualThreadAsyncExecutor() {
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("byte-companions-worker-", 0).factory()
        );
    }

    @Override
    public @NotNull CompletableFuture<Void> runAsync(final @NotNull Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.executor);
    }

    @Override
    public @NotNull <T> CompletableFuture<T> supplyAsync(final @NotNull Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.executor);
    }

    @Override
    public @NotNull ExecutorService executor() {
        return this.executor;
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }

}
