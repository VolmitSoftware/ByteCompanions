package com.volmit.bytecompanions.core.async;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class VirtualAsyncExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public static <T> @NotNull CompletableFuture<T> supplyAsync(final @NotNull Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, VIRTUAL_EXECUTOR);
    }

    public static @NotNull CompletableFuture<Void> runAsync(final @NotNull Runnable runnable) {
        return CompletableFuture.runAsync(runnable, VIRTUAL_EXECUTOR);
    }

}
