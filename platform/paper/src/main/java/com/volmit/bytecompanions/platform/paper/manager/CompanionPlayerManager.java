package com.volmit.bytecompanions.platform.paper.manager;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.*;
import com.volmit.bytecompanions.api.result.EquipCompanionResult;
import com.volmit.bytecompanions.api.result.UnequipCompanionResult;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.api.util.response.context.ResponseContext;
import com.volmit.bytecompanions.core.manager.DefaultUserManager;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.messenger.MessageDispatcher;
import com.volmit.bytecompanions.platform.paper.messenger.MessageKey;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public final class CompanionPlayerManager {

    private final JavaPlugin plugin;
    private final DefaultUserManager userManager;
    private final CompanionManager companionManager;
    private final CompanionTypeRegistry companionTypeRegistry;
    private final MessageDispatcher messageDispatcher;
    private final UserStorage userStorage;
    private final Configuration.CompanionLimits companionLimits;

    public CompanionPlayerManager(
            final @NotNull JavaPlugin plugin,
            final @NotNull DefaultUserManager userManager,
            final @NotNull CompanionManager companionManager,
            final @NotNull MessageDispatcher messageDispatcher,
            final @NotNull CompanionTypeRegistry companionTypeRegistry,
            final @NotNull UserStorage userStorage,
            final @NotNull Configuration.CompanionLimits companionLimits
    ) {
        this.plugin = plugin;
        this.userManager = userManager;
        this.messageDispatcher = messageDispatcher;
        this.companionManager = companionManager;
        this.companionTypeRegistry = companionTypeRegistry;
        this.userStorage = userStorage;
        this.companionLimits = companionLimits;
    }

    public boolean isSpawned(final @NotNull Player player, final @NotNull String resolvedId) {
        return ByteCompanions.getAPI().getCompanionPlayer(player.getUniqueId())
                .map(companionPlayer -> companionPlayer.hasCompanion(resolvedId))
                .orElse(false);
    }

    public boolean canSpawn(final @NotNull Player player) {
        return this.canSpawn(player, null);
    }

    public boolean canSpawn(final @NotNull Player player, final @Nullable CompanionModel companionModel) {
        final CompanionPlayer companionPlayer = this.userManager.find(player).orElse(null);
        if (companionPlayer == null) {
            this.messageDispatcher.send(player, MessageKey.ERROR_PLAYER_NOT_LOADED);
            return false;
        }
        if (companionPlayer.hasAnyExclusive()) {
            this.messageDispatcher.send(player, MessageKey.ERROR_PLAYER_HAS_A_EXCLUSIVE_COMPANION);
            return false;
        }

        final int companionsActive = companionPlayer.companionsActive();
        final int maxTotalPerPlayer = this.companionLimits.maxTotalPerPlayer();

        if (maxTotalPerPlayer > 0 && companionsActive >= maxTotalPerPlayer) {
            this.messageDispatcher.send(player, MessageKey.ERROR_COMPANIONS_LIMIT_REACHED,
                    "{total_limit}", Integer.toString(maxTotalPerPlayer),
                    "{current_active}", Integer.toString(companionsActive)
            );

            return false;
        }

        if (companionModel != null) {
            final int modelLimit = switch (companionModel) {
                case HEAD -> this.companionLimits.perModel().headOnly();
                case FULL_BODY -> this.companionLimits.perModel().fullBody();
            };

            return !this.exceedsModelLimit(player, companionPlayer, companionModel, modelLimit, companionsActive, maxTotalPerPlayer);
        }

        return true;
    }

    public void spawn(final @NotNull Player player, final @NotNull String companionTypeId) {
        final CompanionType companionType = this.companionTypeRegistry.getOrNull(companionTypeId);
        if (companionType == null) {
            this.messageDispatcher.send(player, MessageKey.ERROR_COMPANION_NOT_FOUND, "{companion}", companionTypeId);
            return;
        }

        this.spawn(player, companionType);
    }

    public void spawn(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {

        if (companionType.animations().isEmpty()) {
            this.messageDispatcher.send(player, MessageKey.ERROR_COMPANION_DOES_NOT_HAVE_ANIMATIONS);
            return;
        }

        final String companionTypeId = companionType.id();
        final String companionPermission = FeaturePermission.Companions.resolve(companionTypeId);

        if (!player.hasPermission(companionPermission)) {
            this.messageDispatcher.send(player, MessageKey.ERROR_NO_ACCESS_TO_COMPANION);
            return;
        }

        final CompanionPlayer companionPlayer = this.userManager.find(player).orElse(null);
        if (companionPlayer == null) {
            this.messageDispatcher.send(player, MessageKey.ERROR_PLAYER_NOT_LOADED);
            return;
        }
        if (companionPlayer.hasAnyExclusive()) {
            this.messageDispatcher.send(player, MessageKey.ERROR_PLAYER_HAS_A_EXCLUSIVE_COMPANION);
            return;
        }

        final int companionsActive = companionPlayer.companionsActive();
        final int maxTotalPerPlayer = this.companionLimits.maxTotalPerPlayer();

        if (maxTotalPerPlayer > 0 && companionsActive >= maxTotalPerPlayer) {
            this.messageDispatcher.send(player, MessageKey.ERROR_COMPANIONS_LIMIT_REACHED,
                    "{total_limit}", Integer.toString(maxTotalPerPlayer),
                    "{current_active}", Integer.toString(companionsActive)
            );

            return;
        }

        final CompanionModel companionModel = companionType.model();
        final int modelLimit = switch (companionModel) {
            case HEAD -> this.companionLimits.perModel().headOnly();
            case FULL_BODY -> this.companionLimits.perModel().fullBody();
        };

        if (this.exceedsModelLimit(player, companionPlayer, companionModel, modelLimit, companionsActive, maxTotalPerPlayer)) {
            return;
        }

        final ResponseContext<Companion, EquipCompanionResult> responseContext = this.companionManager.equip(player, companionPlayer, companionType);
        if (!responseContext.success()) {
            switch (responseContext.result()) {
                case ALREADY_EQUIPPED -> this.messageDispatcher.send(player, MessageKey.ERROR_COMPANION_ALREADY_EQUIPPED);
                case COMPANION_TYPE_IS_EXCLUSIVE -> this.messageDispatcher.send(player, MessageKey.ERROR_COMPANION_IS_EXCLUSIVE);
            }

            return;
        }

        this.reportPersistence(player, this.userStorage.async().save(new UserEntry(
                        player.getUniqueId(), player.getName(), companionPlayer.companions().keySet())),
                MessageKey.SUCCESS_COMPANION_EQUIPPED, "{companion}", companionTypeId);
    }

    public void despawn(final @NotNull Player player, final @NotNull String companionTypeId) {
        final ResponseContext<CompanionPlayer, UnequipCompanionResult> responseContext = this.companionManager.unequip(player, companionTypeId);
        if (!responseContext.success()) {
            this.messageDispatcher.send(player, MessageKey.ERROR_NO_COMPANION_EQUIPPED);
            return;
        }

        this.reportPersistence(player, this.userStorage.async().save(new UserEntry(
                        player.getUniqueId(), player.getName(), responseContext.value().companions().keySet())),
                MessageKey.SUCCESS_COMPANION_UNEQUIPPED);
    }

    public void despawn(final @NotNull Player player) {
        final CompanionPlayer companionPlayer = ByteCompanions.getAPI().getCompanionPlayerOrNull(player.getUniqueId());
        if (companionPlayer == null) {
            return;
        }

        final ResponseContext<Collection<String>, UnequipCompanionResult> responseContext = this.companionManager.unequip(player, companionPlayer);
        if (!responseContext.success()) {
            this.messageDispatcher.send(player, MessageKey.ERROR_NO_COMPANION_EQUIPPED);
            return;
        }

        this.reportPersistence(player, this.userStorage.async().save(new UserEntry(
                        player.getUniqueId(), player.getName(), companionPlayer.companions().keySet())),
                MessageKey.SUCCESS_COMPANION_UNEQUIPPED);
    }

    private void reportPersistence(Player player, CompletableFuture<?> operation, MessageKey success, String... replacements) {
        CompanionPlayer session = this.userManager.find(player).orElse(null);
        operation.whenComplete((ignored, failure) -> {
            if (session == null || !this.plugin.isEnabled()) {
                return;
            }
            FoliaScheduler.runEntity(this.plugin, player, () -> {
                if (!player.isOnline() || this.userManager.find(player).orElse(null) != session) {
                    return;
                }
                if (failure == null) {
                    this.messageDispatcher.send(player, success, replacements);
                } else {
                    this.messageDispatcher.send(player, MessageKey.ERROR_PERSISTENCE_FAILED);
                }
            });
        });
    }

    private boolean exceedsModelLimit(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer,
            final @NotNull CompanionModel model,
            final int modelLimit,
            final int totalActive,
            final int maxTotalPerPlayer
    ) {
        if (modelLimit <= 0) {
            return false;
        }

        final int currentActive = companionPlayer.companionsActive(model);
        if (currentActive < modelLimit) {
            return false;
        }

        this.messageDispatcher.send(player, MessageKey.ERROR_COMPANIONS_LIMIT_PER_MODEL_REACHED,
                "{total_limit}", Integer.toString(maxTotalPerPlayer),
                "{model}", model.name(),
                "{current_active}", Integer.toString(totalActive),
                "{current_model_active}", Integer.toString(currentActive),
                "{model_limit}", Integer.toString(modelLimit)
        );

        return true;
    }

}
