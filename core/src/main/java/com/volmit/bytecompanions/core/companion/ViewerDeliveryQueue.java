package com.volmit.bytecompanions.core.companion;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class ViewerDeliveryQueue {
    private final int capacity;
    private final Consumer<Throwable> failures;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Map<CompletableFuture<Void>, Throwable> barriers = new HashMap<>();
    private boolean accepting = true;
    private boolean draining;
    private boolean retired;
    private boolean overflowReported;

    public ViewerDeliveryQueue(int capacity, Consumer<Throwable> failures) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Viewer queue capacity must be positive");
        }
        this.capacity = capacity;
        this.failures = failures;
    }

    public boolean submit(Runnable delivery) {
        RejectedExecutionException overflow;
        synchronized (this) {
            if (!this.accepting) {
                return false;
            }
            if (this.pending.size() < this.capacity) {
                this.pending.addLast(delivery);
                return true;
            }
            if (this.overflowReported) {
                return false;
            }
            this.overflowReported = true;
            overflow = new RejectedExecutionException("Viewer delivery queue exceeded " + this.capacity + " pending deliveries; dropping deliveries until it drains");
        }
        this.failures.accept(overflow);
        return false;
    }

    public synchronized CompletableFuture<Void> flush() {
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        this.barriers.put(barrier, null);
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
                        this.overflowReported = false;
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
        for (CompletableFuture<Void> barrier : List.copyOf(this.barriers.keySet())) {
            this.complete(barrier);
        }
    }

    public void fail(Throwable exception) {
        this.recordFailure(exception);
        this.retire();
    }

    private synchronized void complete(CompletableFuture<Void> barrier) {
        if (!this.barriers.containsKey(barrier)) {
            return;
        }
        Throwable failure = this.barriers.remove(barrier);
        if (failure == null) {
            barrier.complete(null);
        } else {
            barrier.completeExceptionally(failure);
        }
    }

    private void recordFailure(Throwable exception) {
        synchronized (this) {
            this.barriers.replaceAll((barrier, existing) -> existing == null ? exception : existing);
        }
        this.failures.accept(exception);
    }
}
