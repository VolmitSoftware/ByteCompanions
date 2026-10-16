package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMetadataTest {
    private static PluginDescriptionFile descriptor() throws Exception {
        try (InputStream input = PluginMetadataTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(input, "plugin.yml");
            return new PluginDescriptionFile(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }

    @Test
    void reservedPermissionsCarryTheSharedDefaults() throws Exception {
        Map<String, Permission> permissions = descriptor().getPermissions().stream()
                .collect(Collectors.toMap(Permission::getName, Function.identity()));
        for (String everyone : List.of("bytecompanions.command", "bytecompanions.language.self")) {
            assertEquals(PermissionDefault.TRUE, permissions.get(everyone).getDefault(), everyone);
        }
        for (String operators : List.of("bytecompanions.config", "bytecompanions.debug", "bytecompanions.update",
                "bytecompanions.command.edit", "bytecompanions.command.setup")) {
            assertEquals(PermissionDefault.OP, permissions.get(operators).getDefault(), operators);
        }
        assertFalse(permissions.containsKey("bytecompanions.reload"));
        assertFalse(permissions.containsKey("bytecompanions.command.reload"));
    }

    @Test
    void descriptorDeclaresTheRootCommandWithItsAliases() throws Exception {
        PluginDescriptionFile descriptor = descriptor();
        assertEquals("ByteCompanions", descriptor.getName());
        assertEquals("26.1", descriptor.getAPIVersion());
        assertEquals(List.of("VolmitSoftware", "Arcane Arts", "SwiftSwamp", "NextdoorPsycho"), descriptor.getAuthors());
        assertEquals("https://volmitsoftware.com", descriptor.getWebsite());
        Map<String, Map<String, Object>> commands = descriptor.getCommands();
        assertEquals(List.of("bytecompanions"), List.copyOf(commands.keySet()));
        assertEquals(List.of("companions", "bc"), commands.get("bytecompanions").get("aliases"));
    }

    @Test
    void commandTreeHasNoReloadRouteAndHidesVersion() {
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CompanionCommands(null), DirectorEngineOptions.builder().build());
        assertEquals("bytecompanions", engine.getRoot().getDescriptor().getName());
        assertEquals(List.of("companions", "bc"), engine.getRoot().getDescriptor().getAliases());
        List<DirectorRuntimeNode> children = engine.getRoot().getChildren();
        assertTrue(children.stream().noneMatch(node -> node.getDescriptor().getName().equals("reload")));
        DirectorRuntimeNode version = children.stream().filter(node -> node.getDescriptor().getName().equals("version")).findFirst().orElseThrow();
        assertTrue(version.getDescriptor().isHidden());
        DirectorRuntimeNode debug = children.stream().filter(node -> node.getDescriptor().getName().equals("debug")).findFirst().orElseThrow();
        assertEquals(List.of("dump", "version"), debug.getChildren().stream().map(node -> node.getDescriptor().getName()).sorted().toList());
    }
}
