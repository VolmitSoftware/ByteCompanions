package com.volmit.bytecompanions.platform.paper.storage;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.manager.DefaultUserManager;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;
import com.volmit.bytecompanions.platform.paper.manager.CompanionPlayerManager;
import com.volmit.bytecompanions.platform.paper.messenger.MessageDispatcher;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStorageReadinessTest {
    @Test
    void pendingOrFailedLoadCannotCreateAnEmptyRecordOrSaveOverStoredChoices() {
        CompanionPlayerCache cache = new CompanionPlayerCache();
        DefaultUserManager users = new DefaultUserManager(cache, null);
        LanguageService languages = new LanguageService(new File("."), Logger.getAnonymousLogger());
        CompanionPlayerManager manager = new CompanionPlayerManager(null, users, null,
                new MessageDispatcher(languages, () -> false), null, null, null);
        List<Component> messages = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = Player.class.cast(Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> true;
                    case "spigot" -> null;
                    case "sendMessage", "sendRichMessage" -> {
                        messages.add(arguments[0] instanceof Component component ? component : Component.text((String) arguments[0]));
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected player call: " + method.getName());
                }));
        CompanionType type = CompanionType.class.cast(Proxy.newProxyInstance(CompanionType.class.getClassLoader(),
                new Class<?>[]{CompanionType.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "id" -> "bee";
                    case "animations" -> Map.of(AnimationChannel.TRACKING, Map.of());
                    default -> throw new AssertionError("Unexpected companion call: " + method.getName());
                }));
        assertFalse(manager.canSpawn(player));
        manager.spawn(player, type);
        assertTrue(cache.all().isEmpty());
        assertEquals(2, messages.size());
    }
}
