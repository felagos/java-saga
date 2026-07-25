package com.saga.checkout.exceptions;

/** Order/payment creation failed after stock was already reserved (phase 1, before rollback). */
public class CheckoutInitiationException extends RuntimeException {

    public CheckoutInitiationException(Throwable cause) {
        super("Checkout initiation failed: " + cause.getMessage(), cause);
    }
}
