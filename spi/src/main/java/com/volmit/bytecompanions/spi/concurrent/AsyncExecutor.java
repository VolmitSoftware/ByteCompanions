package com.volmit.bytecompanions.spi.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Abstraction over an {@link ExecutorService} used for async storage operations.
 * Implementations must use virtual threads for non-blocking I/O.
 */
public interface AsyncExecutor {

    /**
     * Runs a fire-and-forget task asynchronously.
     * Use for storage writes where the result is not needed (save, delete).
     *
     * @param runnable the task to execute
     * @return a future that completes when the task finishes
     */
    @NotNull CompletableFuture<Void> runAsync(final @NotNull Runnable runnable);

    /**
     * Runs a task asynchronously and returns its result.
     * Use when the result is required to continue (fetch from DB).
     *
     * @param supplier the task to execute
     * @param <T>      the result type
     * @return a future carrying the supplied value
     */
    @NotNull <T> CompletableFuture<T> supplyAsync(final @NotNull Supplier<T> supplier);

    /**
     * Returns the underlying {@link ExecutorService}.
     * Storage providers use this to power their inner {@code Async} implementations.
     *
     * @return the executor service
     */
    @NotNull ExecutorService executor();

    /**
     * Shuts down the executor gracefully.
     * Must be called on plugin disable.
     */
    void shutdown();

}
