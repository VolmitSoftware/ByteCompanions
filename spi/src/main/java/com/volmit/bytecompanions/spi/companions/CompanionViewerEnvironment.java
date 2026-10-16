package com.volmit.bytecompanions.spi.companions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

public interface CompanionViewerEnvironment {
    Collection<ViewerSnapshot> snapshots();

    @Nullable ViewerSnapshot snapshot(Player player);

    boolean visible(ViewerSnapshot viewer, @Nullable Player owner);

    int particleInterval(ViewerSnapshot viewer, @Nullable Player owner);

    boolean deliverVisual(ViewerSnapshot viewer, @Nullable Player owner, UUID expectedWorld,
                          long particleFrame, BooleanSupplier stillValid, Runnable ownerDelivery, Runnable rejected);

    boolean deliver(ViewerSnapshot viewer, UUID expectedWorld, BooleanSupplier stillValid, Runnable ownerDelivery);

    CompletionStage<Void> flush();

    CompletionStage<Void> closeAsync();

    static CompanionViewerEnvironment validationOnly() {
        return new CompanionViewerEnvironment() {
            @Override
            public Collection<ViewerSnapshot> snapshots() {
                return List.of();
            }

            @Override
            public ViewerSnapshot snapshot(Player player) {
                return null;
            }

            @Override
            public boolean visible(ViewerSnapshot viewer, Player owner) { return false; }

            @Override
            public int particleInterval(ViewerSnapshot viewer, Player owner) { return 0; }

            @Override
            public boolean deliverVisual(ViewerSnapshot viewer, Player owner, UUID expectedWorld,
                                         long particleFrame, BooleanSupplier stillValid, Runnable ownerDelivery,
                                         Runnable rejected) {
                throw new IllegalStateException("Native preflight cannot deliver packets");
            }

            @Override
            public boolean deliver(ViewerSnapshot viewer, UUID expectedWorld, BooleanSupplier stillValid, Runnable ownerDelivery) {
                throw new IllegalStateException("Native preflight cannot deliver packets");
            }

            @Override
            public CompletionStage<Void> flush() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> closeAsync() {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
