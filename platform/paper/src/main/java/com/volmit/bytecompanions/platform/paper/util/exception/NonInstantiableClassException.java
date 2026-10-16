package com.volmit.bytecompanions.platform.paper.util.exception;

public final class NonInstantiableClassException extends UnsupportedOperationException {

    public NonInstantiableClassException() {
        super("This class cannot be instantiated.");
    }
}