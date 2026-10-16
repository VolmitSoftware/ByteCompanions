package com.volmit.bytecompanions.providers.storage.sqlcommon;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface SQLFunction<T, R> {

    R apply(final @NotNull T t) throws Exception;

}
