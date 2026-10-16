package com.volmit.bytecompanions.platform.paper.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandAccessTest {

    @Test
    void viewerSettingsUseTheirOwnPermissionWithoutRequiringCollectionAccess() {
        CommandSender sender = sender(Set.of("bytecompanions.command.settings"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender, "settings"));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender, "menu"));
        assertEquals(List.of("help", "settings", "status"), CompanionBukkitCommand.visibleCommands(sender, Set.of()));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command.menu")), "settings"));
    }

    @Test
    void companionOwnershipPermissionDoesNotGrantCommandAccess() {
        CommandSender sender = sender(Set.of("bytecompanions.companion.bee"));
        for (String command : List.of("menu", "collection", "outfits", "interact", "edit", "spawn", "despawn", "debug", "config", "setup", "language")) {
            assertFalse(CompanionBukkitCommand.hasCommandPermission(sender, command), command);
        }
        assertEquals(List.of("help", "status"), CompanionBukkitCommand.visibleCommands(sender, Set.of("particles", "effects")));
    }

    @Test
    void starterSetupRequiresItsOwnAdministrationPermission() {
        CommandSender sender = sender(Set.of("bytecompanions.command.setup"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender, "setup"));
        assertEquals(List.of("help", "setup", "status"), CompanionBukkitCommand.visibleCommands(sender, Set.of()));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command.menu")), "setup"));
    }

    @Test
    void companionEditorDoesNotInheritPlayerMenuOrConfigurationPermissions() {
        CommandSender editor = sender(Set.of("bytecompanions.command.edit"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(editor, "edit"));
        assertEquals(List.of("edit", "help", "status"), CompanionBukkitCommand.visibleCommands(editor, Set.of()));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command.menu",
                "bytecompanions.config", "bytecompanions.command.setup")), "edit"));
    }

    @Test
    void languageSelectionRequiresBothSelfPermissionsOrAdministratorAccess() {
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("volmit.language.self")), "language"));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.language.self")), "language"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("volmit.language.self", "bytecompanions.language.self")), "language"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.config")), "language"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("volmit.language.admin")), "language"));
    }

    @Test
    void diagnosticsUsesOnlyItsCommandPermission() {
        CommandSender sender = sender(Set.of("bytecompanions.debug"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender, "debug"));
        assertEquals(List.of("debug", "help", "status"), CompanionBukkitCommand.visibleCommands(sender, Set.of("particles")));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command.menu")), "debug"));
    }

    @Test
    void commandPermissionsUseTheSingularNamespace() {
        CommandSender sender = sender(Set.of("bytecompanions.command.menu", "bytecompanions.command.spawn"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender, "menu"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(sender, "spawn"));
        assertFalse(CompanionBukkitCommand.hasCommandPermission(sender, "despawn"));
    }

    @Test
    void collectionFeaturesRequireMenuAccessAndTheirOwnPermission() {
        CommandSender menu = sender(Set.of("bytecompanions.command.menu"));
        assertTrue(CompanionBukkitCommand.hasCommandPermission(menu, "collection"));
        for (String command : List.of("outfits", "interact")) {
            assertFalse(CompanionBukkitCommand.hasCommandPermission(menu, command));
            assertFalse(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command." + command)), command));
            assertTrue(CompanionBukkitCommand.hasCommandPermission(sender(Set.of("bytecompanions.command.menu", "bytecompanions.command." + command)), command));
        }
    }

    @Test
    void disabledFeaturesStayOutOfHelpAndCompletionDespitePermissions() {
        CommandSender sender = sender(Set.of("bytecompanions.command.particles.menu"));
        assertEquals(List.of("help", "status"), CompanionBukkitCommand.visibleCommands(sender, Set.of()));
        assertEquals(List.of("help", "particles", "status"),
                CompanionBukkitCommand.visibleCommands(sender, Set.of("particles")));
    }

    private static CommandSender sender(Set<String> permissions) {
        return CommandSender.class.cast(Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return arguments[0].equals("bytecompanions.command") || permissions.contains(arguments[0]);
                    }
                    throw new AssertionError("Unexpected sender call: " + method.getName());
                }));
    }
}
