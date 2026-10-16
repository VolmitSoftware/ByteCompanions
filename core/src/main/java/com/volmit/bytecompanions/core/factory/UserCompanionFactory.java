package com.volmit.bytecompanions.core.factory;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.data.OwnerData;
import com.volmit.bytecompanions.core.companion.DummyCompanion;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class UserCompanionFactory {

    public @NotNull DefaultCompanionPlayer create(
            final @NotNull UserView userView,
            final @NotNull CompanionTypeRegistry typeRegistry
    ) {
        final Collection<String> companions = userView.companionTypeIds();
        final Map<String, Companion> companionMap = new HashMap<>();
        final OwnerData ownerData = new OwnerData(userView.playerId(), userView.playerName());

        if (companions != null && !companions.isEmpty()) {
            companions.stream()
                    .map(typeRegistry::getOrNull)
                    .filter(Objects::nonNull)
                    .forEach(companionType ->
                            companionMap.put(companionType.id(), new DummyCompanion(ownerData, companionType))
                    );
        }

        return new DefaultCompanionPlayer(ownerData, companionMap);
    }

}
