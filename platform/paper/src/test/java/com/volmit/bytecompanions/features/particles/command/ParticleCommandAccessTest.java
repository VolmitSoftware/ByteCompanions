package com.volmit.bytecompanions.features.particles.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.features.particles.FeaturePermission;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import java.io.File;
import java.util.logging.Logger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleCommandAccessTest {

    @Test
    void selectionPermissionDoesNotBypassAnyCommandCheck() {
        List<Component> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of(FeaturePermission.resolveEffect("angel_ring_effect")), messages);
        ParticlesCommand command = new ParticlesCommand(null, null, new LanguageService(new File("."), Logger.getAnonymousLogger()));
        for (String action : List.of("menu", "equip", "unequip")) {
            assertTrue(command.execute(sender, "companions particles", new String[]{action, "bee", "angel_ring_effect"}));
        }
        assertEquals(3, messages.size());
        assertEquals(List.of("help"), command.tabComplete(sender, "companions particles", new String[]{""}));
    }

    @Test
    void particleCommandPermissionsRemainIndependent() {
        CommandSender sender = sender(Set.of("bytecompanions.command.particles.menu"), new ArrayList<>());
        assertTrue(ParticlesCommand.hasCommandPermission(sender, "menu"));
        assertFalse(ParticlesCommand.hasCommandPermission(sender, "equip"));
        assertFalse(ParticlesCommand.hasCommandPermission(sender, "unequip"));
        assertEquals("bytecompanions.particle.angel_ring_effect", FeaturePermission.resolveEffect("angel_ring_effect"));
    }

    private static CommandSender sender(Set<String> permissions, List<Component> messages) {
        return CommandSender.class.cast(Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return permissions.contains(arguments[0]);
                    }
                    if (method.getName().equals("sendMessage") && arguments[0] instanceof Component component) {
                        messages.add(component);
                        return null;
                    }
                    if ((method.getName().equals("sendMessage") || method.getName().equals("sendRichMessage"))
                            && arguments[0] instanceof String text) {
                        messages.add(Component.text(text));
                        return null;
                    }
                    throw new AssertionError("Unexpected sender call: " + method.getName());
                }));
    }
}
