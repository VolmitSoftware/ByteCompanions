package com.volmit.bytecompanions.api;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.CompanionModel;

public interface ConfigurationSettings {

    @NotNull String serializer();

    int maxTotalPerPlayer();

    int maxTotalPerPlayer(final @NotNull CompanionModel companionModel);

    boolean autoLoadCompanions();

    boolean dispatchInteractEvent();

    boolean openDashboardOnInteract();

    int updateInterval();

    int viewerRefreshDelay();

    int viewDistance();

}
