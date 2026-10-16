package com.volmit.bytecompanions.api.companion.data;

import org.jetbrains.annotations.Nullable;

public record AppearanceData(
        boolean small,
        boolean visible,
        boolean showArms,
        boolean showBaseplate,
        boolean marker,
        @Nullable String customName,
        boolean customNameVisible,
        double scale
) {

}
