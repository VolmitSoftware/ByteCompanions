package com.volmit.bytecompanions.internal.itemblueprint;

public enum BlueprintResult {

    SUCCESS,
    MISSING_FIELD,
    UNKNOWN_TYPE,
    INVALID_VALUE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

}
