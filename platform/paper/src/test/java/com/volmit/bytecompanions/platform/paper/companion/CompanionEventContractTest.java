package com.volmit.bytecompanions.platform.paper.companion;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.features.attributes.listener.MechanicListeners;
import com.volmit.bytecompanions.features.buffs.listener.BuffCacheListener;
import com.volmit.bytecompanions.features.particles.listener.CompanionListener;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionEventContractTest {
    private static final Class<?> EFFECTS_LISTENER = com.volmit.bytecompanions.features.effects.listener.MechanicListeners.class;

    @Test
    void gameplayEffectsRunOnlyAfterAcceptedEquipEvents() throws ReflectiveOperationException {
        for (Class<?> listener : List.of(MechanicListeners.class, BuffCacheListener.class,
                EFFECTS_LISTENER)) {
            verify(listener, "onCompanionEquipEvent", CompanionEquipEvent.class);
        }
    }

    @Test
    void cancelledUnequipCannotRemoveModifiersOrBuffsAndParticlesRemainSaved() throws ReflectiveOperationException {
        for (Class<?> listener : List.of(MechanicListeners.class, BuffCacheListener.class,
                EFFECTS_LISTENER)) {
            verify(listener, "onCompanionUnequipEvent", CompanionUnequipEvent.class);
        }
        assertTrue(Arrays.stream(CompanionListener.class.getMethods()).noneMatch(method ->
                method.isAnnotationPresent(EventHandler.class)
                        && Arrays.asList(method.getParameterTypes()).contains(CompanionUnequipEvent.class)));
    }

    private static void verify(Class<?> listener, String name, Class<?> event) throws ReflectiveOperationException {
        EventHandler handler = listener.getMethod(name, event).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority(), listener.getName());
        assertTrue(handler.ignoreCancelled(), listener.getName());
    }
}
