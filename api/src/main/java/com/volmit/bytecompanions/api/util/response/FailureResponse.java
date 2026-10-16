package com.volmit.bytecompanions.api.util.response;

import java.util.Objects;

record FailureResponse<R>(R result) implements Response<R> {

    FailureResponse {
        Objects.requireNonNull(result);
    }

    @Override
    public boolean success() {
        return false;
    }

}
