package com.volmit.bytecompanions.platform.paper.companion;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.platform.paper.component.adapter.ComponentSerializerAdapter;
import com.volmit.bytecompanions.spi.companions.NmsCompanionEntityFactory;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.animation.Animations;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.platform.paper.companion.animations.BuiltinAnimationRegistry;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCompanionFactoryTest {

    @Test
    void privatePreviewRestrictsViewerBeforeAnySpawn() {
        PreviewFixture fixture = new PreviewFixture();
        fixture.factory.createPreview(fixture.player, fixture.type);
        assertEquals(List.of("restrict", "spawn"), fixture.calls);
        fixture.calls.clear();
        fixture.factory.create(fixture.player, fixture.type);
        assertEquals(List.of("spawn"), fixture.calls);
    }

    @Test
    void failedPreviewSpawnClearsAnimationsAndDestroysPartialEntity() {
        PreviewFixture fixture = new PreviewFixture();
        fixture.failSpawn = true;
        assertThrows(IllegalStateException.class, () -> fixture.factory.createPreview(fixture.player, fixture.type));
        assertEquals(List.of("restrict", "spawn", "clear", "destroy"), fixture.calls);
    }

    @Test
    void disabledParticlesSkipAllBuiltInsWithoutLookingUpUnregisteredFactories() throws SerializationException {
        AnimationRegistry registry = BuiltinAnimationRegistry.createEmpty();
        registry.register(AnimationChannel.POST_TRACKING, "custom_trail", data -> new TestAnimation(AnimationChannel.POST_TRACKING));
        registry.register(AnimationChannel.MOVEMENT, "orb_effect", data -> new TestAnimation(AnimationChannel.MOVEMENT));
        Map<String, AnimationData> postTracking = new LinkedHashMap<>();
        for (String id : ParticleEffectCatalog.factories().keySet()) {
            postTracking.put(id, data());
        }
        postTracking.put("custom_trail", data());
        List<String> applied = new ArrayList<>();

        factory(false, registry).applyAnimations(entity(applied), Map.of(
                AnimationChannel.POST_TRACKING, postTracking,
                AnimationChannel.MOVEMENT, Map.of("orb_effect", data())));

        assertEquals(2, applied.size());
        assertEquals(List.of("MOVEMENT:orb_effect", "POST_TRACKING:custom_trail"), applied.stream().sorted().toList());
    }

    @Test
    void enabledParticlesKeepDefinitionAnimations() throws SerializationException {
        AnimationRegistry registry = BuiltinAnimationRegistry.createEmpty();
        registry.register(AnimationChannel.POST_TRACKING, "orb_effect", data -> new TestAnimation(AnimationChannel.POST_TRACKING));
        List<String> applied = new ArrayList<>();

        factory(true, registry).applyAnimations(entity(applied), Map.of(
                AnimationChannel.POST_TRACKING, Map.of("orb_effect", data())));

        assertEquals(List.of("POST_TRACKING:orb_effect"), applied);
    }

    @Test
    void disabledParticlesDoNotHideInvalidCustomAnimationIds() throws SerializationException {
        DefaultCompanionFactory factory = factory(false, BuiltinAnimationRegistry.createEmpty());
        assertThrows(IllegalArgumentException.class, () -> factory.applyAnimations(entity(new ArrayList<>()), Map.of(
                AnimationChannel.POST_TRACKING, Map.of("unknown_custom_effect", data()))));
    }

    private static DefaultCompanionFactory factory(boolean particles, AnimationRegistry registry) throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        node.node("features", "particles").set(particles);
        Configuration configuration = node.get(Configuration.class);
        return new DefaultCompanionFactory(null, null, registry, configuration);
    }

    private static NmsCapableCompanionEntity entity(List<String> applied) {
        Animations animations = Animations.class.cast(Proxy.newProxyInstance(Animations.class.getClassLoader(),
                new Class<?>[]{Animations.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("add")) {
                        Animation<?> animation = (Animation<?>) arguments[1];
                        applied.add(animation.channel().name() + ":" + arguments[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected animation operation: " + method.getName());
                }));
        return NmsCapableCompanionEntity.class.cast(Proxy.newProxyInstance(NmsCapableCompanionEntity.class.getClassLoader(),
                new Class<?>[]{NmsCapableCompanionEntity.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("animations")) {
                        return animations;
                    }
                    throw new AssertionError("Unexpected entity operation: " + method.getName());
                }));
    }

    private static AnimationData data() {
        return AnimationData.class.cast(Proxy.newProxyInstance(AnimationData.class.getClassLoader(),
                new Class<?>[]{AnimationData.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Unexpected animation parameter lookup: " + method.getName());
                }));
    }

    private record TestAnimation(AnimationChannel channel) implements Animation<Void> {
        @Override
        public Void tick(AnimationContext context) {
            return null;
        }
    }

    private static final class PreviewFixture {
        private final List<String> calls = new ArrayList<>();
        private boolean failSpawn;
        private final UUID id = UUID.randomUUID();
        private final Player player = proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> this.id;
            case "getName" -> "Editor";
            default -> throw new AssertionError(method);
        });
        private final CompanionType type = proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "appearance" -> new AppearanceData(true, true, true, false, true, "Preview", true, 1);
            case "model" -> CompanionModel.values()[0];
            case "animations", "equipment", "overlays" -> Map.of();
            default -> throw new AssertionError(method);
        });
        private final Animations animations = proxy(Animations.class, (method, arguments) -> {
            if (!method.equals("clear")) throw new AssertionError(method);
            this.calls.add("clear");
            return null;
        });
        private final NmsCapableCompanionEntity entity = proxy(NmsCapableCompanionEntity.class, (method, arguments) -> switch (method) {
            case "restrictViewer" -> {
                assertSame(this.player, arguments[0]);
                this.calls.add("restrict");
                yield null;
            }
            case "spawn", "destroy" -> {
                this.calls.add(method);
                if (method.equals("spawn") && this.failSpawn) throw new IllegalStateException("Partial spawn");
                yield null;
            }
            case "animations" -> this.animations;
            default -> throw new AssertionError(method);
        });
        private final CompanionAnchor anchor = proxy(CompanionAnchor.class, (method, arguments) -> {
            throw new AssertionError(method);
        });
        private final NmsCompanionEntityFactory nativeFactory = proxy(NmsCompanionEntityFactory.class, (method, arguments) -> switch (method) {
            case "createPlayerAnchor" -> this.anchor;
            case "create" -> this.entity;
            default -> throw new AssertionError(method);
        });
        private final ComponentSerializerAdapter serializer = proxy(ComponentSerializerAdapter.class, (method, arguments) -> switch (method) {
            case "deserialize" -> Component.text((String) arguments[0]);
            default -> throw new AssertionError(method);
        });
        private final DefaultCompanionFactory factory = new DefaultCompanionFactory(this.serializer, this.nativeFactory,
                BuiltinAnimationRegistry.createEmpty(), new Configuration());
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)));
    }
}
