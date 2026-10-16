package com.volmit.bytecompanions.api.util.response;

import org.jetbrains.annotations.NotNull;

public interface Response<R> {

    static <R> @NotNull Response<R> failure(final @NotNull R result) {
        return new FailureResponse<>(result);
    }

    static <R> @NotNull Response<R> success(final @NotNull R result) {
        return new SuccessResponse<>(result);
    }

    @NotNull R result();

    boolean success();

}
