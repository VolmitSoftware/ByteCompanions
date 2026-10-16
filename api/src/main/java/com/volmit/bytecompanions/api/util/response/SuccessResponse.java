package com.volmit.bytecompanions.api.util.response;

import java.util.Objects;

record SuccessResponse<R>(R result) implements Response<R> {

    SuccessResponse {
        Objects.requireNonNull(result);
    }

    @Override
    public boolean success() {
        return true;
    }

}
