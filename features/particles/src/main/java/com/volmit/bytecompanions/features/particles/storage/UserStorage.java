package com.volmit.bytecompanions.features.particles.storage;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.storage.entry.UserEntry;
import com.volmit.bytecompanions.features.particles.storage.view.UserView;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface UserStorage {

    @NotNull List<UserView> find(final @NotNull UUID userId);

    void save(final @NotNull UserEntry userEntry);
    int delete(final @NotNull UserEntry userEntry);

    @NotNull Async async();

    interface Async {

        @NotNull CompletableFuture<List<UserView>> find(final @NotNull UUID userId);

        @NotNull CompletableFuture<Void> save(final @NotNull UserEntry userEntry);

        @NotNull CompletableFuture<Integer> delete(final @NotNull UserEntry userEntry);

    }

}
