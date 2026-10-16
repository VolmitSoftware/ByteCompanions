package com.volmit.bytecompanions.core.manager;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages access to loaded {@link CompanionPlayer} instances.
 *
 * <p><b>Cache vs storage:</b>
 * <ul>
 *   <li>{@link #find(UUID)} — instant in-memory lookup. Only works while the player is online
 *       and their data has been loaded. Returns empty if the player is offline or not yet loaded.</li>
 *   <li>{@link #findAsync(UUID)} — queries storage. Works for offline players and is the
 *       correct choice when you need data that may not be cached.</li>
 * </ul>
 */
public interface UserManager {

    /**
     * Looks up a companion player from the in-memory cache.
     *
     * @param playerId the player's UUID
     * @return an optional containing the companion player, empty if not cached
     */
    @NotNull Optional<CompanionPlayer> find(final @NotNull Player player);

    /**
     * Queries storage asynchronously for the given player.
     * Checks the cache first — if found, completes immediately without hitting storage.
     *
     * @param playerId the player's UUID
     * @return a future resolving to the companion player if found
     */
    @NotNull CompletableFuture<Optional<CompanionPlayer>> findAsync(final @NotNull Player player);

}
