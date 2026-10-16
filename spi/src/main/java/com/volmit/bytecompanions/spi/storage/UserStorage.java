package com.volmit.bytecompanions.spi.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent storage contract for companion player data.
 *
 * <p>Uses distinct types for reading ({@link UserView}) and writing ({@link UserEntry}),
 * following the same CQRS-style separation as byte-clans.
 *
 * <p>Storage only ever holds companions that are currently equipped — a row's
 * presence <i>is</i> the equipped state. There is no separate "owned but
 * unequipped" concept.
 *
 * <p><b>Sync vs Async:</b> sync methods contain the actual storage operations.
 * Async methods delegate execution to the configured storage executor.
 */
public interface UserStorage {

    // ── Sync ─────────────────────────────────────────────────────────────

    /**
     * Fetches the set of currently equipped companions for a player.
     *
     * @param playerId the player's UUID
     * @return the player's view, or {@code null} if the player has no equipped companions
     */
    @Nullable
    UserView fetch(final @NotNull UUID playerId);

    /**
     * Replaces the player's entire equipped-companion set with the one provided
     * in {@code entry}. Companions not present in {@code entry} are unequipped.
     *
     * @param entry the full desired state to persist
     */
    void save(final @NotNull UserEntry entry);

    /**
     * Equips a single companion for the player (inserts a row), leaving the
     * rest of their equipped set untouched.
     *
     * @param userEntry        the UserEntry
     * @param companionTypeId the companion type to equip
     */
    void equip(
            final @NotNull UserEntry userEntry,
            final @NotNull String companionTypeId
    );

    /**
     * Unequips a single companion for the player (deletes a row), leaving the
     * rest of their equipped set untouched.
     *
     * @param playerId        the player's UUID
     * @param companionTypeId the companion type to unequip
     */
    void unequip(
            final @NotNull UUID playerId,
            final @NotNull String companionTypeId
    );

    void unequip(
            final @NotNull UUID playerId,
            final @NotNull Collection<String> companionTypeIds
    );

    /**
     * Deletes all equipped-companion data for a player.
     *
     * @param playerId the player's UUID
     * @return the number of rows deleted
     */
    int delete(final @NotNull UUID playerId);


    // ── Async ────────────────────────────────────────────────────────────

    /**
     * Returns the async variant of this storage, delegating each call to the
     * configured storage executor.
     */
    @NotNull
    Async async();

    interface Async {

        /**
         * Async variant of {@link UserStorage#fetch(UUID)}.
         */
        @NotNull
        CompletableFuture<@Nullable UserView> fetch(
                final @NotNull UUID playerId
        );

        /**
         * Async variant of {@link UserStorage#save(UserEntry)}.
         */
        @NotNull
        CompletableFuture<Void> save(
                final @NotNull UserEntry entry
        );

        /**
         * Async variant of {@link UserStorage#equip(UserEntry, String)}.
         */
        @NotNull
        CompletableFuture<Void> equip(
                final @NotNull UserEntry userEntry,
                final @NotNull String companionTypeId
        );

        @NotNull
        CompletableFuture<Void> unequip(
                final @NotNull UUID playerId,
                final @NotNull Collection<String> companionTypeIds
        );

        /**
         * Async variant of {@link UserStorage#unequip(UUID, String)}.
         */
        @NotNull
        CompletableFuture<Void> unequip(
                final @NotNull UUID playerId,
                final @NotNull String companionTypeId
        );

        /**
         * Async variant of {@link UserStorage#delete(UUID)}.
         */
        @NotNull
        CompletableFuture<Integer> delete(
                final @NotNull UUID playerId
        );
    }

}