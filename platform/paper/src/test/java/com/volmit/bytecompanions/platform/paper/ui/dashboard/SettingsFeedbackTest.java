package com.volmit.bytecompanions.platform.paper.ui.dashboard;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import com.volmit.bytecompanions.platform.paper.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.collection.CollectionProfile;
import com.volmit.bytecompanions.platform.paper.collection.CollectionService;
import com.volmit.bytecompanions.platform.paper.collection.CollectionFailure;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import com.volmit.bytecompanions.platform.paper.util.inventorygui.BukkitTextInput;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SettingsFeedbackTest {
    @Test
    void closingTheSettingsMenuCannotDiscardCommandSaveFeedback() {
        try (Fixture fixture = new Fixture()) {
            fixture.menus.settings(fixture.player, "particles", "full");
            fixture.menus.navigated(fixture.player);
            fixture.complete(CollectionService.Status.SUCCESS);
            verify(fixture.language).send(fixture.player, "collection.saved");
        }
    }

    @Test
    void aFailedOrBusySaveStillReportsItsResultAfterNavigation() {
        for (CollectionService.Status status : List.of(CollectionService.Status.FAILED, CollectionService.Status.BUSY)) {
            try (Fixture fixture = new Fixture()) {
                fixture.menus.settings(fixture.player, "particles", "full");
                fixture.menus.navigated(fixture.player);
                fixture.complete(status);
                verify(fixture.language).send(fixture.player, "collection.rejected", "detail", "Localized rejection");
            }
        }
    }

    @Test
    void replacedRuntimeAndReconnectedPlayerCannotReceiveOldSaveFeedback() {
        try (Fixture fixture = new Fixture()) {
            fixture.menus.settings(fixture.player, "particles", "full");
            fixture.active.set(false);
            fixture.complete(CollectionService.Status.SUCCESS);
            verifyNoInteractions(fixture.language);
        }
        try (Fixture fixture = new Fixture()) {
            fixture.menus.settings(fixture.player, "particles", "full");
            when(fixture.server.getPlayer(fixture.playerId)).thenReturn(mock(Player.class));
            fixture.complete(CollectionService.Status.SUCCESS);
            verifyNoInteractions(fixture.language);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final PaperPlugin plugin = mock(PaperPlugin.class);
        final Server server = mock(Server.class);
        final Player player = mock(Player.class);
        final UUID playerId = UUID.randomUUID();
        final LanguageService language = mock(LanguageService.class);
        final CollectionService service = mock(CollectionService.class);
        final AtomicBoolean active = new AtomicBoolean(true);
        final CompletableFuture<CollectionService.Result> saving = new CompletableFuture<>();
        final List<Runnable> callbacks = new ArrayList<>();
        final MockedStatic<BukkitTextInput> inputs = mockStatic(BukkitTextInput.class);
        final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        final CollectionMenus menus;

        Fixture() {
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            when(server.getPlayer(playerId)).thenReturn(player);
            when(plugin.captureRuntimeGuard()).thenReturn(active::get);
            when(plugin.collectionService()).thenReturn(service);
            when(plugin.languageService()).thenReturn(language);
            when(language.render(eq(player), any(), any())).thenReturn(ComponentText.component(Component.text("Localized rejection")));
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.isOnline()).thenReturn(true);
            when(service.profile(playerId)).thenReturn(Optional.of(CollectionProfile.empty()));
            when(service.updateViewerPreferences(eq(player), any())).thenReturn(saving);
            inputs.when(() -> BukkitTextInput.register(plugin)).thenReturn(mock(BukkitTextInput.class));
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(0L), any(Runnable.class)))
                    .thenAnswer(invocation -> { callbacks.add(invocation.getArgument(2)); return true; });
            CompanionMenus dashboard = mock(CompanionMenus.class);
            when(dashboard.accessible(player, FeaturePermission.Command.SETTINGS)).thenReturn(true);
            menus = new CollectionMenus(plugin, dashboard);
        }

        void complete(CollectionService.Status status) {
            saving.complete(new CollectionService.Result(status, status == CollectionService.Status.SUCCESS ? null
                    : CollectionFailure.of(CollectionFailure.Reason.NO_PERMISSION)));
            assertEquals(1, callbacks.size());
            callbacks.getFirst().run();
        }

        @Override
        public void close() {
            scheduler.close();
            inputs.close();
        }
    }
}
