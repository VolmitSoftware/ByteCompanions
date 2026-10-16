package com.volmit.bytecompanions.platform.paper.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.platform.paper.localization.CompanionMessages;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DirectorCommandTest {
    @Test
    void rootRegistersAllGameplayAndAdministrationRoutesWithLocalizedDescriptions() {
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CompanionCommands(null), DirectorEngineOptions.builder().build());
        assertEquals("bytecompanions", engine.getRoot().getDescriptor().getName());
        assertEquals(Set.of("menu", "collection", "outfits", "interact", "edit", "spawn", "despawn", "debug", "config", "setup", "language", "version",
                        "particles", "upgrades", "boosts",
                        "progression", "booster", "transactions", "settings", "status"),
                engine.getRoot().getChildren().stream().map(node -> node.getDescriptor().getName()).collect(Collectors.toSet()));
        for (DirectorRuntimeNode node : engine.getRoot().getChildren()) {
            assertNotNull(CompanionMessages.catalog().key(node.getDescriptor().getDescriptionKey()));
        }
    }

    @Test
    void inheritedPositionalSyntaxMapsToDirectorNames() {
        assertEquals(List.of("settings", "setting=visibility", "value=own"),
                CompanionBukkitCommand.normalize(new String[]{"settings", "visibility", "own"}));
        assertEquals(List.of("interact", "action=stay", "companion=bee"),
                CompanionBukkitCommand.normalize(new String[]{"interact", "stay", "bee"}));
        assertEquals(List.of("menu", "companion=bee"), CompanionBukkitCommand.normalize(new String[]{"menu", "bee"}));
        assertEquals(List.of("spawn", "companion=bee"), CompanionBukkitCommand.normalize(new String[]{"spawn", "bee"}));
        assertEquals(List.of("particles", "action=equip", "companion=bee", "effect=angel_ring_effect"),
                CompanionBukkitCommand.normalize(new String[]{"particles", "equip", "bee", "angel_ring_effect"}));
        assertEquals(List.of("spawn", "companion=bee"), CompanionBukkitCommand.normalize(new String[]{"spawn", "companion=bee"}));
    }

    @Test
    void progressionActionsKeepPlayerDefinitionAndActivationArgumentsDistinct() {
        assertEquals(List.of("progression", "action=give", "player=Alex", "companion=bee", "xp=250"),
                CompanionBukkitCommand.normalize(new String[]{"progression", "give", "Alex", "bee", "250"}));
        assertEquals(List.of("booster", "action=give", "player=Alex", "booster=weekend", "count=2"),
                CompanionBukkitCommand.normalize(new String[]{"booster", "give", "Alex", "weekend", "2"}));
        assertEquals(List.of("booster", "action=start", "booster=weekend"),
                CompanionBukkitCommand.normalize(new String[]{"booster", "start", "weekend"}));
        assertEquals(List.of("booster", "action=cancel", "activation=activation-id"),
                CompanionBukkitCommand.normalize(new String[]{"booster", "cancel", "activation-id"}));
        assertEquals(List.of("transactions", "action=resolve", "transaction=purchase-id", "resolution=grant"),
                CompanionBukkitCommand.normalize(new String[]{"transactions", "resolve", "purchase-id", "grant"}));
        assertEquals(List.of("upgrades", "companion=bee"),
                CompanionBukkitCommand.normalize(new String[]{"upgrades", "bee"}));
    }
}
