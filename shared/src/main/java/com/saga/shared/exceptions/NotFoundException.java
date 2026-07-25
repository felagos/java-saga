package com.saga.shared.exceptions;

/** Base type for "no aggregate with this id" domain failures, so web layers can map them to 404 in one place. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
