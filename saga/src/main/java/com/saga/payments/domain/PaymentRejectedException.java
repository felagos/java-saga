package com.saga.payments.domain;

public class PaymentRejectedException extends RuntimeException {

    public PaymentRejectedException(String customerId) {
        super("Payment gateway rejected the charge for customer " + customerId);
    }
}
