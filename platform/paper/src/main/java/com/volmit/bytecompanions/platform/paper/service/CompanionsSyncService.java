package com.volmit.bytecompanions.platform.paper.service;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;

public final class CompanionsSyncService {

    private final PaperPlugin paperPlugin;
    private final CompanionManager companionManager;

    public CompanionsSyncService(
            final @NotNull PaperPlugin paperPlugin,
            final @NotNull CompanionManager companionManager
    ) {
        this.paperPlugin = paperPlugin;
        this.companionManager = companionManager;
    }

    public void sync(final @NotNull Player player, final @NotNull Cause cause) {
        switch (cause) {
            case DEATH -> this.companionManager.unequipVisual(player);
            case RESPAWN -> this.scheduleEquip(player);
            case TELEPORT -> {
                this.companionManager.unequipVisual(player);
                this.scheduleEquip(player);
            }

        }

    }

    private void scheduleEquip(Player player) {
        CompanionManager current = this.companionManager;
        FoliaScheduler.runEntity(this.paperPlugin, player, () -> {
            if (player.isOnline() && !player.isDead() && this.paperPlugin.companionManager() == current) {
                current.equipVisual(player);
            }
        }, 1);
    }

    public enum Cause {

        TELEPORT,
        RESPAWN,
        DEATH,

    }

}
