package com.volmit.bytecompanions.features.decorations;

import com.volmit.bytecompanions.api.companion.Companion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class DecorationRuntime {
    private final Supplier<Companion> factory;
    private final Function<Runnable, CompletableFuture<Void>> owner;
    private final Predicate<Runnable> scheduleTick;
    private final Consumer<Throwable> tickFailure;
    private Companion companion;
    private boolean closed;
    private CompletableFuture<Void> started;
    private CompletableFuture<Void> stopped;

    DecorationRuntime(Supplier<Companion> factory, Function<Runnable, CompletableFuture<Void>> owner,
                      Predicate<Runnable> scheduleTick, Consumer<Throwable> tickFailure) {
        this.factory = factory;
        this.owner = owner;
        this.scheduleTick = scheduleTick;
        this.tickFailure = tickFailure;
    }

    synchronized CompletableFuture<Void> startAsync() {
        if (this.started == null) {
            this.started = this.owner.apply(this::start);
        }
        return this.started.copy();
    }

    private synchronized void start() {
        if (this.closed) {
            throw new IllegalStateException("Decoration was stopped before startup completed");
        }
        this.companion = this.factory.get();
        this.companion.tick();
        this.scheduleNextTick();
    }

    private synchronized void tick() {
        if (this.closed || this.companion == null) {
            return;
        }
        try {
            this.companion.tick();
        } catch (RuntimeException | LinkageError failure) {
            this.tickFailure.accept(failure);
        }
        try {
            this.scheduleNextTick();
        } catch (RuntimeException | LinkageError failure) {
            this.tickFailure.accept(failure);
        }
    }

    private void scheduleNextTick() {
        if (!this.scheduleTick.test(this::tick)) {
            throw new RejectedExecutionException("Decoration tick could not reach its owning region");
        }
    }

    synchronized Companion companion() {
        return this.companion;
    }

    synchronized CompletableFuture<Void> closeAsync() {
        if (this.stopped == null) {
            this.closed = true;
            Companion removed = this.companion;
            this.companion = null;
            this.stopped = removed == null ? CompletableFuture.completedFuture(null)
                    : this.owner.apply(removed::destroy);
        }
        return this.stopped.copy();
    }
}
