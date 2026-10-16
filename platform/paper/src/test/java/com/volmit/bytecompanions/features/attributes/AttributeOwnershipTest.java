package com.volmit.bytecompanions.features.attributes;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeOwnershipTest {
    @Test
    void removalPreservesAnotherPluginsModifierWithTheSameLocalKey() {
        NamespacedKey ownKey = new NamespacedKey("bytecompanions", "bee_speed");
        AttributeModifier owned = new AttributeModifier(ownKey, 0.1,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
        AttributeModifier external = new AttributeModifier(new NamespacedKey("other", "bee_speed"), 0.2,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
        List<AttributeModifier> removed = new ArrayList<>();
        AttributeInstance instance = AttributeInstance.class.cast(Proxy.newProxyInstance(
                AttributeInstance.class.getClassLoader(), new Class<?>[]{AttributeInstance.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getModifiers")) {
                        return List.of(owned, external);
                    }
                    if (method.getName().equals("removeModifier")) {
                        removed.add((AttributeModifier) arguments[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
        AttributesFeature.removeModifier(instance, ownKey);
        assertEquals(List.of(owned), removed);
    }
}
