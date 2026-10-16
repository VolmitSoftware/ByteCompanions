package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationFactory;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.features.animations.AnimationsFeature;
import com.volmit.bytecompanions.features.buffs.BuffPlatformSupport;
import com.volmit.bytecompanions.features.buffs.configuration.Companions;
import com.volmit.bytecompanions.features.buffs.configuration.Configuration;
import com.volmit.bytecompanions.features.buffs.configuration.Vanilla;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.util.Operation;
import com.volmit.bytecompanions.platform.paper.companion.animations.BuiltinAnimationRegistry;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionDefinitions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterResourcesTest {
    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void collectionUsesDistinctVanillaIconsAndEquipmentWithoutExternalModels() throws Exception {
        Set<String> names = new HashSet<>();
        assertEquals(8, StarterCollection.ids().size());
        for (String id : StarterCollection.ids()) {
            ConfigurationNode definition = node("companions/" + id + ".toml");
            ConfigurationNode icon = definition.node("display", "item");
            assertTrue(names.add(icon.node("name").getString()), id);
            assertFalse(definition.node("exclusive").getBoolean(), id);
            assertFalse(definition.node("appearance", "marker").getBoolean(), id);
            assertTrue(definition.node("overlays").virtual(), id);
            assertTrue(icon.node("custom-model-data").virtual(), id);
            if (icon.node("type").getString("material").equals("skull")) {
                assertEquals("player", icon.node("source").getString(), id);
                assertEquals("{player}", icon.node("value").getString(), id);
            } else {
                assertNotNull(Material.getMaterial(icon.node("material").getString()), id);
            }
            for (ConfigurationNode equipment : definition.node("equipment").childrenMap().values()) {
                assertNotNull(Material.getMaterial(equipment.node("material").getString()), id);
                assertTrue(equipment.node("custom-model-data").virtual(), id);
                assertTrue(equipment.node("texture").virtual()
                        || equipment.node("texture").getString().equals("owner_head"), id);
            }
        }
    }

    @Test
    void everyStarterAnimationCanBeConstructedFromItsBundledParameters() throws Exception {
        AnimationRegistry registry = BuiltinAnimationRegistry.createDefaults();
        AnimationsFeature.register(registry);
        Map<String, AnimationChannel> channels = Map.of("movement", AnimationChannel.MOVEMENT,
                "rotation", AnimationChannel.ROTATION, "pose", AnimationChannel.POSE);
        for (String id : StarterCollection.ids()) {
            ConfigurationNode animations = node("companions/" + id + ".toml").node("animations");
            assertFalse(animations.node("movement").childrenList().isEmpty(), id);
            for (Map.Entry<String, AnimationChannel> channel : channels.entrySet()) {
                Set<String> seen = new HashSet<>();
                for (ConfigurationNode animation : animations.node(channel.getKey()).childrenList()) {
                    String animationId = animation.node("id").getString();
                    assertTrue(seen.add(animationId), id);
                    AnimationFactory<?> factory = registry.get(channel.getValue(), animationId);
                    assertNotNull(factory, id + ": " + animationId);
                    assertEquals(channel.getValue(), factory.create(new NodeAnimationData(animationId, animation)).channel(), id);
                }
            }
        }
    }

    @Test
    void survivalBuffsEnableOnlyThreeModestSourceBonusesAndAcceptSpigot() throws Exception {
        ConfigurationNode root = node("features/buffs/config.toml");
        assertDoesNotThrow(() -> BuffPlatformSupport.validateNode(root, false));
        Configuration configuration = root.get(Configuration.class);
        assertNotNull(configuration);
        assertEquals(Set.of("copper", "warrior", "scout"), configuration.companions().keySet());
        for (Map.Entry<String, Companions> entry : configuration.companions().entrySet()) {
            Vanilla vanilla = entry.getValue().vanilla();
            Buff expected = switch (entry.getKey()) {
                case "copper" -> vanilla.experienceBlock();
                case "warrior" -> vanilla.experienceMob();
                case "scout" -> vanilla.experienceFishing();
                default -> throw new AssertionError(entry.getKey());
            };
            for (Buff buff : List.of(vanilla.experience(), vanilla.experienceBlock(), vanilla.experienceMob(),
                    vanilla.experienceFurnace(), vanilla.experienceFishing(), vanilla.mobLoot(),
                    vanilla.blockDrops(), vanilla.fishingLoot())) {
                assertEquals(buff == expected, buff.enabled(), entry.getKey());
            }
            assertEquals(Operation.ADD_PERCENT, expected.operation());
            assertEquals(10, expected.value());
            assertEquals(0, expected.cooldown());
            assertFalse(expected.feedBack().actionBar().enabled());
            assertFalse(expected.feedBack().sound().enabled());
        }
    }

    @Test
    void starterEffectsStayAtTierOneWithoutStackingAndPreserveProgressionTargets() throws Exception {
        ConfigurationNode effects = node("features/effects/config.toml");
        assertEquals(Set.of("bee", "myself", "rainbow", "mushroom"), effects.node("companions").childrenMap().keySet());
        Set<Integer> speedPriorities = new HashSet<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> companion : effects.node("companions").childrenMap().entrySet()) {
            assertEquals(1, companion.getValue().childrenList().size());
            ConfigurationNode effect = companion.getValue().childrenList().get(0);
            assertEquals(0, effect.node("amplifier").getInt());
            assertEquals("HIGHEST", effect.node("stack-mode").getString());
            if (companion.getKey().equals("mushroom")) {
                assertEquals("NIGHT_VISION", effect.node("effect").getString());
            } else {
                assertEquals("SPEED", effect.node("effect").getString());
                assertTrue(speedPriorities.add(effect.node("priority").getInt()));
            }
        }
        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : List.of("features/attributes/config.toml", "features/effects/config.toml",
                "features/buffs/config.toml", "features/progression/config.toml")) {
            sources.put(path, resource(path));
        }
        ContentSnapshot content = new ContentSnapshot(sources);
        ProgressionDefinitions progression = ProgressionDefinitions.parse(sources.get("features/progression/config.toml").getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> ProgressionContentPreflight.validate(progression, content, true, true));
    }

    private static ConfigurationNode node(String path) throws Exception {
        return new ContentSnapshot(Map.of(path, resource(path))).node(path, ConfigurationOptions.defaults());
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = StarterResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
