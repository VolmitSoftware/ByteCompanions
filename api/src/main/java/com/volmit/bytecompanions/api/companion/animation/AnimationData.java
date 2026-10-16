
package com.volmit.bytecompanions.api.companion.animation;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.ConfigurationData;

public interface AnimationData extends ConfigurationData {

    @NotNull
    String id();

    @NotNull
    AnimationData section(final @NotNull String path);

}