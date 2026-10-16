package com.volmit.bytecompanions.core.companion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class ViewerDeliveryQueue {
    private final int capacity;
    private final Consumer<Throwable> failures;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> barriers = new ArrayList<>();
    private boolean accepting = true;
    private boolean draining;
    private boolean retired;
    private Throwable failure;

    public ViewerDeliveryQueue(int capacity, Consumer<Throwable> failures) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Viewer queue capacity must be positive");
        }
        this.capacity = capacity;
        this.failures = failures;
    }

    public synchronized void submit(Runnable delivery) {
        if (!this.accepting) {
            throw new RejectedExecutionException("Viewer delivery queue is closed");
        }
        if (this.pending.size() >= this.capacity) {
            RejectedExecutionException rejected = new RejectedExecutionException("Viewer delivery queue exceeded " + this.capacity + " pending deliveries");
            this.recordFailure(rejected);
            throw rejected;
        }
        this.pending.addLast(delivery);
    }

    public synchronized CompletableFuture<Void> flush() {
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        this.barriers.add(barrier);
        if (this.retired) {
            this.complete(barrier);
            return barrier;
        }
        this.pending.addLast(() -> this.complete(barrier));
        return barrier;
    }

    public synchronized CompletableFuture<Void> close() {
        this.accepting = false;
        return this.flush();
    }

    public void drain() {
        synchronized (this) {
            if (this.draining) {
                return;
            }
            this.draining = true;
        }
        try {
            while (true) {
                Runnable delivery;
                synchronized (this) {
                    delivery = this.pending.pollFirst();
                    if (delivery == null) {
                        return;
                    }
                }
                try {
                    delivery.run();
                } catch (RuntimeException | LinkageError exception) {
                    this.recordFailure(exception);
                }
            }
        } finally {
            synchronized (this) {
                this.draining = false;
            }
        }
    }

    public synchronized void retire() {
        this.accepting = false;
        this.retired = true;
        this.pending.clear();
        for (CompletableFuture<Void> barrier : List.copyOf(this.barriers)) {
            this.complete(barrier);
        }
    }

    public void fail(Throwable exception) {
        this.recordFailure(exception);
        this.retire();
    }

    private synchronized void complete(CompletableFuture<Void> barrier) {
        this.barriers.remove(barrier);
        if (this.failure == null) {
            barrier.complete(null);
        } else {
            barrier.completeExceptionally(this.failure);
        }
    }

    private synchronized void recordFailure(Throwable exception) {
        if (this.failure == null) {
            this.failure = exception;
        }
        this.failures.accept(exception);
    }
}
