package com.volmit.bytecompanions.api.util.response.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.util.response.Response;

public interface ResponseContext<V, R> extends Response<R> {

    @Contract(value = "_ -> new", pure = true)
    static <V, R> @NotNull ResponseContext<V, R> failure(final @NotNull R result) {
        return new FailureResponseContext<>(result);
    }

    @Contract(value = "_,_ -> new", pure = true)
    static <V, R> @NotNull ResponseContext<V, R> success(final @NotNull V value, final @NotNull R result) {
        return new SuccessResponseContext<>(value, result);
    }

    V value();

}
