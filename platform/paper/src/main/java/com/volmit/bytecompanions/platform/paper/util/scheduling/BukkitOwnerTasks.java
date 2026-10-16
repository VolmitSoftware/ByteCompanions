package com.volmit.bytecompanions.platform.paper.util.scheduling;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BukkitOwnerTasks {
    private BukkitOwnerTasks() {
    }

    public static CompletableFuture<Void> entity(Plugin plugin, Entity entity, Runnable action) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entity, "entity");
        return dispatch(FoliaScheduler.isOwnedByCurrentRegion(entity), action,
                (run, retired) -> FoliaScheduler.runEntity(plugin, entity, run, 0L, retired));
    }

    public static CompletableFuture<Void> region(Plugin plugin, Location location, Runnable action) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(location, "location");
        Location destination = location.clone();
        return dispatch(FoliaScheduler.isOwnedByCurrentRegion(destination), action,
                (run, retired) -> FoliaScheduler.runRegion(plugin, destination, run));
    }

    static CompletableFuture<Void> dispatch(boolean owned, Runnable action, OwnerScheduler scheduler) {
        Objects.requireNonNull(action, "action");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicBoolean retired = new AtomicBoolean();
        Runnable run = () -> {
            try {
                action.run();
                completion.complete(null);
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        };
        try {
            if (owned) {
                run.run();
            } else {
                boolean scheduled = scheduler.schedule(run, () -> {
                    retired.set(true);
                    if (accepted.get()) {
                        completion.complete(null);
                    }
                });
                if (!scheduled) {
                    completion.completeExceptionally(new RejectedExecutionException("The owning scheduler rejected cleanup"));
                } else {
                    accepted.set(true);
                    if (retired.get()) {
                        completion.complete(null);
                    }
                }
            }
        } catch (Throwable failure) {
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    @FunctionalInterface
    interface OwnerScheduler {
        boolean schedule(Runnable action, Runnable retired);
    }
}
