package com.volmit.bytecompanions.api.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteCompanionsProviderTest {
    private ByteCompanionsProvider.Registration current;

    @AfterEach
    void unregister() {
        if (current != null) current.unregister();
    }

    @Test
    void onlyTheCurrentRegistrationCanRemoveTheProvider() {
        ByteCompanions first = api();
        ByteCompanionsProvider.Registration stale = ByteCompanionsProvider.register(first);
        stale.unregister();
        assertFalse(ByteCompanionsProvider.hasInstance());

        ByteCompanions second = api();
        current = ByteCompanionsProvider.register(second);
        stale.unregister();
        assertSame(second, ByteCompanionsProvider.getInstance());
        assertThrows(IllegalStateException.class, () -> ByteCompanionsProvider.register(api()));

        current.unregister();
        current = null;
        assertFalse(ByteCompanionsProvider.hasInstance());
        assertThrows(NullPointerException.class, ByteCompanionsProvider::getInstance);
    }

    @Test
    void registrationPublishesTheInstance() {
        ByteCompanions api = api();
        current = ByteCompanionsProvider.register(api);
        assertTrue(ByteCompanionsProvider.hasInstance());
        assertSame(api, ByteCompanions.getAPI());
    }

    private static ByteCompanions api() {
        return (ByteCompanions) Proxy.newProxyInstance(ByteCompanions.class.getClassLoader(),
                new Class<?>[]{ByteCompanions.class}, (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
