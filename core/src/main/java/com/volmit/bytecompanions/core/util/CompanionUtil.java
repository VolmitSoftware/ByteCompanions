package com.volmit.bytecompanions.core.util;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;

public final class CompanionUtil {

    private CompanionUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static @NotNull AppearanceData cloneWithCustomName(
            final @NotNull AppearanceData appearanceData,
            final @NotNull String customName
    ) {
        return new AppearanceData(
                appearanceData.small(),
                appearanceData.visible(),
                appearanceData.showArms(),
                appearanceData.showBaseplate(),
                appearanceData.marker(),
                customName,
                appearanceData.customNameVisible(),
                appearanceData.scale()
        );
    }

}
