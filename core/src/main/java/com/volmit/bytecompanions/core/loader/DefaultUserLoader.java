package com.volmit.bytecompanions.core.loader;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.factory.UserCompanionFactory;
import com.volmit.bytecompanions.core.manager.UserManager;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * Handles the load/unload lifecycle of {@link DefaultCompanionPlayer} instances.
 *
 * <p>Separates I/O responsibility from the manager layer:
 * <ul>
 *   <li>{@link #fetchAsync(UUID)} — fetches the persisted selection without accessing player state.</li>
 *   <li>{@link #unload(Player)} — removes the cached player state on quit.</li>
 * </ul>
 *
 * <p>This class is internal to the core module. External code interacts with
 * {@link UserManager} via the API.
 */
public final class DefaultUserLoader {

    private final CompanionPlayerCache cache;
    private final UserStorage storage;
    private final UserCompanionFactory userCompanionFactory;
    private final CompanionTypeRegistry companionTypeRegistry;

    public DefaultUserLoader(
            final @NotNull CompanionPlayerCache cache,
            final @NotNull UserStorage storage,
            final @NotNull UserCompanionFactory companionFactory,
            final @NotNull CompanionTypeRegistry companionTypeRegistry
    ) {
        this.cache = cache;
        this.storage = storage;
        this.userCompanionFactory = companionFactory;
        this.companionTypeRegistry = companionTypeRegistry;
    }

    /**
     * Fetches a persisted selection for installation on the player's owning thread.
     *
     * @param playerId the joining player's ID
     * @return the stored selection, or null for a new player
     */
    public @NotNull CompletableFuture<UserView> fetchAsync(UUID playerId) {
        return this.storage.async().fetch(playerId);
    }

    public DefaultCompanionPlayer create(Player player, UserView view) {
        return view == null ? new DefaultCompanionPlayer(player.getUniqueId(), player.getName())
                : this.userCompanionFactory.create(view, this.companionTypeRegistry);
    }

    /**
     * Removes the player from cache.
     *
     * @param player the quitting player
     * @return the removed player record, or {@code null} if not cached
     */
    public @Nullable DefaultCompanionPlayer unload(final @NotNull Player player) {
        return this.cache.remove(player);
    }

}
