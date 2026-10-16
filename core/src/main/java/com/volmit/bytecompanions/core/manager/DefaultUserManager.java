package com.volmit.bytecompanions.core.manager;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.storage.UserStorage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of {@link UserManager}.
 *
 * <p>Delegates cache access to {@link CompanionPlayerCache}.
 * Delegates I/O to {@link UserStorage}.
 * Load/unload lifecycle is handled separately by
 * {@link com.volmit.bytecompanions.core.loader.DefaultUserLoader}.
 */
public final class DefaultUserManager implements UserManager {

    private final CompanionPlayerCache cache;
    private final UserStorage storage;

    public DefaultUserManager(
            final @NotNull CompanionPlayerCache cache,
            final @NotNull UserStorage storage
    ) {
        this.cache = cache;
        this.storage = storage;
    }

    @Override
    public @NotNull Optional<CompanionPlayer> find(final @NotNull Player player) {
        final CompanionPlayer companionPlayer = this.cache.getByUniqueId(player.getUniqueId());
        return Optional.ofNullable(companionPlayer);
    }

    @Override
    public @NotNull CompletableFuture<Optional<CompanionPlayer>> findAsync(final @NotNull Player player) {
        final DefaultCompanionPlayer cached = this.cache.getByUniqueId(player.getUniqueId());
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        return this.storage.async()
                .fetch(player.getUniqueId())
                .thenApply(userView -> {
                    if (userView == null) {
                        return Optional.empty();
                    }

                    final DefaultCompanionPlayer defaultCompanionPlayer = new DefaultCompanionPlayer(userView.playerId(), userView.playerName());
                    //this.cache.put(player, defaultCompanionPlayer);
                    return Optional.of(defaultCompanionPlayer);
                });
    }

}
